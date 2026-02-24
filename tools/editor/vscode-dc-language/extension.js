const vscode = require("vscode");

const SECTION_KEYS = {
  download: [
    "modrinth_projectid",
    "modrinth_versionid",
    "curseforge_projectid",
    "curseforge_fileid",
    "directUrl",
    "priority"
  ],
  basic: [
    "identifier",
    "file_name",
    "type",
    "appliedTarget",
    "isInList",
    "isHidden",
    "AllowEnableByAll",
    "dependencies",
    "conflicts",
    "alarm",
    "available",
    "ShowFrequency",
    "neverShow",
    "isNoticeEnabled",
    "enabled",
    "platform",
    "base",
    "base_new",
    "dlc",
    "dlc_new"
  ],
  dependencies: [],
  DisplayName: [],
  tip: [],
  notice: [],
  Contents: [],
  ContentUrl: [],
  version: ["platform", "base", "base_new", "dlc", "dlc_new"],
  changelog: [],
  file: [],
  add: [],
  del: [],
  move: [],
  modify: [],
  update: [],
  remote: [],
  required: []
};

const SECTION_SNIPPETS = Object.keys(SECTION_KEYS).map((section) => `[${section}]`);
const BOOL_KEYS = new Set([
  "isInList",
  "isHidden",
  "AllowEnableByAll",
  "alarm",
  "enabled",
  "neverShow",
  "isNoticeEnabled"
]);
const ENUM_VALUES = {
  priority: ["modrinth", "curseforge", "directurl"],
  type: ["mod", "resourcepack", "datapack", "tacz_pack", "others", "config"],
  platform: ["universal", "client", "server"],
  available: ["hidden", "en_us", "zh_cn"],
  ShowFrequency: ["once", "perlaunch", "perlaunchmanager", "always"],
  which: ["all", "last", "-1", "0", "[0,2]"],
  override: ["true", "false", "all", "none"]
};

function activate(context) {
  const diagnostics = vscode.languages.createDiagnosticCollection("dc");
  context.subscriptions.push(diagnostics);
  const correctingDocs = new Set();

  const refresh = (document) => {
    if (document && document.languageId === "dc") {
      validateDocument(document, diagnostics);
    }
  };

  vscode.workspace.textDocuments.forEach((doc) => refresh(doc));
  context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(async (doc) => {
    if (!doc || doc.languageId !== "dc") {
      return;
    }
    const uriKey = doc.uri.toString();
    if (correctingDocs.has(uriKey)) {
      refresh(doc);
      return;
    }
    const changed = await autoCorrectMultilineDoubleQuotes(doc, correctingDocs);
    if (!changed) {
      refresh(doc);
    }
  }));
  context.subscriptions.push(vscode.workspace.onDidChangeTextDocument(async (e) => {
    const doc = e.document;
    if (!doc || doc.languageId !== "dc") {
      return;
    }
    if (!doc.isDirty) {
      refresh(doc);
      return;
    }
    const uriKey = doc.uri.toString();
    if (correctingDocs.has(uriKey)) {
      refresh(doc);
      return;
    }

    const changed = await autoCorrectMultilineDoubleQuotes(doc, correctingDocs);
    if (!changed) {
      refresh(doc);
    }
  }));
  context.subscriptions.push(vscode.workspace.onDidCloseTextDocument((doc) => diagnostics.delete(doc.uri)));

  const provider = vscode.languages.registerCompletionItemProvider(
    { language: "dc", scheme: "file" },
    {
      provideCompletionItems(document, position) {
        return complete(document, position);
      }
    },
    "[",
    "=",
    ":"
  );
  context.subscriptions.push(provider);
}

function deactivate() {}

async function autoCorrectMultilineDoubleQuotes(document, correctingDocs) {
  const multilineFixes = collectMultilineQuoteFixes(document);
  const singleLineTripleFixes = collectSingleLineTripleQuoteFixes(document);
  if (multilineFixes.length === 0 && singleLineTripleFixes.length === 0) {
    return false;
  }

  const edit = new vscode.WorkspaceEdit();
  for (const fix of multilineFixes) {
    const lineText = document.lineAt(fix.line).text;
    const start = fix.column;
    const replaceLen = fix.length || 1;
    const end = Math.min(fix.column + replaceLen, lineText.length);
    edit.replace(document.uri, new vscode.Range(fix.line, start, fix.line, end), "\"\"\"");
  }
  for (const fix of singleLineTripleFixes) {
    const lineText = document.lineAt(fix.line).text;
    edit.replace(document.uri, new vscode.Range(fix.line, 0, fix.line, lineText.length), fix.replacement);
  }

  const uriKey = document.uri.toString();
  correctingDocs.add(uriKey);
  try {
    await vscode.workspace.applyEdit(edit);
  } finally {
    setTimeout(() => correctingDocs.delete(uriKey), 0);
  }
  return true;
}

