declare global {
  interface Window {
    __TAURI__?: unknown;
    __TAURI_INTERNALS__?: unknown;
    dlcEditorHost?: {
      openFiles(): string;
      pickFile(): string;
      saveFile(suggestedName: string, text: string, path?: string): string;
      saveArchive(suggestedName: string, base64: string): string;
    };
  }
}

export function embeddedHost(): NonNullable<Window["dlcEditorHost"]> | null {
  if (typeof window === "undefined" || !window.dlcEditorHost) {
    return null;
  }
  return window.dlcEditorHost;
}

export function isTauriRuntime(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  return typeof window.__TAURI_INTERNALS__ !== "undefined" || typeof window.__TAURI__ !== "undefined";
}
