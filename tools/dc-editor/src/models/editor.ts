export type BackendMode = "local" | "api";

export type DocumentType =
  | "info"
  | "remote"
  | "required"
  | "required_notice"
  | "preset"
  | "notice"
  | "redirect"
  | "update"
  | "version";

export type FieldKind =
  | "text"
  | "textarea"
  | "boolean"
  | "select"
  | "number"
  | "list"
  | "ordered-select-list"
  | "language-visibility"
  | "update-file-del"
  | "update-file-add"
  | "update-file-move"
  | "update-file-modify";
export type LocaleValueKind = "text" | "textarea";
export type TextToolbarMode = "mc" | "mc-markdown";

export interface ValidationIssue {
  severity: "error" | "warning" | "info";
  message: string;
  line?: number;
  source: "syntax" | "schema" | "backend" | "api-placeholder" | "filename";
}

export interface FieldSchema {
  key: string;
  label: string;
  kind: FieldKind;
  help?: string;
  toolbarMode?: TextToolbarMode;
  hiddenInForm?: boolean;
  visibleWhen?: {
    sectionKey: string;
    fieldKey: string;
    equals?: string;
    oneOf?: string[];
  };
  toggleLabels?: {
    checked: string;
    unchecked: string;
  };
  options?: Array<{ value: string; label: string }>;
  placeholder?: string;
}

export interface SectionSchema {
  key: string;
  label: string;
  description?: string;
  fields: FieldSchema[];
  dynamicLocale?: {
    valueKind: LocaleValueKind;
    help?: string;
    toolbarMode?: TextToolbarMode;
  };
}

export interface DocumentSchema {
  type: DocumentType;
  label: string;
  summary: string;
  defaultFileName: string;
  supportsStructured: boolean;
  sections: SectionSchema[];
}

export type StructuredValue = string | boolean | number | string[] | Record<string, string>;
export type StructuredModel = Record<string, Record<string, StructuredValue>>;
export const ROOT_SECTION_KEY = "__root__";

export interface EditorDocument {
  id: string;
  name: string;
  originalName?: string;
  path?: string;
  type: DocumentType;
  rawText: string;
  originalText: string;
  structuredModel: StructuredModel | null;
  structuredEnabled: boolean;
  isDirty: boolean;
  issues: ValidationIssue[];
  source: "new" | "imported";
  backendMode: BackendMode;
}

export interface OpenDocumentResult {
  name: string;
  path?: string;
  text: string;
}

export interface SaveArchiveRequest {
  suggestedName: string;
  data: Uint8Array;
}

export interface PickedFileResult {
  name: string;
  path?: string;
}

export interface SaveDocumentRequest {
  suggestedName: string;
  text: string;
  path?: string;
}

export interface SaveDocumentResult {
  path?: string;
  name: string;
}