function collectMultilineQuoteFixes(document) {
  const fixes = [];
  let pendingSingleQuoteOpen = null;

  for (let i = 0; i < document.lineCount; i++) {
    const line = document.lineAt(i).text;
    if (line.includes("\"\"\"")) {
      pendingSingleQuoteOpen = null;
      continue;
    }

    if (pendingSingleQuoteOpen) {
      const closeIndex = findUnescapedQuote(line, 0);
      if (closeIndex >= 0) {
        fixes.push({ line: pendingSingleQuoteOpen.line, column: pendingSingleQuoteOpen.column, length: 1 });
        fixes.push({ line: i, column: closeIndex, length: 1 });
        pendingSingleQuoteOpen = null;
      }
      continue;
    }

    const openQuoteIndex = findValueOpeningQuote(line);
    if (openQuoteIndex < 0) {
      continue;
    }
    const closeSameLine = findUnescapedQuote(line, openQuoteIndex + 1);
    if (closeSameLine < 0) {
      pendingSingleQuoteOpen = { line: i, column: openQuoteIndex };
    }
  }

  const deduped = [];
  const seen = new Set();
  for (const fix of fixes) {
    const key = `${fix.line}:${fix.column}:${fix.length}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    deduped.push(fix);
  }
  return deduped;
}

function findValueOpeningQuote(line) {
  if (!line || line.includes("\"\"\"")) {
    return -1;
  }
  const m = line.match(/^(\s*(?:[A-Za-z_][A-Za-z0-9_.-]*|"[^"]+")\s*[=:]\s*)"/);
  if (!m) {
    return -1;
  }
  const quoteIndex = m[1].length;
  if (line.startsWith("\"\"\"", quoteIndex)) {
    return -1;
  }
  return quoteIndex;
}

function findUnescapedQuote(text, fromIndex) {
  if (!text) {
    return -1;
  }
  let i = Math.max(0, fromIndex || 0);
  while (i < text.length) {
    const idx = text.indexOf("\"", i);
    if (idx < 0) {
      return -1;
    }
    let backslashes = 0;
    for (let j = idx - 1; j >= 0 && text.charAt(j) === "\\"; j--) {
      backslashes++;
    }
    if (backslashes % 2 === 0) {
      return idx;
    }
    i = idx + 1;
  }
  return -1;
}

function collectSingleLineTripleQuoteFixes(document) {
  const fixes = [];
  const lineRe = /^(\s*(?:[A-Za-z_][A-Za-z0-9_.-]*|"[^"]+")\s*[=:]\s*)"""([\s\S]*?)"""(\s*,?\s*(?:#.*|\/\/.*)?)$/;

  for (let i = 0; i < document.lineCount; i++) {
    const line = document.lineAt(i).text;
    const match = line.match(lineRe);
    if (!match) {
      continue;
    }
    const prefix = match[1];
    const content = match[2];
    const suffix = match[3] || "";
    const escaped = escapeForDoubleQuoted(content);
    fixes.push({
      line: i,
      replacement: `${prefix}"${escaped}"${suffix}`
    });
  }

  return fixes;
}

function escapeForDoubleQuoted(text) {
  if (!text) {
    return "";
  }
  return text.replace(/\\/g, "\\\\").replace(/"/g, "\\\"");
}

function complete(document, position) {
  const line = document.lineAt(position.line).text;
  const prefix = line.slice(0, position.character);
  const section = detectCurrentSection(document, position.line);
  const suggestions = [];

  const sectionPrefixMatch = prefix.match(/^(\s*)\[[^\]]*$/);
  if (/^\s*$/.test(prefix) || sectionPrefixMatch) {
    const replaceStart = sectionPrefixMatch ? sectionPrefixMatch[1].length : position.character;
    const replaceEnd =
      sectionPrefixMatch && line.charAt(position.character) === "]"
        ? position.character + 1
        : position.character;
    for (const sectionHeader of SECTION_SNIPPETS) {
      const item = new vscode.CompletionItem(sectionHeader, vscode.CompletionItemKind.Module);
      item.insertText = sectionHeader;
      if (sectionPrefixMatch) {
        item.range = new vscode.Range(
          position.line,
          replaceStart,
          position.line,
          replaceEnd
        );
      }
      suggestions.push(item);
    }
    return suggestions;
  }

  const keyEqualsMatch = prefix.match(/^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*$/);
  if (keyEqualsMatch) {
    const key = keyEqualsMatch[1];
    if (BOOL_KEYS.has(key)) {
      suggestions.push(new vscode.CompletionItem("true", vscode.CompletionItemKind.Value));
      suggestions.push(new vscode.CompletionItem("false", vscode.CompletionItemKind.Value));
    }
    const enumCandidates = ENUM_VALUES[key];
    if (enumCandidates) {
      for (const value of enumCandidates) {
        const item = new vscode.CompletionItem(`"${value}"`, vscode.CompletionItemKind.Value);
        item.insertText = `"${value}"`;
        suggestions.push(item);
      }
    }
    return suggestions;
  }

  if (/^\s*[A-Za-z_][A-Za-z0-9_.-]*\s*$/.test(prefix) || /^\s*$/.test(prefix)) {
    const keys = SECTION_KEYS[section] || [];
    for (const key of keys) {
      const item = new vscode.CompletionItem(key, vscode.CompletionItemKind.Property);
      item.insertText = `${key} = `;
      suggestions.push(item);
    }
  }

  return suggestions;
}

function validateDocument(document, diagnostics) {
  const items = [];
  let tripleQuoteOpen = false;

  for (let i = 0; i < document.lineCount; i++) {
    const text = document.lineAt(i).text;
    const trimmed = text.trim();

    if (trimmed.length === 0 || trimmed.startsWith("#") || trimmed.startsWith("//")) {
      continue;
    }

    const tripleMatches = countOccurrences(text, "\"\"\"");
    const wasTripleQuoteOpen = tripleQuoteOpen;
    if (tripleMatches > 0 && tripleMatches % 2 === 1) {
      tripleQuoteOpen = !tripleQuoteOpen;
    }
    // Any line inside a triple-quoted block, or containing a triple delimiter,
    // should not be validated as a normal key/value statement.
    if (wasTripleQuoteOpen || tripleMatches > 0) {
      continue;
    }

    if (trimmed.startsWith("[") && !trimmed.endsWith("]")) {
      items.push(new vscode.Diagnostic(
        new vscode.Range(i, 0, i, text.length),
        "Malformed section header. Expected closing ']'.",
        vscode.DiagnosticSeverity.Error
      ));
      continue;
    }
    if (/^\[[^\]]+\]$/.test(trimmed)) {
      continue;
    }

    if (looksLikeListItem(trimmed)) {
      continue;
    }

    if (trimmed.includes("=") || trimmed.includes(":")) {
      continue;
    }

    items.push(new vscode.Diagnostic(
      new vscode.Range(i, 0, i, text.length),
      "Line does not look like a valid key/value statement.",
      vscode.DiagnosticSeverity.Warning
    ));
  }

  if (tripleQuoteOpen && document.lineCount > 0) {
    const lastLine = document.lineCount - 1;
    const lastText = document.lineAt(lastLine).text;
    items.push(new vscode.Diagnostic(
      new vscode.Range(lastLine, 0, lastLine, lastText.length),
      "Unclosed triple-quoted string.",
      vscode.DiagnosticSeverity.Error
    ));
  }

  diagnostics.set(document.uri, items);
}

function looksLikeListItem(trimmed) {
  if (/^"[^"]*"\s*,?$/.test(trimmed)) {
    return true;
  }
  if (/^[A-Za-z_][A-Za-z0-9_.-]*\s*,?$/.test(trimmed)) {
    return true;
  }
  return false;
}

function detectCurrentSection(document, lineNumber) {
  for (let i = lineNumber; i >= 0; i--) {
    const line = document.lineAt(i).text.trim();
    const m = line.match(/^\[([^\]]+)\]$/);
    if (m) {
      return m[1];
    }
  }
  return "basic";
}

function countOccurrences(text, token) {
  if (!text || !token) {
    return 0;
  }
  let idx = 0;
  let count = 0;
  while (idx < text.length) {
    const found = text.indexOf(token, idx);
    if (found < 0) {
      break;
    }
    count += 1;
    idx = found + token.length;
  }
  return count;
}

module.exports = {
  activate,
  deactivate
};
