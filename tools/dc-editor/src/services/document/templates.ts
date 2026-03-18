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
        "dependencies = []",
        "conflicts = []",
        "alarm = false",
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
        "dependencies = []",
        "conflicts = []",
        "alarm = false",
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
        "dependencies = []",
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
        "enable = []",
        "conflicts = []",
        "alarm = false",
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
        "base = \"1.0.0\"",
        "dlc = \"1.0.0\""
      ].join("\n");
    case "meta":
      return [
        'base_old = "1.0.0"',
        'base_new = "1.0.1"',
        'dlc_old = "1.0.0"',
        'dlc_new = "1.0.1"',
        'timestamp = ""',
        "hidden = false",
        "",
        "[changelog]",
        ""
      ].join("\n");
    case "state":
      return ["[DLC]", ""].join("\n");
    case "update_skip":
      return ["[hot_update]", ""].join("\n");
    case "config_client":
      return [
        "[paths]",
        'dlcRoot = ""',
        'taczRoot = ""',
        'resourcepackRoot = ""',
        'datapackRoot = ""',
        "",
        "[ui]",
        "isEnableAllButtonShown = true",
        "isConfigButtonShown = true",
        "isUButtomShown = true",
        "isUpdateManagerOn = true",
        "isSelfUpdateAutoCheckOn = true",
        "isPresetFuncOn = true",
        "ShowOnFirstRun = false",
        "WaitingTime = 0",
        "serverSupport = true",
        'titleAnchor = "top_center"',
        "titleOffsetX = 0",
        "titleOffsetY = 0",
        'titleUpdateAnchor = "auto"',
        "titleUpdateOffsetX = 0",
        "titleUpdateOffsetY = 0",
        'noticeDisplayMode = "manager_button"',
        'noticeButtonAnchor = "bottom_right"',
        "noticeButtonOffsetX = 0",
        "noticeButtonOffsetY = 0",
        'noticePanelAnchor = "top_left"',
        "noticePanelOffsetX = 0",
        "noticePanelOffsetY = 0",
        "noticePanelWidth = 0",
        "noticePanelHeight = 0",
        'modpackUrl = ""',
        'updateGithubRepo = ""',
        "",
        "[downloads]",
        "maxConcurrentDownloads = 4",
        "maxSegmentsPerFile = 8",
        "minSegmentMB = 8",
        "maxRetryCount = 5",
        "connectTimeoutSeconds = 30",
        "taskTimeoutSeconds = 600"
      ].join("\n");
    case "config_server":
      return [
        "[server]",
        "serverSupport = true",
        "serverPort = 25566",
        'serverPassword = ""',
        'modpackUrl = ""',
        'updateGithubRepo = ""',
        'rconHost = ""',
        "rconPort = 25575",
        'rconPassword = ""',
        "",
        "[ui]",
        "isEnableAllButtonShown = true",
        "isUpdateManagerOn = true",
        "isSelfUpdateAutoCheckOn = true",
        "isPresetFuncOn = true",
        "WaitingTime = 0",
        "",
        "[paths]",
        'dlcRoot = ""',
        'taczRoot = ""',
        'resourcepackRoot = ""',
        'datapackRoot = ""',
        "",
        "[downloads]",
        "maxConcurrentDownloads = 4",
        "maxSegmentsPerFile = 8",
        "minSegmentMB = 8",
        "maxRetryCount = 5",
        "connectTimeoutSeconds = 30",
        "taskTimeoutSeconds = 600"
      ].join("\n");
    default:
      return "";
  }
}

export function emptyStructuredModel(): StructuredModel {
  return {};
}
