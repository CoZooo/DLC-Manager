import { ChangeEvent, useEffect, useState } from "react";
import { DocumentSchema, StructuredModel, StructuredValue } from "../models/editor";

interface StructuredEditorProps {
  schema: DocumentSchema;
  model: StructuredModel | null;
  enabled: boolean;
  onChange(next: StructuredModel): void;
}

function withValue(
  model: StructuredModel,
  sectionKey: string,
  fieldKey: string,
  value: StructuredValue
): StructuredModel {
  return {
    ...model,
    [sectionKey]: {
      ...(model[sectionKey] || {}),
      [fieldKey]: value
    }
  };
}

export function StructuredEditor(props: StructuredEditorProps) {
  if (!props.enabled || !props.schema.supportsStructured) {
    return (
      <section className="panel">
        <div className="panel-title">结构化编辑</div>
        <div className="panel-note">
          该配置类型在第一阶段仍以原文编辑为主。
        </div>
      </section>
    );
  }

  const model = props.model || {};

  return (
    <section className="panel">
      <div className="panel-title">结构化编辑</div>
      <div className="panel-note">
        第一阶段提供常用字段的分组表单编辑。复杂嵌套结构仍建议在原文模式中处理。
      </div>
      <div className="section-stack">
        {props.schema.sections.map((section) => (
          <div key={section.key} className="section-card">
            <div className="section-title">{section.label}</div>
            {section.description && <div className="field-help">{section.description}</div>}
            {section.dynamicLocale && (
              <DynamicLocaleSection
                sectionKey={section.key}
                values={model[section.key] || {}}
                valueKind={section.dynamicLocale.valueKind}
                help={section.dynamicLocale.help}
                onChange={(nextValues) => props.onChange({
                  ...model,
                  [section.key]: nextValues
                })}
              />
            )}
            {!section.dynamicLocale && (
            <div className="field-grid">
              {section.fields.map((field) => {
                const sectionValues = model[section.key] || {};
                const value = sectionValues[field.key];
                const label = (
                  <label className="field-label">
                    <span>{field.label}</span>
                    {field.help && <span className="field-label-help">{field.help}</span>}
                  </label>
                );

                const onTextChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
                  props.onChange(withValue(model, section.key, field.key, event.target.value));
                };

                const onBooleanChange = (event: ChangeEvent<HTMLInputElement>) => {
                  props.onChange(withValue(model, section.key, field.key, event.target.checked));
                };

                const onNumberChange = (event: ChangeEvent<HTMLInputElement>) => {
                  const raw = event.target.value.trim();
                  props.onChange(withValue(model, section.key, field.key, raw === "" ? 0 : Number.parseInt(raw, 10) || 0));
                };

                const parseListText = (rawText: string) => {
                  return rawText
                    .split(/[\r\n,]+/)
                    .map((item) => item.trim())
                    .filter(Boolean);
                };

                const onListCommit = (rawText: string) => {
                  const items = parseListText(rawText);
                  props.onChange(withValue(model, section.key, field.key, items));
                };

                return (
                  <div key={`${section.key}.${field.key}`} className="field">
                    {label}
                    {field.kind === "textarea" && (
                      <textarea
                        value={typeof value === "string" ? value : ""}
                        placeholder={field.placeholder}
                        onChange={onTextChange}
                      />
                    )}
                    {field.kind === "text" && (
                      <input
                        type="text"
                        value={typeof value === "string" ? value : ""}
                        placeholder={field.placeholder}
                        onChange={onTextChange}
                      />
                    )}
                    {field.kind === "number" && (
                      <input
                        type="number"
                        value={typeof value === "number" ? value : 0}
                        placeholder={field.placeholder}
                        onChange={onNumberChange}
                      />
                    )}
                    {field.kind === "boolean" && (
                      <label className="checkbox-row">
                        <input type="checkbox" checked={Boolean(value)} onChange={onBooleanChange} />
                        <span>{Boolean(value) ? "true" : "false"}</span>
                      </label>
                    )}
                    {field.kind === "list" && (
                      <ListFieldEditor
                        value={Array.isArray(value) ? value : []}
                        placeholder={field.placeholder || "每行一个值"}
                        onCommit={onListCommit}
                      />
                    )}
                    {field.kind === "select" && (
                      <select
                        value={typeof value === "string" ? value : field.options?.[0]?.value || ""}
                        onChange={(event) => props.onChange(withValue(model, section.key, field.key, event.target.value))}
                      >
                        {(field.options || []).map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    )}
                  </div>
                );
              })}
            </div>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}

interface ListFieldEditorProps {
  value: string[];
  placeholder?: string;
  onCommit(rawText: string): void;
}

function ListFieldEditor(props: ListFieldEditorProps) {
  const [draft, setDraft] = useState(props.value.join("\n"));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    const normalized = props.value.join("\n");
    if (!focused && draft !== normalized) {
      setDraft(normalized);
    }
  }, [props.value, draft, focused]);

  return (
    <textarea
      value={draft}
      placeholder={props.placeholder}
      onFocus={() => setFocused(true)}
      onBlur={() => {
        setFocused(false);
        props.onCommit(draft);
      }}
      onChange={(event) => setDraft(event.target.value)}
    />
  );
}

interface DynamicLocaleSectionProps {
  sectionKey: string;
  values: Record<string, StructuredValue>;
  valueKind: "text" | "textarea";
  help?: string;
  onChange(nextValues: Record<string, string>): void;
}

function DynamicLocaleSection(props: DynamicLocaleSectionProps) {
  const [rows, setRows] = useState<Array<{ locale: string; value: string }>>([{ locale: "", value: "" }]);
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    const record = toLocaleRecordForModel(rows);
    const persisted = extractLocaleRows(props.values);
    if (focused) {
      return;
    }
    if (sameLocaleRecord(record, toLocaleRecordForModel(persisted))) {
      return;
    }
    setRows(persisted.length > 0 ? persisted : [{ locale: "", value: "" }]);
  }, [props.values, rows, focused]);

  const updateRow = (index: number, nextLocale: string, nextValue: string) => {
    const nextRows = rows.map((row, rowIndex) => rowIndex === index ? { locale: nextLocale, value: nextValue } : row);
    setRows(nextRows);
    props.onChange(toLocaleRecordForModel(nextRows));
  };

  const addRow = () => {
    setRows((previous) => [...previous, { locale: "", value: "" }]);
  };

  const removeRow = (index: number) => {
    const nextRows = rows.filter((_, rowIndex) => rowIndex !== index);
    const normalized = nextRows.length > 0 ? nextRows : [{ locale: "", value: "" }];
    setRows(normalized);
    props.onChange(toLocaleRecordForModel(normalized));
  };

  return (
    <div className="field-grid">
      {props.help && <div className="field-help locale-help">{props.help}</div>}
      {rows.map((row, index) => (
        <div key={`${props.sectionKey}.${index}`} className="locale-row">
          <div className="locale-row-main">
            <div className="locale-code-field">
              <label className="field-label">语言代码</label>
              <input
                type="text"
                value={row.locale}
                placeholder="zh_cn"
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
                onChange={(event) => updateRow(index, event.target.value, row.value)}
              />
            </div>
            <div className="locale-text-field">
              <label className="field-label">文本</label>
              {props.valueKind === "textarea" ? (
                <textarea
                  value={row.value}
                  placeholder="填写对应语言的文本"
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onChange={(event) => updateRow(index, row.locale, event.target.value)}
                />
              ) : (
                <input
                  type="text"
                  value={row.value}
                  placeholder="填写对应语言的文本"
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onChange={(event) => updateRow(index, row.locale, event.target.value)}
                />
              )}
            </div>
            <div className="locale-row-actions">
              <label className="field-label field-label-ghost">操作</label>
              <button type="button" onClick={() => removeRow(index)} disabled={rows.length === 1 && !row.locale && !row.value}>
                删除
              </button>
            </div>
          </div>
        </div>
      ))}
      <button type="button" className="secondary-action" onClick={addRow}>
        添加语言
      </button>
    </div>
  );
}

function extractLocaleRows(values: Record<string, StructuredValue>): Array<{ locale: string; value: string }> {
  return Object.entries(values)
    .filter(([, value]) => typeof value === "string")
    .map(([key, value]) => {
      if (key.startsWith("__draft_locale__:")) {
        return { locale: "", value: String(value) };
      }
      return { locale: key, value: String(value) };
    });
}

function toLocaleRecordForModel(rows: Array<{ locale: string; value: string }>): Record<string, string> {
  const result: Record<string, string> = {};
  rows.forEach((row, index) => {
    const locale = row.locale.trim();
    if (locale) {
      result[locale] = row.value;
      return;
    }
    if (row.value.trim()) {
      result[`__draft_locale__:${index}`] = row.value;
    }
  });
  return result;
}

function sameLocaleRecord(left: Record<string, string>, right: Record<string, string>): boolean {
  const leftKeys = Object.keys(left);
  const rightKeys = Object.keys(right);
  if (leftKeys.length !== rightKeys.length) {
    return false;
  }
  return leftKeys.every((key) => left[key] === right[key]);
}
