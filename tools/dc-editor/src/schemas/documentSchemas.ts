import { DocumentSchema, DocumentType, ROOT_SECTION_KEY, SectionSchema } from "../models/editor";

const typeOptions = [
  { value: "mod", label: "mod" },
  { value: "resourcepack", label: "resourcepack" },
  { value: "datapack", label: "datapack" },
  { value: "gunpack", label: "gunpack" },
  { value: "config", label: "config" },
  { value: "others", label: "others" }
];

const priorityOptions = [
  { value: "modrinth", label: "modrinth" },
  { value: "curseforge", label: "curseforge" },
  { value: "directUrl", label: "directUrl" }
];

const basicFields = (options?: { includeType?: boolean; includeAppliedTarget?: boolean; appliedTargetOnlyFor?: string[] }): SectionSchema => ({
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
      help: "DLC 文件名（包含扩展名）；用于确定磁盘上的目标名称。"
    },
    {
      key: "__multiFileEnabled",
      label: "__multiFileEnabled",
      kind: "boolean",
      hiddenInForm: true
    },
    {
      key: "fileNames",
      label: "fileNames",
      kind: "list",
      hiddenInForm: true
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
          ...((options?.appliedTargetOnlyFor && options.appliedTargetOnlyFor.length > 0)
            ? {
                visibleWhen: {
                  sectionKey: "basic",
                  fieldKey: "type",
                  oneOf: options.appliedTargetOnlyFor
                }
              }
            : {}),
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
      help: "是否遵循“全部开启”而开启。"
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

const requiredBasicSection: SectionSchema = {
  key: "basic",
  label: "[basic]",
  fields: [
    {
      key: "identifier",
      label: "identifier",
      kind: "text",
      help: "逻辑标识符；用于前置匹配。"
    },
    {
      key: "file_name",
      label: "file_name",
      kind: "text",
      help: "DLC 文件名（包含扩展名）；用于最终下载文件名。"
    },
    {
      key: "appliedTarget",
      label: "appliedTarget",
      kind: "text",
      help: "应用目标路径；可填写相对路径或绝对路径。"
    },
    {
      key: "isInList",
      label: "isInList",
      kind: "boolean",
      help: "是否在必要资源列表中显示该项。"
    },
    {
      key: "isHidden",
      label: "isHidden",
      kind: "boolean",
      help: "是否隐藏该项。"
    },
    {
      key: "dependencies",
      label: "dependencies",
      kind: "list",
      help: "前置 identifier 列表；当前表单模式仅支持简单列表。"
    }
  ]
};

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

const localeTextSection = (key: string, label: string, help: string): SectionSchema => ({
  key,
  label,
  dynamicLocale: {
    valueKind: "text",
    help
  },
  fields: []
});

const localeTextareaSection = (key: string, label: string, help: string): SectionSchema => ({
  key,
  label,
  dynamicLocale: {
    valueKind: "textarea",
    help
  },
  fields: []
});

export const documentSchemas: Record<DocumentType, DocumentSchema> = {
  info: {
    type: "info",
    label: "Info",
    summary: "DLC 文件的信息\n文件存放于 <DLC>/info 下",
    defaultFileName: "info.dc",
    supportsStructured: true,
    sections: [basicFields({ includeAppliedTarget: true, appliedTargetOnlyFor: ["others", "config"] }), displayNameSection, tipSection]
  },
  remote: {
    type: "remote",
    label: "Remote",
    summary: "需下载的 DLC\n文件存放于 <DLC>/remote 下",
    defaultFileName: "remote.dc",
    supportsStructured: true,
    sections: [
      {
        key: "download",
        label: "[download]",
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
            kind: "ordered-select-list",
            options: priorityOptions,
            help: "下载源优先顺序；勾选来源并调整顺序，写回为列表。"
          }
        ]
      },
      basicFields({ includeAppliedTarget: true, appliedTargetOnlyFor: ["others"] }),
      displayNameSection,
      tipSection
    ]
  },
  required: {
    type: "required",
    label: "Required",
    summary: "必要资源配置\n文件存放于 <DLC>/required 下",
    defaultFileName: "required.dc",
    supportsStructured: true,
    sections: [
      {
        key: "download",
        label: "[download]",
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
            kind: "ordered-select-list",
            options: priorityOptions,
            help: "下载源优先顺序；勾选来源并调整顺序，写回为列表。"
          }
        ]
      },
      requiredBasicSection,
      displayNameSection,
      tipSection
    ]
  },
  required_notice: {
    type: "required_notice",
    label: "Required Notice",
    summary: "必要资源提示配置\n文件通常为 <DLC>/required/notice.dc",
    defaultFileName: "notice.dc",
    supportsStructured: false,
    sections: []
  },
  preset: {
    type: "preset",
    label: "Preset",
    summary: "预设配置\n文件存放于 <DLC>/preset 下",
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
    summary: "公告配置\n文件通常为 <DLC>/notice.dc",
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
            help: "公告显示频率。\nperlaunchmanager：每次进入 DLC 管理器时显示。\npergamesession：每次游戏进程期间只显示一次。\nperlaunchgame：每次启动游戏时显示一次。\nonce：仅显示一次。"
          }
        ]
      },
      {
        ...localeTextSection("Title", "[Title]", "公告标题；手动填写语言代码和对应文本，可继续添加更多语言。")
      },
      {
        key: "Subhead",
        label: "[Subhead]",
        dynamicLocale: {
          valueKind: "text",
          help: "公告副标题；手动填写语言代码和对应文本，可继续添加更多语言。"
        },
        fields: [
          {
            key: "enabled",
            label: "enabled",
            kind: "boolean",
            help: "是否启用副标题。"
          }
        ]
      },
      {
        ...localeTextSection("ContentUrl", "[ContentUrl]", "在线公告内容 URL；优先级高于 Contents，可为不同语言分别填写。")
      },
      {
        ...localeTextareaSection("Contents", "[Contents]", "公告正文；支持 Markdown 与颜色代码，可继续添加更多语言。")
      }
    ]
  },
  redirect: {
    type: "redirect",
    label: "Redirect",
    summary: "链接跳转配置\n文件存放于 <DLC>/remote/redirect 下",
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
            kind: "language-visibility",
            help: "控制可见性；可逐条新增语言代码。留空时所有语言可见，勾选隐藏时所有语言隐藏。"
          }
        ]
      },
      {
        ...localeTextSection("DisplayName", "[DisplayName]", "列表名称；手动填写语言代码和对应文本，可继续添加更多语言。")
      },
      {
        ...localeTextareaSection("notice", "[notice]", "跳转前确认说明；手动填写语言代码和对应文本，可继续添加更多语言。")
      },
      tipSection
    ]
  },
  update: {
    type: "update",
    label: "Update",
    summary: "热更新包配置",
    defaultFileName: "update.dc",
    supportsStructured: false,
    sections: []
  },
  version: {
    type: "version",
    label: "Version",
    summary: "整合包版本配置\n文件通常为 <DLC>/info/version.dc",
    defaultFileName: "version.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "根级字段",
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
  "required_notice",
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
  "required_notice",
  "meta",
  "state",
  "update_skip",
  "config_client",
  "config_server"
]);
