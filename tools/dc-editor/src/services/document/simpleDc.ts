import {
  DocumentSchema,
  FieldKind,
  ROOT_SECTION_KEY,
  StructuredModel,
  StructuredValue,
  SectionSchema,
  ValidationIssue
} from "../../models/editor";
import languageDefinition from "../../../../dclanguage/src/generated/resources/com/cozooo/dclanguage/language-definition.json";

const PRESERVED_RELATION_BLOCKS_SECTION = "__preserved_relation_blocks__";
const PRESERVED_RELATION_BLOCKS_FIELD = "blocks";

function parseQuoted(text: string): string {
  if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
    return text.slice(1, -1).replace(/\\n/g, "\n").replace(/\\"/g, "\"");
  }
  if (text.length >= 2 && text.startsWith("'") && text.endsWith("'")) return text.slice(1, -1);
  return text;
}

function categoryKey(header: string, schema: DocumentSchema): string {
  if (!schema.sectionTemplate) return header;
  let name = parseQuoted(header);
  if (header.startsWith('"')) {
    try { name = JSON.parse(header); } catch { }
  }
  return JSON.stringify(name);
}

function fieldKey(key: string, schema: DocumentSchema): string {
  if (!schema.sectionTemplate) return key;
  const declared = schema.sectionTemplate.fields.find((field) => field.key.toLowerCase() === key.toLowerCase());
  if (declared) return declared.key;
  if (key.toLowerCase().startsWith("displayname.")) return `DisplayName.${key.slice(12).toLowerCase()}`;
  return key;
}

function documentSections(schema: DocumentSchema, model: StructuredModel): SectionSchema[] {
  if (!schema.sectionTemplate) return schema.sections;
  const template = schema.sectionTemplate;
  return Object.keys(model).filter((key) => key !== ROOT_SECTION_KEY).map((key) => ({
    ...template, key, label: `[${key}]`,
    fields: [...new Set([...template.fields.map((field) => field.key), ...Object.keys(model[key])])].map((name) =>
      template.fields.find((field) => field.key === name) ?? { key: name, label: name, kind: "text" })
  }));
}

function formatString(value: string): string {
  if (value.includes("\n")) {
    return `"""${value}"""`;
  }
  return `"${value.replace(/"/g, "\\\"")}"`;
}

function buildFieldKindMap(schema: DocumentSchema): Record<string, FieldKind> {
  const result: Record<string, FieldKind> = {};
  for (const section of [...schema.sections, ...(schema.sectionTemplate ? [schema.sectionTemplate] : [])]) {
    for (const field of section.fields) {
      result[`${section.key}.${field.key}`] = field.kind;
    }
  }
  return result;
}

function countLeadingSpaces(text: string): number {
  let index = 0;
  while (index < text.length && text[index] === " ") {
    index += 1;
  }
  return index;
}

function stripComment(line: string): string {
  return scanRawLine(line, { delimiter: "", escaped: false, depth: 0 });
}

interface RawScanState {
  delimiter: string;
  escaped: boolean;
  depth: number;
}

function scanRawLine(line: string, state: RawScanState): string {
  const syntax = languageDefinition.syntax;
  for (let index = 0; index < line.length; index++) {
    if (state.delimiter) {
      if (state.escaped) {
        state.escaped = false;
      } else if (line.startsWith(state.delimiter, index)) {
        index += state.delimiter.length - 1;
        state.delimiter = "";
      } else if (line[index] === syntax.tokens.escape
        && syntax.stringRules.find((rule) => rule.delimiter === state.delimiter)?.escaped) {
        state.escaped = true;
      }
      continue;
    }
    if (line.startsWith(syntax.tokens.comment, index) || line.startsWith(syntax.tokens.lineComment, index)) {
      return line.slice(0, index);
    }
    const rule = syntax.stringRules.find((candidate) => line.startsWith(candidate.delimiter, index));
    if (rule) {
      state.delimiter = rule.delimiter;
      index += rule.delimiter.length - 1;
    } else if (line[index] === syntax.tokens.arrayOpen || line[index] === syntax.tokens.objectOpen) {
      state.depth += 1;
    } else if (line[index] === syntax.tokens.arrayClose || line[index] === syntax.tokens.objectClose) {
      state.depth -= 1;
    }
  }
  state.escaped = false;
  return line;
}

function isValidLocaleCode(locale: string): boolean {
  return /^[a-z]{2,8}_[a-z0-9]{2,8}$/.test(locale);
}

function parseStringArray(valueText: string): string[] | null {
  if (!valueText.startsWith("[") || !valueText.endsWith("]")) {
    return null;
  }
  const inner = valueText.slice(1, -1).trim();
  if (!inner) {
    return [];
  }
  const result: string[] = [];
  let token = "";
  let inQuote = false;
  let escaped = false;

  for (let index = 0; index < inner.length; index++) {
    const ch = inner[index];
    if (escaped) {
      token += ch;
      escaped = false;
      continue;
    }
    if (ch === "\\") {
      token += ch;
      escaped = true;
      continue;
    }
    if (ch === "\"") {
      token += ch;
      inQuote = !inQuote;
      continue;
    }
    if (ch === "," && !inQuote) {
      const parsed = parseQuoted(token.trim()).trim();
      if (parsed) {
        result.push(parsed);
      }
      token = "";
      continue;
    }
    token += ch;
  }

  const parsed = parseQuoted(token.trim()).trim();
  if (parsed) {
    result.push(parsed);
  }
  return result;
}

