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
import { parseStructuredDocument, serializeStructuredDocument, validateStructuredModel } from "../document/simpleDc";
import { createEmptyTemplate } from "../document/templates";
import { detectDocumentType } from "../document/typeDetection";
import { EditorBackend, ParsedDocumentResult } from "./EditorBackend";

const API_BASE_STORAGE_KEY = "dlc-manager-dc-editor-api-base-v1";
const API_TOKEN_STORAGE_KEY = "dlc-manager-dc-editor-api-token-v1";

interface ApiConfig {
  baseUrl: string;
  token: string;
}

interface ApiIssuePayload {
  severity?: "error" | "warning" | "info";
  source?: "syntax" | "schema" | "backend" | "api-placeholder";
  message?: string;
  line?: number;
}

interface ApiRootFile {
  name?: string;
  path?: string;
  type?: string;
}

interface ApiRootListResponse {
  roots?: Array<{
    rootIndex?: number;
    files?: ApiRootFile[];
  }>;
}

interface RemotePathSelection {
  rootIndex?: number;
  relativePath: string;
}

type ApiResponse<T> = {
  ok: boolean;
  data?: T;
  error?: {
    code?: string;
    message?: string;
  };
};

function getSearchParams(): URLSearchParams {
  if (typeof window === "undefined") {
    return new URLSearchParams();
  }
  return new URLSearchParams(window.location.search);
}

function loadApiConfig(): ApiConfig {
  const params = getSearchParams();
  const baseFromQuery = (params.get("apiBase") || "").trim();
  const tokenFromQuery = (params.get("apiToken") || "").trim();
  const storedBase = typeof window === "undefined" ? "" : window.localStorage.getItem(API_BASE_STORAGE_KEY) || "";
  const storedToken = typeof window === "undefined" ? "" : window.localStorage.getItem(API_TOKEN_STORAGE_KEY) || "";

  const baseUrl = normalizeBaseUrl(baseFromQuery || storedBase);
  const token = tokenFromQuery || storedToken;

  if (typeof window !== "undefined") {
    if (baseFromQuery) {
      window.localStorage.setItem(API_BASE_STORAGE_KEY, baseUrl);
    }
    if (tokenFromQuery) {
      window.localStorage.setItem(API_TOKEN_STORAGE_KEY, token);
    }
  }

  return { baseUrl, token };
}

function normalizeBaseUrl(value: string): string {
  if (!value) {
    return "";
  }
  return value.replace(/\/+$/, "");
}

function mapIssue(issue: ApiIssuePayload): ValidationIssue {
  return {
    severity: issue.severity || "error",
    source: issue.source || "backend",
    message: issue.message || "Unknown API validation issue.",
    line: issue.line
  };
}

function encodeRemotePath(relativePath: string, rootIndex?: number): string {
  const normalized = relativePath.replace(/\\/g, "/").replace(/^\/+/, "");
  if (typeof rootIndex === "number") {
    return `${rootIndex}:${normalized}`;
  }
  return normalized;
}

function decodeRemotePath(path?: string): RemotePathSelection | null {
  const value = (path || "").trim();
  if (!value) {
    return null;
  }
  const match = value.match(/^(\d+):(.*)$/);
  if (match) {
    return {
      rootIndex: Number.parseInt(match[1], 10),
      relativePath: match[2].replace(/^\/+/, "")
    };
  }
  return {
    relativePath: value.replace(/^\/+/, "")
  };
}

export class ApiBackend implements EditorBackend {
  readonly mode = "api" as const;

  private readonly config: ApiConfig;

  constructor() {
    this.config = loadApiConfig();
  }

  async openWithPicker(): Promise<OpenDocumentResult | null> {
    this.ensureConfigured();
    const listing = await this.request<ApiRootListResponse>("GET", "/api/dlc/root/list");
    const choices = this.collectRemoteChoices(listing);
    const promptLines = [
      "请输入要打开的远程配置路径。",
      "单根目录可直接填写相对路径；多根目录请使用 rootIndex:path。",
      ""
    ];
    if (choices.length > 0) {
      promptLines.push("可用配置：");
      promptLines.push(...choices.slice(0, 40).map((choice) => `- ${choice}`));
    }
    const selected = window.prompt(promptLines.join("\n"), choices[0] || "");
    const parsed = decodeRemotePath(selected || "");
    if (!parsed) {
      return null;
    }
    const response = await this.request<{
      name?: string;
      path?: string;
      rootIndex?: number;
      content?: string;
    }>("POST", "/api/dc/read", {
      path: parsed.relativePath,
      rootIndex: parsed.rootIndex
    });
    return {
      name: response.name || this.getPathName(parsed.relativePath),
      path: encodeRemotePath(response.path || parsed.relativePath, response.rootIndex),
      text: response.content || ""
    };
  }

