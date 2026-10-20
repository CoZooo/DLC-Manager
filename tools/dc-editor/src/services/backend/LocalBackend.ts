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
import { documentSchemas } from "../../schemas/documentSchemas";
import { createEmptyTemplate } from "../document/templates";
import { parseStructuredDocument, serializeStructuredDocument, validateStructuredModel } from "../document/simpleDc";
import { detectDocumentType } from "../document/typeDetection";
import { embeddedHost, isTauriRuntime } from "../../runtime/tauri";
import { EditorBackend, ParsedDocumentResult } from "./EditorBackend";
import { invoke } from "@tauri-apps/api/core";
import { open as tauriOpen, save as tauriSave } from "@tauri-apps/plugin-dialog";
import { readTextFile } from "@tauri-apps/plugin-fs";

declare global {
  interface Window {
    showOpenFilePicker?: (options?: unknown) => Promise<Array<{ getFile(): Promise<File> }>>;
    showSaveFilePicker?: (options?: unknown) => Promise<{ createWritable(): Promise<{ write(data: string | Blob | BufferSource): Promise<void>; close(): Promise<void> }> }>;
  }
}

export class LocalBackend implements EditorBackend {
  readonly mode = "local" as const;

  async openWithPicker(): Promise<OpenDocumentResult | null> {
    const opened = await this.openManyWithPicker();
    return opened[0] || null;
  }

  async openManyWithPicker(): Promise<OpenDocumentResult[]> {
    const host = embeddedHost();
    if (host) {
      return JSON.parse(host.openFiles()) as OpenDocumentResult[];
    }
    if (isTauriRuntime()) {
      return this.openManyWithTauriDialog();
    }
    if (window.showOpenFilePicker) {
      const handles = await window.showOpenFilePicker({
        types: [{ description: "DC Config", accept: { "text/plain": [".dc"] } }],
        excludeAcceptAllOption: false,
        multiple: true
      });
      if (!handles || handles.length === 0) {
        return [];
      }
      return Promise.all(handles.map(async (handle) => {
        const file = await handle.getFile();
        return {
          name: file.name,
          path: file.name,
          text: await file.text()
        };
      }));
    }

    return this.openManyWithInputPicker(".dc,text/plain");
  }

