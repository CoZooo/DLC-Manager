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
test("real bundled language service reads UTF-16, diagnoses, completes, hovers and formats", { skip: !fs.existsSync(jar) }, async () => {
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : "java";
  const client = new LspClient(java, ["-jar", jar, "--stdio"]);
  const uri = "file:///workspace/DLC/info/test.dc";
  try {
    await client.start("file:///workspace", { definition: require("../dlc-definition.json") });
    const diagnosed = once(client, "notification");
    await client.notify("textDocument/didOpen", { textDocument: { uri, languageId: "dc", version: 1, text: '[basic]\nidentifier = "中文😀"\nmode = "invalid"\n' } });
    const [, diagnostic] = await diagnosed;
    assert.equal(diagnostic.uri, uri);
    assert.ok(diagnostic.diagnostics.length > 0);
    const items = await client.request("textDocument/completion", { textDocument: { uri }, position: { line: 3, character: 0 } });
    assert.ok((Array.isArray(items) ? items : items.items).length > 0);
    const hover = await client.request("textDocument/hover", { textDocument: { uri }, position: { line: 1, character: 3 } });
    assert.ok(hover && hover.contents);
    const edits = await client.request("textDocument/formatting", { textDocument: { uri }, options: { tabSize: 4, insertSpaces: true } });
    assert.ok(Array.isArray(edits));
    await client.notify("textDocument/didChange", { textDocument: { uri, version: 2 }, contentChanges: [{ text: '[basic]\nidentifier = "fixed"\nmode = "hash"\n' }] });
    await client.notify("textDocument/didClose", { textDocument: { uri } });
  } finally { await client.stop(); }
});
