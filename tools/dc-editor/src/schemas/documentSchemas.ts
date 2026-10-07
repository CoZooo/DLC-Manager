import { DocumentSchema, DocumentType, ROOT_SECTION_KEY, SectionSchema } from "../models/editor";

const typeOptions = [
  { value: "mod", label: "mod" },
  { value: "resourcepack", label: "resourcepack" },
  { value: "datapack", label: "datapack" },
  { value: "gunpack", label: "gunpack" },
  { value: "config", label: "config" },
  { value: "others", label: "others" }
];

const remoteTypeOptions = [
  ...typeOptions,
  { value: "dlcpack", label: "dlcpack" }
];

const priorityOptions = [
  { value: "modrinth", label: "modrinth" },
  { value: "curseforge", label: "curseforge" },
  { value: "directUrl", label: "directUrl" }
];

const downloadMirrorModeOptions = [
  { value: "smart_precheck", label: "smart_precheck" },
  { value: "smart_official_first", label: "smart_official_first" },
  { value: "smart_mirror_first", label: "smart_mirror_first" },
  { value: "official_only", label: "official_only" },
  { value: "mirror_only", label: "mirror_only" },
  { value: "official_first", label: "official_first" },
  { value: "mirror_first", label: "mirror_first" }
];

const downloadMirrorProbeStrategyOptions = [
  { value: "single_best", label: "single_best" },
  { value: "best_of_three", label: "best_of_three" }
];

const basicFields = (options?: { includeType?: boolean; includeDlcPackType?: boolean; includeAppliedTarget?: boolean; appliedTargetOnlyFor?: string[] }): SectionSchema => ({
  key: "basic",
  label: "[basic]",
  fields: [
    {
      key: "identifier",
      label: "identifier",
      kind: "text",
      help: "static.help.identifier"
    },
    {
      key: "file_name",
      label: "file_name",
      kind: "text",
      help: "static.help.fileName"
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
          options: options?.includeDlcPackType ? remoteTypeOptions : typeOptions,
          help: "static.help.dlcType"
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
          help: "static.help.appliedTarget"
        }]
      : []),
    {
      key: "RedirectUrl",
      label: "RedirectUrl",
      kind: "text",
      help: "static.help.redirectUrl"
    },
    {
      key: "isInList",
      label: "isInList",
      kind: "boolean",
      help: "static.help.isInDlcList"
    },
    {
      key: "isHidden",
      label: "isHidden",
      kind: "boolean",
      help: "static.help.isHiddenLibrary",
      toggleLabels: { checked: "static.label.hidden", unchecked: "static.label.hidden" }
    },
    {
      key: "AllowEnableByAll",
      label: "AllowEnableByAll",
      kind: "boolean",
      help: "static.help.allowEnableByAll"
    },
    {
      key: "alarm",
      label: "alarm",
      kind: "boolean",
      help: "static.help.alarm"
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
      help: "static.help.dependencyIdentifier"
    },
    {
      key: "file_name",
      label: "file_name",
      kind: "text",
      help: "static.help.downloadFileName"
    },
    {
      key: "appliedTarget",
      label: "appliedTarget",
      kind: "text",
      help: "static.help.appliedTarget"
    },
    {
      key: "RedirectUrl",
      label: "RedirectUrl",
      kind: "text",
      help: "static.help.redirectUrl"
    },
    {
      key: "isInList",
      label: "isInList",
      kind: "boolean",
      help: "static.help.isInRequiredList"
    },
    {
      key: "isHidden",
      label: "isHidden",
      kind: "boolean",
      help: "static.help.isHidden",
      toggleLabels: { checked: "static.label.hidden", unchecked: "static.label.hidden" }
    },
  ]
};

const dependenciesSection: SectionSchema = {
  key: "dependencies",
  label: "[dependencies]",
  fields: [
    {
      key: "entries",
      label: "dependencies",
      kind: "list",
      help: "static.help.dependencies"
    }
  ]
};

const conflictsSection: SectionSchema = {
  key: "conflicts",
  label: "[conflicts]",
  fields: [
    {
      key: "entries",
      label: "conflicts",
      kind: "list",
      help: "static.help.conflicts"
    }
  ]
};

