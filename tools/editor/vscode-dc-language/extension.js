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
    const changed = await autoCorrectDocumentStructure(doc, correctingDocs);
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

    const changed = await autoCorrectDocumentStructure(doc, correctingDocs);
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
    ":",
    "#",
    "&"
  );
  context.subscriptions.push(provider);

  const colorProvider = vscode.languages.registerColorProvider(
    { language: "dc", scheme: "file" },
    {
      provideDocumentColors(document) {
        return provideDocumentColors(document);
      },
      provideColorPresentations(color, context) {
        return provideColorPresentations(color, context);
      }
    }
  );
  context.subscriptions.push(colorProvider);

  const pickColorCommand = vscode.commands.registerCommand(PICK_COLOR_COMMAND, async (rangeArg) => {
    await runPickColorCommand(rangeArg);
  });
  context.subscriptions.push(pickColorCommand);

  context.subscriptions.push(vscode.workspace.onDidChangeTextDocument((e) => {
    if (!e || !e.document || e.document.languageId !== "dc" || e.contentChanges.length !== 1) {
      return;
    }
    const change = e.contentChanges[0];
    if (!change) {
      return;
    }
    const smallEdit = change.text.length <= 1 && change.rangeLength <= 1;
    if (!smallEdit) {
      return;
    }
    const editor = vscode.window.activeTextEditor;
    if (!editor || editor.document.uri.toString() !== e.document.uri.toString()) {
      return;
    }
    setTimeout(() => {
      const activeEditor = vscode.window.activeTextEditor;
      if (!activeEditor || activeEditor.document.uri.toString() !== e.document.uri.toString()) {
        return;
      }
      const pos = activeEditor.selection.active;
      if (!hasAnyCompletionContext(e.document, pos)) {
        return;
      }
      vscode.commands.executeCommand("editor.action.triggerSuggest");
    }, 0);
  }));
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

async function autoCorrectDocumentStructure(document, correctingDocs) {
  const changedQuotes = await autoCorrectMultilineDoubleQuotes(document, correctingDocs);
  if (changedQuotes) {
    return true;
  }
  return autoCorrectIndentation(document, correctingDocs);
}

