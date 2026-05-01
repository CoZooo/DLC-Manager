import { ChangeEvent, InputHTMLAttributes, KeyboardEvent as ReactKeyboardEvent, MouseEvent, ReactElement, ReactNode, TextareaHTMLAttributes, cloneElement, isValidElement, useEffect, useLayoutEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { DocumentSchema, ROOT_SECTION_KEY, StructuredModel, StructuredValue } from "../models/editor";
import { EditorLocale, t, translateStaticText } from "../i18n/editorI18n";

interface StructuredEditorProps {
  schema: DocumentSchema;
  model: StructuredModel | null;
  enabled: boolean;
  locale: EditorLocale;
  fileName?: string;
  onFileNameChange?(nextFileName: string): void;
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

function withoutValue(
  model: StructuredModel,
  sectionKey: string,
  fieldKey: string
): StructuredModel {
  const sectionValues = model[sectionKey] || {};
  const { [fieldKey]: _, ...rest } = sectionValues;
  return {
    ...model,
    [sectionKey]: rest
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
      {props.fileName !== undefined && props.onFileNameChange && (
        <div className="section-card document-name-card">
          <div className="field">
            <label className="field-label">
              <span>{t(props.locale, "fileName")}</span>
              <span className="field-label-help">{t(props.locale, "fileNameHelp")}</span>
            </label>
            <UndoableInput
              type="text"
              value={props.fileName}
              onChangeValue={props.onFileNameChange}
            />
          </div>
        </div>
      )}
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
                    {props.schema.type === "update" && section.key === ROOT_SECTION_KEY && field.key === "UpdateDLCM" && (
                      <UpdateDLCMField
                        locale={props.locale}
                        value={value}
                        placeholder={field.placeholder}
                        onChange={(nextValue) => {
                          if (nextValue === undefined) {
                            props.onChange(withoutValue(model, section.key, field.key));
                            return;
                          }
                          props.onChange(withValue(model, section.key, field.key, nextValue));
                        }}
                      />
                    )}
                    {field.kind === "textarea" && (
                      <FormattedTextarea
                        locale={props.locale}
                        value={typeof value === "string" ? value : ""}
                        toolbarMode={field.toolbarMode ?? "mc"}
                        placeholder={field.placeholder}
                        onChangeValue={(nextValue) => props.onChange(withValue(model, section.key, field.key, nextValue))}
                      />
                    )}
                    {field.kind === "text" && !(props.schema.type === "update" && section.key === ROOT_SECTION_KEY && field.key === "UpdateDLCM") && (
                      <>
                        <UndoableInput
                          type="text"
                          value={typeof value === "string" ? value : ""}
                          placeholder={field.placeholder}
                          onChangeValue={(nextValue) =>
                            props.onChange(withValue(model, section.key, field.key, nextValue))
                          }
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
                      <UndoableInput
                        type="number"
                        value={String(typeof value === "number" ? value : 0)}
                        placeholder={field.placeholder}
                        onChangeValue={(nextValue) => {
                          const raw = nextValue.trim();
                          props.onChange(withValue(model, section.key, field.key, raw === "" ? 0 : Number.parseInt(raw, 10) || 0));
                        }}
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
                    {field.kind === "update-file-del" && (
                      <UpdateDeleteEditor
                        locale={props.locale}
                        value={typeof value === "string" ? value : ""}
                        onChange={(nextValue) => props.onChange(withValue(model, section.key, field.key, nextValue))}
                      />
                    )}
                    {field.kind === "update-file-add" && (
                      <UpdateAddEditor
                        locale={props.locale}
                        value={typeof value === "string" ? value : ""}
                        onChange={(nextValue) => props.onChange(withValue(model, section.key, field.key, nextValue))}
                      />
                    )}
                    {field.kind === "update-file-move" && (
                      <UpdateMoveEditor
                        locale={props.locale}
                        value={typeof value === "string" ? value : ""}
                        onChange={(nextValue) => props.onChange(withValue(model, section.key, field.key, nextValue))}
                      />
                    )}
                    {field.kind === "update-file-modify" && (
                      <UpdateModifyEditor
                        locale={props.locale}
                        value={typeof value === "string" ? value : ""}
                        onChange={(nextValue) => props.onChange(withValue(model, section.key, field.key, nextValue))}
                      />
                    )}
                    {field.kind === "select" && (
                      <select
                        className={
                          field.key === "type"
                            ? "compact-select"
                            : field.key === "platform" && section.key === "version"
                              ? "compact-select compact-select-platform"
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
                toolbarMode={section.dynamicLocale.toolbarMode}
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

interface UpdateDLCMFieldProps {
  locale: EditorLocale;
  value: StructuredValue | undefined;
  placeholder?: string;
  onChange(nextValue: StructuredValue | undefined): void;
}

function UpdateDLCMField(props: UpdateDLCMFieldProps) {
  const currentMode = props.value === true
    ? "latest"
    : typeof props.value === "string"
      ? "version"
      : "off";
  const [mode, setMode] = useState<"off" | "latest" | "version">(currentMode);
  const [versionDraft, setVersionDraft] = useState(typeof props.value === "string" ? props.value : "");

  useEffect(() => {
    const nextMode = props.value === true
      ? "latest"
      : typeof props.value === "string"
        ? "version"
        : "off";
    setMode(nextMode);
    setVersionDraft(typeof props.value === "string" ? props.value : "");
  }, [props.value]);

  return (
    <div className="update-dlcm-field">
      <select
        className="compact-select"
        value={mode}
        onChange={(event) => {
          const nextMode = event.target.value as "off" | "latest" | "version";
          setMode(nextMode);
          if (nextMode === "off") {
            props.onChange(undefined);
            return;
          }
          if (nextMode === "latest") {
            props.onChange(true);
            return;
          }
        }}
      >
        <option value="off">{t(props.locale, "updateDLCMOff")}</option>
        <option value="latest">{t(props.locale, "updateDLCMLatest")}</option>
        <option value="version">{t(props.locale, "updateDLCMVersion")}</option>
      </select>
      {mode === "version" && (
        <UndoableInput
          type="text"
          value={versionDraft}
          onChangeValue={(nextValue) => {
            setVersionDraft(nextValue);
          }}
          onBlur={() => {
            props.onChange(versionDraft.trim());
          }}
        />
      )}
    </div>
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
    <UndoableAutoResizeTextarea
      value={draft}
      placeholder={props.placeholder}
      onFocus={() => setFocused(true)}
      onBlur={() => {
        setFocused(false);
        props.onCommit(draft);
      }}
      onChangeValue={setDraft}
    />
  );
}

interface DynamicLocaleSectionProps {
  locale: EditorLocale;
  sectionKey: string;
  values: Record<string, StructuredValue>;
  reservedKeys?: string[];
  valueKind: "text" | "textarea";
  toolbarMode?: "mc" | "mc-markdown";
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
              <UndoableInput
                type="text"
                value={row.locale}
                className={!row.locale.trim() ? "locale-code-input locale-code-input-implicit" : "locale-code-input"}
                placeholder={t(props.locale, "localePlaceholder")}
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
                onChangeValue={(nextValue) => updateRow(index, nextValue, row.value)}
              />
            </div>
            <div className="locale-text-field">
              <label className="field-label">{t(props.locale, "text")}</label>
              {props.valueKind === "textarea" ? (
                <FormattedTextarea
                  locale={props.locale}
                  value={row.value}
                  toolbarMode={props.toolbarMode ?? "mc"}
                  placeholder={t(props.locale, "localeTextPlaceholder")}
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onChangeValue={(nextValue) => updateRow(index, row.locale, nextValue)}
                />
              ) : (
                <UndoableInput
                  type="text"
                  value={row.value}
                  placeholder={t(props.locale, "localeTextPlaceholder")}
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onChangeValue={(nextValue) => updateRow(index, row.locale, nextValue)}
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

type MutableInputRef = { current: HTMLInputElement | null };
type MutableTextareaRef = { current: HTMLTextAreaElement | null };
type TextareaRefProp = MutableTextareaRef | ((node: HTMLTextAreaElement | null) => void);

interface AutoResizeTextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  textareaRef?: TextareaRefProp;
}

function AutoResizeTextarea(props: AutoResizeTextareaProps) {
  const internalRef = useRef<HTMLTextAreaElement | null>(null);
  const { textareaRef, ...textareaProps } = props;
  const ref = internalRef;

  useEffect(() => {
    if (!textareaRef) {
      return;
    }
    if (typeof textareaRef === "function") {
      textareaRef(ref.current);
      return;
    }
    textareaRef.current = ref.current;
  }, [textareaRef]);

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
      {...textareaProps}
      ref={ref}
      rows={textareaProps.rows ?? 1}
      onInput={(event) => {
        const element = event.currentTarget;
        element.style.height = "auto";
        element.style.height = `${element.scrollHeight}px`;
        textareaProps.onInput?.(event);
      }}
    />
  );
}

function useUndoableTextController<T extends HTMLInputElement | HTMLTextAreaElement>(
  value: string,
  onChangeValue: (nextValue: string) => void
) {
  const elementRef = useRef<T | null>(null);
  const [draft, setDraft] = useState(value);
  const historyRef = useRef<Array<{ value: string; selectionStart: number; selectionEnd: number }>>([
    { value, selectionStart: value.length, selectionEnd: value.length }
  ]);
  const historyIndexRef = useRef(0);
  const selectionStartRef = useRef(value.length);
  const selectionEndRef = useRef(value.length);
  const pendingSelectionRef = useRef<{ start: number; end: number } | null>(null);
  const shouldRestoreFocusRef = useRef(false);
  const localEchoRef = useRef<string | null>(null);

  useEffect(() => {
    if (localEchoRef.current !== null) {
      if (value === localEchoRef.current) {
        localEchoRef.current = null;
      }
      return;
    }
    if (value !== draft) {
      setDraft(value);
      selectionStartRef.current = value.length;
      selectionEndRef.current = value.length;
      historyRef.current = [{ value, selectionStart: value.length, selectionEnd: value.length }];
      historyIndexRef.current = 0;
    }
  }, [value, draft]);

  useLayoutEffect(() => {
    const pending = pendingSelectionRef.current;
    const element = elementRef.current;
    if (!pending || !element) {
      return;
    }
    pendingSelectionRef.current = null;
    const shouldRestoreFocus = shouldRestoreFocusRef.current;
    shouldRestoreFocusRef.current = false;
    if (shouldRestoreFocus) {
      element.focus();
    }
    element.setSelectionRange(pending.start, pending.end);
  }, [draft]);

  const syncSelection = (element?: T | null) => {
    const target = element ?? elementRef.current;
    if (!target) {
      return;
    }
    selectionStartRef.current = target.selectionStart ?? draft.length;
    selectionEndRef.current = target.selectionEnd ?? draft.length;
  };

  const pushSnapshot = (nextValue: string, nextSelectionStart: number, nextSelectionEnd: number) => {
    const current = historyRef.current[historyIndexRef.current];
    if (
      current &&
      current.value === nextValue &&
      current.selectionStart === nextSelectionStart &&
      current.selectionEnd === nextSelectionEnd
    ) {
      return;
    }
    const nextHistory = historyRef.current
      .slice(0, historyIndexRef.current + 1)
      .concat([{ value: nextValue, selectionStart: nextSelectionStart, selectionEnd: nextSelectionEnd }]);
    historyRef.current = nextHistory.slice(-100);
    historyIndexRef.current = historyRef.current.length - 1;
  };

  const commitValue = (nextValue: string, nextSelectionStart: number, nextSelectionEnd: number, forceFocus = false) => {
    selectionStartRef.current = nextSelectionStart;
    selectionEndRef.current = nextSelectionEnd;
    pushSnapshot(nextValue, nextSelectionStart, nextSelectionEnd);
    pendingSelectionRef.current = { start: nextSelectionStart, end: nextSelectionEnd };
    shouldRestoreFocusRef.current = forceFocus || (typeof window !== "undefined" && window.document.activeElement === elementRef.current);
    localEchoRef.current = nextValue;
    setDraft(nextValue);
    onChangeValue(nextValue);
    const element = elementRef.current;
    if (element) {
      element.value = nextValue;
    }
  };

  const handleChange = (event: ChangeEvent<T>) => {
    const nextValue = event.target.value;
    selectionStartRef.current = event.target.selectionStart ?? nextValue.length;
    selectionEndRef.current = event.target.selectionEnd ?? nextValue.length;
    pushSnapshot(nextValue, selectionStartRef.current, selectionEndRef.current);
    localEchoRef.current = nextValue;
    setDraft(nextValue);
    onChangeValue(nextValue);
  };

  const restoreSnapshot = (snapshot: { value: string; selectionStart: number; selectionEnd: number }) => {
    selectionStartRef.current = snapshot.selectionStart;
    selectionEndRef.current = snapshot.selectionEnd;
    pendingSelectionRef.current = { start: snapshot.selectionStart, end: snapshot.selectionEnd };
    shouldRestoreFocusRef.current = true;
    localEchoRef.current = snapshot.value;
    setDraft(snapshot.value);
    onChangeValue(snapshot.value);
    const element = elementRef.current;
    if (element) {
      element.value = snapshot.value;
    }
  };

  const handleKeyDown = (event: ReactKeyboardEvent<T>) => {
    if (!isNativeUndoRedoShortcut(event)) {
      return;
    }
    event.stopPropagation();
    const key = event.key.toLowerCase();
    const isUndo = key === "z" && !event.shiftKey;
    const isRedo = (key === "z" && event.shiftKey) || key === "y";
    if (isUndo) {
      if (historyIndexRef.current === 0) {
        return;
      }
      event.preventDefault();
      historyIndexRef.current -= 1;
      restoreSnapshot(historyRef.current[historyIndexRef.current]);
      return;
    }
    if (isRedo) {
      if (historyIndexRef.current >= historyRef.current.length - 1) {
        return;
      }
      event.preventDefault();
      historyIndexRef.current += 1;
      restoreSnapshot(historyRef.current[historyIndexRef.current]);
    }
  };

  return {
    draft,
    elementRef,
    selectionStartRef,
    selectionEndRef,
    syncSelection,
    commitValue,
    handleChange,
    handleKeyDown
  };
}

function isNativeUndoRedoShortcut(event: ReactKeyboardEvent<HTMLElement>): boolean {
  const isMod = event.metaKey || event.ctrlKey;
  if (!isMod || event.altKey) {
    return false;
  }
  const key = event.key.toLowerCase();
  return (key === "z") || (key === "y");
}

interface UndoableInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> {
  value: string;
  inputRef?: MutableInputRef;
  onChangeValue(nextValue: string): void;
}

function UndoableInput(props: UndoableInputProps) {
  const {
    value,
    inputRef,
    onChangeValue,
    onSelect,
    onClick,
    onKeyUp,
    ...inputProps
  } = props;
  const controller = useUndoableTextController<HTMLInputElement>(value, onChangeValue);

  return (
    <input
      {...inputProps}
      ref={(element) => {
        controller.elementRef.current = element;
        if (inputRef) {
          inputRef.current = element;
        }
      }}
      value={controller.draft}
      onChange={controller.handleChange}
      onKeyDown={(event) => {
        controller.handleKeyDown(event);
      }}
      onSelect={(event) => {
        controller.syncSelection(event.currentTarget);
        onSelect?.(event);
      }}
      onClick={(event) => {
        controller.syncSelection(event.currentTarget);
        onClick?.(event);
      }}
      onKeyUp={(event) => {
        controller.syncSelection(event.currentTarget);
        onKeyUp?.(event);
      }}
    />
  );
}

interface UndoableAutoResizeTextareaProps extends Omit<AutoResizeTextareaProps, "value" | "onChange"> {
  value: string;
  onChangeValue(nextValue: string): void;
}

function UndoableAutoResizeTextarea(props: UndoableAutoResizeTextareaProps) {
  const {
    value,
    onChangeValue,
    onSelect,
    onClick,
    onKeyUp,
    textareaRef,
    ...textareaProps
  } = props;
  const controller = useUndoableTextController<HTMLTextAreaElement>(value, onChangeValue);

  return (
    <AutoResizeTextarea
      {...textareaProps}
      textareaRef={(element) => {
        controller.elementRef.current = element;
        if (textareaRef && typeof textareaRef !== "function") {
          textareaRef.current = element;
        }
        if (typeof textareaRef === "function") {
          textareaRef(element);
        }
      }}
      value={controller.draft}
      onChange={controller.handleChange}
      onKeyDown={(event) => {
        controller.handleKeyDown(event);
      }}
      onSelect={(event) => {
        controller.syncSelection(event.currentTarget);
        onSelect?.(event);
      }}
      onClick={(event) => {
        controller.syncSelection(event.currentTarget);
        onClick?.(event);
      }}
      onKeyUp={(event) => {
        controller.syncSelection(event.currentTarget);
        onKeyUp?.(event);
      }}
    />
  );
}

interface FormattedTextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "onChange" | "value"> {
  locale: EditorLocale;
  value: string;
  toolbarMode?: "mc" | "mc-markdown";
  onChangeValue(nextValue: string): void;
}

const MC_COLOR_PRESETS = [
  "#000000", "#0000aa", "#00aa00", "#00aaaa",
  "#aa0000", "#aa00aa", "#ffaa00", "#aaaaaa",
  "#555555", "#5555ff", "#55ff55", "#55ffff",
  "#ff5555", "#ff55ff", "#ffff55", "#ffffff"
];

const MC_CLASSIC_COLORS: Record<string, string> = {
  "0": "#000000",
  "1": "#0000aa",
  "2": "#00aa00",
  "3": "#00aaaa",
  "4": "#aa0000",
  "5": "#aa00aa",
  "6": "#ffaa00",
  "7": "#aaaaaa",
  "8": "#555555",
  "9": "#5555ff",
  "a": "#55ff55",
  "b": "#55ffff",
  "c": "#ff5555",
  "d": "#ff55ff",
  "e": "#ffff55",
  "f": "#ffffff"
};

function normalizeMcHexColor(raw: string, fallback: string): string {
  const trimmed = raw.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(trimmed)) {
    return trimmed.toLowerCase();
  }
  if (/^[0-9a-fA-F]{6}$/.test(trimmed)) {
    return `#${trimmed.toLowerCase()}`;
  }
  return fallback;
}

interface McTextStyleState {
  color?: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  strikethrough?: boolean;
  obfuscated?: boolean;
}

function buildMcStyle(style: McTextStyleState) {
  return {
    color: style.color,
    fontWeight: style.bold ? 700 : undefined,
    fontStyle: style.italic ? "italic" : undefined,
    textDecoration: [
      style.underline ? "underline" : "",
      style.strikethrough ? "line-through" : ""
    ].filter(Boolean).join(" ") || undefined
  };
}

function nextMcState(style: McTextStyleState, code: string): McTextStyleState {
  const lowered = code.toLowerCase();
  if (lowered === "r") {
    return {};
  }
  if (MC_CLASSIC_COLORS[lowered]) {
    return { color: MC_CLASSIC_COLORS[lowered] };
  }
  if (lowered === "l") {
    return { ...style, bold: true };
  }
  if (lowered === "o") {
    return { ...style, italic: true };
  }
  if (lowered === "n") {
    return { ...style, underline: true };
  }
  if (lowered === "m") {
    return { ...style, strikethrough: true };
  }
  if (lowered === "k") {
    return { ...style, obfuscated: true };
  }
  return style;
}

function randomizeObfuscatedText(source: string): string {
  const pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*+-=?<>[]{}()/_~";

  return Array.from(source).map((char) => {
    if (/\s/.test(char)) {
      return char;
    }
    return pool[Math.floor(Math.random() * pool.length)];
  }).join("");
}

interface McObfuscatedSpanProps {
  text: string;
  style: ReturnType<typeof buildMcStyle>;
}

function McObfuscatedSpan(props: McObfuscatedSpanProps) {
  const [displayText, setDisplayText] = useState(() => randomizeObfuscatedText(props.text));

  useEffect(() => {
    setDisplayText(randomizeObfuscatedText(props.text));
    const timer = window.setInterval(() => {
      setDisplayText(randomizeObfuscatedText(props.text));
    }, 90);
    return () => window.clearInterval(timer);
  }, [props.text]);

  return (
    <span className="mc-preview-obfuscated" style={props.style}>
      {displayText}
    </span>
  );
}

function renderMcFormattedText(value: string): ReactNode {
  if (!value) {
    return null;
  }

  const lines = value.split(/\r?\n/);
  return lines.map((line, lineIndex) => {
    const parts: ReactNode[] = [];
    let buffer = "";
    let style: McTextStyleState = {};

    const flush = (key: string) => {
      if (!buffer) {
        return;
      }
      const renderedStyle = buildMcStyle(style);
      if (style.obfuscated) {
        parts.push(
          <McObfuscatedSpan
            key={key}
            text={buffer}
            style={renderedStyle}
          />
        );
      } else {
        parts.push(
          <span key={key} style={renderedStyle}>
            {buffer}
          </span>
        );
      }
      buffer = "";
    };

    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if ((ch === "§" || ch === "&") && i + 1 < line.length) {
        const marker = line[i + 1];
        if (marker === "#" && i + 7 < line.length) {
          const hex = line.slice(i + 2, i + 8);
          if (/^[0-9a-fA-F]{6}$/.test(hex)) {
            flush(`${lineIndex}-${parts.length}`);
            style = { color: `#${hex.toLowerCase()}` };
            i += 7;
            continue;
          }
        }
        const lowered = marker.toLowerCase();
        if (MC_CLASSIC_COLORS[lowered] || "lonmrk".includes(lowered)) {
          flush(`${lineIndex}-${parts.length}`);
          style = nextMcState(style, lowered);
          i += 1;
          continue;
        }
      }
      buffer += ch;
    }

    flush(`${lineIndex}-${parts.length}`);
    return (
      <div key={`mc-line-${lineIndex}`} className="mc-preview-line">
        {parts.length > 0 ? parts : <br />}
      </div>
    );
  });
}

function renderMcInlineText(value: string, keyPrefix: string): ReactNode[] {
  if (!value) {
    return [];
  }

  const parts: ReactNode[] = [];
  let buffer = "";
  let style: McTextStyleState = {};

  const flush = (key: string) => {
    if (!buffer) {
      return;
    }
    const renderedStyle = buildMcStyle(style);
    if (style.obfuscated) {
      parts.push(
        <McObfuscatedSpan
          key={key}
          text={buffer}
          style={renderedStyle}
        />
      );
    } else {
      parts.push(
        <span key={key} style={renderedStyle}>
          {buffer}
        </span>
      );
    }
    buffer = "";
  };

  for (let i = 0; i < value.length; i++) {
    const ch = value[i];
    if ((ch === "§" || ch === "&") && i + 1 < value.length) {
      const marker = value[i + 1];
      if (marker === "#" && i + 7 < value.length) {
        const hex = value.slice(i + 2, i + 8);
        if (/^[0-9a-fA-F]{6}$/.test(hex)) {
          flush(`${keyPrefix}-${parts.length}`);
          style = { color: `#${hex.toLowerCase()}` };
          i += 7;
          continue;
        }
      }
      const lowered = marker.toLowerCase();
      if (MC_CLASSIC_COLORS[lowered] || "lonmrk".includes(lowered)) {
        flush(`${keyPrefix}-${parts.length}`);
        style = nextMcState(style, lowered);
        i += 1;
        continue;
      }
    }
    buffer += ch;
  }

  flush(`${keyPrefix}-${parts.length}`);
  return parts;
}

function renderMcMarkdownChildren(children: ReactNode, keyPrefix: string): ReactNode {
  return Array.isArray(children)
    ? children.map((child, index) => renderMcMarkdownNode(child, `${keyPrefix}-${index}`))
    : renderMcMarkdownNode(children, keyPrefix);
}

function renderMcMarkdownNode(node: ReactNode, keyPrefix: string): ReactNode {
  if (typeof node === "string") {
    return renderMcInlineText(node, keyPrefix);
  }
  if (Array.isArray(node)) {
    return node.map((child, index) => renderMcMarkdownNode(child, `${keyPrefix}-${index}`));
  }
  if (isValidElement(node)) {
    const element = node as ReactElement<{ children?: ReactNode }>;
    return cloneElement(element, {
      children: renderMcMarkdownChildren(element.props.children, `${keyPrefix}-child`)
    });
  }
  return node;
}

function renderMarkdownPreview(value: string): ReactNode {
  return (
    <div className="notice-markdown notice-markdown-preview">
      <ReactMarkdown
        components={{
          h1: ({ children }) => <h1>{renderMcMarkdownChildren(children, "h1")}</h1>,
          h2: ({ children }) => <h2>{renderMcMarkdownChildren(children, "h2")}</h2>,
          h3: ({ children }) => <h3>{renderMcMarkdownChildren(children, "h3")}</h3>,
          h4: ({ children }) => <h4>{renderMcMarkdownChildren(children, "h4")}</h4>,
          p: ({ children }) => <p>{renderMcMarkdownChildren(children, "p")}</p>,
          li: ({ children }) => <li>{renderMcMarkdownChildren(children, "li")}</li>,
          blockquote: ({ children }) => <blockquote>{renderMcMarkdownChildren(children, "blockquote")}</blockquote>,
          strong: ({ children }) => <strong>{renderMcMarkdownChildren(children, "strong")}</strong>,
          em: ({ children }) => <em>{renderMcMarkdownChildren(children, "em")}</em>,
          a: ({ href, children }) => <a href={href}>{renderMcMarkdownChildren(children, "a")}</a>,
          code: ({ children, ...rest }) => <code {...rest}>{renderMcMarkdownChildren(children, "code")}</code>,
          pre: ({ children }) => <pre>{children}</pre>
        }}
      >
        {value}
      </ReactMarkdown>
    </div>
  );
}

function FormattedTextarea(props: FormattedTextareaProps) {
  const nativeColorInputRef = useRef<HTMLInputElement | null>(null);
  const colorDraftInputRef = useRef<HTMLInputElement | null>(null);
  const [selectedColor, setSelectedColor] = useState("#55ffff");
  const [colorDraft, setColorDraft] = useState("#55ffff");
  const selectedColorRef = useRef("#55ffff");
  const [showColorPicker, setShowColorPicker] = useState(false);
  const { locale, value, onChangeValue, toolbarMode = "mc", ...inputProps } = props;
  const controller = useUndoableTextController<HTMLTextAreaElement>(value, onChangeValue);

  const updateSelectedColor = (nextColor: string) => {
    selectedColorRef.current = nextColor;
    setSelectedColor(nextColor);
    setColorDraft(nextColor);
  };

  const keepTextareaSelection = (event: MouseEvent<HTMLElement>) => {
    event.preventDefault();
  };

  const syncSelectionFromTextarea = () => {
    controller.syncSelection();
  };

  const commitTextareaValue = (nextValue: string, nextSelectionStart: number, nextSelectionEnd: number, forceFocus = false) => {
    controller.commitValue(nextValue, nextSelectionStart, nextSelectionEnd, forceFocus);
  };

  const restoreTextareaCursor = (position: number) => {
    if (typeof window === "undefined") {
      return;
    }
    const apply = () => {
      const element = controller.elementRef.current;
      if (!element) {
        return;
      }
      element.focus();
      element.setSelectionRange(position, position);
      controller.selectionStartRef.current = position;
      controller.selectionEndRef.current = position;
    };
    window.requestAnimationFrame(() => {
      apply();
      window.requestAnimationFrame(apply);
    });
  };

  const applyWrap = (prefix: string, suffix = "") => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const end = Math.min(controller.selectionEndRef.current, currentValue.length);
    const selected = currentValue.slice(start, end);
    const nextValue = `${currentValue.slice(0, start)}${prefix}${selected}${suffix}${currentValue.slice(end)}`;
    const nextCursor = start + prefix.length + selected.length + suffix.length;
    commitTextareaValue(nextValue, nextCursor, nextCursor);
  };

  const insertAtCursor = (snippet: string) => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const end = Math.min(controller.selectionEndRef.current, currentValue.length);
    const nextValue = `${currentValue.slice(0, start)}${snippet}${currentValue.slice(end)}`;
    const nextCursor = start + snippet.length;
    commitTextareaValue(nextValue, nextCursor, nextCursor);
  };

  const applyColorInsert = (colorCode: string) => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const end = Math.min(controller.selectionEndRef.current, currentValue.length);
    if (end > start) {
      const selected = currentValue.slice(start, end);
      const resetCode = "§r";
      const nextValue = `${currentValue.slice(0, start)}${colorCode}${selected}${resetCode}${currentValue.slice(end)}`;
      const nextCursor = start + colorCode.length + selected.length + resetCode.length;
      commitTextareaValue(nextValue, nextCursor, nextCursor, true);
      restoreTextareaCursor(nextCursor);
      return;
    }
    const nextCursor = start + colorCode.length;
    const nextValue = `${currentValue.slice(0, start)}${colorCode}${currentValue.slice(end)}`;
    commitTextareaValue(nextValue, nextCursor, nextCursor, true);
    restoreTextareaCursor(nextCursor);
  };

  const applyMarkdownWrap = (prefix: string, suffix = prefix) => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const end = Math.min(controller.selectionEndRef.current, currentValue.length);
    const selected = currentValue.slice(start, end);
    const content = selected || t(locale, "markdownPlaceholder");
    const nextValue = `${currentValue.slice(0, start)}${prefix}${content}${suffix}${currentValue.slice(end)}`;
    const nextCursor = start + prefix.length + content.length + suffix.length;
    commitTextareaValue(nextValue, nextCursor, nextCursor);
  };

  const applyMarkdownLinePrefix = (prefix: string) => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const end = Math.min(controller.selectionEndRef.current, currentValue.length);
    const lineStart = currentValue.lastIndexOf("\n", Math.max(0, start - 1)) + 1;
    const lineEndIndex = currentValue.indexOf("\n", end);
    const lineEnd = lineEndIndex === -1 ? currentValue.length : lineEndIndex;
    const selectedBlock = currentValue.slice(lineStart, lineEnd);
    const content = selectedBlock || t(locale, "markdownPlaceholder");
    const transformed = content
      .split(/\r?\n/)
      .map((line) => `${prefix}${line}`)
      .join("\n");
    const nextValue = `${currentValue.slice(0, lineStart)}${transformed}${currentValue.slice(lineEnd)}`;
    const nextCursor = lineStart + transformed.length;
    commitTextareaValue(nextValue, nextCursor, nextCursor);
  };

  const insertMarkdownLink = () => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const end = Math.min(controller.selectionEndRef.current, currentValue.length);
    const content = currentValue.slice(start, end) || t(locale, "markdownPlaceholder");
    const snippet = `[${content}](https://example.com)`;
    const nextValue = `${currentValue.slice(0, start)}${snippet}${currentValue.slice(end)}`;
    const nextCursor = start + snippet.length;
    commitTextareaValue(nextValue, nextCursor, nextCursor);
  };

  const insertMarkdownOrderedList = () => {
    const currentValue = controller.draft ?? "";
    const start = Math.min(controller.selectionStartRef.current, currentValue.length);
    const lineStart = currentValue.lastIndexOf("\n", Math.max(0, start - 1)) + 1;
    const beforeLines = currentValue.slice(0, lineStart).split(/\r?\n/);
    let nextNumber = 1;
    for (let index = beforeLines.length - 1; index >= 0; index -= 1) {
      const match = beforeLines[index].match(/^\s*(\d+)\.\s+/);
      if (match) {
        nextNumber = Number.parseInt(match[1], 10) + 1;
        break;
      }
      if (beforeLines[index].trim()) {
        break;
      }
    }
    insertAtCursor(`${nextNumber}. `);
  };

  return (
    <div className="formatted-textarea">
      {toolbarMode === "mc-markdown" && (
        <div className="format-toolbar markdown-toolbar">
          <span className="format-toolbar-label">{t(locale, "markdownToolbar")}</span>
          <button type="button" title={t(locale, "markdownHeading")} onMouseDown={keepTextareaSelection} onClick={() => applyMarkdownLinePrefix("## ")}>
            {t(locale, "markdownHeading")}
          </button>
          <button type="button" title={t(locale, "markdownBold")} onMouseDown={keepTextareaSelection} onClick={() => applyMarkdownWrap("**")}>
            {t(locale, "markdownBold")}
          </button>
          <button type="button" title={t(locale, "markdownItalic")} onMouseDown={keepTextareaSelection} onClick={() => applyMarkdownWrap("*")}>
            {t(locale, "markdownItalic")}
          </button>
          <button type="button" title={t(locale, "markdownQuote")} onMouseDown={keepTextareaSelection} onClick={() => applyMarkdownLinePrefix("> ")}>
            {t(locale, "markdownQuote")}
          </button>
          <button type="button" title={t(locale, "markdownList")} onMouseDown={keepTextareaSelection} onClick={() => applyMarkdownLinePrefix("- ")}>
            {t(locale, "markdownList")}
          </button>
          <button type="button" title={t(locale, "markdownOrderedList")} onMouseDown={keepTextareaSelection} onClick={insertMarkdownOrderedList}>
            {t(locale, "markdownOrderedList")}
          </button>
          <button type="button" title={t(locale, "markdownLink")} onMouseDown={keepTextareaSelection} onClick={insertMarkdownLink}>
            {t(locale, "markdownLink")}
          </button>
          <button type="button" title={t(locale, "markdownInlineCode")} onMouseDown={keepTextareaSelection} onClick={() => applyMarkdownWrap("`")}>
            {t(locale, "markdownInlineCode")}
          </button>
          <button type="button" title={t(locale, "markdownDivider")} onMouseDown={keepTextareaSelection} onClick={() => insertAtCursor("\n---\n")}>
            {t(locale, "markdownDivider")}
          </button>
        </div>
      )}
      <div className="format-toolbar">
        {toolbarMode === "mc-markdown" && (
          <span className="format-toolbar-label">{t(locale, "mcToolbar")}</span>
        )}
        <button type="button" title={t(locale, "mcBold")} onMouseDown={keepTextareaSelection} onClick={() => applyWrap("§l")}>{t(locale, "mcBold")}</button>
        <button type="button" title={t(locale, "mcItalic")} onMouseDown={keepTextareaSelection} onClick={() => applyWrap("§o")}>{t(locale, "mcItalic")}</button>
        <button type="button" title={t(locale, "mcUnderline")} onMouseDown={keepTextareaSelection} onClick={() => applyWrap("§n")}>{t(locale, "mcUnderline")}</button>
        <button type="button" title={t(locale, "mcStrikethrough")} onMouseDown={keepTextareaSelection} onClick={() => applyWrap("§m")}>{t(locale, "mcStrikethrough")}</button>
        <button type="button" title={t(locale, "mcObfuscated")} onMouseDown={keepTextareaSelection} onClick={() => applyWrap("§k")}>{t(locale, "mcObfuscated")}</button>
        <button type="button" title={t(locale, "mcReset")} onMouseDown={keepTextareaSelection} onClick={() => insertAtCursor("§r")}>{t(locale, "mcReset")}</button>
        <div className="format-color-picker">
          <button type="button" title={t(locale, "mcColor")} onMouseDown={keepTextareaSelection} onClick={() => setShowColorPicker((value) => !value)}>
            {t(locale, "mcColor")}
          </button>
          {showColorPicker && (
            <div className="format-color-popover">
              <div className="format-color-current">
                <button
                  type="button"
                  className="format-color-swatch format-color-swatch-button"
                  style={{ backgroundColor: selectedColor }}
                  title={t(locale, "mcColor")}
                  onMouseDown={keepTextareaSelection}
                  onClick={() => nativeColorInputRef.current?.click()}
                  aria-label={t(locale, "mcColor")}
                />
                <UndoableInput
                  inputRef={colorDraftInputRef}
                  type="text"
                  value={colorDraft}
                  placeholder="#55ffff"
                  onChangeValue={(nextDraft) => {
                    setColorDraft(nextDraft);
                    if (/^#?[0-9a-fA-F]{6}$/.test(nextDraft.trim())) {
                      updateSelectedColor(normalizeMcHexColor(nextDraft, selectedColorRef.current));
                    }
                  }}
                  onBlur={() => {
                    const normalized = normalizeMcHexColor(colorDraft, selectedColorRef.current);
                    updateSelectedColor(normalized);
                  }}
                />
                <input
                  ref={nativeColorInputRef}
                  className="format-color-native-input"
                  type="color"
                  value={selectedColor}
                  onInput={(event) => {
                    updateSelectedColor((event.target as HTMLInputElement).value);
                  }}
                  onChange={(event) => {
                    updateSelectedColor(event.target.value);
                  }}
                />
              </div>
              <div className="format-color-grid">
                {MC_COLOR_PRESETS.map((color) => (
                  <button
                    key={color}
                    type="button"
                    className={`format-color-chip${selectedColor === color ? " is-active" : ""}`}
                    style={{ backgroundColor: color }}
                    title={color}
                    onMouseDown={keepTextareaSelection}
                    onClick={() => updateSelectedColor(color)}
                    aria-label={color}
                  />
                ))}
              </div>
              <button
                type="button"
                className="format-color-insert"
                title={t(locale, "mcInsertColor")}
                onMouseDown={(event) => {
                  keepTextareaSelection(event);
                  const draftValue = colorDraftInputRef.current?.value ?? colorDraft;
                  const normalized = normalizeMcHexColor(draftValue, selectedColorRef.current);
                  updateSelectedColor(normalized);
                  applyColorInsert(`&#${normalized.slice(1)}`);
                  setShowColorPicker(false);
                }}
              >
                {t(locale, "mcInsertColor")}
              </button>
            </div>
          )}
        </div>
      </div>
      <UndoableAutoResizeTextarea
        {...inputProps}
        textareaRef={controller.elementRef}
        value={controller.draft}
        onChangeValue={onChangeValue}
        onSelect={syncSelectionFromTextarea}
        onClick={syncSelectionFromTextarea}
        onKeyUp={syncSelectionFromTextarea}
      />
      {controller.draft.trim() && (
        <div className="mc-preview">
          <div className="mc-preview-label">{t(locale, "mcPreview")}</div>
          <div className="mc-preview-content">
            {toolbarMode === "mc-markdown" ? renderMarkdownPreview(controller.draft) : renderMcFormattedText(controller.draft)}
          </div>
        </div>
      )}
    </div>
  );
}

interface OrderedSelectListEditorProps {
  locale: EditorLocale;
  value: string[];
  options: Array<{ value: string; label: string }>;
  onChange(nextItems: string[]): void;
}

function OrderedSelectListEditor(props: OrderedSelectListEditorProps) {
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null);
  const [dropIndex, setDropIndex] = useState<number | null>(null);
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

  const moveTo = (from: number, to: number) => {
    if (from === to || from < 0 || to < 0 || from >= ordered.length || to >= ordered.length) {
      return;
    }
    const next = [...ordered];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    props.onChange(next);
  };

  return (
    <div className="ordered-select-list">
      <div className="ordered-select-selected">
        <div className="ordered-select-selected-title">{t(props.locale, "currentOrder")}</div>
        {ordered.map((value, index) => {
          const label = props.options.find((option) => option.value === value)?.label || value;
          const dragStateClass =
            draggingIndex === index
              ? " is-dragging"
              : dropIndex === index
                ? " is-drop-target"
                : "";
          return (
            <div
              key={value}
              className={`ordered-select-item${dragStateClass}`}
              onDragOver={(event) => {
                event.preventDefault();
                if (draggingIndex !== null && draggingIndex !== index && dropIndex !== index) {
                  setDropIndex(index);
                }
              }}
              onDrop={(event) => {
                event.preventDefault();
                if (draggingIndex !== null) {
                  moveTo(draggingIndex, index);
                }
                setDraggingIndex(null);
                setDropIndex(null);
              }}
              onDragEnd={() => {
                setDraggingIndex(null);
                setDropIndex(null);
              }}
            >
              <button
                type="button"
                className="drag-handle-button"
                draggable
                aria-label={`${t(props.locale, "currentOrder")} ${label}`}
                onDragStart={(event) => {
                  setDraggingIndex(index);
                  setDropIndex(index);
                  event.dataTransfer.effectAllowed = "move";
                  event.dataTransfer.setData("text/plain", value);
                }}
              >
                ⋮⋮
              </button>
              <span className="ordered-select-item-label">{label}</span>
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

interface UpdateDeleteEditorProps {
  locale: EditorLocale;
  value: string;
  onChange(nextValue: string): void;
}

function UpdateDeleteEditor(props: UpdateDeleteEditorProps) {
  const [rows, setRows] = useState<string[]>(parseUpdateRows(props.value));

  useEffect(() => {
    setRows(parseUpdateRows(props.value));
  }, [props.value]);

  const updateRows = (nextRows: string[]) => {
    setRows(nextRows);
    props.onChange(nextRows.map((item) => item.trim()).filter(Boolean).join("\n"));
  };

  return (
    <div className="update-rule-editor">
      {rows.map((row, index) => (
        <div key={`del-${index}`} className="update-rule-row update-rule-row-single">
          <UndoableInput
            type="text"
            value={row}
            placeholder={t(props.locale, "updateDeletePath")}
            onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? nextValue : item))}
          />
          <button type="button" onClick={() => updateRows(rows.filter((_, rowIndex) => rowIndex !== index))}>
            {t(props.locale, "delete")}
          </button>
        </div>
      ))}
      <button type="button" className="secondary-action" onClick={() => updateRows([...rows, ""])}>
        {t(props.locale, "addEntry")}
      </button>
    </div>
  );
}

interface UpdateAddEditorProps {
  locale: EditorLocale;
  value: string;
  onChange(nextValue: string): void;
}

function UpdateAddEditor(props: UpdateAddEditorProps) {
  const [rows, setRows] = useState<Array<{ source: string; target: string; override: string }>>(parseUpdateAddRows(props.value));

  useEffect(() => {
    setRows(parseUpdateAddRows(props.value));
  }, [props.value]);

  const updateRows = (nextRows: Array<{ source: string; target: string; override: string }>) => {
    setRows(nextRows);
    props.onChange(
      nextRows
        .map((row) => [row.source.trim(), row.target.trim(), row.override.trim() || "true"].join(" => "))
        .filter((line) => line.split(/\s*=>\s*/)[0])
        .join("\n")
    );
  };

  return (
    <div className="update-rule-editor">
      {rows.map((row, index) => (
        <div key={`add-${index}`} className="update-rule-card">
          <div className="update-rule-grid update-rule-grid-add">
            <UndoableInput
              type="text"
              value={row.source}
              placeholder={t(props.locale, "updateSourcePath")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, source: nextValue } : item))}
            />
            <UndoableInput
              type="text"
              value={row.target}
              placeholder={t(props.locale, "updateAppliedTarget")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, target: nextValue } : item))}
            />
            <div className="update-override-editor">
              <ToggleButton
                checked={row.override === "true" || row.override === "all"}
                label={t(props.locale, "updateOverrideEnabled")}
                onToggle={() => updateRows(rows.map((item, rowIndex) => rowIndex === index ? {
                  ...item,
                  override: item.override === "true" || item.override === "all" ? "false" : "true"
                } : item))}
              />
            </div>
          </div>
          <button type="button" onClick={() => updateRows(rows.filter((_, rowIndex) => rowIndex !== index))}>
            {t(props.locale, "delete")}
          </button>
        </div>
      ))}
      <button type="button" className="secondary-action" onClick={() => updateRows([...rows, { source: "", target: "", override: "true" }])}>
        {t(props.locale, "addEntry")}
      </button>
    </div>
  );
}

