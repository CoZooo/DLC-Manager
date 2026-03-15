import {
  DocumentSchema,
  FieldKind,
  ROOT_SECTION_KEY,
  StructuredModel,
  StructuredValue,
  ValidationIssue
} from "../../models/editor";

function parseQuoted(text: string): string {
  if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
    return text.slice(1, -1).replace(/\\n/g, "\n").replace(/\\"/g, "\"");
  }
  return text;
}

function formatString(value: string): string {
  if (value.includes("\n")) {
    return `"""${value}"""`;
  }
  return `"${value.replace(/"/g, "\\\"")}"`;
}

function buildFieldKindMap(schema: DocumentSchema): Record<string, FieldKind> {
  const result: Record<string, FieldKind> = {};
  for (const section of schema.sections) {
    for (const field of section.fields) {
      result[`${section.key}.${field.key}`] = field.kind;
    }
  }
  return result;
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
  return parseQuoted(valueText);
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
    const trimmed = rawLine.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      currentSection = trimmed.slice(1, -1).trim();
      if (!model[currentSection]) {
        model[currentSection] = {};
      }
      continue;
    }

    const eq = trimmed.indexOf("=");
    if (eq < 0) {
      issues.push({
        severity: "warning",
        message: "该行看起来不是有效的 key/value 语句。",
        line: index + 1,
        source: "syntax"
      });
      continue;
    }

    const key = trimmed.slice(0, eq).trim();
    let valueText = trimmed.slice(eq + 1).trim();
    if (!currentSection) {
      currentSection = ROOT_SECTION_KEY;
      model[currentSection] = model[currentSection] || {};
    }

    if (valueText.startsWith("\"\"\"")) {
      let block = valueText.slice(3);
      while (!block.endsWith("\"\"\"") && index + 1 < lines.length) {
        index += 1;
        block += `\n${lines[index]}`;
      }
      if (block.endsWith("\"\"\"")) {
        valueText = block.slice(0, -3);
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

    const kind = fieldKinds[`${currentSection}.${key}`] || "text";
    model[currentSection][key] = parseValueByKind(valueText, kind);
  }

  return { model, issues };
}

export function serializeStructuredDocument(model: StructuredModel, schema: DocumentSchema): string {
  const out: string[] = [];
  for (let index = 0; index < schema.sections.length; index++) {
    const section = schema.sections[index];
    const sectionKey = section.key;
    const values = model[sectionKey] || {};
    const hasValues = section.dynamicLocale
      ? Object.keys(values).length > 0
      : section.fields.some((field) => values[field.key] !== undefined);
    if (sectionKey !== ROOT_SECTION_KEY) {
      out.push(section.label);
    }
    if (section.dynamicLocale) {
      for (const [locale, rawValue] of Object.entries(values)) {
        if (typeof rawValue !== "string" || !locale.trim() || locale.startsWith("__draft_locale__:")) {
          continue;
        }
        out.push(`${locale} = ${serializeValue(rawValue)}`);
      }
    } else {
      for (const field of section.fields) {
        const value = values[field.key];
        if (value === undefined) {
          continue;
        }
        out.push(`${field.key} = ${serializeValue(value)}`);
      }
    }
    if (sectionKey === ROOT_SECTION_KEY && hasValues && index < schema.sections.length - 1) {
      out.push("");
    } else if (sectionKey !== ROOT_SECTION_KEY) {
      out.push("");
    }
  }
  return out.join("\n").replace(/\n+$/g, "\n");
}

function serializeValue(value: StructuredValue): string {
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (typeof value === "number") {
    return String(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => formatString(item)).join(", ")}]`;
  }
  return formatString(value);
}

export function validateStructuredModel(model: StructuredModel | null, schema: DocumentSchema): ValidationIssue[] {
  if (!schema.supportsStructured || !model) {
    return [];
  }
  const issues: ValidationIssue[] = [];
  for (const section of schema.sections) {
    const values = model[section.key] || {};
    if (section.dynamicLocale) {
      for (const [locale, value] of Object.entries(values)) {
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
      continue;
    }
    for (const field of section.fields) {
      const value = values[field.key];
      if (field.key === "identifier" && typeof value === "string" && !value.trim()) {
        issues.push({
          severity: "warning",
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
  }
  return issues;
}
