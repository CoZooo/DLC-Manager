const { test } = require("node:test");
const assert = require("node:assert/strict");
const { EventEmitter } = require("node:events");
const Module = require("node:module");

function harness() {
  const callbacks = {}, providers = {}, edits = [], diagnostics = new Map();
  const doc = { languageId: "dc", uri: { toString: () => "file:///workspace/example.dc" }, version: 1,
    text: '[custom]\nunknown = "kept"\n', getText() { return this.text; }, lineAt(index) { return { text: this.text.split("\n")[index] || "" }; } };
  const disposable = () => ({ dispose() {} });
  const event = (name) => (callback) => { callbacks[name] = callback; return disposable(); };
  class Range { constructor(a, b, c, d) { this.start = { line: a, character: b }; this.end = { line: c, character: d }; } }
  class Diagnostic { constructor(range, message, severity) { Object.assign(this, { range, message, severity }); } }
  class MarkdownString { constructor() { this.value = ""; } appendText(text) { this.value += text; } appendMarkdown(text) { this.value += text; } }
  const vscode = {
    Range, Diagnostic, MarkdownString, DiagnosticSeverity: { Error: 0, Warning: 1, Information: 2, Hint: 3 },
    CompletionItem: class { constructor(label, kind) { Object.assign(this, { label, kind }); } },
    CompletionList: class { constructor(items, isIncomplete) { Object.assign(this, { items, isIncomplete }); } },
    Hover: class { constructor(contents, range) { Object.assign(this, { contents, range }); } },
    TextEdit: class { constructor(range, newText) { Object.assign(this, { range, newText }); } },
    Uri: { parse: (uri) => ({ toString: () => uri }) },
    workspace: { textDocuments: [doc], workspaceFolders: [], getConfiguration: () => ({ get: (_, fallback) => fallback }),
      onDidOpenTextDocument: event("open"), onDidChangeTextDocument: event("change"), onDidCloseTextDocument: event("close"),
      applyEdit: (value) => { edits.push(value); return Promise.resolve(true); } },
    window: { createOutputChannel: () => ({ append() {}, appendLine() {}, dispose() {} }), showErrorMessage() {} },
    commands: { registerCommand: () => disposable() },
    languages: {
      createDiagnosticCollection: () => ({ set(uri, value) { diagnostics.set(uri.toString(), value); }, delete(uri) { diagnostics.delete(uri.toString()); }, dispose() {} }),
      registerCompletionItemProvider: (_, provider) => { providers.completion = provider; return disposable(); },
      registerHoverProvider: (_, provider) => { providers.hover = provider; return disposable(); },
      registerDocumentFormattingEditProvider: (_, provider) => { providers.format = provider; return disposable(); },
      registerColorProvider: (_, provider) => { providers.color = provider; return disposable(); }
    }
  };
  let client;
  class FakeClient extends EventEmitter {
    constructor() { super(); client = this; this.sent = []; }
    start(root, options) { this.options = options; return Promise.resolve({}); }
    notify(method, params) { this.sent.push({ method, params }); return Promise.resolve(); }
    request(method, params) { this.sent.push({ method, params }); return Promise.resolve(this.response); }
    stop() { this.stopped = true; return Promise.resolve(); }
  }
  const original = Module._load;
  Module._load = function(name, parent, main) {
    if (name === "vscode") return vscode;
    if (name === "./lsp-client") return { LspClient: FakeClient };
    if (name === "node:fs" && parent?.filename.endsWith("extension.js")) return { existsSync: () => true };
    return original.call(this, name, parent, main);
  };
  let extension;
  try {
    delete require.cache[require.resolve("../extension")];
    extension = require("../extension");
  } finally { Module._load = original; }
  extension.activate({ subscriptions: [], asAbsolutePath: (path) => path });
  return { extension, client, callbacks, providers, doc, edits, diagnostics };
}

test("opening and typing synchronize unchanged text without correcting user files", async () => {
  const h = harness();
  try {
    assert.deepEqual(h.client.options, { definition: require("../dlc-definition.json") });
    assert.equal(h.client.sent[0].method, "textDocument/didOpen");
    assert.equal(h.client.sent[0].params.textDocument.text, h.doc.text);
    h.doc.text = "malformed [\n  keep my whitespace"; h.doc.version++;
    h.callbacks.change({ document: h.doc });
    assert.equal(h.client.sent.at(-1).params.contentChanges[0].text, h.doc.text);
    assert.deepEqual(h.edits, []);
    h.callbacks.close(h.doc);
    assert.equal(h.client.sent.at(-1).method, "textDocument/didClose");
  } finally { await h.extension.deactivate(); }
  assert.ok(h.client.stopped);
});

test("diagnostics reject stale versions and completion uses arbitrary server fields", async () => {
  const h = harness();
  try {
    h.doc.version = 3; h.callbacks.change({ document: h.doc });
    const diagnostic = { range: { start: { line: 0, character: 0 }, end: { line: 0, character: 1 } }, message: "from shared schema", severity: 2 };
    h.client.emit("notification", "textDocument/publishDiagnostics", { uri: h.doc.uri.toString(), version: 2, diagnostics: [diagnostic] });
    assert.equal(h.diagnostics.size, 0);
    h.client.emit("notification", "textDocument/publishDiagnostics", { uri: h.doc.uri.toString(), version: 3, diagnostics: [diagnostic] });
    assert.equal(h.diagnostics.get(h.doc.uri.toString())[0].message, diagnostic.message);
    h.client.response = [{ label: "from_authoritative_schema", kind: 10, textEdit: { range: diagnostic.range, newText: "new_field = " } }];
    const result = await h.providers.completion.provideCompletionItems(h.doc, { line: 1, character: 0 });
    assert.equal(result.items[0].label, "from_authoritative_schema");
    assert.equal(result.items[0].insertText, "new_field = ");
    assert.ok(h.providers.color);
  } finally { await h.extension.deactivate(); }
});