interface UpdateMoveEditorProps {
  locale: EditorLocale;
  value: string;
  onChange(nextValue: string): void;
}

function UpdateMoveEditor(props: UpdateMoveEditorProps) {
  const [rows, setRows] = useState<Array<{ from: string; to: string }>>(parseUpdateMoveRows(props.value));

  useEffect(() => {
    setRows(parseUpdateMoveRows(props.value));
  }, [props.value]);

  const updateRows = (nextRows: Array<{ from: string; to: string }>) => {
    setRows(nextRows);
    props.onChange(
      nextRows
        .map((row) => `${row.from.trim()} => ${row.to.trim()}`)
        .filter((line) => line.split(/\s*=>\s*/)[0])
        .join("\n")
    );
  };

  return (
    <div className="update-rule-editor">
      {rows.map((row, index) => (
        <div key={`move-${index}`} className="update-rule-card">
          <div className="update-rule-grid update-rule-grid-two">
            <UndoableInput
              type="text"
              value={row.from}
              placeholder={t(props.locale, "updateFromPath")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, from: nextValue } : item))}
            />
            <UndoableInput
              type="text"
              value={row.to}
              placeholder={t(props.locale, "updateToPath")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, to: nextValue } : item))}
            />
          </div>
          <button type="button" onClick={() => updateRows(rows.filter((_, rowIndex) => rowIndex !== index))}>
            {t(props.locale, "delete")}
          </button>
        </div>
      ))}
      <button type="button" className="secondary-action" onClick={() => updateRows([...rows, { from: "", to: "" }])}>
        {t(props.locale, "addEntry")}
      </button>
    </div>
  );
}

