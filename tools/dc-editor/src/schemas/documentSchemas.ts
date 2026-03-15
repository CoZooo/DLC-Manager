import { DocumentSchema, DocumentType, ROOT_SECTION_KEY, SectionSchema } from "../models/editor";

const typeOptions = [
  { value: "mod", label: "mod" },
  { value: "resourcepack", label: "resourcepack" },
  { value: "datapack", label: "datapack" },
  { value: "gunpack", label: "gunpack" },
  { value: "config", label: "config" },
  { value: "others", label: "others" }
];

const basicFields = (options?: { includeType?: boolean; includeAppliedTarget?: boolean }): SectionSchema => ({
  key: "basic",
  label: "[basic]",
  fields: [
    {
      key: "identifier",
      label: "identifier",
      kind: "text",
      help: "逻辑标识符；用于依赖、冲突、预设等匹配。"
    },
    {
      key: "file_name",
      label: "file_name",
      kind: "text",
      help: "实际文件名；用于确定磁盘上的目标名称。"
    },
    ...((options?.includeType ?? true)
      ? [{
          key: "type",
          label: "type",
          kind: "select" as const,
          options: typeOptions,
          help: "DLC 类型；可选 mod、resourcepack、datapack、gunpack、config、others。"
        }]
      : []),
    ...((options?.includeAppliedTarget ?? false)
      ? [{
          key: "appliedTarget",
          label: "appliedTarget",
          kind: "text" as const,
          help: "应用目标路径；可填写相对路径或绝对路径。"
        }]
      : []),
    {
      key: "isInList",
      label: "isInList",
      kind: "boolean",
      help: "是否在 DLC 列表中显示该项。"
    },
    {
      key: "isHidden",
      label: "isHidden",
      kind: "boolean",
      help: "是否隐藏该项；适合无独立意义的库或前置。"
    },
    {
      key: "AllowEnableByAll",
      label: "AllowEnableByAll",
      kind: "boolean",
      help: "“全部开启”是否会影响该项。"
    },
    {
      key: "dependencies",
      label: "dependencies",
      kind: "list",
      help: "前置 identifier 列表；当前表单模式仅支持简单列表。"
    },
    {
      key: "conflicts",
      label: "conflicts",
      kind: "list",
      help: "冲突 identifier 列表。"
    },
    {
      key: "alarm",
      label: "alarm",
      kind: "boolean",
      help: "启用前是否弹出警告确认页面。"
    }
  ]
});

const displayNameSection: SectionSchema = {
  key: "DisplayName",
  label: "[DisplayName]",
  dynamicLocale: {
    valueKind: "text",
    help: "本地化显示名称；手动填写语言代码和对应文本，可继续添加更多语言。"
  },
  fields: []
};

const tipSection: SectionSchema = {
  key: "tip",
  label: "[tip]",
  dynamicLocale: {
    valueKind: "textarea",
    help: "本地化提示文本；手动填写语言代码和对应文本，可继续添加更多语言。"
  },
  fields: []
};

