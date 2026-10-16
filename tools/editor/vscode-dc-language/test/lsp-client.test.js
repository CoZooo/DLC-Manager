const { test } = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs");
const { once } = require("node:events");
const { LspClient } = require("../lsp-client");
const fake = () => new LspClient(process.execPath, [path.join(__dirname, "fake-server.cjs")], { timeout: 250 });

test("framed UTF-8 JSON-RPC survives split headers and closes its subprocess", async () => {
  const client = fake();
  try {
    await client.start("file:///workspace");
    assert.deepEqual(await client.request("echo", { value: "中文😀" }), { value: "中文😀" });
    const notification = once(client, "notification");
    await client.notify("textDocument/didOpen", { textDocument: { uri: "file:///example.dc", version: 7, text: "key = 1" } });
    const [method, params] = await notification;
    assert.equal(method, "textDocument/publishDiagnostics");
    assert.equal(params.version, 7);
  } finally { await client.stop(); }
  assert.equal(client.process.exitCode, 0);
  assert.equal(client.pending.size, 0);
});

test("timeouts remove pending requests without killing a responsive server", async () => {
  const client = fake();
  try {
    await client.start();
    await assert.rejects(client.request("neverReply", {}), /timed out/);
    assert.equal(client.pending.size, 0);
    assert.equal(await client.request("echo", 4), 4);
  } finally { await client.stop(); }
});

test("cancelled requests are removed while later requests still succeed", async () => {
  const client = fake();
  try {
    await client.start();
    let cancel;
    const token = { isCancellationRequested: false, onCancellationRequested(callback) { cancel = callback; return { dispose() {} }; } };
    const waiting = client.request("neverReply", {}, token);
    await new Promise(setImmediate);
    cancel();
    await assert.rejects(waiting, /cancelled/);
    assert.equal(client.pending.size, 0);
    assert.equal(await client.request("echo", 9), 9);
  } finally { const first = client.stop(); assert.equal(first, client.stop()); await first; }
});

test("subprocess failures reject waiting callers", async () => {
  const client = fake();
  try {
    await client.start();
    await assert.rejects(client.request("crash", {}), /exited/);
  } finally { await client.stop(); }
});

const jar = process.env.DC_LANGUAGE_SERVER_JAR || path.resolve(__dirname, "../../../dclanguage/lsp/build/libs/dclanguage-lsp.jar");
test("default language service accepts arbitrary fields even in application-looking paths", { skip: !fs.existsSync(jar) }, async () => {
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : "java";
  const client = new LspClient(java, ["-jar", jar, "--stdio"]);
  const uri = "file:///workspace/DLC/info/test.dc";
  try {
    await client.start("file:///workspace");
    const diagnosed = once(client, "notification");
    await client.notify("textDocument/didOpen", { textDocument: { uri, languageId: "dc", version: 1, text: '[basic]\nidentifier = "中文😀"\nmode = "invalid"\n' } });
    const [, diagnostic] = await diagnosed;
    assert.equal(diagnostic.uri, uri);
    assert.deepEqual(diagnostic.diagnostics, []);
    const items = await client.request("textDocument/completion", { textDocument: { uri }, position: { line: 3, character: 0 } });
    assert.deepEqual(Array.isArray(items) ? items : items.items, []);
    const hover = await client.request("textDocument/hover", { textDocument: { uri }, position: { line: 1, character: 3 } });
    assert.equal(hover, null);
    const edits = await client.request("textDocument/formatting", { textDocument: { uri }, options: { tabSize: 4, insertSpaces: true } });
    assert.ok(Array.isArray(edits));
    const invalid = once(client, "notification");
    await client.notify("textDocument/didChange", { textDocument: { uri, version: 2 }, contentChanges: [{ text: '[custom]\nvalue = "unterminated\n' }] });
    const [, syntax] = await invalid;
    assert.ok(syntax.diagnostics.some((issue) => issue.code === "syntax"));
    await client.notify("textDocument/didClose", { textDocument: { uri } });
  } finally { await client.stop(); }
});


test("explicit external definitions enable application validation, completion and hover", { skip: !fs.existsSync(jar) }, async () => {
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : "java";
  const client = new LspClient(java, ["-jar", jar, "--stdio"]);
  const uri = "file:///workspace/settings.dc";
  const definition = { formatVersion: 1, profiles: [{ id: "settings", filePatterns: ["**/settings.dc"], categories: [{
    path: ["options"], fields: [
      { name: "mode", type: "string", enumValues: ["fast", "safe"], description: "External mode documentation" },
      { name: "enabled", type: "boolean" }
    ]
  }] }] };
  try {
    await client.start("file:///workspace", { definition });
    const diagnosed = once(client, "notification");
    await client.notify("textDocument/didOpen", { textDocument: { uri, languageId: "dc", version: 1, text: '[options]\nmode = "invalid"\n' } });
    const [, diagnostic] = await diagnosed;
    assert.ok(diagnostic.diagnostics.length > 0);
    const result = await client.request("textDocument/completion", { textDocument: { uri }, position: { line: 2, character: 0 } });
    assert.ok((Array.isArray(result) ? result : result.items).some((item) => item.label === "enabled"));
    const hover = await client.request("textDocument/hover", { textDocument: { uri }, position: { line: 1, character: 2 } });
    assert.match(JSON.stringify(hover.contents), /External mode documentation/);
  } finally { await client.stop(); }
});
