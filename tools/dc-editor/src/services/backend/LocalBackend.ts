import {
  DocumentType,
  OpenDocumentResult,
  PickedFileResult,
  SaveDocumentRequest,
  SaveDocumentResult,
  StructuredModel,
  ValidationIssue
} from "../../models/editor";
import { documentSchemas } from "../../schemas/documentSchemas";
import { createEmptyTemplate } from "../document/templates";
import { parseStructuredDocument, serializeStructuredDocument, validateStructuredModel } from "../document/simpleDc";
import { detectDocumentType } from "../document/typeDetection";
import { EditorBackend, ParsedDocumentResult } from "./EditorBackend";

declare global {
  interface Window {
    showOpenFilePicker?: (options?: unknown) => Promise<Array<{ getFile(): Promise<File> }>>;
    showSaveFilePicker?: (options?: unknown) => Promise<{ createWritable(): Promise<{ write(data: string): Promise<void>; close(): Promise<void> }> }>;
  }
}

export class LocalBackend implements EditorBackend {
  readonly mode = "local" as const;

  async openWithPicker(): Promise<OpenDocumentResult | null> {
    if (window.showOpenFilePicker) {
      const [handle] = await window.showOpenFilePicker({
        types: [{ description: "DC Config", accept: { "text/plain": [".dc"] } }],
        excludeAcceptAllOption: false,
        multiple: false
      });
      if (!handle) {
        return null;
      }
      const file = await handle.getFile();
      return {
        name: file.name,
        path: file.name,
        text: await file.text()
      };
    }

    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = ".dc,text/plain";
      input.onchange = async () => {
        const file = input.files?.[0];
        if (!file) {
          resolve(null);
          return;
        }
        resolve({
          name: file.name,
          path: file.name,
          text: await file.text()
        });
      };
      input.click();
    });
  }

  async pickFile(): Promise<PickedFileResult | null> {
    if (window.showOpenFilePicker) {
      const [handle] = await window.showOpenFilePicker({
        excludeAcceptAllOption: false,
        multiple: false
      });
      if (!handle) {
        return null;
      }
      const file = await handle.getFile();
      return {
        name: file.name,
        path: file.name
      };
    }

    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.onchange = async () => {
        const file = input.files?.[0];
        if (!file) {
          resolve(null);
          return;
        }
        resolve({
          name: file.name,
          path: file.name
        });
      };
      input.click();
    });
  }

  async createNew(type: DocumentType): Promise<OpenDocumentResult> {
    const schema = documentSchemas[type];
    return {
      name: schema.defaultFileName,
      path: schema.defaultFileName,
      text: createEmptyTemplate(type)
    };
  }

  async detectType(path: string | undefined, text: string): Promise<DocumentType> {
    return detectDocumentType(path, text);
  }

  async parse(type: DocumentType, text: string): Promise<ParsedDocumentResult> {
    const schema = documentSchemas[type];
    const parsed = parseStructuredDocument(text, schema);
    return {
      type,
      model: parsed.model,
      issues: parsed.issues
    };
  }

  async validate(type: DocumentType, text: string, model: StructuredModel | null): Promise<ValidationIssue[]> {
    const schema = documentSchemas[type];
    const parsed = parseStructuredDocument(text, schema);
    return [...parsed.issues, ...validateStructuredModel(model ?? parsed.model, schema)];
  }

  async serialize(type: DocumentType, model: StructuredModel): Promise<string> {
    return serializeStructuredDocument(model, documentSchemas[type]);
  }

  async save(request: SaveDocumentRequest): Promise<SaveDocumentResult> {
    if (window.showSaveFilePicker) {
      const handle = await window.showSaveFilePicker({
        suggestedName: request.suggestedName,
        types: [{ description: "DC Config", accept: { "text/plain": [".dc"] } }]
      });
      const writable = await handle.createWritable();
      await writable.write(request.text);
      await writable.close();
      return {
        name: request.suggestedName,
        path: request.path ?? request.suggestedName
      };
    }

    const blob = new Blob([request.text], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = request.suggestedName;
    anchor.click();
    URL.revokeObjectURL(url);
    return {
      name: request.suggestedName,
      path: request.path ?? request.suggestedName
    };
  }
}
