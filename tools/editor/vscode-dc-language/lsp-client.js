const { spawn, execFile } = require("node:child_process");
const { promisify } = require("node:util");

async function findJava(candidates) {
  for (const command of [...new Set(candidates)]) {
    try {
      const output = await promisify(execFile)(command, ["-version"], { timeout: 3000, windowsHide: true });
      const match = /(?:openjdk|java) version "(\d+)(?:\.(\d+))?/.exec(output.stdout + output.stderr);
      if (match && Number(match[1] === "1" ? match[2] : match[1]) >= 17) return command;
    } catch (_) {}
  }
  throw new Error("Java 17+ was not found in the configured executable, JAVA_HOME or PATH");
}
const { EventEmitter } = require("node:events");

class LspClient extends EventEmitter {
  constructor(command, args, options = {}) {
    super();
    this.command = command;
    this.args = args;
    this.options = options;
    this.pending = new Map();
    this.nextId = 1;
    this.buffer = Buffer.alloc(0);
    this.closed = false;
  }

  start(rootUri, initializationOptions = {}) {
    if (this.ready) return this.ready;
    this.ready = this.initialize(rootUri, initializationOptions);
    return this.ready;
  }

  async initialize(rootUri, initializationOptions) {
    const command = this.options.javaCandidates ? await findJava(this.options.javaCandidates) : this.command;
    if (this.closed) throw new Error("DC language server stopped");
    this.process = spawn(command, this.args, { stdio: ["pipe", "pipe", "pipe"], windowsHide: true });
    this.process.stdout.on("data", (chunk) => {
      try { this.receive(chunk); } catch (error) { this.fail(error); this.process.kill(); }
    });
    this.process.stderr.on("data", (data) => this.emit("log", data.toString("utf8")));
    this.process.on("error", (error) => this.fail(error));
    this.process.on("exit", (code, signal) => {
      if (!this.closed) this.fail(new Error(`DC language server exited (${code ?? signal})`));
      else this.rejectPending(new Error("DC language server stopped"));
      this.emit("exit", code);
    });
    this.process.stdin.on("error", (error) => { if (!this.closed) this.fail(error); });
    const result = await this.rawRequest("initialize", {
      processId: process.pid, rootUri: rootUri || null,
      capabilities: { general: { positionEncodings: ["utf-16"] }, textDocument: {
        completion: { completionItem: { snippetSupport: false } },
        hover: { contentFormat: ["markdown", "plaintext"] },
        publishDiagnostics: { versionSupport: true }, synchronization: { didSave: false }
      } }, initializationOptions
    });
    this.send({ jsonrpc: "2.0", method: "initialized", params: {} });
    return result;
  }

  receive(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (true) {
      const separator = this.buffer.indexOf("\r\n\r\n");
      if (separator < 0) {
        if (this.buffer.length > 8192) throw new Error("Invalid language server frame header");
        return;
      }
      const header = this.buffer.subarray(0, separator).toString("ascii");
      const match = /^Content-Length:\s*(\d+)\s*$/im.exec(header);
      if (!match) throw new Error("Missing language server Content-Length");
      const length = Number(match[1]);
      if (length > 16 * 1024 * 1024) throw new Error("Language server frame is too large");
      if (this.buffer.length < separator + 4 + length) return;
      const message = JSON.parse(this.buffer.subarray(separator + 4, separator + 4 + length).toString("utf8"));
      this.buffer = this.buffer.subarray(separator + 4 + length);
      if (message.id !== undefined && !message.method) {
        const pending = this.pending.get(message.id);
        if (pending) {
          this.pending.delete(message.id);
          clearTimeout(pending.timer);
          pending.cancel?.dispose();
          if (message.error) pending.reject(new Error(message.error.message || "Language server request failed"));
          else pending.resolve(message.result);
        }
      } else if (message.method && message.id === undefined) {
        this.emit("notification", message.method, message.params);
      } else if (message.method) {
        this.send({ jsonrpc: "2.0", id: message.id, error: { code: -32601, message: "Client method not supported" } });
      }
    }
  }

  send(message) {
    if (this.failure) throw this.failure;
    if (!this.process?.stdin.writable) throw new Error("Language server input is closed");
    const data = Buffer.from(JSON.stringify(message), "utf8");
    this.process.stdin.write(Buffer.concat([Buffer.from(`Content-Length: ${data.length}\r\n\r\n`, "ascii"), data]));
  }

  rawRequest(method, params, token, timeout = this.options.timeout || 10000) {
    if (token?.isCancellationRequested) return Promise.reject(new Error("Request cancelled"));
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const cancel = (message) => {
        const pending = this.pending.get(id);
        if (!pending) return;
        this.pending.delete(id);
        clearTimeout(pending.timer);
        pending.cancel?.dispose();
        try { this.send({ jsonrpc: "2.0", method: "$/cancelRequest", params: { id } }); } catch (_) { }
        reject(new Error(message));
      };
      const pending = { resolve, reject, timer: setTimeout(() => cancel(`Language server timed out: ${method}`), timeout) };
      this.pending.set(id, pending);
      pending.cancel = token?.onCancellationRequested(() => cancel("Request cancelled"));
      try { this.send({ jsonrpc: "2.0", id, method, params }); }
      catch (error) { this.pending.delete(id); clearTimeout(pending.timer); pending.cancel?.dispose(); reject(error); }
    });
  }

  async request(method, params, token) {
    await this.ready;
    if (this.closed) throw new Error("DC language server stopped");
    return this.rawRequest(method, params, token);
  }

  async notify(method, params) {
    await this.ready;
    if (this.closed) return;
    this.send({ jsonrpc: "2.0", method, params });
  }

  rejectPending(error) {
    for (const item of this.pending.values()) { clearTimeout(item.timer); item.cancel?.dispose(); item.reject(error); }
    this.pending.clear();
  }

  fail(error) {
    if (this.failure) return;
    this.failure = error;
    this.rejectPending(error);
    this.emit("failure", error);
  }

  stop() {
    if (!this.stopping) this.stopping = this.close();
    return this.stopping;
  }

  async close() {
    if (this.closed) return;
    this.closed = true;
    if (!this.process) return;
    let startupTimer;
    try {
      await Promise.race([this.ready, new Promise((_, reject) => { startupTimer = setTimeout(() => reject(new Error("Startup interrupted")), 1000); })]).finally(() => clearTimeout(startupTimer));
      await this.rawRequest("shutdown", null, undefined, 1000);
      this.send({ jsonrpc: "2.0", method: "exit" });
    } catch (_) { }
    this.process.stdin.end();
    await new Promise((resolve) => {
      if (this.process.exitCode !== null) return resolve();
      const timer = setTimeout(() => { this.process.kill("SIGKILL"); resolve(); }, 1000);
      this.process.once("exit", () => { clearTimeout(timer); resolve(); });
    });
    this.rejectPending(new Error("DC language server stopped"));
  }
}

module.exports = { LspClient, findJava };
