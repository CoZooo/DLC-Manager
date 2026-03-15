import { DocumentType } from "../../models/editor";

function pathBaseName(path?: string): string {
  if (!path) {
    return "";
  }
  const slash = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return slash >= 0 ? path.substring(slash + 1) : path;
}

export function detectDocumentType(path?: string, text?: string): DocumentType {
  const name = pathBaseName(path).toLowerCase();
  if (name === "notice.dc") {
    return "notice";
  }
  if (name === "update.dc") {
    return "update";
  }
  if (name === "version.dc") {
    return "version";
  }
  if (name === "meta.dc") {
    return "meta";
  }
  if (name === "state.dc") {
    return "state";
  }
  if (name === "update_skip.dc") {
    return "update_skip";
  }
  if (name === "config.dc") {
    return "config_client";
  }
  if (name === "server-config.dc" || name === "server_config.dc") {
    return "config_server";
  }

  const lowerPath = (path || "").toLowerCase();
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

  const body = (text || "").toLowerCase();
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
  if (body.includes("[server]") && body.includes("serverport")) {
    return "config_server";
  }
  if (body.includes("[downloads]") && body.includes("maxconcurrentdownloads")) {
    return "config_client";
  }
  if (body.includes("redirecturl")) {
    return "redirect";
  }
  return "info";
}