const packBasicSection: SectionSchema = {
  key: "basic",
  label: "[basic]",
  fields: [
    {
      key: "identifier",
      label: "identifier",
      kind: "text",
      help: "static.help.packIdentifier"
    },
    {
      key: "version",
      label: "version",
      kind: "text",
      help: "static.help.packVersion"
    },
    {
      key: "platform",
      label: "platform",
      kind: "select",
      options: [
        { value: "universal", label: "universal" },
        { value: "client", label: "client" },
        { value: "server", label: "server" }
      ],
      help: "static.help.packPlatform"
    },
    {
      key: "gameVersion",
      label: "gameVersion",
      kind: "text",
      help: "static.help.packGameVersion"
    },
    {
      key: "modLoader",
      label: "modLoader",
      kind: "text",
      help: "static.help.packModLoader"
    },
    {
      key: "author",
      label: "author",
      kind: "list",
      help: "static.help.packAuthor"
    }
  ]
};

const packModpackSection: SectionSchema = {
  key: "modpack",
  label: "[modpack]",
  fields: [
    {
      key: "modpack",
      label: "modpack",
      kind: "list",
      help: "static.help.packModpack"
    },
    {
      key: "base",
      label: "base",
      kind: "list",
      help: "static.help.packBase"
    },
    {
      key: "dlc",
      label: "dlc",
      kind: "list",
      help: "static.help.packDlc"
    }
  ]
};

const packLinksSection: SectionSchema = {
  key: "links",
  label: "[links]",
  fields: [
    {
      key: "homepage",
      label: "homepage",
      kind: "text",
      help: "static.help.packHomepage"
    },
    {
      key: "issue",
      label: "issue",
      kind: "text",
      help: "static.help.packIssue"
    },
    {
      key: "sponsor",
      label: "sponsor",
      kind: "text",
      help: "static.help.packSponsor"
    },
    {
      key: "__localizedLinks",
      label: "localized links",
      kind: "localized-link-group",
      help: "static.help.packLocalizedLinks"
    }
  ]
};

const configPathsSection: SectionSchema = {
  key: "paths",
  label: "[paths]",
  fields: [
    { key: "dlcRoot", label: "dlcRoot", kind: "text", help: "static.help.configDlcRoot" },
    { key: "taczRoot", label: "taczRoot", kind: "text", help: "static.help.configTaczRoot" },
    { key: "resourcepackRoot", label: "resourcepackRoot", kind: "text", help: "static.help.configResourcepackRoot" },
    { key: "datapackRoot", label: "datapackRoot", kind: "text", help: "static.help.configDatapackRoot" }
  ]
};