async function autoCorrectIndentation(document, correctingDocs) {
  const indentationFixes = collectIndentationFixes(document);
  if (indentationFixes.length === 0) {
    return false;
  }

  const edit = new vscode.WorkspaceEdit();
  for (const fix of indentationFixes) {
    const lineText = document.lineAt(fix.line).text;
    const leading = lineText.match(/^\s*/)[0].length;
    if (leading === fix.expectedIndent) {
      continue;
    }
    edit.replace(
      document.uri,
      new vscode.Range(fix.line, 0, fix.line, leading),
      " ".repeat(Math.max(0, fix.expectedIndent))
    );
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
  const colorContext = detectColorContext(document, position);

  if (colorContext) {
    return buildColorCompletionItems(colorContext);
  }

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

function hasAnyCompletionContext(document, position) {
  const items = complete(document, position);
  return Array.isArray(items) && items.length > 0;
}

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

  const indentIssues = collectIndentationIssues(document);
  for (const issue of indentIssues) {
    const text = document.lineAt(issue.line).text;
    items.push(new vscode.Diagnostic(
      new vscode.Range(issue.line, 0, issue.line, text.length),
      issue.message,
      vscode.DiagnosticSeverity.Warning
    ));
  }

  diagnostics.set(document.uri, items);
}

function collectIndentationFixes(document) {
  const issues = collectIndentationIssues(document);
  const byLine = new Map();
  for (const issue of issues) {
    if (!byLine.has(issue.line)) {
      byLine.set(issue.line, issue);
    }
  }
  return Array.from(byLine.values());
}

function collectIndentationIssues(document) {
  const states = buildLineStates(document);
  const issues = [];
  issues.push(...collectUpdateIndentationIssues(states));
  issues.push(...collectDependenciesIndentationIssues(states));
  return issues;
}

function buildLineStates(document) {
  const out = [];
  let tripleQuoteOpen = false;
  for (let i = 0; i < document.lineCount; i++) {
    const text = document.lineAt(i).text;
    const trimmed = text.trim();
    const tripleMatches = countOccurrences(text, "\"\"\"");
    const wasTripleOpen = tripleQuoteOpen;
    if (tripleMatches > 0 && tripleMatches % 2 === 1) {
      tripleQuoteOpen = !tripleQuoteOpen;
    }
    const inTriple = wasTripleOpen || tripleMatches > 0;
    const ignored =
      inTriple ||
      trimmed.length === 0 ||
      trimmed.startsWith("#") ||
      trimmed.startsWith("//");
    const indentMatch = text.match(/^\s*/);
    out.push({
      line: i,
      text,
      trimmed,
      indent: indentMatch ? indentMatch[0].length : 0,
      ignored
    });
  }
  return out;
}

function collectUpdateIndentationIssues(states) {
  const issues = [];
  const rootHeaders = new Set(["version", "changelog", "file", "update"]);
  const fileSubHeaders = new Set(["del", "add", "move", "modify"]);
  const updateSubHeaders = new Set(["remote", "required"]);

  let root = null;
  let sub = null;
  let inMapEntry = false;

  const hasUpdateShape = states.some(
    (s) => !s.ignored && (sectionName(s.trimmed) === "file" || sectionName(s.trimmed) === "update")
  );
  if (!hasUpdateShape) {
    return issues;
  }

  for (const s of states) {
    if (s.ignored) {
      continue;
    }
    const sec = sectionName(s.trimmed);
    if (sec) {
      if (rootHeaders.has(sec)) {
        pushIndentIssue(issues, s.line, s.indent, 0, "update");
        root = sec;
        sub = null;
        inMapEntry = false;
        continue;
      }
      if (root === "file" && fileSubHeaders.has(sec)) {
        pushIndentIssue(issues, s.line, s.indent, 2, "update");
        sub = sec;
        inMapEntry = false;
        continue;
      }
      if (root === "update" && updateSubHeaders.has(sec)) {
        pushIndentIssue(issues, s.line, s.indent, 2, "update");
        sub = sec;
        inMapEntry = false;
        continue;
      }
      inMapEntry = false;
      continue;
    }

    if (!root) {
      continue;
    }

    if (root === "version" || root === "changelog") {
      if (isKeyValueLine(s.trimmed)) {
        pushIndentIssue(issues, s.line, s.indent, 0, "update");
      }
      continue;
    }

    if (root === "file") {
      if (sub === "del") {
        if (looksLikeListItem(s.trimmed) || isKeyValueLine(s.trimmed)) {
          pushIndentIssue(issues, s.line, s.indent, 4, "update");
        }
        inMapEntry = false;
        continue;
      }
      if (sub === "move") {
        if (isKeyValueLine(s.trimmed)) {
          pushIndentIssue(issues, s.line, s.indent, 4, "update");
        }
        inMapEntry = false;
        continue;
      }
      if (sub === "add" || sub === "modify") {
        if (isMapEntryLine(s.trimmed)) {
          pushIndentIssue(issues, s.line, s.indent, 4, "update");
          inMapEntry = true;
          continue;
        }
        if (isKeyValueLine(s.trimmed)) {
          pushIndentIssue(issues, s.line, s.indent, inMapEntry ? 6 : 4, "update");
          continue;
        }
        inMapEntry = false;
        continue;
      }
      inMapEntry = false;
      continue;
    }

    if (root === "update" && (sub === "remote" || sub === "required")) {
      if (isMapEntryLine(s.trimmed)) {
        pushIndentIssue(issues, s.line, s.indent, 4, "update");
        inMapEntry = true;
        continue;
      }
      if (isKeyValueLine(s.trimmed)) {
        pushIndentIssue(issues, s.line, s.indent, inMapEntry ? 6 : 4, "update");
        continue;
      }
      inMapEntry = false;
    }
  }
  return issues;
}

function collectDependenciesIndentationIssues(states) {
  const issues = [];
  const nodeSections = new Set([
    "download",
    "basic",
    "displayname",
    "tip",
    "notice",
    "contents",
    "contenturl"
  ]);

  const hasDependencies = states.some((s) => !s.ignored && sectionName(s.trimmed) === "dependencies");
  if (!hasDependencies) {
    return issues;
  }

  const stack = [];
  for (const s of states) {
    if (s.ignored) {
      continue;
    }

    const secRaw = sectionNameRaw(s.trimmed);
    const sec = secRaw ? secRaw.toLowerCase() : null;
    const depEntry = isMapEntryLine(s.trimmed);

    if (!sec && !depEntry) {
      continue;
    }

    while (stack.length > 0 && s.indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }

    if (sec === "dependencies") {
      const expected =
        stack.length > 0 && stack[stack.length - 1].type === "depNode"
          ? stack[stack.length - 1].indent + 2
          : 0;
      pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
      stack.push({ type: "depContainer", indent: expected });
      continue;
    }

    if (depEntry && stack.length > 0 && stack[stack.length - 1].type === "depContainer") {
      const expected = stack[stack.length - 1].indent + 2;
      pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
      stack.push({ type: "depNode", indent: expected });
      continue;
    }

    if (sec && stack.length > 0 && stack[stack.length - 1].type === "depContainer" && !nodeSections.has(sec)) {
      const expected = stack[stack.length - 1].indent + 2;
      pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
      stack.push({ type: "depNode", indent: expected });
      continue;
    }

    if (sec && stack.length > 0 && stack[stack.length - 1].type === "depNode" && nodeSections.has(sec)) {
      const expected = stack[stack.length - 1].indent + 2;
      pushIndentIssue(issues, s.line, s.indent, expected, "dependencies");
    }
  }

  return issues;
}

function pushIndentIssue(items, line, actual, expected, scope) {
  if (actual === expected) {
    return;
  }
  const label = scope === "update" ? "热更新结构" : "dependencies结构";
  items.push({
    line,
    expectedIndent: expected,
    message: `${label}缩进不正确：期望 ${expected} 个空格，当前为 ${actual}。`
  });
}

function sectionNameRaw(trimmed) {
  const m = trimmed.match(/^\[([^\]]+)\]$/);
  if (!m) {
    return null;
  }
  return m[1].trim();
}

function sectionName(trimmed) {
  const raw = sectionNameRaw(trimmed);
  return raw ? raw.toLowerCase() : null;
}

function isKeyValueLine(trimmed) {
  return /^(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*.*$/.test(trimmed);
}

function isMapEntryLine(trimmed) {
  return /^(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_.-]*)\s*:\s*$/.test(trimmed);
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
