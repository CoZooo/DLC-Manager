declare global {
  interface Window {
    __TAURI__?: unknown;
    __TAURI_INTERNALS__?: unknown;
  }
}

export function isTauriRuntime(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  return typeof window.__TAURI_INTERNALS__ !== "undefined" || typeof window.__TAURI__ !== "undefined";
}
