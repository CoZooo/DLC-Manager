const vscode = require("vscode");
const path = require("node:path");
const fs = require("node:fs");
const { LspClient } = require("./lsp-client");
const definition = require("./language-definition.json");
const syntaxTriggers = [...new Set(["categoryOpen", "assignment", "block", "categorySeparator", "comment"].map((name) => Array.from(definition.syntax.tokens[name] || "").at(-1)).filter(Boolean).concat(["&", "#"]))];
let activeClient;

function activate(context) {
  const diagnostics = vscode.languages.createDiagnosticCollection("dc");
  const output = vscode.window.createOutputChannel("DC Language Server");
  const documents = new Map();
  const config = vscode.workspace.getConfiguration("dc");
  const java = config.get("javaPath") || (process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java");
  const jar = context.asAbsolutePath("server/dclanguage-lsp.jar");
  const client = new LspClient(java, ["-jar", jar, "--stdio"], { javaCandidates: config.get("javaPath") ? [java] : [java, "java"] });
  activeClient = client;
  let reported = false;
  const report = (error) => {
    if (client.closed) return;
    output.appendLine(String(error.message || error));
    if (!reported) {
      reported = true;
      vscode.window.showErrorMessage("DC language server could not start. Install Java 17+ or set dc.javaPath; see DC Language Server output.");
    }
  };
  client.on("log", (text) => output.append(text));
  client.on("failure", report);
  client.on("notification", (method, params) => {
    if (method !== "textDocument/publishDiagnostics") return;
    const current = documents.get(params.uri);
    if (!current || (params.version !== undefined && current.version !== params.version)) return;
    diagnostics.set(vscode.Uri.parse(params.uri), (params.diagnostics || []).map((value) => {
      const item = new vscode.Diagnostic(toRange(value.range), value.message,
        ({ 1: vscode.DiagnosticSeverity.Error, 2: vscode.DiagnosticSeverity.Warning, 3: vscode.DiagnosticSeverity.Information, 4: vscode.DiagnosticSeverity.Hint })[value.severity] ?? vscode.DiagnosticSeverity.Error);
      item.source = value.source || "dc";
      item.code = value.code;
      return item;
    }));
  });
  if (!fs.existsSync(jar)) report(new Error("Bundled language server is missing: " + jar));
  const profile = config.get("profile", "auto");
  client.start(vscode.workspace.workspaceFolders?.[0]?.uri.toString(), !profile || profile === "auto" ? {} : { profile }).catch(report);
  const sync = (document) => {
    if (document.languageId !== "dc") return;
    const uri = document.uri.toString();
    const previous = documents.get(uri);
    if (previous?.version === document.version) return;
    const value = { uri, languageId: "dc", version: document.version, text: document.getText() };
    documents.set(uri, { version: value.version });
    const operation = previous
      ? client.notify("textDocument/didChange", { textDocument: { uri, version: value.version }, contentChanges: [{ text: value.text }] })
      : client.notify("textDocument/didOpen", { textDocument: value });
    operation.catch((error) => output.appendLine(error.message));
  };
  const call = async (method, document, params, token) => {
    sync(document);
    const version = document.version;
    try {
      const result = await client.request(method, { textDocument: { uri: document.uri.toString() }, ...params }, token);
      return version === document.version && !token?.isCancellationRequested ? result : undefined;
    } catch (error) {
      if (!token?.isCancellationRequested) output.appendLine(error.message);
      return undefined;
    }
  };
  const selector = { language: "dc" };
  context.subscriptions.push(diagnostics, output,
    vscode.workspace.onDidOpenTextDocument(sync),
    vscode.workspace.onDidChangeTextDocument((event) => sync(event.document)),
    vscode.workspace.onDidCloseTextDocument((document) => {
      const uri = document.uri.toString();
      if (!documents.delete(uri)) return;
      diagnostics.delete(document.uri);
      client.notify("textDocument/didClose", { textDocument: { uri } }).catch(() => {});
    }),
    vscode.languages.registerCompletionItemProvider(selector, {
      async provideCompletionItems(document, position, token) {
        const color = detectColorContext(document, position);
        if (color) return buildColorCompletionItems(color);
        const result = await call("textDocument/completion", document, { position }, token);
        if (!result) return undefined;
        const items = Array.isArray(result) ? result : result.items;
        return new vscode.CompletionList(items.map((value) => {
          const item = new vscode.CompletionItem(value.label, value.kind === undefined ? undefined : value.kind - 1);
          item.detail = value.detail;
          item.sortText = value.sortText;
          item.filterText = value.filterText;
          item.insertText = value.insertText || value.label;
          if (value.documentation) item.documentation = markup(value.documentation);
          if (value.textEdit) { item.range = toRange(value.textEdit.range || value.textEdit.replace); item.insertText = value.textEdit.newText; }
          if (value.additionalTextEdits) item.additionalTextEdits = value.additionalTextEdits.map(toEdit);
          return item;
        }), !!result.isIncomplete);
      }
    }, ...syntaxTriggers),
    vscode.languages.registerHoverProvider(selector, {
      async provideHover(document, position, token) {
        const value = await call("textDocument/hover", document, { position }, token);
        if (!value?.contents) return undefined;
        const contents = Array.isArray(value.contents) ? value.contents.map(markup) : markup(value.contents);
        return new vscode.Hover(contents, value.range ? toRange(value.range) : undefined);
      }
    }),
    vscode.languages.registerDocumentFormattingEditProvider(selector, {
      async provideDocumentFormattingEdits(document, options, token) {
        return (await call("textDocument/formatting", document, { options }, token) || []).map(toEdit);
      }
    }),
    vscode.languages.registerColorProvider(selector, { provideDocumentColors, provideColorPresentations }),
    vscode.commands.registerCommand(PICK_COLOR_COMMAND, runPickColorCommand),
    { dispose() { void client.stop(); } }
  );
  vscode.workspace.textDocuments.forEach(sync);
}

function toRange(value) { return new vscode.Range(value.start.line, value.start.character, value.end.line, value.end.character); }
function toEdit(value) { return new vscode.TextEdit(toRange(value.range), value.newText); }
function markup(value) {
  const text = typeof value === "string" ? value : value.value || "";
  const result = new vscode.MarkdownString();
  if (typeof value === "object" && value.kind === "plaintext") result.appendText(text);
  else result.appendMarkdown(text);
  result.isTrusted = false;
  return result;
}
async function deactivate() { await activeClient?.stop(); activeClient = undefined; }

const PICK_COLOR_COMMAND = "dc.pickHexColor";
const COLOR_TOKEN_RE = /&#([0-9a-fA-F]{6})/g;
const COLOR_TRIGGER_RE = /(&|&#([0-9a-fA-F]{0,6}))$/;
const PRESET_COLORS = [
  { name: "White", hex: "FFFFFF" },
  { name: "Black", hex: "000000" },
  { name: "Red", hex: "FF5555" },
  { name: "Orange", hex: "FFAA00" },
  { name: "Yellow", hex: "FFFF55" },
  { name: "Green", hex: "55FF55" },
  { name: "Aqua", hex: "55FFFF" },
  { name: "Blue", hex: "5555FF" },
  { name: "Purple", hex: "AA00AA" },
  { name: "Gray", hex: "AAAAAA" }
];

function provideDocumentColors(document) {
  const infos = [];
  for (let i = 0; i < document.lineCount; i++) {
    const text = document.lineAt(i).text;
    let match;
    COLOR_TOKEN_RE.lastIndex = 0;
    while ((match = COLOR_TOKEN_RE.exec(text)) !== null) {
      const full = match[0];
      const hex = normalizeHexColor(match[1]);
      if (!hex) {
        continue;
      }
      const start = match.index;
      const end = start + full.length;
      const range = new vscode.Range(i, start, i, end);
      infos.push(new vscode.ColorInformation(range, hexToColor(hex)));
    }
  }
  return infos;
}

function provideColorPresentations(color, context) {
  const hex = colorToHex(color);
  const text = `&#${hex}`;
  const presentation = new vscode.ColorPresentation(text);
  presentation.textEdit = new vscode.TextEdit(context.range, text);
  return [presentation];
}

function detectColorContext(document, position) {
  if (!document || !position) {
    return null;
  }
  const line = document.lineAt(position.line).text;
  const prefix = line.slice(0, position.character);
  const match = prefix.match(COLOR_TRIGGER_RE);
  if (!match) {
    return null;
  }
  const token = match[0];
  const typed = match[2] || "";
  const start = position.character - token.length;
  if (start < 0) {
    return null;
  }
  return {
    token,
    typedHex: typed.toUpperCase(),
    range: new vscode.Range(position.line, start, position.line, position.character)
  };
}

function buildColorCompletionItems(colorContext) {
  const items = [];
  const custom = new vscode.CompletionItem("Pick custom color...", vscode.CompletionItemKind.Function);
  custom.insertText = colorContext.token;
  custom.range = colorContext.range;
  custom.sortText = "000_picker";
  custom.filterText = colorContext.token;
  custom.preselect = true;
  custom.command = {
    title: "Pick custom color",
    command: PICK_COLOR_COMMAND,
    arguments: [colorContext.range]
  };
  items.push(custom);

  for (const preset of PRESET_COLORS) {
    if (!preset || !preset.hex) {
      continue;
    }
    if (colorContext.typedHex && !preset.hex.startsWith(colorContext.typedHex)) {
      continue;
    }
    const item = new vscode.CompletionItem(
      `${preset.name} (&#${preset.hex})`,
      vscode.CompletionItemKind.Color
    );
    item.insertText = `&#${preset.hex}`;
    item.range = colorContext.range;
    item.sortText = `100_${preset.hex}`;
    item.detail = "DC hex color";
    const md = new vscode.MarkdownString();
    md.appendMarkdown(`<span style="color:#${preset.hex};">███</span> \`&#${preset.hex}\``);
    item.documentation = md;
    items.push(item);
  }
  return items;
}

async function runPickColorCommand(rangeArg) {
  const editor = vscode.window.activeTextEditor;
  if (!editor || !editor.document || editor.document.languageId !== "dc") {
    return;
  }
  const range = resolveColorEditRange(editor.document, rangeArg, editor.selection.active);
  const current = (range ? extractHexFromColorToken(editor.document.getText(range)) : "") || "FFFFFF";
  const replaceRange = range || new vscode.Range(editor.selection.active, editor.selection.active);
  const token = `&#${current}`;
  await editor.edit((editBuilder) => {
    editBuilder.replace(replaceRange, token);
  });

  const selectStart = replaceRange.start;
  const selectEnd = replaceRange.start.translate(0, Math.min(3, token.length));
  editor.selection = new vscode.Selection(selectStart, selectEnd);
  editor.revealRange(new vscode.Range(selectStart, selectEnd));

  try {
    await vscode.commands.executeCommand("editor.action.showOrFocusStandaloneColorPicker");
  } catch (_e) {
    const input = await vscode.window.showInputBox({
      prompt: "Input color as RRGGBB, #RRGGBB, or &#RRGGBB",
      value: `#${current}`,
      validateInput(value) {
        return normalizeHexColor(value) ? null : "Invalid color. Use RRGGBB, #RRGGBB, or &#RRGGBB.";
      }
    });
    const hex = normalizeHexColor(input || "");
    if (!hex) {
      return;
    }
    await editor.edit((editBuilder) => {
      editBuilder.replace(
        new vscode.Range(replaceRange.start, replaceRange.start.translate(0, token.length)),
        `&#${hex}`
      );
    });
  }
}

function resolveColorEditRange(document, rangeArg, cursor) {
  if (rangeArg instanceof vscode.Range) {
    return rangeArg;
  }
  const detected = detectColorContext(document, cursor);
  if (detected) {
    return detected.range;
  }
  return null;
}

function extractHexFromColorToken(text) {
  if (!text) {
    return "";
  }
  const match = text.match(/^&#([0-9a-fA-F]{6})$/);
  return match ? match[1].toUpperCase() : "";
}

function normalizeHexColor(input) {
  if (!input) {
    return "";
  }
  const normalized = input.trim().replace(/^&#/, "").replace(/^#/, "");
  if (!/^[0-9a-fA-F]{6}$/.test(normalized)) {
    return "";
  }
  return normalized.toUpperCase();
}

function hexToColor(hex) {
  const value = normalizeHexColor(hex);
  if (!value) {
    return new vscode.Color(1, 1, 1, 1);
  }
  const r = parseInt(value.slice(0, 2), 16) / 255;
  const g = parseInt(value.slice(2, 4), 16) / 255;
  const b = parseInt(value.slice(4, 6), 16) / 255;
  return new vscode.Color(r, g, b, 1);
}

function colorToHex(color) {
  const clamp = (value) => Math.max(0, Math.min(255, Math.round(value * 255)));
  const to2 = (value) => value.toString(16).toUpperCase().padStart(2, "0");
  return `${to2(clamp(color.red))}${to2(clamp(color.green))}${to2(clamp(color.blue))}`;
}


module.exports = { activate, deactivate };
