import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import JSZip from "jszip";
import { Sidebar } from "../components/Sidebar";
import { NoticePreview } from "../components/NoticePreview";
import { RawDcEditor } from "../components/RawDcEditor";
import { StructuredEditor } from "../components/StructuredEditor";
import { Toolbar } from "../components/Toolbar";
import {
  DocumentSchema,
  DocumentType,
  EditorDocument,
  StructuredModel,
  ValidationIssue
} from "../models/editor";
import {
  configurableDocumentTypes,
  documentSchemas
} from "../schemas/documentSchemas";
import { EditorBackend } from "../services/backend/EditorBackend";
import { LocalBackend } from "../services/backend/LocalBackend";
import { detectDocumentType } from "../services/document/typeDetection";
import { deriveDocumentNaming, documentSavePath, effectiveDocumentName } from "../services/document/documentNaming";
import { formatPresetListFieldsInRaw, serializeStructuredDocumentWithPreservedRaw } from "../services/document/simpleDc";
import { EDITOR_LOCALE_STORAGE_KEY, EditorLocale, t, translateStaticText } from "../i18n/editorI18n";

const APP_STATE_STORAGE_KEY = "dlc-manager-dc-editor-state-v1";

function makeDocumentId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
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
  documents: EditorDocument[];
  activeDocumentId: string | null;
  activeView: EditorView;
  sidebarVisible: boolean;
  homeVisible: boolean;
  status: string;
}

interface DocumentHistoryEntry {
  name: string;
  lastIdentifier?: string;
  rawText: string;
  structuredModel: StructuredModel | null;
  structuredEnabled: boolean;
}

interface DocumentHistoryState {
  docId: string | null;
  past: DocumentHistoryEntry[];
  future: DocumentHistoryEntry[];
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
    const legacyDocument = (parsed as { document?: EditorDocument | null }).document;
    const documents = Array.isArray(parsed.documents)
      ? parsed.documents.filter((item) => item && isDocumentTypeValue(item.type))
      : legacyDocument && isDocumentTypeValue(legacyDocument.type)
        ? [legacyDocument]
        : [];
    const activeDocumentId = typeof parsed.activeDocumentId === "string"
      ? parsed.activeDocumentId
      : documents[0]?.id ?? null;
    const homeVisible = typeof parsed.homeVisible === "boolean" ? parsed.homeVisible : documents.length === 0;
    const status = typeof parsed.status === "string" ? parsed.status : "就绪。";
    return {
      currentType,
      documents,
      activeDocumentId,
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

function getIssueSourceLabel(locale: EditorLocale, source: ValidationIssue["source"]) {
  if (source === "syntax") {
    return t(locale, "validationSourceSyntax");
  }
  if (source === "backend") {
    return t(locale, "validationSourceBackend");
  }
  if (source === "api-placeholder") {
    return t(locale, "validationSourceApiPlaceholder");
  }
  if (source === "filename") {
    return t(locale, "validationSourceFileName");
  }
  return t(locale, "validationSourceSchema");
}

function validateDocumentFileName(name: string): ValidationIssue[] {
  const trimmed = name.trim();
  if (!trimmed) {
    return [{
      severity: "error",
      message: "配置文件名为空。",
      source: "filename"
    }];
  }
  if (trimmed !== name) {
    return [{
      severity: "error",
      message: "配置文件名不能包含首尾空格。",
      source: "filename"
    }];
  }
  if (trimmed === "." || trimmed === "..") {
    return [{
      severity: "error",
      message: "配置文件名不能是 . 或 ..。",
      source: "filename"
    }];
  }
  if (/[\\/:*?"<>|]/.test(trimmed)) {
    return [{
      severity: "error",
      message: "配置文件名包含非法字符：\\ / : * ? \" < > |",
      source: "filename"
    }];
  }
  if (/[\x00-\x1F]/.test(trimmed)) {
    return [{
      severity: "error",
      message: "配置文件名包含控制字符。",
      source: "filename"
    }];
  }
  const dot = trimmed.lastIndexOf(".");
  const baseName = dot > 0 ? trimmed.slice(0, dot) : trimmed;
  const extension = dot > 0 ? trimmed.slice(dot + 1) : "";
  if (/[ .]$/.test(trimmed) || /[ .]$/.test(baseName) || extension.endsWith(" ")) {
    return [{
      severity: "error",
      message: "配置文件名或扩展名前不能以空格或点结尾。",
      source: "filename"
    }];
  }
  return [];
}

function isConfigurableType(type: DocumentType): boolean {
  return configurableDocumentTypes.includes(type);
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
  path?: string
): EditorDocument {
  const documentName = type === "folder_metadata" ? "_metadata.dc" : type === "pack" ? "pack.dc" : name;
  return {
    id: makeDocumentId(),
    name: documentName,
    originalName: documentName,
    path: type === "pack" ? "pack.dc" : path,
    type,
    rawText: text,
    originalText: text,
    structuredModel: schema.supportsStructured ? {} : null,
    structuredEnabled: schema.supportsStructured,
    isDirty: false,
    issues: [],
    source
  };
}

function snapshotDocument(document: EditorDocument): DocumentHistoryEntry {
  return {
    name: document.name,
    lastIdentifier: document.lastIdentifier,
    rawText: document.rawText,
    structuredModel: document.structuredModel ? structuredClone(document.structuredModel) : null,
    structuredEnabled: document.structuredEnabled
  };
}

function restoreDocumentSnapshot(document: EditorDocument, snapshot: DocumentHistoryEntry): EditorDocument {
  const originalName = document.originalName ?? document.name;
  return {
    ...document,
    name: snapshot.name,
    lastIdentifier: snapshot.lastIdentifier,
    rawText: snapshot.rawText,
    structuredModel: snapshot.structuredModel ? structuredClone(snapshot.structuredModel) : null,
    structuredEnabled: snapshot.structuredEnabled,
    isDirty: snapshot.rawText !== document.originalText || snapshot.name !== originalName
  };
}

function isEditableFocusTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) {
    return false;
  }
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
    return true;
  }
  if (target.closest(".cm-editor")) {
    return true;
  }
  if (target instanceof HTMLElement && target.isContentEditable) {
    return true;
  }
  return Boolean(target.closest("[contenteditable='true']"));
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
  const currentDocumentIdRef = useRef<string | null>(null);
  const persisted = loadPersistedState();
  const [locale, setLocale] = useState<EditorLocale>(initialLocale);
  const backend: EditorBackend = useMemo(() => {
    return new LocalBackend();
  }, []);