  async pickFile(): Promise<PickedFileResult | null> {
    const host = embeddedHost();
    if (host) {
      return JSON.parse(host.pickFile()) as PickedFileResult | null;
    }
    if (isTauriRuntime()) {
      return this.pickWithTauriDialog();
    }
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

    return this.pickWithInputPicker();
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

  async serializeForSave(type: DocumentType, model: StructuredModel): Promise<string> {
    return serializeStructuredDocument(model, documentSchemas[type], { applyImplicitDefaultLocale: true });
  }

  async save(request: SaveDocumentRequest): Promise<SaveDocumentResult> {
    const host = embeddedHost();
    if (host) {
      return JSON.parse(host.saveFile(request.suggestedName, request.text, request.path)) as SaveDocumentResult;
    }
    if (isTauriRuntime()) {
      return this.saveWithTauriDialog(request);
    }
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

    return this.saveWithDownload(request);
  }

  async saveArchive(request: SaveArchiveRequest): Promise<SaveDocumentResult> {
    const host = embeddedHost();
    if (host) {
      let binary = "";
      for (const byte of request.data) {
        binary += String.fromCharCode(byte);
      }
      const base64 = btoa(binary);
      return JSON.parse(host.saveArchive(request.suggestedName, base64)) as SaveDocumentResult;
    }
    const archiveBytes = Uint8Array.from(request.data);
    if (isTauriRuntime()) {
      return this.saveArchiveWithTauriDialog({
        ...request,
        data: archiveBytes
      });
    }
    if (window.showSaveFilePicker) {
      const handle = await window.showSaveFilePicker({
        suggestedName: request.suggestedName,
        types: [{ description: "ZIP Archive", accept: { "application/zip": [".zip"] } }]
      });
      const writable = await handle.createWritable();
      await writable.write(archiveBytes);
      await writable.close();
      return {
        name: request.suggestedName,
        path: request.suggestedName
      };
    }
    return this.saveArchiveWithDownload({
      ...request,
      data: archiveBytes
    });
  }

  async fetchNoticeContent(url: string): Promise<string | null> {
    const normalized = url.trim();
    if (!normalized) {
      return null;
    }
    if (isTauriRuntime()) {
      const text = await invoke<string | null>("fetch_notice_content", { url: normalized });
      return text && text.trim() ? text : null;
    }
    const response = await fetch(normalized, {
      method: "GET",
      redirect: "follow"
    });
    if (!response.ok) {
      return null;
    }
    let text = await response.text();
    if (text.startsWith("\uFEFF")) {
      text = text.slice(1);
    }
    return text && text.trim() ? text : null;
  }

  private openManyWithInputPicker(accept: string): Promise<OpenDocumentResult[]> {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = accept;
      input.multiple = true;
      input.onchange = async () => {
        const files = Array.from(input.files || []);
        if (files.length === 0) {
          resolve([]);
          return;
        }
        resolve(await Promise.all(files.map(async (file) => ({
          name: file.name,
          path: file.name,
          text: await file.text()
        }))));
      };
      input.click();
    });
  }

  private pickWithInputPicker(): Promise<PickedFileResult | null> {
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

  private saveWithDownload(request: SaveDocumentRequest): SaveDocumentResult {
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

  private saveArchiveWithDownload(request: SaveArchiveRequest): SaveDocumentResult {
    const blob = new Blob([Uint8Array.from(request.data)], { type: "application/zip" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = request.suggestedName;
    anchor.click();
    URL.revokeObjectURL(url);
    return {
      name: request.suggestedName,
      path: request.suggestedName
    };
  }

  private async openManyWithTauriDialog(): Promise<OpenDocumentResult[]> {
    const selected = await tauriOpen({
      title: "打开 .dc 配置",
      directory: false,
      multiple: true,
      filters: [{ name: "DC Config", extensions: ["dc"] }]
    });
    if (!selected) {
      return [];
    }
    const paths = Array.isArray(selected) ? selected : [selected];
    return Promise.all(paths.map(async (path) => ({
      name: this.getPathName(path),
      path,
      text: await readTextFile(path)
    })));
  }

  private async pickWithTauriDialog(): Promise<PickedFileResult | null> {
    const selected = await tauriOpen({
      title: "选择 DLC 文件",
      directory: false,
      multiple: false
    });
    if (!selected || Array.isArray(selected)) {
      return null;
    }
    return {
      name: this.getPathName(selected),
      path: selected
    };
  }

  private async saveWithTauriDialog(request: SaveDocumentRequest): Promise<SaveDocumentResult> {
    const target = await tauriSave({
      title: "导出 .dc 配置",
      defaultPath: request.path ?? request.suggestedName,
      filters: [{ name: "DC Config", extensions: ["dc"] }]
    });
    if (!target) {
      throw new Error("用户取消了导出。");
    }
    await invoke("write_text_file_direct", { path: target, text: request.text });
    return {
      name: this.getPathName(target),
      path: target
    };
  }

  private async saveArchiveWithTauriDialog(request: SaveArchiveRequest): Promise<SaveDocumentResult> {
    const target = await tauriSave({
      title: "导出 ZIP 压缩包",
      defaultPath: request.suggestedName,
      filters: [{ name: "ZIP Archive", extensions: ["zip"] }]
    });
    if (!target) {
      throw new Error("用户取消了导出。");
    }
    await invoke("write_binary_file_direct", { path: target, bytes: Array.from(request.data) });
    return {
      name: this.getPathName(target),
      path: target
    };
  }

  private getPathName(path: string): string {
    const normalized = path.replace(/\\/g, "/");
    const segments = normalized.split("/");
    return segments[segments.length - 1] || path;
  }
}
