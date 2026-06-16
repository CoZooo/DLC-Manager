import {
  DocumentType,
  OpenDocumentResult,
  PickedFileResult,
  SaveArchiveRequest,
  SaveDocumentRequest,
  SaveDocumentResult,
  StructuredModel,
  ValidationIssue
} from "../../models/editor";

export interface ParsedDocumentResult {
  type: DocumentType;
  model: StructuredModel | null;
  issues: ValidationIssue[];
}

export interface EditorBackend {
  readonly mode: "local";
  openWithPicker(): Promise<OpenDocumentResult | null>;
  openManyWithPicker(): Promise<OpenDocumentResult[]>;
  pickFile(): Promise<PickedFileResult | null>;
  createNew(type: DocumentType): Promise<OpenDocumentResult>;
  detectType(path: string | undefined, text: string): Promise<DocumentType>;
  parse(type: DocumentType, text: string): Promise<ParsedDocumentResult>;
  validate(type: DocumentType, text: string, model: StructuredModel | null): Promise<ValidationIssue[]>;
  serialize(type: DocumentType, model: StructuredModel): Promise<string>;
  serializeForSave(type: DocumentType, model: StructuredModel): Promise<string>;
  save(request: SaveDocumentRequest): Promise<SaveDocumentResult>;
  saveArchive(request: SaveArchiveRequest): Promise<SaveDocumentResult>;
  fetchNoticeContent(url: string): Promise<string | null>;
}
