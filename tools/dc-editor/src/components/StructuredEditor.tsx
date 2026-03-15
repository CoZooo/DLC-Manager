import { ChangeEvent, TextareaHTMLAttributes, useEffect, useLayoutEffect, useRef, useState } from "react";
import { DocumentSchema, StructuredModel, StructuredValue } from "../models/editor";
import { EditorLocale, t, translateStaticText } from "../i18n/editorI18n";

interface StructuredEditorProps {
  schema: DocumentSchema;
  model: StructuredModel | null;
  enabled: boolean;
  locale: EditorLocale;
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
        <div className="panel-title">{t(props.locale, "formTitle")}</div>
        <div className="panel-note">
          {t(props.locale, "rawOnlyNote")}
        </div>
      </section>
    );
  }

  const model = props.model || {};

  return (
    <section className="panel">
      <div className="panel-title">{t(props.locale, "formTitle")}</div>
      <div className="section-stack">
        {props.schema.sections.map((section) => (
          <div key={section.key} className="section-card">
            <div className="section-title">{translateStaticText(props.locale, section.label) || section.label}</div>
            {section.description && <div className="field-help">{translateStaticText(props.locale, section.description)}</div>}
            {section.fields.length > 0 && (
            <div className="field-grid">
              {section.fields.map((field) => {
                if (field.hiddenInForm) {
                  return null;
                }
                const sectionValues = model[section.key] || {};
                if (field.visibleWhen) {
                  const visibleSection = model[field.visibleWhen.sectionKey] || {};
                  const visibleValue = visibleSection[field.visibleWhen.fieldKey];
                  const expected = field.visibleWhen.oneOf ?? (field.visibleWhen.equals ? [field.visibleWhen.equals] : []);
                  if (expected.length > 0 && !expected.includes(String(visibleValue ?? ""))) {
                    return null;
                  }
                }
                const value = sectionValues[field.key];
                const label = (
                  <label className="field-label">
                    <span>{field.label}</span>
                    {field.help && <span className="field-label-help">{translateStaticText(props.locale, field.help)}</span>}
                  </label>
                );

                const onTextChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
                  props.onChange(withValue(model, section.key, field.key, event.target.value));
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
                      <AutoResizeTextarea
                        value={typeof value === "string" ? value : ""}
                        placeholder={field.placeholder}
                        onChange={onTextChange}
                      />
                    )}
                    {field.kind === "text" && (
                      <>
                        <input
                          type="text"
                          value={typeof value === "string" ? value : ""}
                          placeholder={field.placeholder}
                          onChange={onTextChange}
                        />
                        {props.schema.type === "info" && section.key === "basic" && field.key === "file_name" && (
                          <InfoMultiFileEditor
                            locale={props.locale}
                            typeValue={typeof sectionValues.type === "string" ? sectionValues.type : ""}
                            enabled={Boolean(sectionValues.__multiFileEnabled)}
                            value={Array.isArray(sectionValues.fileNames) ? sectionValues.fileNames : []}
                            onChange={(nextEnabled, nextFiles) =>
                              props.onChange({
                                ...model,
                                [section.key]: {
                                  ...sectionValues,
                                  __multiFileEnabled: nextEnabled,
                                  fileNames: nextFiles
                                }
                              })
                            }
                          />
                        )}
                      </>
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
                      <ToggleButton
                        checked={Boolean(value)}
                        label={resolveToggleLabel(props.locale, field.help, field.toggleLabels)}
                        onToggle={() =>
                          props.onChange(withValue(model, section.key, field.key, !Boolean(value)))
                        }
                      />
                    )}
                    {field.kind === "list" && (
                      <ListFieldEditor
                        value={Array.isArray(value) ? value : []}
                        placeholder={field.placeholder || t(props.locale, "oneValuePerLine")}
                        onCommit={onListCommit}
                      />
                    )}
                    {field.kind === "ordered-select-list" && (
                      <OrderedSelectListEditor
                        locale={props.locale}
                        value={Array.isArray(value) ? value : []}
                        options={field.options || []}
                        onChange={(nextItems) => props.onChange(withValue(model, section.key, field.key, nextItems))}
                      />
                    )}
                    {field.kind === "language-visibility" && (
                      <LanguageVisibilityEditor
                        locale={props.locale}
                        value={value}
                        onChange={(nextValue) => props.onChange(withValue(model, section.key, field.key, nextValue))}
                      />
                    )}
                    {field.kind === "select" && (
                      <select
                        className={
                          field.key === "type"
                            ? "compact-select"
                            : field.key === "ShowFrequency"
                              ? "compact-select compact-select-frequency"
                              : undefined
                        }
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
            {section.dynamicLocale && (
              <DynamicLocaleSection
                locale={props.locale}
                sectionKey={section.key}
                values={model[section.key] || {}}
                reservedKeys={section.fields.map((field) => field.key)}
                valueKind={section.dynamicLocale.valueKind}
                help={translateStaticText(props.locale, section.dynamicLocale.help)}
                onChange={(nextValues) => props.onChange({
                  ...model,
                  [section.key]: nextValues
                })}
              />
            )}
          </div>
        ))}
      </div>
    </section>
  );
}

interface InfoMultiFileEditorProps {
  locale: EditorLocale;
  typeValue: string;
  enabled: boolean;
  value: string[];
  onChange(enabled: boolean, nextFiles: string[]): void;
}

function InfoMultiFileEditor(props: InfoMultiFileEditorProps) {
  const supportsMulti = props.typeValue === "others" || props.typeValue === "config";

  if (!supportsMulti) {
    return null;
  }

  return (
    <div className="info-multi-file-editor">
      <ToggleButton
        checked={props.enabled}
        label={t(props.locale, "multiFile")}
        onToggle={() => props.onChange(!props.enabled, props.value)}
      />
      {props.enabled && (
        <ListFieldEditor
          value={props.value}
          placeholder={t(props.locale, "extraFilePlaceholder")}
          onCommit={(rawText) => props.onChange(true, parseListText(rawText))}
        />
      )}
    </div>
  );
}

interface ListFieldEditorProps {
  value: string[];
  placeholder?: string;
  onCommit(rawText: string): void;
}

function parseListText(rawText: string): string[] {
  return rawText
    .split(/[\r\n,]+/)
    .map((item) => item.trim())
    .filter(Boolean);
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
    <AutoResizeTextarea
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
  locale: EditorLocale;
  sectionKey: string;
  values: Record<string, StructuredValue>;
  reservedKeys?: string[];
  valueKind: "text" | "textarea";
  help?: string;
  onChange(nextValues: Record<string, string>): void;
}

function DynamicLocaleSection(props: DynamicLocaleSectionProps) {
  const [rows, setRows] = useState<Array<{ locale: string; value: string }>>([{ locale: "", value: "" }]);
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    const record = toLocaleRecordForModel(rows, props.reservedKeys);
    const persisted = extractLocaleRows(props.values, props.reservedKeys);
    if (focused) {
      return;
    }
    if (sameLocaleRecord(record, toLocaleRecordForModel(persisted, props.reservedKeys))) {
      return;
    }
    setRows(persisted.length > 0 ? persisted : [{ locale: "", value: "" }]);
  }, [props.values, rows, focused]);

  const updateRow = (index: number, nextLocale: string, nextValue: string) => {
    const nextRows = rows.map((row, rowIndex) => rowIndex === index ? { locale: nextLocale, value: nextValue } : row);
    setRows(nextRows);
    props.onChange(toLocaleRecordForModel(nextRows, props.reservedKeys));
  };

  const addRow = () => {
    setRows((previous) => [...previous, { locale: "", value: "" }]);
  };

  const removeRow = (index: number) => {
    const nextRows = rows.filter((_, rowIndex) => rowIndex !== index);
    const normalized = nextRows.length > 0 ? nextRows : [{ locale: "", value: "" }];
    setRows(normalized);
    props.onChange(toLocaleRecordForModel(normalized, props.reservedKeys));
  };

  return (
    <div className="field-grid">
      {props.help && <div className="field-help locale-help">{props.help}</div>}
      {rows.map((row, index) => (
        <div key={`${props.sectionKey}.${index}`} className="locale-row">
          <div className="locale-row-main">
            <div className="locale-code-field">
              <label className="field-label">{t(props.locale, "localeCode")}</label>
              <input
                type="text"
                value={row.locale}
                className={!row.locale.trim() ? "locale-code-input locale-code-input-implicit" : "locale-code-input"}
                placeholder={t(props.locale, "localePlaceholder")}
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
                onChange={(event) => updateRow(index, event.target.value, row.value)}
              />
            </div>
            <div className="locale-text-field">
              <label className="field-label">{t(props.locale, "text")}</label>
              {props.valueKind === "textarea" ? (
                <AutoResizeTextarea
                  value={row.value}
                  placeholder={t(props.locale, "localeTextPlaceholder")}
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onChange={(event) => updateRow(index, row.locale, event.target.value)}
                />
              ) : (
                <input
                  type="text"
                  value={row.value}
                  placeholder={t(props.locale, "localeTextPlaceholder")}
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onChange={(event) => updateRow(index, row.locale, event.target.value)}
                />
              )}
            </div>
            <div className="locale-row-actions">
              <label className="field-label field-label-ghost">{t(props.locale, "action")}</label>
              <button type="button" onClick={() => removeRow(index)} disabled={rows.length === 1 && !row.locale && !row.value}>
                {t(props.locale, "delete")}
              </button>
            </div>
          </div>
        </div>
      ))}
      <button type="button" className="secondary-action" onClick={addRow}>
        {t(props.locale, "addLanguage")}
      </button>
    </div>
  );
}

function AutoResizeTextarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const ref = useRef<HTMLTextAreaElement | null>(null);

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) {
      return;
    }
    element.style.height = "auto";
    element.style.height = `${element.scrollHeight}px`;
  }, [props.value]);

  return (
    <textarea
      {...props}
      ref={ref}
      rows={props.rows ?? 1}
      onInput={(event) => {
        const element = event.currentTarget;
        element.style.height = "auto";
        element.style.height = `${element.scrollHeight}px`;
        props.onInput?.(event);
      }}
    />
  );
}