  const [currentType, setCurrentType] = useState<DocumentType>(persisted?.currentType ?? "info");
  const [documents, setDocuments] = useState<EditorDocument[]>(persisted?.documents ?? []);
  const [activeDocumentId, setActiveDocumentId] = useState<string | null>(persisted?.activeDocumentId ?? persisted?.documents?.[0]?.id ?? null);
  const [activeView, setActiveView] = useState<EditorView>(persisted?.activeView ?? "form");
  const [sidebarVisible, setSidebarVisible] = useState(persisted?.sidebarVisible ?? true);
  const [homeVisible, setHomeVisible] = useState(persisted?.homeVisible ?? !(persisted?.documents?.length));
  const [status, setStatus] = useState(persisted?.status ?? t(initialLocale, "ready"));
  const [history, setHistory] = useState<DocumentHistoryState>({
    docId: persisted?.activeDocumentId ?? persisted?.documents?.[0]?.id ?? null,
    past: [],
    future: []
  });

  const editingDocument = useMemo(() => {
    if (homeVisible) {
      return null;
    }
    return documents.find((item) => item.id === activeDocumentId) || null;
  }, [documents, activeDocumentId, homeVisible]);
  const activeDocumentIndex = useMemo(() => {
    if (!editingDocument) {
      return -1;
    }
    return documents.findIndex((item) => item.id === editingDocument.id);
  }, [documents, editingDocument]);
  const schema = editingDocument ? documentSchemas[editingDocument.type] : documentSchemas[currentType];
  const allowPreview = editingDocument?.type === "notice";

