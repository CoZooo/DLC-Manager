import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Sidebar } from "../components/Sidebar";
import { NoticePreview } from "../components/NoticePreview";
import { RawDcEditor } from "../components/RawDcEditor";
import { StructuredEditor } from "../components/StructuredEditor";
import { Toolbar } from "../components/Toolbar";
import {
  BackendMode,
  DocumentSchema,
  DocumentType,
  EditorDocument,
  StructuredModel
} from "../models/editor";
import {
  configurableDocumentTypes,
  documentSchemas,
  internalDocumentTypes
} from "../schemas/documentSchemas";
import { ApiBackend } from "../services/backend/ApiBackend";
import { EditorBackend } from "../services/backend/EditorBackend";
import { LocalBackend } from "../services/backend/LocalBackend";
import { detectDocumentType } from "../services/document/typeDetection";
import { serializeStructuredDocumentWithPreservedRaw } from "../services/document/simpleDc";
import { EDITOR_LOCALE_STORAGE_KEY, EditorLocale, t, translateStaticText } from "../i18n/editorI18n";

const APP_STATE_STORAGE_KEY = "dlc-manager-dc-editor-state-v1";

function makeDocumentId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function deriveDocumentName(type: DocumentType, model: StructuredModel | null, fallbackName: string): string {
  if (type !== "info" || !model) {
    return fallbackName;
  }
  const fileName = model.basic?.file_name;
  if (typeof fileName !== "string" || !fileName.trim()) {
    return fallbackName;
  }
  return `${fileName.trim()}.dc`;
}

function stripExtension(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(0, dot) : name;
}

