import { DocumentType } from "../../models/editor";

function pathBaseName(path?: string): string {
  if (!path) {
    return "";
  }
  const slash = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return slash >= 0 ? path.substring(slash + 1) : path;
}

function hasAssignment(body: string, key: string): boolean {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`(^|\\n)\\s*${escaped}\\s*=`, "m");
  return pattern.test(body);
}

function hasRootAssignment(body: string, key: string): boolean {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`^\\s*${escaped}\\s*=`);
  for (const line of body.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith("//")) {
      continue;
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      return false;
    }
    if (pattern.test(line)) {
      return true;
    }
  }
  return false;
}

export function detectDocumentType(path?: string, text?: string): DocumentType {
  const name = pathBaseName(path).toLowerCase();
  const lowerPath = (path || "").toLowerCase();
  const body = (text || "").toLowerCase();

  if (
    (name === "notice.dc" && (lowerPath.includes("/required/") || lowerPath.includes("\\required\\"))) ||
    body.includes("[download_desc]") ||
    body.includes("[manage_desc]")
  ) {
    return "required_notice";
  }

  if (name === "notice.dc") {
    return "notice";
  }
  if (name === "remote.dc") {
    return "remote";
  }
  if (name === "required.dc") {
    return "required";
  }
  if (name === "preset.dc") {
    return "preset";
  }
  if (name === "redirect.dc") {
    return "redirect";
  }
  if (name === "info.dc") {
    return "info";
  }
  if (name === "update.dc") {
    return "update";
  }
  if (name === "version.dc") {
    return "version";
  }
  if (lowerPath.includes("/remote/") || lowerPath.includes("\\remote\\")) {
    return "remote";
  }
  if (lowerPath.includes("/required/") || lowerPath.includes("\\required\\")) {
    return "required";
  }
  if (lowerPath.includes("/preset/") || lowerPath.includes("\\preset\\")) {
    return "preset";
  }
  if (lowerPath.includes("/redirect/") || lowerPath.includes("\\redirect\\")) {
    return "redirect";
  }
  if (lowerPath.includes("/info/") || lowerPath.includes("\\info\\")) {
    return "info";
  }

  if (body.includes("[download]")) {
    return body.includes("modrinth_projectid") || body.includes("curseforge_projectid") ? "remote" : "required";
  }
  if (body.includes("[content]") || body.includes("contenturl")) {
    return "notice";
  }
  if (body.includes("isnoticeenabled") || body.includes("[contents]") || body.includes("[title]")) {
    return "notice";
  }
  if (body.includes("rollback_from") || body.includes("changelog_shown")) {
    return "version";
  }
  if (body.includes("[file]") || body.includes("[update]")) {
    return "update";
  }
  if (hasAssignment(body, "enable") && body.includes("[displayname]") && body.includes("[tip]")) {
    return "preset";
  }
  if (hasRootAssignment(body, "redirecturl") && (hasRootAssignment(body, "available") || body.includes("[notice]"))) {
    return "redirect";
  }
  return "info";
}