function parseValueByKind(valueText: string, kind: FieldKind): StructuredValue {
  if (kind === "boolean") {
    return valueText === "true";
  }
  if (kind === "number") {
    const parsed = Number.parseInt(valueText, 10);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (kind === "list") {
    const array = parseStringArray(valueText);
    if (array) {
      return array;
    }
    return parseQuoted(valueText)
      .split(/[\n,]/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  if (kind === "ordered-select-list") {
    const array = parseStringArray(valueText);
    if (array) {
      return array;
    }
    return parseQuoted(valueText)
      .split(/[\n,]/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  if (kind === "language-visibility") {
    const array = parseStringArray(valueText);
    if (array) {
      return array;
    }
    return parseQuoted(valueText);
  }
  return parseQuoted(valueText);
}

function isBracketListKind(kind: FieldKind): boolean {
  return kind === "list" || kind === "ordered-select-list" || kind === "language-visibility";
}

function isPresetRootListField(sectionKey: string, key: string): boolean {
  return (sectionKey === ROOT_SECTION_KEY || sectionKey === "basic")
    && (key === "enable" || key === "conflicts");
}

function isRelationSection(sectionKey: string): boolean {
  return sectionKey === "dependencies" || sectionKey === "conflicts";
}

function isRelationSubsection(sectionKey: string): boolean {
  return sectionKey.startsWith("dependencies.") || sectionKey.startsWith("conflicts.");
}

function parseUpdateDLCMValue(valueText: string): StructuredValue {
  const normalized = valueText.trim();
  if (normalized === "true") {
    return true;
  }
  if (normalized === "false") {
    return false;
  }
  const parsed = parseQuoted(normalized).trim();
  if (!parsed) {
    return false;
  }
  if (parsed === "true") {
    return true;
  }
  if (parsed === "false") {
    return false;
  }
  return parsed;
}

export function parseStructuredDocument(text: string, schema: DocumentSchema): {
  model: StructuredModel | null;
  issues: ValidationIssue[];
} {
  if (!schema.supportsStructured) {
    return {
      model: null,
      issues: [
        {
          severity: "info",
          message: "该类型在第一阶段以原文编辑为主。",
          source: "schema"
        }
      ]
    };
  }

  const model: StructuredModel = {};
  const issues: ValidationIssue[] = [];
  const fieldKinds = buildFieldKindMap(schema);
  let currentSection = "";
  const lines = text.replace(/\r\n/g, "\n").split("\n");

  for (let index = 0; index < lines.length; index++) {
    const rawLine = lines[index];
    const trimmed = stripComment(rawLine).trim();
    const indent = countLeadingSpaces(rawLine);
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    if (!schema.sectionTemplate && indent === 0 && trimmed.startsWith("[") && trimmed.endsWith("]") && isRelationSubsection(trimmed.slice(1, -1).trim())) {
      const blockLines = [rawLine];
      while (index + 1 < lines.length) {
        const nextLine = lines[index + 1];
        const nextTrimmed = nextLine.trim();
        const nextIndent = countLeadingSpaces(nextLine);
        if (nextIndent === 0 && nextTrimmed.startsWith("[") && nextTrimmed.endsWith("]")) {
          break;
        }
        index += 1;
        blockLines.push(nextLine);
      }
      appendPreservedRelationBlock(model, blockLines.join("\n"));
      continue;
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      const header = trimmed.slice(1, -1).trim();
      if (schema.sectionTemplate && header.includes(".") && !header.startsWith('"') && !header.startsWith("'")) {
        issues.push({ severity: "error", message: '包含点号的文件夹名需要加引号，例如 ["A.B"]。', line: index + 1, source: "syntax" });
      }
      currentSection = categoryKey(header, schema);
      if (!model[currentSection]) {
        model[currentSection] = {};
      }
      continue;
    }

  if (
    schema.type === "update"
    && (
      currentSection === "file"
      || currentSection === "del"
      || currentSection === "add"
      || currentSection === "move"
      || currentSection === "modify"
      || currentSection.startsWith("file.")
      || currentSection.startsWith("update.")
    )
  ) {
      continue;
    }

    const eq = trimmed.indexOf("=");
    if (eq < 0 && !schema.sectionTemplate && isRelationSection(currentSection)) {
      const value = parseQuoted(trimmed).trim();
      if (value) {
        const section = model[currentSection] || {};
        const existing = section.entries;
        section.entries = Array.isArray(existing) ? [...existing, value] : [value];
        model[currentSection] = section;
      }
      continue;
    }
    if (eq < 0) {
      issues.push({
        severity: "warning",
        message: "该行看起来不是有效的 key/value 语句。",
        line: index + 1,
        source: "syntax"
      });
      continue;
    }

    const key = fieldKey(trimmed.slice(0, eq).trim(), schema);
    let valueText = trimmed.slice(eq + 1).trim();
    if (!currentSection) {
      currentSection = ROOT_SECTION_KEY;
      model[currentSection] = model[currentSection] || {};
    }

    const kind = fieldKinds[`${currentSection}.${key}`] || fieldKinds[`*.${key}`] || "text";
    if (schema.sectionTemplate && kind === "number") {
      const number = new RegExp(`^(?:${languageDefinition.syntax.numberPattern})$`).test(valueText) ? Number(valueText) : NaN;
      model[currentSection][key] = Number.isFinite(number) ? number : valueText;
      continue;
    }

    if (isBracketListKind(kind) && valueText.startsWith("[") && !valueText.endsWith("]")) {
      let block = valueText;
      while (index + 1 < lines.length) {
        index += 1;
        const nextLine = stripComment(lines[index]).trim();
        if (!nextLine) {
          continue;
        }
        block += ` ${nextLine}`;
        if (nextLine.endsWith("]")) {
          break;
        }
      }
      valueText = block;
    }

    const multiline = languageDefinition.syntax.stringRules.find((rule) => rule.multiline && valueText.startsWith(rule.delimiter));
    if (multiline) {
      let block = valueText.slice(multiline.delimiter.length);
      while (!block.endsWith(multiline.delimiter) && index + 1 < lines.length) {
        index += 1;
        block += `\n${lines[index]}`;
      }
      if (block.endsWith(multiline.delimiter)) {
        valueText = block.slice(0, -multiline.delimiter.length);
      } else {
        issues.push({
          severity: "error",
          message: "三引号字符串未正常结束。",
          line: index + 1,
          source: "syntax"
        });
      }
      model[currentSection][key] = valueText;
      continue;
    }

    if (schema.type === "update" && currentSection === ROOT_SECTION_KEY && key === "UpdateDLCM") {
      model[currentSection][key] = parseUpdateDLCMValue(valueText);
      continue;
    }
    if (schema.sectionTemplate && key.startsWith("DisplayName.")) {
      if (valueText.startsWith('"') && valueText.endsWith('"')) {
        try { model[currentSection][key] = JSON.parse(valueText); continue; } catch { }
      }
      if (new RegExp(`^(?:${languageDefinition.syntax.numberPattern})$`).test(valueText)) {
        model[currentSection][key] = Number(valueText);
        continue;
      }
      if (/^(true|false)$/i.test(valueText)) {
        model[currentSection][key] = valueText.toLowerCase() === "true";
        continue;
      }
      if (valueText === "null" || valueText.startsWith("[") || valueText.startsWith("{")) {
        issues.push({ severity: "error", message: `${key} 应为文本名称。`, line: index + 1, source: "schema" });
      }
    }
    model[currentSection][key] = parseValueByKind(valueText, kind);
  }

  if (schema.type === "info") {
    normalizeInfoMultiFileModel(model);
  }
  normalizeRelationModel(model, schema.type);
  if (schema.type === "preset") {
    normalizePresetModel(model);
  }
  if (schema.type === "update") {
    hydrateUpdateFileSections(text, model, issues);
  }

  normalizeVersionAliases(model, schema);

  return { model, issues };
}

function normalizeVersionAliases(model: StructuredModel, schema: DocumentSchema): void {
  if (schema.type === "version") {
    const root = model[ROOT_SECTION_KEY] || {};
    if (readString(root.version)) {
      delete root.base;
    } else if (readString(root.base)) {
      root.version = root.base;
      delete root.base;
    }
    model[ROOT_SECTION_KEY] = root;
    return;
  }
  if (schema.type === "update") {
    const section = model.version || {};
    if (readString(section.version)) {
      delete section.base;
    } else if (readString(section.base)) {
      section.version = section.base;
      delete section.base;
    }
    if (readString(section.version_new)) {
      delete section.base_new;
    } else if (readString(section.base_new)) {
      section.version_new = section.base_new;
      delete section.base_new;
    }
    model.version = section;
  }
}

interface SerializeOptions {
  applyImplicitDefaultLocale?: boolean;
}

interface SerializeFieldContext {
  schemaType: DocumentSchema["type"];
  sectionKey: string;
  fieldKey: string;
}

export function serializeStructuredDocument(model: StructuredModel, schema: DocumentSchema, options: SerializeOptions = {}): string {
  const out: string[] = [];
  const sections = documentSections(schema, model);
  for (let index = 0; index < sections.length; index++) {
    const section = sections[index];
    const sectionKey = section.key;
    const values = model[sectionKey] || {};
    const fieldKeys = new Set(section.fields.map((field) => field.key));
    const dynamicLocaleState = section.dynamicLocale ? collectDynamicLocaleState(values, fieldKeys) : null;
    const hasFieldValues = section.fields.some((field) => values[field.key] !== undefined);
    const hasLocaleValues = dynamicLocaleState
      ? dynamicLocaleState.localeEntries.length > 0 || dynamicLocaleState.hasSingleImplicitEnUs
      : false;
    const hasValues = hasFieldValues || hasLocaleValues;
    if (sectionKey !== ROOT_SECTION_KEY) {
      out.push(section.label);
    }
    for (const field of section.fields) {
      if (field.key === "__multiFileEnabled") {
        continue;
      }
      if (field.kind === "localized-link-group") {
        continue;
      }
      if (schema.type === "info" && section.key === "basic" && field.key === "fileNames") {
        const enabled = Boolean(values.__multiFileEnabled);
        if (!enabled) {
          continue;
        }
        const primary = typeof values.file_name === "string" ? values.file_name.trim() : "";
        const extras = Array.isArray(values.fileNames)
          ? values.fileNames.map((item) => item.trim()).filter(Boolean)
          : [];
        const merged = primary ? [primary, ...extras] : [...extras];
        if (merged.length > 0) {
          out.push(`fileNames = ${serializeValue(merged)}`);
        }
        continue;
      }
      if (!schema.sectionTemplate && isRelationSection(section.key) && field.key === "entries") {
        if (Array.isArray(values.entries)) {
          for (const entry of values.entries.map((item) => item.trim()).filter(Boolean)) {
            out.push(formatString(entry));
          }
        }
        continue;
      }
      const rawValue = values[field.key];
      const value =
        rawValue === undefined && schema.type === "update" && section.key === "version" && field.key === "platform"
          ? field.options?.[0]?.value
          : rawValue;
      if (value === undefined) {
        continue;
      }
      if (schema.type === "update" && sectionKey === ROOT_SECTION_KEY && field.key === "UpdateDLCM") {
        const serialized = serializeUpdateDLCMValue(value);
        if (serialized !== null) {
          out.push(`${field.key} = ${serialized}`);
        }
        continue;
      }
      out.push(`${field.key} = ${serializeValue(value, {
        schemaType: schema.type,
        sectionKey,
        fieldKey: field.key
      })}`);
    }
    if (section.dynamicLocale) {
      if (options.applyImplicitDefaultLocale && dynamicLocaleState?.hasSingleImplicitEnUs) {
        out.push(`en_us = ${serializeValue(dynamicLocaleState.draftEntries[0])}`);
      }
      for (const [locale, rawValue] of dynamicLocaleState?.localeEntries || []) {
        out.push(`${locale} = ${serializeValue(rawValue)}`);
      }
    }
    if (schema.type === "pack" && sectionKey === "links") {
      const localizedLinks = serializeLocalizedLinkSections(model);
      if (localizedLinks) {
        if (out.length > 0 && out[out.length - 1] !== "") {
          out.push("");
        }
        out.push(localizedLinks);
      }
    }
    if (sectionKey === ROOT_SECTION_KEY && hasValues && index < sections.length - 1) {
      out.push("");
    } else if (sectionKey !== ROOT_SECTION_KEY) {
      out.push("");
    }
  }
  const preservedRelationBlocks = readPreservedRelationBlocks(model);
  if (preservedRelationBlocks) {
    if (out.length > 0 && out[out.length - 1] !== "") {
      out.push("");
    }
    out.push(preservedRelationBlocks);
  }
  return out.join("\n").replace(/\n+$/g, "\n");
}

export function serializeStructuredDocumentWithPreservedRaw(
  rawText: string,
  model: StructuredModel,
  schema: DocumentSchema,
  options: SerializeOptions = {}
): string {
  if (schema.type === "required" || schema.sectionTemplate) {
    return patchStructuredDocument(rawText, model, schema);
  }
  if (schema.type !== "update") {
    return serializeStructuredDocument(model, schema, options);
  }

  const preservedBlocks = extractPreservedUpdateBlocks(rawText);
  const generated = serializeUpdateStructuredDocument(model, schema, options).replace(/\n+$/g, "");
  const preserved = preservedBlocks.join("\n\n").replace(/\n+$/g, "");

  if (!generated) {
    return preserved ? `${preserved}\n` : "";
  }
  if (!preserved) {
    return `${generated}\n`;
  }
  return `${generated}\n\n${preserved}\n`;
}

interface RawFieldSpan {
  section: string;
  key: string;
  start: number;
  valueStart: number;
  valueEnd: number;
  end: number;
}

function patchStructuredDocument(rawText: string, model: StructuredModel, schema: DocumentSchema): string {
  const previous = parseStructuredDocument(rawText, schema).model || {};
  const newline = rawText.includes("\r\n") ? "\r\n" : "\n";
  const fields: RawFieldSpan[] = [];
  const sectionEnds = new Map<string, number>();
  const sectionSpans: Array<{ key: string; start: number; end: number }> = [];
  const lines = rawText.match(/[^\n]*\n|[^\n]+$/g) || [];
  let section = ROOT_SECTION_KEY;
  let offset = 0;
  for (let index = 0; index < lines.length; index++) {
    const start = offset;
    const line = lines[index].replace(/\r?\n$/, "");
    offset += lines[index].length;
    const state: RawScanState = { delimiter: "", escaped: false, depth: 0 };
    const content = scanRawLine(line, state);
    const trimmed = content.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      sectionEnds.set(section, start);
      if (sectionSpans.length) sectionSpans[sectionSpans.length - 1].end = start;
      section = categoryKey(trimmed.slice(1, -1).trim(), schema);
      sectionSpans.push({ key: section, start, end: rawText.length });
      continue;
    }
    const assignment = content.match(/^(\s*[^=]+?\s*=\s*)(.*)$/);
    const key = assignment ? fieldKey(content.slice(0, content.indexOf("=")).trim(), schema) : "entries";
    const valueStart = start + (assignment?.[1].length || 0);
    let valueEnd = start + content.trimEnd().length;
    while ((state.delimiter || state.depth > 0) && index + 1 < lines.length) {
      index += 1;
      const nextLine = lines[index].replace(/\r?\n$/, "");
      valueEnd = offset + scanRawLine(nextLine, state).trimEnd().length;
      offset += lines[index].length;
    }
    if (content.trim()) {
      fields.push({ section, key, start, valueStart, valueEnd: Math.max(valueStart, valueEnd), end: offset });
    }
  }
  sectionEnds.set(section, rawText.length);
  const edits: Array<{ start: number; end: number; text: string }> = [];
  const insertions = new Map<number, string[]>();
  if (schema.sectionTemplate) {
    for (const span of sectionSpans) {
      if (!Object.prototype.hasOwnProperty.call(model, span.key)) edits.push({ start: span.start, end: span.end, text: "" });
    }
  }
  const combined = Object.fromEntries(Object.keys(model).map((key) => [key, { ...previous[key], ...model[key] }]));
  for (const sectionSchema of documentSections(schema, combined)) {
    const additions: string[] = [];
    for (const field of sectionSchema.fields) {
      const value = model[sectionSchema.key]?.[field.key];
      const serialize = (value: StructuredValue) => serializeValue(value, { schemaType: schema.type, sectionKey: sectionSchema.key, fieldKey: field.key }).replace(/\n/g, newline);
      if (JSON.stringify(previous[sectionSchema.key]?.[field.key]) === JSON.stringify(value)) {
        continue;
      }
      const matches = fields.filter((span) => (span.section === sectionSchema.key && span.key === field.key)
        || (sectionSchema.key === "dependencies" && field.key === "entries" && span.section === "basic" && span.key === "dependencies"));
      const removeField = (match: RawFieldSpan) => ({
        start: match.start,
        end: match.end,
        text: rawText.slice(match.valueEnd, match.end).trim() ? rawText.slice(match.valueEnd, match.end) : ""
      });
      if (!schema.sectionTemplate && isRelationSection(sectionSchema.key) && field.key === "entries") {
        for (const match of matches) {
          edits.push(removeField(match));
        }
        if (Array.isArray(value)) {
          additions.push(...value.map(formatString));
        }
      } else if (matches.length > 0) {
        for (const match of matches) {
          edits.push(value === undefined
            ? removeField(match)
            : { start: match.valueStart, end: match.valueEnd, text: serialize(value) });
        }
      } else if (value !== undefined) {
        additions.push(`${field.key} = ${serialize(value)}`);
      }
    }
    if (additions.length > 0) {
      const end = sectionEnds.get(sectionSchema.key);
      const position = end ?? rawText.length;
      const linesToInsert = insertions.get(position) || [];
      if (end === undefined) {
        linesToInsert.push(sectionSchema.label);
      }
      linesToInsert.push(...additions);
      insertions.set(position, linesToInsert);
    }
  }
  for (const [position, additions] of insertions) {
    const prefix = position > 0 && !rawText.slice(0, position).endsWith("\n") ? newline : "";
    edits.push({ start: position, end: position, text: prefix + additions.join(newline) + newline });
  }
  let result = rawText;
  for (const edit of edits.sort((left, right) => right.start - left.start || right.end - left.end)) {
    result = result.slice(0, edit.start) + edit.text + result.slice(edit.end);
  }
  return result;
}

export function formatPresetListFieldsInRaw(text: string): string {
  const normalizedText = text.replace(/\r\n/g, "\n");
  const lines = normalizedText.split("\n");
  const out: string[] = [];
  let currentSection = ROOT_SECTION_KEY;

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    const stripped = stripComment(line);
    const trimmed = stripped.trim();
    const indent = line.match(/^\s*/)?.[0] ?? "";

    if (!trimmed) {
      out.push(line);
      continue;
    }

    if (!indent && trimmed.startsWith("[") && trimmed.endsWith("]")) {
      currentSection = trimmed.slice(1, -1).trim();
      out.push(line);
      continue;
    }

    const eq = stripped.indexOf("=");
    if (eq < 0) {
      out.push(line);
      continue;
    }

    const key = stripped.slice(0, eq).trim();
    let valueText = stripped.slice(eq + 1).trim();
    if (!isPresetRootListField(currentSection, key) || !valueText.startsWith("[")) {
      out.push(line);
      continue;
    }

    while (!valueText.endsWith("]") && index + 1 < lines.length) {
      index += 1;
      const nextLine = stripComment(lines[index]).trim();
      if (nextLine) {
        valueText += ` ${nextLine}`;
      }
    }

    const values = parseStringArray(valueText);
    if (!values) {
      out.push(line);
      continue;
    }

    if (values.length === 0) {
      out.push(`${indent}${key} = []`);
      continue;
    }

    out.push(`${indent}${key} = [`);
    for (let valueIndex = 0; valueIndex < values.length; valueIndex++) {
      const suffix = valueIndex === values.length - 1 ? "" : ",";
      out.push(`${indent}  ${formatString(values[valueIndex])}${suffix}`);
    }
    out.push(`${indent}]`);
  }

  return out.join("\n");
}

function normalizeInfoMultiFileModel(model: StructuredModel) {
  const basic = model.basic || {};
  const current = basic.fileNames;
  if (!Array.isArray(current)) {
    return;
  }
  const primary = typeof basic.file_name === "string" ? basic.file_name.trim() : "";
  let removedPrimary = false;
  const extras = current.filter((item) => {
    const trimmed = item.trim();
    if (!trimmed) {
      return false;
    }
    if (!removedPrimary && primary && trimmed === primary) {
      removedPrimary = true;
      return false;
    }
    return true;
  });
  model.basic = {
    ...basic,
    __multiFileEnabled: true,
    fileNames: extras
  };
}

function normalizeRelationModel(model: StructuredModel, type: DocumentSchema["type"]) {
  if (type !== "info" && type !== "remote" && type !== "required" && type !== "preset" && type !== "pack") {
    return;
  }
  const basic = model.basic || {};
  const root = model[ROOT_SECTION_KEY] || {};
  if (type === "preset") {
    moveLegacyRelationField(model, root, "enable", "dependencies");
    moveLegacyRelationField(model, root, "conflicts", "conflicts");
    moveLegacyRelationField(model, basic, "enable", "dependencies");
    moveLegacyRelationField(model, basic, "conflicts", "conflicts");
  } else {
    moveLegacyRelationField(model, basic, "dependencies", "dependencies");
    if (type !== "required") {
      moveLegacyRelationField(model, basic, "conflicts", "conflicts");
    }
  }
}

function appendPreservedRelationBlock(model: StructuredModel, block: string) {
  const section = model[PRESERVED_RELATION_BLOCKS_SECTION] || {};
  const current = typeof section[PRESERVED_RELATION_BLOCKS_FIELD] === "string"
    ? section[PRESERVED_RELATION_BLOCKS_FIELD] as string
    : "";
  section[PRESERVED_RELATION_BLOCKS_FIELD] = current ? `${current}\n\n${block}` : block;
  model[PRESERVED_RELATION_BLOCKS_SECTION] = section;
}

function moveLegacyRelationField(
  model: StructuredModel,
  source: Record<string, StructuredValue>,
  sourceKey: "enable" | "dependencies" | "conflicts",
  targetKey: "dependencies" | "conflicts"
) {
  const value = source[sourceKey];
  if (value === undefined) {
    return;
  }
  const section = model[targetKey] || {};
  const current = Array.isArray(section.entries) ? section.entries : [];
  const values = Array.isArray(value)
    ? value
    : typeof value === "string"
      ? value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean)
      : [];
  section.entries = [...current, ...values];
  model[targetKey] = section;
  delete source[sourceKey];
}

function normalizePresetModel(model: StructuredModel) {
  const root = model[ROOT_SECTION_KEY] || {};
  const basic = model.basic || {};
  const nextBasic = {
    ...basic
  };
  for (const key of ["identifier", "alarm"] as const) {
    if (nextBasic[key] === undefined && root[key] !== undefined) {
      nextBasic[key] = root[key];
      delete root[key];
    }
  }
  model.basic = nextBasic;
  model[ROOT_SECTION_KEY] = root;
}

function shouldUseExpandedListFormat(value: StructuredValue, context?: SerializeFieldContext): value is string[] {
  if (!Array.isArray(value)) {
    return false;
  }
  return context?.schemaType === "preset"
    && context.sectionKey === ROOT_SECTION_KEY
    && (context.fieldKey === "enable" || context.fieldKey === "conflicts");
}

function shouldUsePackSingleOrListFormat(value: StructuredValue, context?: SerializeFieldContext): value is string[] {
  return Array.isArray(value)
    && context?.schemaType === "pack"
    && (
      (context.sectionKey === "basic" && context.fieldKey === "author") ||
      (context.sectionKey === "modpack" && (context.fieldKey === "modpack" || context.fieldKey === "base" || context.fieldKey === "dlc"))
    );
}

function serializePackSingleOrListValue(value: string[]): string {
  const normalized = value.map((item) => item.trim()).filter(Boolean);
  if (normalized.length === 0) {
    return formatString("");
  }
  if (normalized.length === 1) {
    return formatString(normalized[0]);
  }
  return `[${normalized.map((item) => formatString(item)).join(", ")}]`;
}

function serializeExpandedStringList(value: string[]): string {
  if (value.length === 0) {
    return "[]";
  }
  return `[\n  ${value.map((item) => formatString(item)).join(",\n  ")}\n]`;
}

function serializeInlineStringList(value: string[]): string {
  return `[${value.map((item) => formatString(item)).join(", ")}]`;
}

function serializeValue(value: StructuredValue, context?: SerializeFieldContext): string {
  if (context?.schemaType === "folder_metadata" && typeof value === "string") return JSON.stringify(value);
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (typeof value === "number") {
    return String(value);
  }
  if (Array.isArray(value)) {
    const listValue = value as string[];
    if (shouldUsePackSingleOrListFormat(listValue, context)) {
      return serializePackSingleOrListValue(listValue);
    }
    if (shouldUseExpandedListFormat(listValue, context)) {
      return serializeExpandedStringList(listValue);
    }
    return serializeInlineStringList(listValue);
  }
  if (typeof value !== "string") {
    return formatString("");
  }
  return formatString(value);
}

function readPreservedRelationBlocks(model: StructuredModel): string {
  const value = model[PRESERVED_RELATION_BLOCKS_SECTION]?.[PRESERVED_RELATION_BLOCKS_FIELD];
  return typeof value === "string" ? value.trim() : "";
}

function serializeLocalizedLinkSections(model: StructuredModel): string {
  const out: string[] = [];
  for (const [sectionKey, values] of Object.entries(model)) {
    if (!sectionKey.startsWith("links.")) {
      continue;
    }
    const locale = sectionKey.slice("links.".length).trim();
    if (!locale || locale.startsWith("__draft_locale__:") || !values || typeof values !== "object" || Array.isArray(values)) {
      continue;
    }
    const sectionValues = values as Record<string, StructuredValue>;
    const lines: string[] = [];
    for (const key of ["homepage", "issue", "sponsor"]) {
      const value = sectionValues[key];
      if (typeof value === "string" && value.trim()) {
        lines.push(`${key} = ${serializeValue(value)}`);
      }
    }
    if (lines.length === 0) {
      continue;
    }
    if (out.length > 0) {
      out.push("");
    }
    out.push(`[links.${locale}]`);
    out.push(...lines);
  }
  return out.join("\n");
}

function serializeUpdateDLCMValue(value: StructuredValue): string | null {
  if (typeof value === "boolean") {
    return value ? "true" : null;
  }
  if (typeof value === "string") {
    const normalized = value.trim();
    if (!normalized || normalized === "false") {
      return null;
    }
    if (normalized === "true") {
      return "true";
    }
    return formatString(normalized);
  }
  return null;
}

function extractPreservedUpdateBlocks(rawText: string): string[] {
  const lines = rawText.replace(/\r\n/g, "\n").split("\n");
  const blocks: string[] = [];
  let currentHeader: string | null = null;
  let currentLines: string[] = [];

  const flush = () => {
    if (!currentHeader || currentLines.length === 0) {
      currentHeader = null;
      currentLines = [];
      return;
    }
    if (currentHeader === "version" || currentHeader === "changelog" || currentHeader === "file") {
      currentHeader = null;
      currentLines = [];
      return;
    }
    if (currentHeader === "update" && isEmptyUpdateBlock(currentLines)) {
      currentHeader = null;
      currentLines = [];
      return;
    }
    blocks.push(trimTrailingBlankLines(currentLines).join("\n"));
    currentHeader = null;
    currentLines = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    const indent = countLeadingSpaces(line);
    if (indent === 0 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
      flush();
      currentHeader = trimmed.slice(1, -1).trim();
      currentLines = [line];
      continue;
    }
    if (currentHeader) {
      currentLines.push(line);
    }
  }

  flush();
  return blocks.filter(Boolean);
}

function isEmptyUpdateBlock(lines: string[]): boolean {
  if (lines.length <= 1) {
    return true;
  }
  return lines.slice(1).every((line) => !stripComment(line).trim());
}

function trimTrailingBlankLines(lines: string[]): string[] {
  const next = [...lines];
  while (next.length > 0 && !next[next.length - 1].trim()) {
    next.pop();
  }
  return next;
}

function hydrateUpdateFileSections(rawText: string, model: StructuredModel, issues: ValidationIssue[]) {
  const lines = rawText.replace(/\r\n/g, "\n").split("\n");
  const del: string[] = [];
  const add: string[] = [];
  const move: string[] = [];
  const modify: string[] = [];
  let inFile = false;
  let currentSub: "" | "del" | "add" | "move" | "modify" = "";
  let pendingSource = "";
  let pendingTarget = "";
  let pendingOverride = "";
  let pendingSourceLine = 0;
  let pendingTargetLine = 0;
  let pendingModifyTarget = "";
  let pendingFind = "";
  let pendingReplace = "";
  let pendingWhich = "";

  const flushAdd = (lineNumber?: number) => {
    const line = lineNumber || pendingTargetLine || pendingSourceLine || 0;
    if (!pendingSource) {
      if (pendingTarget || pendingOverride) {
        issues.push({
          severity: "error",
          message: "[file].add 缺少源路径。",
          line: line || undefined,
          source: "syntax"
        });
      }
      pendingTarget = "";
      pendingOverride = "";
      pendingTargetLine = 0;
      return;
    }
    if (!pendingTarget) {
      issues.push({
        severity: "error",
        message: "[file].add 缺少目标路径。",
        line: line || undefined,
        source: "syntax"
      });
      pendingSource = "";
      pendingOverride = "";
      pendingSourceLine = 0;
      return;
    }
    add.push([pendingSource, pendingTarget, pendingOverride].map((item) => item.trim()).join(" => "));
    pendingSource = "";
    pendingTarget = "";
    pendingOverride = "";
    pendingSourceLine = 0;
    pendingTargetLine = 0;
  };

  const flushModify = () => {
    if (!pendingModifyTarget) {
      return;
    }
    modify.push([pendingModifyTarget, pendingFind, pendingReplace, pendingWhich].map((item) => item.trim()).join(" => "));
    pendingModifyTarget = "";
    pendingFind = "";
    pendingReplace = "";
    pendingWhich = "";
  };

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    const stripped = stripComment(line);
    const indent = countLeadingSpaces(stripped);
    const trimmed = stripped.trim();
    const lineNumber = index + 1;
    if (!trimmed) {
      continue;
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      const header = trimmed.slice(1, -1).trim();
      if (indent === 0) {
        if (inFile) {
          flushAdd(lineNumber);
          flushModify();
        }
        inFile = header === "file";
        currentSub = "";
        continue;
      }
      if (!inFile) {
        continue;
      }
      flushAdd(lineNumber);
      flushModify();
      currentSub = header === "del" || header === "add" || header === "move" || header === "modify" ? header : "";
      continue;
    }
    if (!inFile || !currentSub) {
      continue;
    }
    if (currentSub === "del") {
      const item = trimmed.endsWith(",") ? trimmed.slice(0, -1).trim() : trimmed;
      del.push(parseUpdateInlineValue(item));
      continue;
    }
    if (currentSub === "move") {
      const eq = trimmed.indexOf("=");
      if (eq < 0) {
        continue;
      }
      const left = unquoteUpdateKey(trimmed.slice(0, eq).trim());
      const right = parseUpdateInlineValue(trimmed.slice(eq + 1).trim());
      if (left && right) {
        move.push(`${left} => ${right}`);
      }
      continue;
    }
    if (currentSub === "add") {
      if (trimmed.endsWith(":") && !trimmed.includes("=")) {
        flushAdd(lineNumber);
        pendingSource = unquoteUpdateKey(trimmed.slice(0, -1).trim());
        pendingSourceLine = lineNumber;
        pendingTargetLine = 0;
        continue;
      }
      const eq = trimmed.indexOf("=");
      if (eq < 0) {
        continue;
      }
      const key = trimmed.slice(0, eq).trim();
      const value = parseUpdateInlineValue(trimmed.slice(eq + 1).trim());
      if ("appliedTarget" === key) {
        pendingTarget = value;
        pendingTargetLine = lineNumber;
      } else if ("override" === key) {
        pendingOverride = value;
      }
      continue;
    }
    if (currentSub === "modify") {
      if (trimmed.endsWith(":") && !trimmed.includes("=")) {
        flushModify();
        pendingModifyTarget = unquoteUpdateKey(trimmed.slice(0, -1).trim());
        continue;
      }
      const eq = trimmed.indexOf("=");
      if (eq < 0) {
        continue;
      }
      const key = trimmed.slice(0, eq).trim();
      const value = parseUpdateInlineValue(trimmed.slice(eq + 1).trim());
      if ("find" === key) {
        pendingFind = value;
      } else if ("replace" === key) {
        pendingReplace = value;
      } else if ("which" === key) {
        pendingWhich = value;
      }
    }
  }

  flushAdd(lines.length);
  flushModify();

  model.file_del = { entries: del.join("\n") };
  model.file_add = { entries: add.join("\n") };
  model.file_move = { entries: move.join("\n") };
  model.file_modify = { entries: modify.join("\n") };
}

function serializeUpdateStructuredDocument(model: StructuredModel, schema: DocumentSchema, options: SerializeOptions): string {
  const out: string[] = [];
  const baseSchema: DocumentSchema = {
    ...schema,
    sections: schema.sections.filter((section) => !section.key.startsWith("file_"))
  };
  const base = serializeStructuredDocument(model, baseSchema, options).replace(/\n+$/g, "");
  if (base) {
    out.push(base);
  }
  const fileBlocks = serializeUpdateFileSections(model);
  if (fileBlocks) {
    if (out.length > 0) {
      out.push("");
    }
    out.push(fileBlocks);
  }
  return out.join("\n").replace(/\n+$/g, "\n");
}

function serializeUpdateFileSections(model: StructuredModel): string {
  const delLines = readUpdateMultiline(model.file_del?.entries);
  const addLines = readUpdateMultiline(model.file_add?.entries);
  const moveLines = readUpdateMultiline(model.file_move?.entries);
  const modifyLines = readUpdateMultiline(model.file_modify?.entries);
  if (delLines.length === 0 && addLines.length === 0 && moveLines.length === 0 && modifyLines.length === 0) {
    return "";
  }

  const out: string[] = ["[file]"];
  if (delLines.length > 0) {
    out.push("  [del]");
    for (const line of delLines) {
      out.push(`    ${formatString(line)}`);
    }
    out.push("");
  }
  if (addLines.length > 0) {
    out.push("  [add]");
    for (const line of addLines) {
      const [source, target, overrideValue] = splitRule(line, 3);
      if (!source) {
        continue;
      }
      out.push(`    ${formatString(source)}:`);
      out.push(`      appliedTarget = ${formatString(target)}`);
      if (overrideValue) {
        out.push(`      override = ${serializeOverrideValue(overrideValue)}`);
      }
      out.push("");
    }
  }
  if (moveLines.length > 0) {
    out.push("  [move]");
    for (const line of moveLines) {
      const [from, to] = splitRule(line, 2);
      if (!from || !to) {
        continue;
      }
      out.push(`    ${formatString(from)} = ${formatString(to)}`);
    }
    out.push("");
  }
  if (modifyLines.length > 0) {
    out.push("  [modify]");
    for (const line of modifyLines) {
      const [target, find, replace, which] = splitRule(line, 4);
      if (!target) {
        continue;
      }
      out.push(`    ${formatString(target)}:`);
      out.push(`      find = ${formatString(find)}`);
      out.push(`      replace = ${formatString(replace)}`);
      if (which) {
        out.push(`      which = ${formatString(which)}`);
      }
      out.push("");
    }
  }

  return out.join("\n").replace(/\n+$/g, "");
}

function readUpdateMultiline(value: StructuredValue | undefined): string[] {
  if (typeof value !== "string") {
    return [];
  }
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function splitRule(text: string, limit: number): string[] {
  const parts = text.split(/\s*=>\s*/);
  const result = parts.slice(0, limit);
  while (result.length < limit) {
    result.push("");
  }
  return result.map((item) => item.trim());
}

function serializeOverrideValue(value: string): string {
  const trimmed = value.trim().toLowerCase();
  if (trimmed === "true" || trimmed === "false") {
    return trimmed;
  }
  return formatString(value.trim());
}

function unquoteUpdateKey(text: string): string {
  if (!text) {
    return "";
  }
  if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
    return parseQuoted(text);
  }
  return text;
}

function parseUpdateInlineValue(text: string): string {
  const trimmed = text.trim();
  if (!trimmed) {
    return "";
  }
  return unquoteUpdateKey(trimmed);
}

function collectDynamicLocaleState(
  values: Record<string, StructuredValue>,
  fieldKeys: Set<string>
): {
  localeEntries: Array<[string, string]>;
  draftEntries: string[];
  hasSingleImplicitEnUs: boolean;
} {
  const localeEntries: Array<[string, string]> = [];
  const draftEntries: string[] = [];

  for (const [key, rawValue] of Object.entries(values)) {
    if (fieldKeys.has(key) || typeof rawValue !== "string") {
      continue;
    }
    if (key.startsWith("__draft_locale__:")) {
      if (rawValue.trim()) {
        draftEntries.push(rawValue);
      }
      continue;
    }
    if (key.trim()) {
      localeEntries.push([key, rawValue]);
    }
  }

  return {
    localeEntries,
    draftEntries,
    hasSingleImplicitEnUs: localeEntries.length === 0 && draftEntries.length === 1
  };
}

export function validateStructuredModel(model: StructuredModel | null, schema: DocumentSchema): ValidationIssue[] {
  if (!schema.supportsStructured || !model) {
    return [];
  }
  const issues: ValidationIssue[] = [];
  for (const section of documentSections(schema, model)) {
    const values = model[section.key] || {};
    const fieldKeys = new Set(section.fields.map((field) => field.key));
    for (const field of section.fields) {
      const value = values[field.key];
      if (field.key === "identifier"
        && !(schema.type === "pack" && section.key === "basic")
        && typeof value === "string"
        && !value.trim()
      ) {
        issues.push({
          severity: "error",
          message: `[${section.key}].identifier 为空。`,
          source: "schema"
        });
      }
      if (field.kind === "number" && value !== undefined && typeof value !== "number") {
        issues.push({
          severity: "warning",
          message: `[${section.key}].${field.key} 应为数字。`,
          source: "schema"
        });
      }
    }
    if (section.dynamicLocale) {
      const dynamicLocaleState = collectDynamicLocaleState(values, fieldKeys);
      if (dynamicLocaleState.hasSingleImplicitEnUs) {
        continue;
      }
      for (const [locale, value] of Object.entries(values)) {
        if (fieldKeys.has(locale)) {
          continue;
        }
        if (locale.startsWith("__draft_locale__:")) {
          issues.push({
            severity: "error",
            message: `[${section.key}] 存在未填写语言代码的文本条目。`,
            source: "schema"
          });
          continue;
        }
        if (!locale.trim()) {
          issues.push({
            severity: "warning",
            message: `[${section.key}] 中存在空语言代码。`,
            source: "schema"
          });
        }
        if (locale.trim() && !isValidLocaleCode(locale.trim())) {
          issues.push({
            severity: "error",
            message: `[${section.key}].${locale} 不是合法的语言代码，应使用类似 en_us / zh_cn / pt_br 的格式。`,
            source: "schema"
          });
        }
        if (typeof value !== "string") {
          issues.push({
            severity: "warning",
            message: `[${section.key}].${locale} 应为文本。`,
            source: "schema"
          });
        }
      }
    }
  }
  if (schema.type === "pack") {
    validateLocalizedLinkSections(model, issues);
  }
  if (schema.sectionTemplate) {
    for (const [key, values] of Object.entries(model)) {
      let name = "";
      try { name = JSON.parse(key); } catch { }
      if (!name.trim() || name === "." || name === ".." || /[/\\]/.test(name)) {
        issues.push({ severity: "error", message: "文件夹名必须是非空的单级目录名称。", source: "schema" });
      }
      const priority = values.priority;
      if (priority !== undefined && (typeof priority !== "number" || !Number.isInteger(priority) || priority < -2147483648 || priority > 2147483647)) {
        issues.push({ severity: "error", message: `[${name}].priority 必须为 32 位整数，可以为负数。`, source: "schema" });
      }
      for (const [field, value] of Object.entries(values)) {
        if (field.startsWith("DisplayName.") && (!isValidLocaleCode(field.slice(12)) || typeof value !== "string")) {
          issues.push({ severity: "error", message: `[${name}].${field} 应为语言代码和文本名称。`, source: "schema" });
        }
      }
    }
  }
  addDocumentSpecificIssues(model, schema, issues);
  return issues;
}

function validateLocalizedLinkSections(model: StructuredModel, issues: ValidationIssue[]) {
  for (const [sectionKey, values] of Object.entries(model)) {
    if (!sectionKey.startsWith("links.")) {
      continue;
    }
    const locale = sectionKey.slice("links.".length).trim();
    if (!locale || locale.startsWith("__draft_locale__:")) {
      if (isEmptyLocalizedLinkSection(values)) {
        continue;
      }
      issues.push({
        severity: "error",
        message: "[links] 存在未填写语言代码的链接条目。",
        source: "schema"
      });
      continue;
    }
    if (!isValidLocaleCode(locale)) {
      issues.push({
        severity: "error",
        message: `[links.${locale}] 不是合法的语言代码，应使用类似 en_us / zh_cn / pt_br 的格式。`,
        source: "schema"
      });
    }
    if (!values || typeof values !== "object" || Array.isArray(values)) {
      issues.push({
        severity: "warning",
        message: `[links.${locale}] 应为链接表。`,
        source: "schema"
      });
    }
  }
}

function isEmptyLocalizedLinkSection(values: Record<string, StructuredValue>): boolean {
  if (!values || typeof values !== "object" || Array.isArray(values)) {
    return false;
  }
  for (const key of ["homepage", "issue", "sponsor"]) {
    const value = values[key];
    if (typeof value === "string" && value.trim()) {
      return false;
    }
  }
  return true;
}

function addDocumentSpecificIssues(model: StructuredModel, schema: DocumentSchema, issues: ValidationIssue[]) {
  if (schema.type === "info") {
    const basic = model.basic || {};
    requireNonEmptyString(issues, basic.file_name, "[basic].file_name 为空。", "warning");
    const typeValue = readString(basic.type);
    if (typeValue === "dlcpack") {
      issues.push({
        severity: "error",
        message: "[basic].type 为 dlcpack 只能用于 Remote 配置。",
        source: "schema"
      });
    }
    if ((typeValue === "others" || typeValue === "config") && !readString(basic.appliedTarget)) {
      issues.push({
        severity: "warning",
        message: "[basic].appliedTarget 为空；type 为 others/config 时通常需要填写应用目标路径。",
        source: "schema"
      });
    }
    return;
  }

  if (schema.type === "remote") {
    const basic = model.basic || {};
    const download = model.download || {};
    requireNonEmptyString(issues, basic.file_name, "[basic].file_name 为空。", "warning");
    const typeValue = readString(basic.type);
    if (typeValue !== "dlcpack") {
      requireNonEmptyString(issues, basic.identifier, "[basic].identifier 为空。", "error");
    }
    if (typeValue === "others" && !readString(basic.appliedTarget)) {
      issues.push({
        severity: "warning",
        message: "[basic].appliedTarget 为空；type 为 others 时通常需要填写应用目标路径。",
        source: "schema"
      });
    }
    if (!hasAnyDownloadSource(download)) {
      issues.push({
        severity: "warning",
        message: "[download] 未填写任何下载源；至少应配置 directUrl、Modrinth 或 CurseForge 之一。",
        source: "schema"
      });
    }
    validateDirectUrls(issues, "download", download);
    return;
  }

  if (schema.type === "required") {
    const basic = model.basic || {};
    const download = model.download || {};
    requireNonEmptyString(issues, basic.file_name, "[basic].file_name 为空。", "warning");
    if (!hasAnyDownloadSource(download)) {
      issues.push({
        severity: "warning",
        message: "[download] 未填写任何下载源；至少应配置 directUrl、Modrinth 或 CurseForge 之一。",
        source: "schema"
      });
    }
    validateDirectUrls(issues, "download", download);
    return;
  }

  if (schema.type === "redirect") {
    const root = model[ROOT_SECTION_KEY] || {};
    requireNonEmptyString(issues, root.RedirectUrl, "[根级字段].RedirectUrl 为空。", "error");
    return;
  }

  if (schema.type === "notice") {
    const root = model[ROOT_SECTION_KEY] || {};
    const title = model.Title || {};
    const contentUrl = model.ContentUrl || {};
    const contents = model.Contents || {};
    const enabled = root.isNoticeEnabled !== false;
    if (!enabled) {
      return;
    }
    if (!hasAnyLocaleValue(title) && !hasAnyLocaleValue(contentUrl) && !hasAnyLocaleValue(contents)) {
      issues.push({
        severity: "warning",
        message: "信息已启用，但 Title、ContentUrl、Contents 都为空。",
        source: "schema"
      });
    }
    return;
  }

  if (schema.type === "version") {
    const root = model[ROOT_SECTION_KEY] || {};
    requireNonEmptyString(issues, root.version, "[根级字段].version 为空。", "error");
    return;
  }

  if (schema.type === "pack") {
    const basic = model.basic || {};
    requireNonEmptyString(issues, basic.identifier, "[basic].identifier 为空。", "error");
    requireNonEmptyString(issues, basic.version, "[basic].version 为空。", "error");
    const identifier = readString(basic.identifier);
    if (identifier && (/[\\/:*?"<>|]/.test(identifier) || /[\x00-\x1F]/.test(identifier) || identifier !== identifier.trim())) {
      issues.push({
        severity: "error",
        message: "[basic].identifier 包含非法字符。",
        source: "schema"
      });
    }
    const version = readString(basic.version);
    if (version && !/^\d+(?:\.\d+)*(?:[-+][A-Za-z0-9_.-]+)?$/.test(version)) {
      issues.push({
        severity: "error",
        message: "[basic].version 格式不合法。",
        source: "schema"
      });
    }
    const platform = readString(basic.platform).toLowerCase();
    if (platform && !["client", "server", "universal"].includes(platform)) {
      issues.push({
        severity: "error",
        message: "[basic].platform 只能是 client、server 或 universal。",
        source: "schema"
      });
    }
    return;
  }

  if (schema.type === "update") {
    const version = model.version || {};
    requireNonEmptyString(issues, version.platform, "[version].platform 为空。", "warning");
    requireNonEmptyString(issues, version.version, "[version].version 为空。", "error");
    if (!readString(version.version_new) && !readString(version.dlc_new)) {
      issues.push({
        severity: "error",
        message: "[version].version_new 与 [version].dlc_new 都为空；该热更新不会声明任何新版本。",
        source: "schema"
      });
    }
  }
}

function readString(value: StructuredValue | undefined): string {
  return typeof value === "string" ? value.trim() : "";
}

function readList(value: StructuredValue | undefined): string[] {
  return Array.isArray(value) ? value.map((item) => item.trim()).filter(Boolean) : [];
}

function requireNonEmptyString(
  issues: ValidationIssue[],
  value: StructuredValue | undefined,
  message: string,
  severity: ValidationIssue["severity"]
) {
  if (!readString(value)) {
    issues.push({
      severity,
      message,
      source: "schema"
    });
  }
}

function isValidHttpUrl(value: string): boolean {
  if (!value) {
    return false;
  }
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

function validateDirectUrls(
  issues: ValidationIssue[],
  sectionKey: string,
  values: Record<string, StructuredValue>
) {
  for (const item of readList(values.directUrl)) {
    if (isValidHttpUrl(item)) {
      continue;
    }
    issues.push({
      severity: "error",
      message: `[${sectionKey}].directUrl 包含非法链接：${item}`,
      source: "schema"
    });
  }
}

function hasAnyDownloadSource(values: Record<string, StructuredValue>): boolean {
  return Boolean(
    readString(values.modrinth_projectid)
      || readString(values.modrinth_versionid)
      || readString(values.curseforge_projectid)
      || readString(values.curseforge_fileid)
      || readList(values.directUrl).length > 0
  );
}

function hasAnyLocaleValue(values: Record<string, StructuredValue>): boolean {
  return Object.entries(values).some(([key, value]) => {
    if (key.startsWith("__draft_locale__:")) {
      return typeof value === "string" && value.trim().length > 0;
    }
    return typeof value === "string" && value.trim().length > 0;
  });
}