interface OrderedSelectListEditorProps {
  locale: EditorLocale;
  value: string[];
  options: Array<{ value: string; label: string }>;
  onChange(nextItems: string[]): void;
}

function OrderedSelectListEditor(props: OrderedSelectListEditorProps) {
  const ordered = [
    ...props.value.filter((value) => props.options.some((option) => option.value === value)),
    ...props.options.map((option) => option.value).filter((value) => !props.value.includes(value))
  ];

  const move = (index: number, delta: -1 | 1) => {
    const target = index + delta;
    if (target < 0 || target >= ordered.length) {
      return;
    }
    const next = [...ordered];
    [next[index], next[target]] = [next[target], next[index]];
    props.onChange(next);
  };

  return (
    <div className="ordered-select-list">
      <div className="ordered-select-selected">
        <div className="ordered-select-selected-title">{t(props.locale, "currentOrder")}</div>
        {ordered.map((value, index) => {
          const label = props.options.find((option) => option.value === value)?.label || value;
          return (
            <div key={value} className="ordered-select-item">
              <span>{label}</span>
              <div className="ordered-select-actions">
                <button type="button" onClick={() => move(index, -1)} disabled={index === 0}>{t(props.locale, "moveUp")}</button>
                <button type="button" onClick={() => move(index, 1)} disabled={index === ordered.length - 1}>{t(props.locale, "moveDown")}</button>
              </div>
            </div>
          );
        })}
        <div className="ordered-select-note">{t(props.locale, "orderedSelectNote")}</div>
      </div>
    </div>
  );
}