const configUiSection: SectionSchema = {
  key: "ui",
  label: "[ui]",
  fields: [
    { key: "isEnableAllButtonShown", label: "isEnableAllButtonShown", kind: "boolean", help: "static.help.configEnableAllButton" },
    { key: "isConfigButtonShown", label: "isConfigButtonShown", kind: "boolean", help: "static.help.configConfigButton" },
    { key: "isUButtomShown", label: "isUButtomShown", kind: "boolean", help: "static.help.configUButton" },
    { key: "isUpdateManagerOn", label: "isUpdateManagerOn", kind: "boolean", help: "static.help.configUpdateManager" },
    { key: "isSelfUpdateAutoCheckOn", label: "isSelfUpdateAutoCheckOn", kind: "boolean", help: "static.help.configSelfUpdateAutoCheck" },
    { key: "isPresetFuncOn", label: "isPresetFuncOn", kind: "boolean", help: "static.help.configPresetFunc" },
    { key: "isPackFuncOn", label: "isPackFuncOn", kind: "boolean", help: "static.help.configPackFunc" },
    { key: "isPButtomShown", label: "isPButtomShown", kind: "boolean", help: "static.help.configPButton" },
    { key: "ShowOnFirstRun", label: "ShowOnFirstRun", kind: "boolean", help: "static.help.configShowOnFirstRun" },
    { key: "WaitingTime", label: "WaitingTime", kind: "number", help: "static.help.configWaitingTime" },
    { key: "serverSupport", label: "serverSupport", kind: "boolean", help: "static.help.configServerSupport" },
    { key: "apiEnabled", label: "apiEnabled", kind: "boolean", help: "static.help.configApiEnabled" },
    { key: "apiPort", label: "apiPort", kind: "number", help: "static.help.configApiPort" },
    { key: "apiToken", label: "apiToken", kind: "text", help: "static.help.configApiToken" },
    { key: "noticeDisplayMode", label: "noticeDisplayMode", kind: "text", help: "static.help.configNoticeDisplayMode" },
    { key: "noticeButtonAnchor", label: "noticeButtonAnchor", kind: "text", help: "static.help.configNoticeButtonAnchor" },
    { key: "noticeButtonOffsetX", label: "noticeButtonOffsetX", kind: "number", help: "static.help.configNoticeButtonOffset" },
    { key: "noticeButtonOffsetY", label: "noticeButtonOffsetY", kind: "number", help: "static.help.configNoticeButtonOffset" },
    { key: "noticePanelAnchor", label: "noticePanelAnchor", kind: "text", help: "static.help.configNoticePanelAnchor" },
    { key: "noticePanelOffsetX", label: "noticePanelOffsetX", kind: "number", help: "static.help.configNoticePanelOffset" },
    { key: "noticePanelOffsetY", label: "noticePanelOffsetY", kind: "number", help: "static.help.configNoticePanelOffset" },
    { key: "noticePanelWidth", label: "noticePanelWidth", kind: "number", help: "static.help.configNoticePanelSize" },
    { key: "noticePanelHeight", label: "noticePanelHeight", kind: "number", help: "static.help.configNoticePanelSize" },
    { key: "titleAnchor", label: "titleAnchor", kind: "text", help: "static.help.configTitleAnchor" },
    { key: "titleOffsetX", label: "titleOffsetX", kind: "number", help: "static.help.configTitleOffset" },
    { key: "titleOffsetY", label: "titleOffsetY", kind: "number", help: "static.help.configTitleOffset" },
    { key: "titleUpdateAnchor", label: "titleUpdateAnchor", kind: "text", help: "static.help.configTitleUpdateAnchor" },
    { key: "titleUpdateOffsetX", label: "titleUpdateOffsetX", kind: "number", help: "static.help.configTitleUpdateOffset" },
    { key: "titleUpdateOffsetY", label: "titleUpdateOffsetY", kind: "number", help: "static.help.configTitleUpdateOffset" }
  ]
};

const configModpackSection: SectionSchema = {
  key: "modpack",
  label: "[modpack]",
  fields: [
    { key: "modpack", label: "modpack", kind: "text", help: "static.help.configModpack" },
    { key: "modpackUrl", label: "modpackUrl", kind: "text", help: "static.help.configModpackUrl" },
    { key: "updateGitRepo", label: "updateGitRepo", kind: "text", help: "static.help.configUpdateGitRepo" }
  ]
};

const configDownloadsSection: SectionSchema = {
  key: "downloads",
  label: "[downloads]",
  fields: [
    { key: "maxConcurrentDownloads", label: "maxConcurrentDownloads", kind: "number", help: "static.help.configMaxConcurrentDownloads" },
    { key: "maxSegmentsPerFile", label: "maxSegmentsPerFile", kind: "number", help: "static.help.configMaxSegmentsPerFile" },
    { key: "minSegmentMB", label: "minSegmentMB", kind: "number", help: "static.help.configMinSegmentMB" },
    { key: "maxRetryCount", label: "maxRetryCount", kind: "number", help: "static.help.configMaxRetryCount" },
    { key: "connectTimeoutSeconds", label: "connectTimeoutSeconds", kind: "number", help: "static.help.configConnectTimeoutSeconds" },
    { key: "taskTimeoutSeconds", label: "taskTimeoutSeconds", kind: "number", help: "static.help.configTaskTimeoutSeconds" },
    { key: "downloadMirrorMode", label: "downloadMirrorMode", kind: "select", options: downloadMirrorModeOptions, help: "static.help.configDownloadMirrorMode" },
    { key: "downloadMirrorProbeCount", label: "downloadMirrorProbeCount", kind: "number", help: "static.help.configDownloadMirrorProbeCount" },
    { key: "downloadMirrorProbeStrategy", label: "downloadMirrorProbeStrategy", kind: "select", options: downloadMirrorProbeStrategyOptions, help: "static.help.configDownloadMirrorProbeStrategy" }
  ]
};