interface UpdateModifyEditorProps {
  locale: EditorLocale;
  value: string;
  onChange(nextValue: string): void;
}

function UpdateModifyEditor(props: UpdateModifyEditorProps) {
  const [rows, setRows] = useState<Array<{ target: string; find: string; replace: string; which: string }>>(parseUpdateModifyRows(props.value));

  useEffect(() => {
    setRows(parseUpdateModifyRows(props.value));
  }, [props.value]);

  const updateRows = (nextRows: Array<{ target: string; find: string; replace: string; which: string }>) => {
    setRows(nextRows);
    props.onChange(
      nextRows
        .map((row) => [row.target.trim(), row.find.trim(), row.replace.trim(), row.which.trim()].join(" => "))
        .filter((line) => line.split(/\s*=>\s*/)[0])
        .join("\n")
    );
  };

  return (
    <div className="update-rule-editor">
      {rows.map((row, index) => (
        <div key={`modify-${index}`} className="update-rule-card">
          <div className="update-rule-grid">
            <UndoableInput
              type="text"
              value={row.target}
              placeholder={t(props.locale, "updateTargetPath")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, target: nextValue } : item))}
            />
            <UndoableInput
              type="text"
              value={row.find}
              placeholder={t(props.locale, "updateFind")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, find: nextValue } : item))}
            />
            <UndoableInput
              type="text"
              value={row.replace}
              placeholder={t(props.locale, "updateReplace")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, replace: nextValue } : item))}
            />
            <UndoableInput
              type="text"
              value={row.which}
              placeholder={t(props.locale, "updateWhich")}
              onChangeValue={(nextValue) => updateRows(rows.map((item, rowIndex) => rowIndex === index ? { ...item, which: nextValue } : item))}
            />
          </div>
          <button type="button" onClick={() => updateRows(rows.filter((_, rowIndex) => rowIndex !== index))}>
            {t(props.locale, "delete")}
          </button>
        </div>
      ))}
      <button type="button" className="secondary-action" onClick={() => updateRows([...rows, { target: "", find: "", replace: "", which: "" }])}>
        {t(props.locale, "addEntry")}
      </button>
    </div>
  );
}