  async pickFile(): Promise<PickedFileResult | null> {
    this.ensureConfigured();
    const selected = window.prompt("请输入 DLC 文件相对路径（相对于游戏目录）。", "");
    const parsed = decodeRemotePath(selected || "");
    if (!parsed || !parsed.relativePath) {
      return null;
    }
    return {
      name: this.getPathName(parsed.relativePath),
      path: encodeRemotePath(parsed.relativePath, parsed.rootIndex)
    };
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
    this.ensureConfigured();
    const parsedPath = decodeRemotePath(path);
    const response = await this.request<{ type?: string }>("POST", "/api/dc/detect-type", {
      path: parsedPath?.relativePath || path || "",
      content: text,
      name: path ? this.getPathName(path) : ""
    });
    return detectDocumentType(path, response.type || text);
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
    this.ensureConfigured();
    const response = await this.request<{
      issues?: ApiIssuePayload[];
    }>("POST", "/api/dc/validate", {
      type,
      content: text
    });
    const remoteIssues = (response.issues || []).map(mapIssue);
    const localIssues = validateStructuredModel(model, documentSchemas[type]);
    return [...remoteIssues, ...localIssues];
  }

  async serialize(type: DocumentType, model: StructuredModel): Promise<string> {
    return serializeStructuredDocument(model, documentSchemas[type]);
  }

  async serializeForSave(type: DocumentType, model: StructuredModel): Promise<string> {
    return serializeStructuredDocument(model, documentSchemas[type], { applyImplicitDefaultLocale: true });
  }

  async save(request: SaveDocumentRequest): Promise<SaveDocumentResult> {
    this.ensureConfigured();
    const existing = decodeRemotePath(request.path);
    let target = existing;
    if (!target) {
      const selected = window.prompt("请输入导出到远程工作区的相对路径。", request.suggestedName);
      target = decodeRemotePath(selected || "");
    }
    if (!target) {
      throw new Error("已取消导出。");
    }
    const response = await this.request<{
      name?: string;
      path?: string;
      rootIndex?: number;
    }>("POST", "/api/dc/write", {
      path: target.relativePath,
      rootIndex: target.rootIndex,
      type: detectDocumentType(request.path, request.text),
      content: request.text
    });
    return {
      name: response.name || this.getPathName(target.relativePath),
      path: encodeRemotePath(response.path || target.relativePath, response.rootIndex)
    };
  }

  private collectRemoteChoices(listing: ApiRootListResponse): string[] {
    const choices: string[] = [];
    for (const root of listing.roots || []) {
      const rootIndex = typeof root.rootIndex === "number" ? root.rootIndex : undefined;
      for (const file of root.files || []) {
        const path = (file.path || "").trim();
        if (!path) {
          continue;
        }
        choices.push(encodeRemotePath(path, rootIndex));
      }
    }
    return choices;
  }

  private ensureConfigured(): void {
    if (!this.config.baseUrl || !this.config.token) {
      throw new Error("API 模式未配置。请通过 URL 参数 backend=api&apiBase=...&apiToken=... 进入。");
    }
  }

  private async request<T>(method: "GET" | "POST", path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${this.config.baseUrl}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${this.config.token}`,
        ...(body ? { "Content-Type": "application/json" } : {})
      },
      body: body ? JSON.stringify(body) : undefined
    });
    const parsed = (await response.json()) as ApiResponse<T>;
    if (!response.ok || !parsed.ok) {
      throw new Error(parsed.error?.message || `API request failed: ${response.status}`);
    }
    return (parsed.data || {}) as T;
  }

  private getPathName(path: string): string {
    const withoutPrefix = path.replace(/^\d+:/, "");
    const normalized = withoutPrefix.replace(/\\/g, "/");
    const segments = normalized.split("/");
    return segments[segments.length - 1] || path;
  }
}