interface LanguageVisibilityEditorProps {
  locale: EditorLocale;
  value: StructuredValue | undefined;
  onChange(nextValue: string | string[]): void;
}

function LanguageVisibilityEditor(props: LanguageVisibilityEditorProps) {
  const hidden = typeof props.value === "string" && props.value.trim().toLowerCase() === "hidden";
  const items = normalizeLanguageVisibilityItems(props.value);

  const onHiddenChange = (checked: boolean) => {
    if (checked) {
      props.onChange("hidden");
      return;
    }
    props.onChange(items);
  };

  return (
    <div className="field-grid">
      <ToggleButton
        checked={hidden}
        label={t(props.locale, "hiddenAllLanguages")}
        onToggle={() => onHiddenChange(!hidden)}
      />
      {!hidden && (
        <ListFieldEditor
          value={items}
          placeholder={t(props.locale, "visibilityPlaceholder")}
          onCommit={(rawText) => props.onChange(toLanguageVisibilityValue(parseListText(rawText)))}
        />
      )}
    </div>
  );
}

function extractLocaleRows(values: Record<string, StructuredValue>, reservedKeys: string[] = []): Array<{ locale: string; value: string }> {
  return Object.entries(values)
    .filter(([key, value]) => typeof value === "string" && !reservedKeys.includes(key))
    .map(([key, value]) => {
      if (key.startsWith("__draft_locale__:")) {
        return { locale: "", value: String(value) };
      }
      return { locale: key, value: String(value) };
    });
}

function toLocaleRecordForModel(rows: Array<{ locale: string; value: string }>, reservedKeys: string[] = []): Record<string, string> {
  const result: Record<string, string> = {};
  rows.forEach((row, index) => {
    const locale = row.locale.trim();
    if (locale) {
      if (reservedKeys.includes(locale)) {
        return;
      }
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

function normalizeLanguageVisibilityItems(value: StructuredValue | undefined): string[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value !== "string") {
    return [];
  }
  if (value.trim().toLowerCase() === "hidden") {
    return [];
  }
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function toLanguageVisibilityValue(items: string[]): string[] {
  return items.map((item) => item.trim()).filter(Boolean);
}

interface ToggleButtonProps {
  checked: boolean;
  label: string;
  onToggle(): void;
}

function ToggleButton(props: ToggleButtonProps) {
  return (
    <button
      type="button"
      className={`toggle-button${props.checked ? " is-active" : ""}`}
      aria-pressed={props.checked}
      onClick={props.onToggle}
    >
      <span className={`toggle-button-box${props.checked ? " is-active" : ""}`} aria-hidden="true">
        {props.checked ? "✓" : ""}
      </span>
      <span className="toggle-button-label">{props.label}</span>
    </button>
  );
}

function resolveToggleLabel(
  locale: EditorLocale,
  help: string | undefined,
  toggleLabels: { checked: string; unchecked: string } | undefined
): string {
  if (toggleLabels) {
    return translateStaticText(locale, toggleLabels.checked) || toggleLabels.checked;
  }
  if (help?.includes("是否")) {
    return t(locale, "yes");
  }
  return t(locale, "enabled");
}