export const documentSchemas: Record<DocumentType, DocumentSchema> = {
  info: {
    type: "info",
    label: "Info",
    summary: "编辑 DLC/info 下的 info 配置。",
    defaultFileName: "info.dc",
    supportsStructured: true,
    sections: [basicFields(), displayNameSection, tipSection]
  },
  remote: {
    type: "remote",
    label: "Remote",
    summary: "编辑 DLC/remote 下的远程下载配置。",
    defaultFileName: "remote.dc",
    supportsStructured: true,
    sections: [
      {
        key: "download",
        label: "[download]",
        description: "下载源。第一阶段支持常用字段与简单 URL 列表。",
        fields: [
          {
            key: "modrinth_projectid",
            label: "modrinth_projectid",
            kind: "text",
            help: "Modrinth 项目 ID 或 slug。"
          },
          {
            key: "modrinth_versionid",
            label: "modrinth_versionid",
            kind: "text",
            help: "Modrinth 版本 ID 或版本号。"
          },
          {
            key: "curseforge_projectid",
            label: "curseforge_projectid",
            kind: "text",
            help: "CurseForge 项目 ID 或 slug。"
          },
          {
            key: "curseforge_fileid",
            label: "curseforge_fileid",
            kind: "text",
            help: "CurseForge 文件 ID、版本串或文件名。"
          },
          {
            key: "directUrl",
            label: "directUrl",
            kind: "list",
            help: "直接下载链接；每行一个 URL，会写回为字符串数组。"
          },
          {
            key: "priority",
            label: "priority",
            kind: "text",
            help: "下载源优先级；使用逗号分隔，例如 curseforge,modrinth,directurl。"
          }
        ]
      },
      basicFields({ includeAppliedTarget: true }),
      displayNameSection,
      tipSection
    ]
  },
  required: {
    type: "required",
    label: "Required",
    summary: "编辑 DLC/required 下的必要资源配置。",
    defaultFileName: "required.dc",
    supportsStructured: true,
    sections: [
      {
        key: "download",
        label: "[download]",
        description: "下载源。第一阶段支持常用字段与简单 URL 列表。",
        fields: [
          {
            key: "modrinth_projectid",
            label: "modrinth_projectid",
            kind: "text",
            help: "Modrinth 项目 ID 或 slug。"
          },
          {
            key: "modrinth_versionid",
            label: "modrinth_versionid",
            kind: "text",
            help: "Modrinth 版本 ID 或版本号。"
          },
          {
            key: "curseforge_projectid",
            label: "curseforge_projectid",
            kind: "text",
            help: "CurseForge 项目 ID 或 slug。"
          },
          {
            key: "curseforge_fileid",
            label: "curseforge_fileid",
            kind: "text",
            help: "CurseForge 文件 ID、版本串或文件名。"
          },
          {
            key: "directUrl",
            label: "directUrl",
            kind: "list",
            help: "直接下载链接；每行一个 URL，会写回为字符串数组。"
          },
          {
            key: "priority",
            label: "priority",
            kind: "text",
            help: "下载源优先级；使用逗号分隔，例如 curseforge,modrinth,directurl。"
          }
        ]
      },
      basicFields({ includeType: false, includeAppliedTarget: true }),
      displayNameSection,
      tipSection
    ]
  },
  preset: {
    type: "preset",
    label: "Preset",
    summary: "编辑 DLC/preset 下的预设配置。",
    defaultFileName: "preset.dc",
    supportsStructured: true,
    sections: [
      {
        key: "basic",
        label: "[basic]",
        fields: [
          {
            key: "enable",
            label: "enable",
            kind: "list",
            help: "预设启用的 DLC 或预设 identifier 列表。"
          },
          {
            key: "conflicts",
            label: "conflicts",
            kind: "list",
            help: "与该预设冲突的 DLC identifier 列表。"
          },
          {
            key: "alarm",
            label: "alarm",
            kind: "boolean",
            help: "启用预设前是否先显示警告确认。"
          }
        ]
      },
      displayNameSection,
      tipSection
    ]
  },
  notice: {
    type: "notice",
    label: "Notice",
    summary: "编辑公告 notice.dc 与显示参数。",
    defaultFileName: "notice.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "根级字段",
        fields: [
          {
            key: "isNoticeEnabled",
            label: "isNoticeEnabled",
            kind: "boolean",
            help: "是否启用公告功能。"
          },
          {
            key: "neverShow",
            label: "neverShow",
            kind: "boolean",
            help: "是否永不显示该公告。"
          },
          {
            key: "ShowFrequency",
            label: "ShowFrequency",
            kind: "select",
            options: [
              { value: "perlaunchmanager", label: "perlaunchmanager" },
              { value: "pergamesession", label: "pergamesession" },
              { value: "perlaunchgame", label: "perlaunchgame" },
              { value: "once", label: "once" }
            ],
            help: "公告显示频率；支持 perlaunchmanager、pergamesession、perlaunchgame、once。"
          }
        ]
      },
      {
        key: "Title",
        label: "[Title]",
        fields: [
          { key: "en_us", label: "en_us", kind: "text", help: "公告标题（英文）。" },
          { key: "zh_cn", label: "zh_cn", kind: "text", help: "公告标题（简体中文）。" }
        ]
      },
      {
        key: "Subhead",
        label: "[Subhead]",
        fields: [
          {
            key: "enabled",
            label: "enabled",
            kind: "boolean",
            help: "是否启用副标题。"
          },
          { key: "en_us", label: "en_us", kind: "text", help: "公告副标题（英文）。" },
          { key: "zh_cn", label: "zh_cn", kind: "text", help: "公告副标题（简体中文）。" }
        ]
      },
      {
        key: "ContentUrl",
        label: "[ContentUrl]",
        fields: [
          { key: "en_us", label: "en_us", kind: "text", help: "在线公告内容 URL（英文）；优先级高于 Contents。" },
          { key: "zh_cn", label: "zh_cn", kind: "text", help: "在线公告内容 URL（简体中文）；优先级高于 Contents。" }
        ]
      },
      {
        key: "Contents",
        label: "[Contents]",
        fields: [
          { key: "en_us", label: "en_us", kind: "textarea", help: "公告正文（英文）；支持 Markdown 与颜色代码。" },
          { key: "zh_cn", label: "zh_cn", kind: "textarea", help: "公告正文（简体中文）；支持 Markdown 与颜色代码。" }
        ]
      }
    ]
  },
  redirect: {
    type: "redirect",
    label: "Redirect",
    summary: "编辑 redirect 配置。",
    defaultFileName: "redirect.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "根级字段",
        fields: [
          {
            key: "RedirectUrl",
            label: "RedirectUrl",
            kind: "text",
            help: "跳转目标 URL。"
          },
          {
            key: "available",
            label: "available",
            kind: "text",
            help: "控制可见性；可留空、填写语言代码，或填 hidden。"
          }
        ]
      },
      {
        key: "DisplayName",
        label: "[DisplayName]",
        fields: [
          { key: "en_us", label: "en_us", kind: "text", help: "列表名称（英文）。" },
          { key: "zh_cn", label: "zh_cn", kind: "text", help: "列表名称（简体中文）。" }
        ]
      },
      {
        key: "notice",
        label: "[notice]",
        fields: [
          { key: "en_us", label: "en_us", kind: "textarea", help: "跳转前确认说明（英文）。" },
          { key: "zh_cn", label: "zh_cn", kind: "textarea", help: "跳转前确认说明（简体中文）。" }
        ]
      },
      tipSection
    ]
  },
  update: {
    type: "update",
    label: "Update",
    summary: "编辑 update.dc。第一阶段以原文模式为主。",
    defaultFileName: "update.dc",
    supportsStructured: false,
    sections: []
  },
  version: {
    type: "version",
    label: "Version",
    summary: "编辑 version.dc。",
    defaultFileName: "version.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "根级字段",
        description: "version.dc 顶部直接写字段，不包在任何表头下。",
        fields: [
          {
            key: "base",
            label: "base",
            kind: "text",
            help: "当前整合包底包版本。"
          },
          {
            key: "dlc",
            label: "dlc",
            kind: "text",
            help: "当前 DLC 版本。"
          },
          {
            key: "rollback_from",
            label: "rollback_from",
            kind: "text",
            help: "最近一次回溯来源版本对。"
          },
          {
            key: "changelog_shown",
            label: "changelog_shown",
            kind: "text",
            help: "已显示过更新日志的版本标记。"
          }
        ]
      }
    ]
  },
  meta: {
    type: "meta",
    label: "Meta",
    summary: "编辑 meta.dc。第一阶段以原文模式为主。",
    defaultFileName: "meta.dc",
    supportsStructured: false,
    sections: []
  },
  state: {
    type: "state",
    label: "State",
    summary: "编辑 state.dc。第一阶段以原文模式为主。",
    defaultFileName: "state.dc",
    supportsStructured: false,
    sections: []
  },
  update_skip: {
    type: "update_skip",
    label: "Update Skip",
    summary: "编辑 update_skip.dc。第一阶段以原文模式为主。",
    defaultFileName: "update_skip.dc",
    supportsStructured: false,
    sections: []
  },
  config_client: {
    type: "config_client",
    label: "Client Config",
    summary: "编辑客户端 config/dlc_manager/config.dc。",
    defaultFileName: "config.dc",
    supportsStructured: true,
    sections: [
      {
        key: "paths",
        label: "[paths]",
        fields: [
          { key: "dlcRoot", label: "dlcRoot", kind: "text" },
          { key: "taczRoot", label: "taczRoot", kind: "text" },
          { key: "resourcepackRoot", label: "resourcepackRoot", kind: "text" },
          { key: "datapackRoot", label: "datapackRoot", kind: "text" }
        ]
      },
      {
        key: "ui",
        label: "[ui]",
        fields: [
          { key: "isEnableAllButtonShown", label: "isEnableAllButtonShown", kind: "boolean" },
          { key: "isConfigButtonShown", label: "isConfigButtonShown", kind: "boolean" },
          { key: "isUButtomShown", label: "isUButtomShown", kind: "boolean" },
          { key: "isUpdateManagerOn", label: "isUpdateManagerOn", kind: "boolean" },
          { key: "isSelfUpdateAutoCheckOn", label: "isSelfUpdateAutoCheckOn", kind: "boolean" },
          { key: "isPresetFuncOn", label: "isPresetFuncOn", kind: "boolean" },
          { key: "ShowOnFirstRun", label: "ShowOnFirstRun", kind: "boolean" },
          { key: "WaitingTime", label: "WaitingTime", kind: "number" },
          { key: "serverSupport", label: "serverSupport", kind: "boolean" },
          { key: "titleAnchor", label: "titleAnchor", kind: "text" },
          { key: "titleOffsetX", label: "titleOffsetX", kind: "number" },
          { key: "titleOffsetY", label: "titleOffsetY", kind: "number" },
          { key: "titleUpdateAnchor", label: "titleUpdateAnchor", kind: "text" },
          { key: "titleUpdateOffsetX", label: "titleUpdateOffsetX", kind: "number" },
          { key: "titleUpdateOffsetY", label: "titleUpdateOffsetY", kind: "number" },
          { key: "noticeDisplayMode", label: "noticeDisplayMode", kind: "text" },
          { key: "noticeButtonAnchor", label: "noticeButtonAnchor", kind: "text" },
          { key: "noticeButtonOffsetX", label: "noticeButtonOffsetX", kind: "number" },
          { key: "noticeButtonOffsetY", label: "noticeButtonOffsetY", kind: "number" },
          { key: "noticePanelAnchor", label: "noticePanelAnchor", kind: "text" },
          { key: "noticePanelOffsetX", label: "noticePanelOffsetX", kind: "number" },
          { key: "noticePanelOffsetY", label: "noticePanelOffsetY", kind: "number" },
          { key: "noticePanelWidth", label: "noticePanelWidth", kind: "number" },
          { key: "noticePanelHeight", label: "noticePanelHeight", kind: "number" },
          { key: "modpackUrl", label: "modpackUrl", kind: "text" },
          { key: "updateGithubRepo", label: "updateGithubRepo", kind: "textarea" }
        ]
      },
      {
        key: "downloads",
        label: "[downloads]",
        fields: [
          { key: "maxConcurrentDownloads", label: "maxConcurrentDownloads", kind: "number" },
          { key: "maxSegmentsPerFile", label: "maxSegmentsPerFile", kind: "number" },
          { key: "minSegmentMB", label: "minSegmentMB", kind: "number" },
          { key: "maxRetryCount", label: "maxRetryCount", kind: "number" },
          { key: "connectTimeoutSeconds", label: "connectTimeoutSeconds", kind: "number" },
          { key: "taskTimeoutSeconds", label: "taskTimeoutSeconds", kind: "number" }
        ]
      }
    ]
  },
  config_server: {
    type: "config_server",
    label: "Server Config",
    summary: "编辑服务端配置 dc 文件。",
    defaultFileName: "server-config.dc",
    supportsStructured: true,
    sections: [
      {
        key: "server",
        label: "[server]",
        fields: [
          { key: "serverSupport", label: "serverSupport", kind: "boolean" },
          { key: "serverPort", label: "serverPort", kind: "number" },
          { key: "serverPassword", label: "serverPassword", kind: "text" },
          { key: "modpackUrl", label: "modpackUrl", kind: "text" },
          { key: "updateGithubRepo", label: "updateGithubRepo", kind: "textarea" },
          { key: "rconHost", label: "rconHost", kind: "text" },
          { key: "rconPort", label: "rconPort", kind: "number" },
          { key: "rconPassword", label: "rconPassword", kind: "text" }
        ]
      },
      {
        key: "ui",
        label: "[ui]",
        fields: [
          { key: "isEnableAllButtonShown", label: "isEnableAllButtonShown", kind: "boolean" },
          { key: "isUpdateManagerOn", label: "isUpdateManagerOn", kind: "boolean" },
          { key: "isSelfUpdateAutoCheckOn", label: "isSelfUpdateAutoCheckOn", kind: "boolean" },
          { key: "isPresetFuncOn", label: "isPresetFuncOn", kind: "boolean" },
          { key: "WaitingTime", label: "WaitingTime", kind: "number" }
        ]
      },
      {
        key: "paths",
        label: "[paths]",
        fields: [
          { key: "dlcRoot", label: "dlcRoot", kind: "text" },
          { key: "taczRoot", label: "taczRoot", kind: "text" },
          { key: "resourcepackRoot", label: "resourcepackRoot", kind: "text" },
          { key: "datapackRoot", label: "datapackRoot", kind: "text" }
        ]
      },
      {
        key: "downloads",
        label: "[downloads]",
        fields: [
          { key: "maxConcurrentDownloads", label: "maxConcurrentDownloads", kind: "number" },
          { key: "maxSegmentsPerFile", label: "maxSegmentsPerFile", kind: "number" },
          { key: "minSegmentMB", label: "minSegmentMB", kind: "number" },
          { key: "maxRetryCount", label: "maxRetryCount", kind: "number" },
          { key: "connectTimeoutSeconds", label: "connectTimeoutSeconds", kind: "number" },
          { key: "taskTimeoutSeconds", label: "taskTimeoutSeconds", kind: "number" }
        ]
      }
    ]
  }
};

export const orderedDocumentTypes: DocumentType[] = [
  "info",
  "remote",
  "required",
  "preset",
  "notice",
  "redirect",
  "update",
  "version",
  "meta",
  "state",
  "update_skip",
  "config_client",
  "config_server"
];

export const configurableDocumentTypes: DocumentType[] = [
  "info",
  "remote",
  "required",
  "preset",
  "notice",
  "redirect",
  "update",
  "version"
];

export const internalDocumentTypes = new Set<DocumentType>([
  "meta",
  "state",
  "update_skip",
  "config_client",
  "config_server"
]);
