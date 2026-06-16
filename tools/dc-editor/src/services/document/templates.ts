import { DocumentType, StructuredModel } from "../../models/editor";

export function createEmptyTemplate(type: DocumentType): string {
  switch (type) {
    case "info":
      return [
        "[basic]",
        'identifier = ""',
        'file_name = ""',
        'type = "mod"',
        'RedirectUrl = ""',
        "isInList = true",
        "isHidden = false",
        "AllowEnableByAll = true",
        "alarm = false",
        "",
        "[dependencies]",
        "",
        "[conflicts]",
        "",
        "[DisplayName]",
        "",
        "[tip]",
        ""
      ].join("\n");
    case "remote":
      return [
        "[download]",
        'modrinth_projectid = ""',
        'modrinth_versionid = ""',
        'curseforge_projectid = ""',
        'curseforge_fileid = ""',
        "directUrl = []",
        'priority = ["curseforge", "modrinth", "directUrl"]',
        "",
        "[basic]",
        'identifier = ""',
        'file_name = ""',
        'type = "mod"',
        'RedirectUrl = ""',
        "isInList = true",
        "isHidden = false",
        "AllowEnableByAll = true",
        "alarm = false",
        "",
        "[dependencies]",
        "",
        "[conflicts]",
        "",
        "[DisplayName]",
        "",
        "[tip]",
        ""
      ].join("\n");
    case "required":
      return [
        "[download]",
        'modrinth_projectid = ""',
        'modrinth_versionid = ""',
        'curseforge_projectid = ""',
        'curseforge_fileid = ""',
        "directUrl = []",
        'priority = ["curseforge", "modrinth", "directUrl"]',
        "",
        "[basic]",
        'identifier = ""',
        'file_name = ""',
        'appliedTarget = ""',
        'RedirectUrl = ""',
        "isInList = true",
        "isHidden = false",
        "",
        "[dependencies]",
        "",
        "[DisplayName]",
        "",
        "[tip]",
        ""
      ].join("\n");
    case "required_notice":
      return [
        "[download_desc]",
        "",
        "[manage_desc]",
        ""
      ].join("\n");
    case "preset":
      return [
        "[basic]",
        'identifier = ""',
        "alarm = false",
        "",
        "[dependencies]",
        "",
        "[conflicts]",
        "",
        "[DisplayName]",
        "",
        "[tip]",
        ""
      ].join("\n");
    case "notice":
      return [
        "isNoticeEnabled = false",
        "neverShow = false",
        'ShowFrequency = "perlaunchmanager"',
        "",
        "[Title]",
        "",
        "[Subhead]",
        "enabled = false",
        "",
        "[ContentUrl]",
        "",
        "[Contents]",
        ""
      ].join("\n");
    case "redirect":
      return [
        'RedirectUrl = ""',
        'available = ""',
        "",
        "[DisplayName]",
        "",
        "[notice]",
        "",
        "[tip]",
        ""
      ].join("\n");
    case "update":
      return [
        "isHidden = false",
        "",
        "[version]",
        'platform = "universal"',
        'base = ""',
        'dlc = ""',
        "",
        "[changelog]",
        "",
        "[file]",
        ""
      ].join("\n");
    case "version":
      return [
        "modpack = \"\"",
        "base = \"1.0.0\"",
        "dlc = \"1.0.0\""
      ].join("\n");
    case "pack":
      return [
        "[basic]",
        'identifier = ""',
        'version = "1.0.0"',
        "author = []",
        "",
        "[modpack]",
        "modpack = any",
        'base = ["any"]',
        'dlc = ["any"]',
        "",
        "[links]",
        'homepage = ""',
        'issue = ""',
        'sponsor = ""',
        "",
        "[dependencies]",
        "",
        "[conflicts]",
        "",
        "[DisplayName]",
        "",
        "[description]",
        ""
      ].join("\n");
    default:
      return "";
  }
}

export function emptyStructuredModel(): StructuredModel {
  return {};
}