const displayNameSection: SectionSchema = {
  key: "DisplayName",
  label: "[DisplayName]",
  dynamicLocale: {
    valueKind: "textarea",
    help: "static.help.displayName"
  },
  fields: []
};

const tipSection: SectionSchema = {
  key: "tip",
  label: "[tip]",
  dynamicLocale: {
    valueKind: "textarea",
    help: "static.help.tip"
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
    summary: "static.summary.info",
    defaultFileName: "info.dc",
    supportsStructured: true,
    sections: [
      basicFields({ includeAppliedTarget: true, appliedTargetOnlyFor: ["others", "config"] }),
      dependenciesSection,
      conflictsSection,
      displayNameSection,
      tipSection
    ]
  },
  remote: {
    type: "remote",
    label: "Remote",
    summary: "static.summary.remote",
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
            help: "static.help.modrinthProject"
          },
          {
            key: "modrinth_versionid",
            label: "modrinth_versionid",
            kind: "text",
            help: "static.help.modrinthVersion"
          },
          {
            key: "curseforge_projectid",
            label: "curseforge_projectid",
            kind: "text",
            help: "static.help.curseforgeProject"
          },
          {
            key: "curseforge_fileid",
            label: "curseforge_fileid",
            kind: "text",
            help: "static.help.curseforgeFile"
          },
          {
            key: "directUrl",
            label: "directUrl",
            kind: "list",
            help: "static.help.directUrl"
          },
          {
            key: "priority",
            label: "priority",
            kind: "ordered-select-list",
            options: priorityOptions,
            help: "static.help.priority"
          }
        ]
      },
      basicFields({ includeDlcPackType: true, includeAppliedTarget: true, appliedTargetOnlyFor: ["others"] }),
      dependenciesSection,
      conflictsSection,
      displayNameSection,
      tipSection
    ]
  },
  required: {
    type: "required",
    label: "Required",
    summary: "static.summary.required",
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
            help: "static.help.modrinthProject"
          },
          {
            key: "modrinth_versionid",
            label: "modrinth_versionid",
            kind: "text",
            help: "static.help.modrinthVersion"
          },
          {
            key: "curseforge_projectid",
            label: "curseforge_projectid",
            kind: "text",
            help: "static.help.curseforgeProject"
          },
          {
            key: "curseforge_fileid",
            label: "curseforge_fileid",
            kind: "text",
            help: "static.help.curseforgeFile"
          },
          {
            key: "directUrl",
            label: "directUrl",
            kind: "list",
            help: "static.help.directUrl"
          },
          {
            key: "priority",
            label: "priority",
            kind: "ordered-select-list",
            options: priorityOptions,
            help: "static.help.priority"
          }
        ]
      },
      requiredBasicSection,
      dependenciesSection,
      displayNameSection,
      tipSection
    ]
  },
  preset: {
    type: "preset",
    label: "Preset",
    summary: "static.summary.preset",
    defaultFileName: "preset.dc",
    supportsStructured: true,
    sections: [
      {
        key: "basic",
        label: "[basic]",
        fields: [
          {
            key: "identifier",
            label: "identifier",
            kind: "text",
            help: "static.help.presetIdentifier"
          },
          {
            key: "alarm",
            label: "alarm",
            kind: "boolean",
            help: "static.help.presetAlarm"
          }
        ]
      },
      dependenciesSection,
      conflictsSection,
      displayNameSection,
      tipSection
    ]
  },
  notice: {
    type: "notice",
    label: "Information",
    summary: "static.summary.notice",
    defaultFileName: "notice.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "static.label.rootFields",
        fields: [
          {
            key: "isNoticeEnabled",
            label: "isNoticeEnabled",
            kind: "boolean",
            help: "static.help.noticeEnabled",
            toggleLabels: { checked: "static.label.enabled", unchecked: "static.label.enabled" }
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
            help: "static.help.noticeFrequency"
          }
        ]
      },
      {
        ...localeTextareaSection("Title", "[Title]", "static.help.noticeTitle")
      },
      {
        key: "Subhead",
        label: "[Subhead]",
        dynamicLocale: {
          valueKind: "textarea",
          help: "static.help.noticeSubhead"
        },
        fields: [
          {
            key: "enabled",
            label: "enabled",
            kind: "boolean",
            help: "static.help.noticeSubheadEnabled",
            toggleLabels: { checked: "static.label.enabled", unchecked: "static.label.enabled" }
          }
        ]
      },
      {
        ...localeTextSection("ContentUrl", "[ContentUrl]", "static.help.noticeContentUrl")
      },
      {
        ...localeTextareaSection("Contents", "[Contents]", "static.help.noticeContents"),
        dynamicLocale: {
          valueKind: "textarea",
          help: "static.help.noticeContents",
          toolbarMode: "mc-markdown"
        }
      }
    ]
  },
  redirect: {
    type: "redirect",
    label: "Redirect",
    summary: "static.summary.redirect",
    defaultFileName: "redirect.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "static.label.rootFields",
        fields: [
          {
            key: "RedirectUrl",
            label: "RedirectUrl",
            kind: "text",
            help: "static.help.redirectTarget"
          },
          {
            key: "available",
            label: "available",
            kind: "language-visibility",
            help: "static.help.visibility"
          }
        ]
      },
      {
        ...localeTextareaSection("DisplayName", "[DisplayName]", "static.help.listName")
      },
      {
        ...localeTextareaSection("notice", "[notice]", "static.help.redirectNotice")
      },
      tipSection
    ]
  },
  update: {
    type: "update",
    label: "Update",
    summary: "static.summary.update",
    defaultFileName: "update.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "static.label.rootFields",
        fields: [
          {
            key: "isHidden",
            label: "isHidden",
            kind: "boolean",
            help: "static.help.updateHidden",
            toggleLabels: { checked: "static.label.hidden", unchecked: "static.label.hidden" }
          },
          {
            key: "UpdateDLCM",
            label: "UpdateDLCM",
            kind: "text",
            help: "static.help.updateDlcm"
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
            help: "static.help.updatePlatform"
          },
          {
            key: "base",
            label: "base",
            kind: "text",
            help: "static.help.updateBase"
          },
          {
            key: "base_new",
            label: "base_new",
            kind: "text",
            help: "static.help.updateBaseNew"
          },
          {
            key: "dlc",
            label: "dlc",
            kind: "text",
            help: "static.help.updateDlc"
          },
          {
            key: "dlc_new",
            label: "dlc_new",
            kind: "text",
            help: "static.help.updateDlcNew"
          }
        ]
      },
      {
        ...localeTextareaSection("changelog", "[changelog]", "static.help.changelog"),
        dynamicLocale: {
          valueKind: "textarea",
          help: "static.help.changelog",
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
            help: "static.help.updateDeletePath"
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
            help: "static.help.updateAddRule"
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
            help: "static.help.updateMoveRule"
          }
        ]
      },
      {
        key: "file_modify",
        label: "[file].modify",
        description: "static.help.updateModifyRaw",
        fields: []
      }
    ]
  },
  version: {
    type: "version",
    label: "Version",
    summary: "static.summary.version",
    defaultFileName: "version.dc",
    supportsStructured: true,
    sections: [
      {
        key: ROOT_SECTION_KEY,
        label: "static.label.rootFields",
        fields: [
          {
            key: "base",
            label: "base",
            kind: "text",
            help: "static.help.versionBase"
          },
          {
            key: "dlc",
            label: "dlc",
            kind: "text",
            help: "static.help.versionDlc"
          }
        ]
      }
    ]
  },
  config: {
    type: "config",
    label: "Config",
    summary: "static.summary.config",
    defaultFileName: "config.dc",
    supportsStructured: true,
    sections: [
      configPathsSection,
      configUiSection,
      configModpackSection,
      configDownloadsSection
    ]
  },
  pack: {
    type: "pack",
    label: "DLC Pack",
    summary: "static.summary.pack",
    defaultFileName: "pack.dc",
    supportsStructured: true,
    sections: [
      packBasicSection,
      packModpackSection,
      packLinksSection,
      dependenciesSection,
      conflictsSection,
      displayNameSection,
      {
        ...localeTextareaSection("description", "[description]", "static.help.packDescription"),
        dynamicLocale: {
          valueKind: "textarea",
          help: "static.help.packDescription",
          toolbarMode: "mc-markdown"
        }
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
  "config",
  "pack"
];

export const configurableDocumentTypes: DocumentType[] = [
  "info",
  "remote",
  "required",
  "preset",
  "notice",
  "redirect",
  "update",
  "version",
  "config",
  "pack"
];
