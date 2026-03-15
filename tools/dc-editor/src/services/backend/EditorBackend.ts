import {
  DocumentType,
  OpenDocumentResult,
  PickedFileResult,
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
  readonly mode: "local" | "api";
  openWithPicker(): Promise<OpenDocumentResult | null>;
  pickFile(): Promise<PickedFileResult | null>;
  createNew(type: DocumentType): Promise<OpenDocumentResult>;
  detectType(path: string | undefined, text: string): Promise<DocumentType>;
  parse(type: DocumentType, text: string): Promise<ParsedDocumentResult>;
  validate(type: DocumentType, text: string, model: StructuredModel | null): Promise<ValidationIssue[]>;
  serialize(type: DocumentType, model: StructuredModel): Promise<string>;
  serializeForSave(type: DocumentType, model: StructuredModel): Promise<string>;
  save(request: SaveDocumentRequest): Promise<SaveDocumentResult>;
}