function toIdentifier(name: string): string {
  return stripExtension(name)
    .replace(/[^A-Za-z0-9._-]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function guessDlcTypeFromFileName(name: string): string | null {
  const lower = name.toLowerCase();
  if (lower.endsWith(".jar")) {
    return "mod";
  }
  if (lower.endsWith(".properties") || lower.endsWith(".toml") || lower.endsWith(".cfg") || lower.endsWith(".ini") || lower.endsWith(".yaml") || lower.endsWith(".yml")) {
    return "config";
  }
  return null;
}

type EditorView = "form" | "raw" | "preview" | "validation";

interface PersistedAppState {
  currentType: DocumentType;
  document: EditorDocument | null;
  activeView: EditorView;
  sidebarVisible: boolean;
  homeVisible: boolean;
  status: string;
}

function isDocumentTypeValue(value: unknown): value is DocumentType {
  return typeof value === "string" && value in documentSchemas;
}

function isEditorViewValue(value: unknown): value is EditorView {
  return value === "form" || value === "raw" || value === "preview" || value === "validation";
}

function loadPersistedState(): PersistedAppState | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(APP_STATE_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<PersistedAppState>;
    const currentType = isDocumentTypeValue(parsed.currentType) ? parsed.currentType : "info";
    const activeView = isEditorViewValue(parsed.activeView) ? parsed.activeView : "form";
    const sidebarVisible = typeof parsed.sidebarVisible === "boolean" ? parsed.sidebarVisible : true;
    const homeVisible = typeof parsed.homeVisible === "boolean" ? parsed.homeVisible : !parsed.document;
    const status = typeof parsed.status === "string" ? parsed.status : "就绪。";
    const document = parsed.document && isDocumentTypeValue(parsed.document.type)
      ? parsed.document
      : null;
    return {
      currentType,
      document,
      activeView,
      sidebarVisible,
      homeVisible,
      status
    };
  } catch {
    return null;
  }
}

function loadPersistedLocale(): EditorLocale {
  if (typeof window === "undefined") {
    return "zh_cn";
  }
  const value = window.localStorage.getItem(EDITOR_LOCALE_STORAGE_KEY);
  return value === "en_us" ? "en_us" : "zh_cn";
}

function getIssueSeverityLabel(locale: EditorLocale, severity: "error" | "warning" | "info") {
  if (severity === "error") {
    return t(locale, "validationSeverityError");
  }
  if (severity === "warning") {
    return t(locale, "validationSeverityWarning");
  }
  return t(locale, "validationSeverityInfo");
}

function getIssueSourceLabel(locale: EditorLocale, source: "syntax" | "schema" | "backend" | "api-placeholder") {
  if (source === "syntax") {
    return t(locale, "validationSourceSyntax");
  }
  if (source === "backend") {
    return t(locale, "validationSourceBackend");
  }
  if (source === "api-placeholder") {
    return t(locale, "validationSourceApiPlaceholder");
  }
  return t(locale, "validationSourceSchema");
}

function isConfigurableType(type: DocumentType): boolean {
  return !internalDocumentTypes.has(type);
}

function schemaHasField(schema: DocumentSchema, sectionKey: string, fieldKey: string): boolean {
  return schema.sections.some((section) =>
    section.key === sectionKey && section.fields.some((field) => field.key === fieldKey)
  );
}

function buildDocument(
  type: DocumentType,
  name: string,
  text: string,
  schema: DocumentSchema,
  source: "new" | "imported",
  backendMode: BackendMode,
  path?: string
): EditorDocument {
  return {
    id: makeDocumentId(),
    name,
    path,
    type,
    rawText: text,
    originalText: text,
    structuredModel: schema.supportsStructured ? {} : null,
    structuredEnabled: schema.supportsStructured,
    isDirty: false,
    issues: [],
    source,
    backendMode
  };
}

export function App() {
  const initialLocale = loadPersistedLocale();
  const mainPanelRef = useRef<HTMLElement | null>(null);
  const scrollPositionsRef = useRef<Record<EditorView, number>>({
    form: 0,
    raw: 0,
    preview: 0,
    validation: 0
  });
  const preserveStructuredDraftRef = useRef<string | null>(null);
  const persisted = loadPersistedState();
  const [locale, setLocale] = useState<EditorLocale>(initialLocale);
  const [backendMode] = useState<BackendMode>("local");
  const backend: EditorBackend = useMemo(() => {
    return backendMode === "local" ? new LocalBackend() : new ApiBackend();
  }, [backendMode]);

  const [currentType, setCurrentType] = useState<DocumentType>(persisted?.currentType ?? "info");
  const [document, setDocument] = useState<EditorDocument | null>(persisted?.document ?? null);
  const [activeView, setActiveView] = useState<EditorView>(persisted?.activeView ?? "form");
  const [sidebarVisible, setSidebarVisible] = useState(persisted?.sidebarVisible ?? true);
  const [homeVisible, setHomeVisible] = useState(persisted?.homeVisible ?? !(persisted?.document));
  const [status, setStatus] = useState(persisted?.status ?? t(initialLocale, "ready"));

  const editingDocument = homeVisible ? null : document;
  const schema = editingDocument ? documentSchemas[editingDocument.type] : documentSchemas[currentType];
  const allowPreview = editingDocument?.type === "notice";

  useEffect(() => {
    if (!allowPreview && activeView === "preview") {
      setActiveView("raw");
    }
  }, [activeView, allowPreview]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const snapshot: PersistedAppState = {
      currentType,
      document,
      activeView,
      sidebarVisible,
      homeVisible,
      status
    };
    window.sessionStorage.setItem(APP_STATE_STORAGE_KEY, JSON.stringify(snapshot));
  }, [currentType, document, activeView, sidebarVisible, homeVisible, status]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    window.localStorage.setItem(EDITOR_LOCALE_STORAGE_KEY, locale);
  }, [locale]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    window.document.title = t(locale, "appTitle");
  }, [locale]);

  useEffect(() => {
    if (!document) {
      return;
    }
    let cancelled = false;
      backend.parse(document.type, document.rawText).then((parsed) => {
        if (cancelled) {
          return;
        }
        backend.validate(document.type, document.rawText, parsed.model).then((issues) => {
        if (cancelled) {
          return;
        }
        setDocument((previous) => {
          if (!previous || previous.id !== document.id) {
            return previous;
          }
          const keepStructuredDraft = preserveStructuredDraftRef.current === previous.id;
          return {
            ...previous,
            name: deriveDocumentName(previous.type, keepStructuredDraft ? previous.structuredModel : parsed.model, previous.name),
            structuredModel: keepStructuredDraft ? previous.structuredModel : parsed.model,
            issues
          };
        });
      });
    }).catch((error: Error) => {
      if (cancelled) {
        return;
      }
      setDocument((previous) => {
        if (!previous || previous.id !== document.id) {
          return previous;
        }
        return {
          ...previous,
          issues: [
            {
              severity: "error",
              message: error.message,
              source: "backend"
            }
          ]
        };
      });
    });

    return () => {
      cancelled = true;
    };
  }, [backend, document?.id, document?.rawText, document?.type]);

  const importDocument = async () => {
    try {
      const opened = await backend.openWithPicker();
      if (!opened) {
        return;
      }
      const type = await backend.detectType(opened.path, opened.text);
      if (!isConfigurableType(type)) {
        setStatus(t(locale, "typeNotEditable", { type: documentSchemas[type].label }));
        return;
      }
      const next = buildDocument(type, opened.name, opened.text, documentSchemas[type], "imported", backendMode, opened.path);
      setCurrentType(type);
      setDocument(next);
      setHomeVisible(false);
      setSidebarVisible(false);
      setActiveView(documentSchemas[type].supportsStructured ? "form" : "raw");
      setStatus(t(locale, "importedFile", { name: opened.name }));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "importFailed"));
    }
  };

  const createNew = async () => {
    try {
      const created = await backend.createNew(currentType);
      const type = detectDocumentType(created.path, created.text);
      const next = buildDocument(type, created.name, created.text, documentSchemas[type], "new", backendMode, created.path);
      setDocument(next);
      setHomeVisible(false);
      setSidebarVisible(false);
      setActiveView(documentSchemas[type].supportsStructured ? "form" : "raw");
      setStatus(t(locale, "createdType", { type: documentSchemas[type].label }));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "createFailed"));
    }
  };

  const createFromDlcFile = async () => {
    try {
      if (currentType !== "info") {
        setStatus(t(locale, "infoOnlyFromDlc"));
        return;
      }
      const picked = await backend.pickFile();
      if (!picked) {
        return;
      }
      const created = await backend.createNew("info");
      const type = detectDocumentType(created.path, created.text);
      const targetSchema = documentSchemas[type];
      const parsed = await backend.parse(type, created.text);
      const model = parsed.model ? structuredClone(parsed.model) : {};

      if (!model.basic) {
        setStatus(t(locale, "noPrefillFallback", { type: targetSchema.label }));
        const next = buildDocument(type, created.name, created.text, targetSchema, "new", backendMode, created.path);
        setDocument(next);
        setHomeVisible(false);
        setSidebarVisible(false);
        setActiveView(targetSchema.supportsStructured ? "form" : "raw");
        return;
      }

      const canFillFileName = schemaHasField(targetSchema, "basic", "file_name");
      const canFillIdentifier = schemaHasField(targetSchema, "basic", "identifier");
      const canFillType = schemaHasField(targetSchema, "basic", "type");

      if (!canFillFileName && !canFillIdentifier && !canFillType) {
        setStatus(t(locale, "noPrefillFallback", { type: targetSchema.label }));
        const next = buildDocument(type, created.name, created.text, targetSchema, "new", backendMode, created.path);
        setDocument(next);
        setHomeVisible(false);
        setSidebarVisible(false);
        setActiveView(targetSchema.supportsStructured ? "form" : "raw");
        return;
      }

      if (canFillFileName) {
        model.basic.file_name = picked.name;
      }
      if (canFillIdentifier && typeof model.basic.identifier === "string") {
        model.basic.identifier = toIdentifier(picked.name);
      }
      if (canFillType && typeof model.basic.type === "string") {
        const guessedType = guessDlcTypeFromFileName(picked.name);
        if (guessedType) {
          model.basic.type = guessedType;
        }
      }

      const nextRaw = await backend.serialize(type, model);
      const next = buildDocument(
        type,
        deriveDocumentName(type, model, created.name),
        nextRaw,
        targetSchema,
        "new",
        backendMode,
        created.path
      );
      next.structuredModel = model;
      next.isDirty = next.rawText !== next.originalText;
      setDocument(next);
      setHomeVisible(false);
      setSidebarVisible(false);
      setActiveView(targetSchema.supportsStructured ? "form" : "raw");
      setStatus(t(locale, "createdFromDlc", { name: picked.name, type: targetSchema.label }));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "createFromDlcFailed"));
    }
  };

  const updateRawText = (rawText: string) => {
    preserveStructuredDraftRef.current = null;
    setDocument((previous) => previous ? {
      ...previous,
      rawText,
      isDirty: rawText !== previous.originalText
    } : previous);
  };

  const updateStructured = async (model: StructuredModel) => {
    if (!document) {
      return;
    }
    const nextRaw = document.type === "update"
      ? serializeStructuredDocumentWithPreservedRaw(document.rawText, model, documentSchemas[document.type], { applyImplicitDefaultLocale: true })
      : await backend.serializeForSave(document.type, model);
    preserveStructuredDraftRef.current = document.id;
    setDocument({
      ...document,
      name: deriveDocumentName(document.type, model, document.name),
      structuredModel: model,
      rawText: nextRaw,
      isDirty: nextRaw !== document.originalText
    });
  };

  const saveDocument = async () => {
    if (!document) {
      return;
    }
    const errorCount = document.issues.filter((item) => item.severity === "error").length;
    if (errorCount > 0) {
      setActiveView("validation");
      const message = t(locale, "validationBlocked", { count: errorCount });
      setStatus(message);
      window.alert(message);
      return;
    }
    try {
      const textToSave = document.structuredEnabled && document.structuredModel
        ? document.type === "update"
          ? serializeStructuredDocumentWithPreservedRaw(document.rawText, document.structuredModel, documentSchemas[document.type], { applyImplicitDefaultLocale: true })
          : await backend.serializeForSave(document.type, document.structuredModel)
        : document.rawText;
      if (document.structuredEnabled && document.structuredModel) {
        preserveStructuredDraftRef.current = document.id;
      }
      const result = await backend.save({
        suggestedName: document.name,
        path: document.path,
        text: textToSave
      });
      setDocument({
        ...document,
        name: result.name,
        path: result.path,
        rawText: textToSave,
        originalText: textToSave,
        isDirty: false
      });
      setStatus(t(locale, "savedFile", { name: result.name }));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "saveFailed"));
    }
  };

  const validationSummary = useMemo(() => {
    const issues = editingDocument?.issues || [];
    const counts = {
      error: issues.filter((item) => item.severity === "error").length,
      warning: issues.filter((item) => item.severity === "warning").length,
      info: issues.filter((item) => item.severity === "info").length
    };
    return counts;
  }, [document?.issues]);

  useLayoutEffect(() => {
    const panel = mainPanelRef.current;
    if (!panel || homeVisible) {
      return;
    }
    const nextTop = scrollPositionsRef.current[activeView] ?? 0;
    panel.scrollTop = nextTop;
  }, [activeView, homeVisible]);

  const changeActiveView = (nextView: EditorView) => {
    const panel = mainPanelRef.current;
    if (panel) {
      scrollPositionsRef.current[activeView] = panel.scrollTop;
    }
    setActiveView(nextView);
  };

  return (
    <div className="app-shell">
      <Toolbar
        document={editingDocument}
        activeView={activeView}
        allowPreview={allowPreview}
        sidebarVisible={sidebarVisible}
        locale={locale}
        onGoHome={() => {
          setHomeVisible(true);
          setSidebarVisible(true);
          setStatus(t(locale, "goHomeStatus"));
        }}
        onToggleSidebar={() => setSidebarVisible((previous) => !previous)}
        onChangeLocale={setLocale}
        onChangeView={changeActiveView}
        onSave={saveDocument}
      />

      <div className={`workspace${sidebarVisible ? "" : " workspace-collapsed"}`}>
        <div className={`sidebar-shell${sidebarVisible ? "" : " sidebar-shell-collapsed"}`}>
          <Sidebar
            locale={locale}
            currentType={currentType}
            schemas={documentSchemas}
            orderedTypes={configurableDocumentTypes}
            showDocumentActions={!homeVisible}
            onTypeSelect={setCurrentType}
            onNew={createNew}
            onImport={importDocument}
          />
        </div>

        <main className="main-panel" ref={mainPanelRef}>
          {homeVisible && (
            <section className="welcome">
              <h1>{t(locale, "homeTitle")}</h1>
              <p>{t(locale, "homeDescription")}</p>
              <div className={`welcome-actions${currentType === "info" ? "" : " welcome-actions-two"}`}>
                <button onClick={createNew}>{t(locale, "newConfig")}</button>
                <button onClick={importDocument}>{t(locale, "importDc")}</button>
                {currentType === "info" ? (
                  <button onClick={createFromDlcFile}>{t(locale, "importFromDlc")}</button>
                ) : null}
              </div>
              <div className="welcome-note">
                {t(locale, "homeNote")}
              </div>
            </section>
          )}

          {!homeVisible && editingDocument && (
            <div className="editor-grid">
              {activeView === "form" && (
                <StructuredEditor
                  locale={locale}
                  schema={schema}
                  model={editingDocument.structuredModel}
                  enabled={editingDocument.structuredEnabled}
                  onChange={updateStructured}
                />
              )}

              {activeView === "raw" && (
                <section className="panel">
                  <div className="panel-title">{t(locale, "rawTitle")}</div>
                  <RawDcEditor
                    value={editingDocument.rawText}
                    onChange={updateRawText}
                  />
                </section>
              )}

              {activeView === "preview" && (
                <NoticePreview locale={locale} model={editingDocument.structuredModel} />
              )}

              {activeView === "validation" && (
                <section className="panel">
                  <div className="panel-title">{t(locale, "validationTitle")}</div>
                  <div className="validation-summary">
                    <span>{t(locale, "validationError")}: {validationSummary.error}</span>
                    <span>{t(locale, "validationWarning")}: {validationSummary.warning}</span>
                    <span>{t(locale, "validationInfo")}: {validationSummary.info}</span>
                  </div>
                  <div className="issue-list">
                    {editingDocument.issues.length === 0 && <div className="issue ok">{t(locale, "validationNoIssues")}</div>}
                    {editingDocument.issues.map((issue, index) => (
                      <div key={`${issue.message}-${index}`} className={`issue ${issue.severity}`}>
                        <div className="issue-title">
                          <span>{getIssueSeverityLabel(locale, issue.severity)}</span>
                          <span>{getIssueSourceLabel(locale, issue.source)}</span>
                          {issue.line ? <span>{locale === "zh_cn" ? `第 ${issue.line} 行` : `Line ${issue.line}`}</span> : null}
                        </div>
                        <div>{translateStaticText(locale, issue.message) || issue.message}</div>
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
