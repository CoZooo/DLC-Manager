import { DocumentSchema, DocumentType, ROOT_SECTION_KEY, SectionSchema } from "../models/editor";

const typeOptions = [
  { value: "mod", label: "mod" },
  { value: "resourcepack", label: "resourcepack" },
  { value: "datapack", label: "datapack" },
  { value: "gunpack", label: "gunpack" },
  { value: "dlcpack", label: "dlcpack" },
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
          options: typeOptions,
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
    }
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
      basicFields({ includeAppliedTarget: true, appliedTargetOnlyFor: ["others"] }),
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
  required_notice: {
    type: "required_notice",
    label: "Required Notice",
    summary: "static.summary.requiredNotice",
    defaultFileName: "notice.dc",
    supportsStructured: false,
    sections: []
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
    label: "Notice",
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
            key: "modpack",
            label: "modpack",
            kind: "text",
            help: "static.help.versionModpack"
          },
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
  "required_notice",
  "preset",
  "notice",
  "redirect",
  "update",
  "version",
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
  "pack"
];

export const internalDocumentTypes = new Set<DocumentType>([
  "required_notice"
]);
