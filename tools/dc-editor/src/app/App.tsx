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
  const mainPanelRef = useRef<HTMLElement | null>(null);
  const scrollPositionsRef = useRef<Record<EditorView, number>>({
    form: 0,
    raw: 0,
    preview: 0,
    validation: 0
  });
  const preserveStructuredDraftRef = useRef<string | null>(null);
  const persisted = loadPersistedState();
  const [backendMode] = useState<BackendMode>("local");
  const backend: EditorBackend = useMemo(() => {
    return backendMode === "local" ? new LocalBackend() : new ApiBackend();
  }, [backendMode]);

  const [currentType, setCurrentType] = useState<DocumentType>(persisted?.currentType ?? "info");
  const [document, setDocument] = useState<EditorDocument | null>(persisted?.document ?? null);
  const [activeView, setActiveView] = useState<EditorView>(persisted?.activeView ?? "form");
  const [sidebarVisible, setSidebarVisible] = useState(persisted?.sidebarVisible ?? true);
  const [homeVisible, setHomeVisible] = useState(persisted?.homeVisible ?? !(persisted?.document));
  const [status, setStatus] = useState(persisted?.status ?? "就绪。");

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
        setStatus(`已识别为 ${documentSchemas[type].label}，该类型不属于编辑器的配置目标范围。`);
        return;
      }
      const next = buildDocument(type, opened.name, opened.text, documentSchemas[type], "imported", backendMode, opened.path);
      setCurrentType(type);
      setDocument(next);
      setHomeVisible(false);
      setSidebarVisible(false);
      setActiveView(documentSchemas[type].supportsStructured ? "form" : "raw");
      setStatus(`已导入 ${opened.name}。`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "导入失败。");
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
      setStatus(`已创建 ${documentSchemas[type].label} 配置。`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "创建文档失败。");
    }
  };

  const createFromDlcFile = async () => {
    try {
      if (currentType !== "info") {
        setStatus("“从导入DLC文件开始”目前仅支持 Info 类型。");
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
        setStatus(`当前类型 ${targetSchema.label} 不支持从 DLC 文件预填，已改为普通新建。`);
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
        setStatus(`当前类型 ${targetSchema.label} 不支持从 DLC 文件预填，已改为普通新建。`);
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
      setStatus(`已从 ${picked.name} 生成 ${targetSchema.label} 配置。`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "从 DLC 文件开始失败。");
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
    const nextRaw = await backend.serializeForSave(document.type, model);
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
      setStatus(`当前存在 ${errorCount} 个错误，未修复前无法保存或导出。`);
      window.alert(`当前存在 ${errorCount} 个错误，未修复前无法保存或导出。`);
      return;
    }
    try {
      const textToSave = document.structuredEnabled && document.structuredModel
        ? await backend.serializeForSave(document.type, document.structuredModel)
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
      setStatus(`已保存 ${result.name}。`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "保存失败。");
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
        onGoHome={() => {
          setHomeVisible(true);
          setSidebarVisible(true);
          setStatus("已返回主页。");
        }}
        onToggleSidebar={() => setSidebarVisible((previous) => !previous)}
        onChangeView={changeActiveView}
        onSave={saveDocument}
      />

      <div className={`workspace${sidebarVisible ? "" : " workspace-collapsed"}`}>
        <div className={`sidebar-shell${sidebarVisible ? "" : " sidebar-shell-collapsed"}`}>
          <Sidebar
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
              <h1>DC 配置工作台</h1>
              <p>新建一个 dc 配置，或导入现有文件后开始编辑。</p>
              <div className={`welcome-actions${currentType === "info" ? "" : " welcome-actions-two"}`}>
                <button onClick={createNew}>新建配置</button>
                <button onClick={importDocument}>导入 .dc</button>
                {currentType === "info" ? (
                  <button onClick={createFromDlcFile}>从导入DLC文件开始</button>
                ) : null}
              </div>
              <div className="welcome-note">
                可先新建或导入已有 dc 文件。
              </div>
            </section>
          )}

          {!homeVisible && editingDocument && (
            <div className="editor-grid">
              {activeView === "form" && (
                <StructuredEditor
                  schema={schema}
                  model={editingDocument.structuredModel}
                  enabled={editingDocument.structuredEnabled}
                  onChange={updateStructured}
                />
              )}

              {activeView === "raw" && (
                <section className="panel">
                  <div className="panel-title">原始 DC</div>
                  <RawDcEditor
                    value={editingDocument.rawText}
                    onChange={updateRawText}
                  />
                </section>
              )}

              {activeView === "preview" && (
                <NoticePreview model={editingDocument.structuredModel} />
              )}

              {activeView === "validation" && (
                <section className="panel">
                  <div className="panel-title">校验</div>
                  <div className="validation-summary">
                    <span>错误: {validationSummary.error}</span>
                    <span>警告: {validationSummary.warning}</span>
                    <span>信息: {validationSummary.info}</span>
                  </div>
                  <div className="issue-list">
                    {editingDocument.issues.length === 0 && <div className="issue ok">没有问题。</div>}
                    {editingDocument.issues.map((issue, index) => (
                      <div key={`${issue.message}-${index}`} className={`issue ${issue.severity}`}>
                        <div className="issue-title">
                          <span>{issue.severity.toUpperCase()}</span>
                          <span>{issue.source}</span>
                          {issue.line ? <span>第 {issue.line} 行</span> : null}
                        </div>
                        <div>{issue.message}</div>
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