function parseUpdateRows(value: string): string[] {
  const rows = value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
  return rows.length > 0 ? rows : [""];
}

function parseUpdateAddRows(value: string): Array<{ source: string; target: string; override: string }> {
  const rows = value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [source = "", target = "", override = ""] = line.split(/\s*=>\s*/);
      return {
        source,
        target,
        override: normalizeUpdateOverride(override)
      };
    });
  return rows.length > 0 ? rows : [{ source: "", target: "", override: "true" }];
}

function normalizeUpdateOverride(value: string): string {
  const normalized = value.trim().toLowerCase();
  if (normalized === "false") {
    return "false";
  }
  if (normalized === "none") {
    return "none";
  }
  if (normalized === "all") {
    return "all";
  }
  return "true";
}

function parseUpdateMoveRows(value: string): Array<{ from: string; to: string }> {
  const rows = value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [from = "", to = ""] = line.split(/\s*=>\s*/);
      return { from, to };
    });
  return rows.length > 0 ? rows : [{ from: "", to: "" }];
}

function parseUpdateModifyRows(value: string): Array<{ target: string; find: string; replace: string; which: string }> {
  const rows = value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [target = "", find = "", replace = "", which = ""] = line.split(/\s*=>\s*/);
      return { target, find, replace, which };
    });
  return rows.length > 0 ? rows : [{ target: "", find: "", replace: "", which: "" }];
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
