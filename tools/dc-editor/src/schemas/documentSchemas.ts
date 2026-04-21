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
      help: "逻辑标识符，用于依赖、冲突、预设等匹配。"
    },
    {
      key: "file_name",
      label: "file_name",
      kind: "text",
      help: "DLC 文件名（包含扩展名）。"
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
          help: "DLC 类型，可选 mod、resourcepack、datapack、gunpack、config、others。"
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
          help: "应用目标路径，填写相对路径。"
        }]
      : []),
    {
      key: "RedirectUrl",
      label: "RedirectUrl",
      kind: "text",
      help: "双击 DLC 名称时打开的链接。"
    },
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
      help: "是否隐藏该项，适合无独立意义的库或前置。",
      toggleLabels: { checked: "隐藏", unchecked: "隐藏" }
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
      help: "前置 identifier 列表。"
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
      help: "逻辑标识符，用于前置匹配。"
    },
    {
      key: "file_name",
      label: "file_name",
      kind: "text",
      help: "DLC 文件名（包含扩展名），用于最终下载文件名。"
    },
    {
      key: "appliedTarget",
      label: "appliedTarget",
      kind: "text",
      help: "应用目标路径，填写相对路径。"
    },
    {
      key: "RedirectUrl",
      label: "RedirectUrl",
      kind: "text",
      help: "双击 DLC 名称时打开的链接。"
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
      help: "是否隐藏该项。",
      toggleLabels: { checked: "隐藏", unchecked: "隐藏" }
    },
    {
      key: "dependencies",
      label: "dependencies",
      kind: "list",
      help: "前置 identifier 列表。"
    }
  ]
};

const displayNameSection: SectionSchema = {
  key: "DisplayName",
  label: "[DisplayName]",
  dynamicLocale: {
    valueKind: "textarea",
    help: "本地化显示名称。"
  },
  fields: []
};

const tipSection: SectionSchema = {
  key: "tip",
  label: "[tip]",
  dynamicLocale: {
    valueKind: "textarea",
    help: "本地化提示文本。"
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
            help: "直链，每行一个 URL。"
          },
          {
            key: "priority",
            label: "priority",
            kind: "ordered-select-list",
            options: priorityOptions,
            help: "调整下载源优先顺序。"
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
            help: "直链，每行一个 URL。"
          },
          {
            key: "priority",
            label: "priority",
            kind: "ordered-select-list",
            options: priorityOptions,
            help: "调整下载源优先顺序。"
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
        key: ROOT_SECTION_KEY,
        label: "根级字段",
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
            help: "与该预设冲突的 DLC identifier 或预设列表。"
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
            help: "是否启用公告功能。",
            toggleLabels: { checked: "启用", unchecked: "启用" }
          },
          //{
          //  key: "neverShow",
          //  label: "neverShow",
          //  kind: "boolean",
          //  help: "是否永不显示该公告。",
          //  toggleLabels: { checked: "永不显示", unchecked: "永不显示" }
          //},
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
        ...localeTextareaSection("Title", "[Title]", "公告标题。")
      },
      {
        key: "Subhead",
        label: "[Subhead]",
        dynamicLocale: {
          valueKind: "textarea",
          help: "公告副标题。"
        },
        fields: [
          {
            key: "enabled",
            label: "enabled",
            kind: "boolean",
            help: "是否启用副标题。",
            toggleLabels: { checked: "启用", unchecked: "启用" }
          }
        ]
      },
      {
        ...localeTextSection("ContentUrl", "[ContentUrl]", "在线公告内容 URL，优先级高于 Contents。")
      },
      {
        ...localeTextareaSection("Contents", "[Contents]", "公告正文，支持 Markdown 与颜色代码。"),
        dynamicLocale: {
          valueKind: "textarea",
          help: "公告正文，支持 Markdown 与颜色代码。",
          toolbarMode: "mc-markdown"
        }
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
            help: "控制可见性。留空时所有语言可见，勾选隐藏时所有语言隐藏。"
          }
        ]
      },
      {
        ...localeTextareaSection("DisplayName", "[DisplayName]", "列表名称。")
      },
      {
        ...localeTextareaSection("notice", "[notice]", "跳转前确认说明。")
      },
      tipSection
    ]
  },
  update: {
    type: "update",
    label: "Update",
    summary: "热更新包配置",
    defaultFileName: "update.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "根级字段",
        fields: [
          {
            key: "isHidden",
            label: "isHidden",
            kind: "boolean",
            help: "是否在更新列表中隐藏该热更新包。",
            toggleLabels: { checked: "隐藏", unchecked: "隐藏" }
          },
          {
            key: "UpdateDLCM",
            label: "UpdateDLCM",
            kind: "text",
            help: "更新 DLC 管理器。"
          }
        ]
      },
      {
        key: "version",
        label: "[version]",
        fields: [
          {
            key: "platform",
            label: "platform",
            kind: "select",
            options: [
              { value: "universal", label: "universal" },
              { value: "client", label: "client" },
              { value: "server", label: "server" }
            ],
            help: "热更新包适用的平台，可选 universal、client、server。"
          },
          {
            key: "base",
            label: "base",
            kind: "text",
            help: "应用该热更新前要求的底包版本。"
          },
          {
            key: "base_new",
            label: "base_new",
            kind: "text",
            help: "应用后新的底包版本，不变时可留空。"
          },
          {
            key: "dlc",
            label: "dlc",
            kind: "text",
            help: "应用该热更新前要求的 DLC 版本。"
          },
          {
            key: "dlc_new",
            label: "dlc_new",
            kind: "text",
            help: "应用后新的 DLC 版本，不变时可留空。"
          }
        ]
      },
      {
        ...localeTextareaSection("changelog", "[changelog]", "更新日志。"),
        dynamicLocale: {
          valueKind: "textarea",
          help: "更新日志。",
          toolbarMode: "mc-markdown"
        }
      },
      {
        key: "file_del",
        label: "[file].del",
        fields: [
          {
            key: "entries",
            label: "entries",
            kind: "update-file-del",
            help: "需删除文件的相对路径。"
          }
        ]
      },
      {
        key: "file_add",
        label: "[file].add",
        fields: [
          {
            key: "entries",
            label: "entries",
            kind: "update-file-add",
            help: "新增文件规则。覆盖模式：\n勾选：覆盖（true），文件夹内替换每一个文件。\n不勾选：不覆盖（false），文件夹内跳过同名文件。\nall：替换整个文件夹，需在原文中手动调整。\nnone：存在同名文件夹时跳过，需在原文中手动调整。"
          }
        ]
      },
      {
        key: "file_move",
        label: "[file].move",
        fields: [
          {
            key: "entries",
            label: "entries",
            kind: "update-file-move",
            help: "移动规则：原路径 => 新路径。"
          }
        ]
      },
      {
        key: "file_modify",
        label: "[file].modify",
        description: "请在原文模式中编辑 [file].modify。",
        fields: []
      }
    ]
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
  "version"
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
  "required_notice"
]);
