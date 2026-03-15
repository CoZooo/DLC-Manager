import {
  DocumentType,
  OpenDocumentResult,
  PickedFileResult,
  SaveDocumentRequest,
  SaveDocumentResult,
  StructuredModel,
  ValidationIssue
} from "../../models/editor";
import { EditorBackend, ParsedDocumentResult } from "./EditorBackend";

export class ApiBackend implements EditorBackend {
  readonly mode = "api" as const;

  private unsupported(): never {
    throw new Error("API backend is reserved for future DLC Manager external API integration.");
  }

  async openWithPicker(): Promise<OpenDocumentResult | null> {
    this.unsupported();
  }

  async pickFile(): Promise<PickedFileResult | null> {
    this.unsupported();
  }

  async createNew(type: DocumentType): Promise<OpenDocumentResult> {
    void type;
    this.unsupported();
  }

  async detectType(path: string | undefined, text: string): Promise<DocumentType> {
    void path;
    void text;
    this.unsupported();
  }

  async parse(type: DocumentType, text: string): Promise<ParsedDocumentResult> {
    void type;
    void text;
    this.unsupported();
  }

  async validate(type: DocumentType, text: string, model: StructuredModel | null): Promise<ValidationIssue[]> {
    void type;
    void text;
    void model;
    this.unsupported();
  }

  async serialize(type: DocumentType, model: StructuredModel): Promise<string> {
    void type;
    void model;
    this.unsupported();
  }

  async save(request: SaveDocumentRequest): Promise<SaveDocumentResult> {
    void request;
    this.unsupported();
  }
}