  useEffect(() => {
    currentDocumentIdRef.current = editingDocument?.id ?? null;
  }, [editingDocument?.id]);

  useEffect(() => {
    if (!documents.length) {
      if (activeDocumentId !== null) {
        setActiveDocumentId(null);
      }
      return;
    }
    if (!activeDocumentId || !documents.some((item) => item.id === activeDocumentId)) {
      setActiveDocumentId(documents[0].id);
    }
  }, [documents, activeDocumentId]);

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
      documents,
      activeDocumentId,
      activeView,
      sidebarVisible,
      homeVisible,
      status
    };
    window.sessionStorage.setItem(APP_STATE_STORAGE_KEY, JSON.stringify(snapshot));
  }, [currentType, documents, activeDocumentId, activeView, sidebarVisible, homeVisible, status]);

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
    if (!editingDocument) {
      return;
    }
    let cancelled = false;
      backend.parse(editingDocument.type, editingDocument.rawText).then((parsed) => {
        if (cancelled) {
          return;
        }
        const formattedRawText = editingDocument.type === "preset"
          ? formatPresetListFieldsInRaw(editingDocument.rawText)
          : editingDocument.rawText;
        backend.validate(editingDocument.type, formattedRawText, parsed.model).then((issues) => {
        if (cancelled) {
          return;
        }
        setDocuments((previous) => {
          const index = previous.findIndex((item) => item.id === editingDocument.id);
          if (index < 0) {
            return previous;
          }
          const current = previous[index];
          const keepStructuredDraft = preserveStructuredDraftRef.current === current.id;
          const model = keepStructuredDraft ? current.structuredModel : parsed.model;
          const naming = deriveDocumentNaming(current, parsed.issues.some((issue) => issue.severity === "error") ? null : model);
          const currentName = naming.name;
          const next = {
            ...current,
            rawText: formattedRawText,
            structuredModel: model,
            ...naming,
            isDirty: formattedRawText !== current.originalText || currentName !== (current.originalName ?? current.name),
            issues: current.type === "pack" ? issues : [...issues, ...validateDocumentFileName(currentName)]
          };
          const copy = [...previous];
          copy[index] = next;
          return copy;
        });
      });
    }).catch((error: Error) => {
      if (cancelled) {
        return;
      }
      setDocuments((previous) => {
        const index = previous.findIndex((item) => item.id === editingDocument.id);
        if (index < 0) {
          return previous;
        }
        const copy = [...previous];
        copy[index] = {
          ...copy[index],
          issues: [
            {
              severity: "error",
              message: error.message,
              source: "backend"
            }
          ]
        };
        return copy;
      });
    });

    return () => {
      cancelled = true;
    };
  }, [backend, editingDocument?.id, editingDocument?.rawText, editingDocument?.type]);

  useEffect(() => {
    if (history.docId === (editingDocument?.id ?? null)) {
      return;
    }
    setHistory({
      docId: editingDocument?.id ?? null,
      past: [],
      future: []
    });
  }, [editingDocument?.id, history.docId]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!editingDocument || homeVisible) {
        return;
      }
      if (isEditableFocusTarget(event.target) || isEditableFocusTarget(window.document.activeElement)) {
        return;
      }
      const isMod = event.metaKey || event.ctrlKey;
      if (!isMod || event.altKey) {
        return;
      }
      const key = event.key.toLowerCase();
      if (key === "z" && !event.shiftKey) {
        if (history.docId !== editingDocument.id || history.past.length === 0) {
          return;
        }
        event.preventDefault();
        preserveStructuredDraftRef.current = editingDocument.id;
        const previous = history.past[history.past.length - 1];
        setHistory({
          docId: editingDocument.id,
          past: history.past.slice(0, -1),
          future: [snapshotDocument(editingDocument), ...history.future]
        });
        setDocuments((current) => current.map((item) => item.id === editingDocument.id ? restoreDocumentSnapshot(editingDocument, previous) : item));
        return;
      }
      if ((key === "z" && event.shiftKey) || key === "y") {
        if (history.docId !== editingDocument.id || history.future.length === 0) {
          return;
        }
        event.preventDefault();
        preserveStructuredDraftRef.current = editingDocument.id;
        const next = history.future[0];
        setHistory({
          docId: editingDocument.id,
          past: [...history.past, snapshotDocument(editingDocument)],
          future: history.future.slice(1)
        });
        setDocuments((current) => current.map((item) => item.id === editingDocument.id ? restoreDocumentSnapshot(editingDocument, next) : item));
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [editingDocument, history, homeVisible]);

  const mergeDocuments = (incoming: EditorDocument[]) => {
    if (incoming.length === 0) {
      return;
    }
    setDocuments((previous) => {
      const next = [...previous];
      for (const doc of incoming) {
        const index = next.findIndex((item) => item.path && doc.path && item.path === doc.path);
        if (index >= 0) {
          next[index] = doc;
        } else {
          next.push(doc);
        }
      }
      return next;
    });
    const active = incoming[incoming.length - 1];
    setActiveDocumentId(active.id);
    setCurrentType(active.type);
    setHomeVisible(false);
    setSidebarVisible(false);
    setActiveView(documentSchemas[active.type].supportsStructured ? "form" : "raw");
  };

  const commitDocumentEdit = (nextDocument: EditorDocument) => {
    if (!editingDocument || editingDocument.id !== nextDocument.id) {
      setDocuments((previous) => previous.map((item) => item.id === nextDocument.id ? nextDocument : item));
      return;
    }
    setHistory((previous) => ({
      docId: editingDocument.id,
      past: [
        ...(previous.docId === editingDocument.id ? previous.past : []),
        snapshotDocument(editingDocument)
      ].slice(-100),
      future: []
    }));
    setDocuments((previous) => previous.map((item) => item.id === nextDocument.id ? nextDocument : item));
  };

  const importDocument = async () => {
    try {
      const opened = await backend.openManyWithPicker();
      if (!opened.length) {
        return;
      }
      const importedDocs: EditorDocument[] = [];
      for (const item of opened) {
        const type = await backend.detectType(item.path, item.text);
        if (!isConfigurableType(type)) {
          setStatus(t(locale, "typeNotEditable", { type: documentSchemas[type].label }));
          continue;
        }
        importedDocs.push(buildDocument(type, item.name, item.text, documentSchemas[type], "imported", item.path));
      }
      if (!importedDocs.length) {
        return;
      }
      mergeDocuments(importedDocs);
      setStatus(t(locale, "importedFile", { name: importedDocs[importedDocs.length - 1].name }));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "importFailed"));
    }
  };

  const createNew = async () => {
    try {
      const created = await backend.createNew(currentType);
      const type = detectDocumentType(created.path, created.text);
      const next = buildDocument(type, created.name, created.text, documentSchemas[type], "new", created.path);
      mergeDocuments([next]);
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
        const next = buildDocument(type, created.name, created.text, targetSchema, "new", created.path);
        mergeDocuments([next]);
        return;
      }

      const canFillFileName = schemaHasField(targetSchema, "basic", "file_name");
      const canFillIdentifier = schemaHasField(targetSchema, "basic", "identifier");
      const canFillType = schemaHasField(targetSchema, "basic", "type");

      if (!canFillFileName && !canFillIdentifier && !canFillType) {
        setStatus(t(locale, "noPrefillFallback", { type: targetSchema.label }));
        const next = buildDocument(type, created.name, created.text, targetSchema, "new", created.path);
        mergeDocuments([next]);
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
        created.name,
        nextRaw,
        targetSchema,
        "new",
        created.path
      );
      Object.assign(next, deriveDocumentNaming(next, model));
      next.structuredModel = model;
      next.isDirty = next.rawText !== next.originalText || next.name !== next.originalName;
      mergeDocuments([next]);
      setStatus(t(locale, "createdFromDlc", { name: picked.name, type: targetSchema.label }));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "createFromDlcFailed"));
    }
  };

  const updateRawText = (rawText: string) => {
    preserveStructuredDraftRef.current = null;
    if (!editingDocument) {
      return;
    }
    commitDocumentEdit({
      ...editingDocument,
      rawText,
      isDirty: rawText !== editingDocument.originalText || effectiveDocumentName(editingDocument) !== (editingDocument.originalName ?? editingDocument.name)
    });
  };

  const updateDocumentName = (name: string) => {
    if (!editingDocument || editingDocument.type === "pack" || editingDocument.type === "folder_metadata") {
      return;
    }
    preserveStructuredDraftRef.current = editingDocument.id;
    commitDocumentEdit({
      ...editingDocument,
      name,
      isDirty: editingDocument.rawText !== editingDocument.originalText || name !== (editingDocument.originalName ?? editingDocument.name),
      issues: [
        ...editingDocument.issues.filter((issue) => issue.source !== "filename"),
        ...validateDocumentFileName(name)
      ]
    });
  };

  const updateStructured = async (model: StructuredModel) => {
    if (!editingDocument) {
      return;
    }
    const nextRaw = (editingDocument.type === "update" || editingDocument.type === "required" || editingDocument.type === "folder_metadata")
      ? serializeStructuredDocumentWithPreservedRaw(editingDocument.rawText, model, documentSchemas[editingDocument.type], { applyImplicitDefaultLocale: true })
      : await backend.serializeForSave(editingDocument.type, model);
    preserveStructuredDraftRef.current = editingDocument.id;
    const naming = deriveDocumentNaming(editingDocument, model);
    commitDocumentEdit({
      ...editingDocument,
      structuredModel: model,
      rawText: nextRaw,
      ...naming,
      isDirty: nextRaw !== editingDocument.originalText || naming.name !== (editingDocument.originalName ?? editingDocument.name),
      issues: [
        ...editingDocument.issues.filter((issue) => issue.source !== "filename"),
        ...validateDocumentFileName(naming.name)
      ]
    });
  };

  const saveCurrentDocument = async () => {
    if (!editingDocument) {
      return;
    }
    const savingDocumentId = editingDocument.id;
    const errorCount = editingDocument.issues.filter((item) => item.severity === "error").length;
    if (errorCount > 0) {
      setActiveView("validation");
      const message = t(locale, "validationBlocked", { count: errorCount });
      setStatus(message);
      window.alert(message);
      return;
    }
    try {
      const textToSave = editingDocument.structuredEnabled && editingDocument.structuredModel
        ? (editingDocument.type === "update" || editingDocument.type === "required" || editingDocument.type === "folder_metadata")
          ? serializeStructuredDocumentWithPreservedRaw(editingDocument.rawText, editingDocument.structuredModel, documentSchemas[editingDocument.type], { applyImplicitDefaultLocale: true })
          : await backend.serializeForSave(editingDocument.type, editingDocument.structuredModel)
        : editingDocument.rawText;
      if (editingDocument.structuredEnabled && editingDocument.structuredModel) {
        preserveStructuredDraftRef.current = editingDocument.id;
      }
      const saveName = effectiveDocumentName(editingDocument);
      const result = await backend.save({
        suggestedName: saveName,
        path: editingDocument.type === "pack" ? saveName : documentSavePath(editingDocument.path, saveName),
        text: textToSave
      });
      if (currentDocumentIdRef.current === savingDocumentId) {
        setDocuments((previous) => {
          const index = previous.findIndex((item) => item.id === savingDocumentId);
          if (index < 0) {
            return previous;
          }
          const copy = [...previous];
          copy[index] = {
            ...copy[index],
            name: saveName,
            originalName: saveName,
            path: editingDocument.type === "pack" ? saveName : result.path,
            rawText: textToSave,
            originalText: textToSave,
            isDirty: false
          };
          return copy;
        });
        setStatus(t(locale, "savedFile", { name: saveName }));
      }
    } catch (error) {
      setStatus(error instanceof Error ? error.message : t(locale, "saveFailed"));
    }
  };

  const saveDocument = async () => {
    if (!editingDocument) {
      return;
    }
    if (documents.length <= 1) {
      await saveCurrentDocument();
      return;
    }
    const invalidDocument = documents.find((doc) => doc.type !== "pack" && validateDocumentFileName(effectiveDocumentName(doc)).some((issue) => issue.severity === "error"));
    if (invalidDocument) {
      setActiveDocumentId(invalidDocument.id);
      setActiveView("validation");
      const message = t(locale, "validationBlocked", { count: validateDocumentFileName(effectiveDocumentName(invalidDocument)).length });
      setStatus(message);
      window.alert(message);
      return;
    }
    try {
      const zip = new JSZip();
      for (const doc of documents) {
        const textToSave = doc.structuredEnabled && doc.structuredModel
          ? (doc.type === "update" || doc.type === "required" || doc.type === "folder_metadata")
            ? serializeStructuredDocumentWithPreservedRaw(doc.rawText, doc.structuredModel, documentSchemas[doc.type], { applyImplicitDefaultLocale: true })
            : await backend.serializeForSave(doc.type, doc.structuredModel)
          : doc.rawText;
        zip.file(effectiveDocumentName(doc), textToSave);
      }
      const data = await zip.generateAsync({ type: "uint8array" });
      const result = await backend.saveArchive({
        suggestedName: "dc-files.zip",
        data
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
  }, [editingDocument?.issues]);

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

  const selectDocument = (id: string) => {
    setActiveDocumentId(id);
    setHomeVisible(false);
  };

  const selectPreviousDocument = () => {
    if (documents.length <= 1 || activeDocumentIndex < 0) {
      return;
    }
    const nextIndex = (activeDocumentIndex - 1 + documents.length) % documents.length;
    selectDocument(documents[nextIndex].id);
  };

  const selectNextDocument = () => {
    if (documents.length <= 1 || activeDocumentIndex < 0) {
      return;
    }
    const nextIndex = (activeDocumentIndex + 1) % documents.length;
    selectDocument(documents[nextIndex].id);
  };

  const removeDocument = (id: string) => {
    const index = documents.findIndex((item) => item.id === id);
    if (index < 0) {
      return;
    }
    const remaining = documents.filter((item) => item.id !== id);
    setDocuments(remaining);
    if (remaining.length === 0) {
      setActiveDocumentId(null);
      setHomeVisible(true);
      setSidebarVisible(true);
      setHistory({
        docId: null,
        past: [],
        future: []
      });
      return;
    }
    if (activeDocumentId === id) {
      const nextIndex = Math.min(index, remaining.length - 1);
      setActiveDocumentId(remaining[nextIndex].id);
    }
  };

  const clearDocuments = () => {
    setDocuments([]);
    setActiveDocumentId(null);
    setHomeVisible(true);
    setSidebarVisible(true);
    setHistory({
      docId: null,
      past: [],
      future: []
    });
  };

  return (
    <div className="app-shell">
      <Toolbar
        documents={documents}
        document={editingDocument}
        activeDocumentIndex={activeDocumentIndex}
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
        onChangeView={changeActiveView}
        onSelectDocument={selectDocument}
        onSelectPreviousDocument={selectPreviousDocument}
        onSelectNextDocument={selectNextDocument}
        onRemoveDocument={removeDocument}
        onClearDocuments={clearDocuments}
        onSaveCurrent={saveCurrentDocument}
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
            onChangeLocale={setLocale}
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
                  key={`form-${editingDocument.id}`}
                  locale={locale}
                  schema={schema}
                  model={editingDocument.structuredModel}
                  enabled={editingDocument.structuredEnabled}
                  fileName={editingDocument.type === "pack" || editingDocument.type === "folder_metadata" ? undefined : editingDocument.name}
                  onFileNameChange={editingDocument.type === "pack" || editingDocument.type === "folder_metadata" ? undefined : updateDocumentName}
                  onChange={updateStructured}
                />
              )}

              {activeView === "raw" && (
                <section className="panel">
                  <div className="panel-title">{t(locale, "rawTitle")}</div>
                  <RawDcEditor
                    key={`raw-${editingDocument.id}`}
                    value={editingDocument.rawText}
                    onChange={updateRawText}
                  />
                </section>
              )}

              {activeView === "preview" && (
                <NoticePreview
                  key={`preview-${editingDocument.id}`}
                  locale={locale}
                  model={editingDocument.structuredModel}
                  backend={backend}
                />
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
