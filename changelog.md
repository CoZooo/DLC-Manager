# Changelog

## 5.0.0

### DLC Manager

#### Features

- Added Minecraft 1.18.2 and 1.19.2 support for Forge, and 26.1.x support for Fabric and NeoForge.
- Redesigned the in-game UI with ApricityUI.
- Added early installation of required resources on Forge and NeoForge. Fabric installs required resources after startup.
- Added content-based resource matching with SHA-256 and a `mode` option for choosing hash or filename matching. Resource installation and removal now keep history and backups for recovery.
- Added bidirectional DLC Sync between the in-game manager and servers, supports DLC and enabled-state synchronization.
- DLC Packs now support Modrinth `.mrpack` files.
- Added a choice when an update contains unknown parameters: cancel, ignore the parameters and continue, or update DLC Manager before retrying. The latter will attempt to complete the update during the loading phase of the next game launch.
- Added save backups before hot updates.
- Added `[modpack].autoUpdate` and `forceUpdate`, both disabled by default. Automatic updates will be finished during loading. Client-side forced updates cannot be skipped or ignored, while unavailable update versions can be bypassed.
- Added CNB support for update repositories.
- Added optional `_metadata.dc` files for localized folder names and ordering.
- Added localized `[Alarm]` messages for DLC warning pages. When `alarm = true`, the matching message takes precedence over the tooltip.
- Added `[ui].showDependencyReminder` to control dependency reminders when enabling DLC. It is disabled by default.
- Added a hidden option for the DLC Manager entry-button anchor.
- Added server file search.

#### Fixes

- Moved DLC Pack metadata reading to the background and added a loading message to reduce stalls when previewing large packs.
- Fixed configuration paths containing backslashes to recognize them as path separators.
- Fixed the issue where updating across multiple versions, show only the changelog for the final version.

#### Changed

- Changed `taczRoot` to configure the directory where TACZ mod stores gun packs, relative to the game directory. DLC source packs are identified by `type = "gunpack"`.
- Moved the default locations of `notice.dc` and `version.dc` to `config/dlc_manager/`.
- Remove the separate DLC versions and change the original "base" name to "version".
- ApricityUI is now a required external dependency for in-game interfaces and must be installed separately. MaLiLib/MaFgLib are no longer dependencies.
- Required resources are installed automatically; the previous required-resource management page has been removed.
- ZIP required resources now use an explicit `unzip` option, which defaults to `false` and only applies to `.zip` files. `appliedTarget` accepts directories ending in `/` as well as explicit filenames for renaming; the target filename no longer determines whether an archive is extracted. Existing required-resource configurations that rely on implicit ZIP extraction must explicitly set `unzip = true`.
- DLC management connections now use the Minecraft server's game port, including connections from the External Manager. Removed the separate management port. Report bugs if you encounter any issues. Existing server connection entries must point to the Minecraft game port rather than the former standalone management port.
- Server-side DLC management is always enabled; removed its `serverSupport` setting. The client-side setting remains available.
- Moved `servers.dc` and DLC Pack `order.dc` from the configuration directory to `DLC/tmp`, with migration of existing files.

#### Deprecation

- Removed the legacy `dlc_name` and `dlc_identifier` aliases. Use `file_name` and `identifier` instead.

### DC Editor and Language Integrations

#### Features

- Added a folder-metadata document category and structured editor for `_metadata.dc`.
- Added editing support for resource match modes, required-resource `unzip`, localized `[Alarm]` messages, automatic/forced updates, and dependency reminders.

#### Fixes

- Configuration filenames follow `identifier` while they retain their default or identifier-derived name. Manually named configuration files keep their names when the identifier changes.

#### Changed

- Empty required `identifier` values are now errors rather than warnings.
- Separated generic DC syntax from DLC Manager's application definition. VS Code and IntelliJ integrations remain generic; DLC-specific fields are maintained by the main project and registered through the DC category/field APIs.

### DLC 管理器

#### 新增功能

- 新增 Minecraft 1.18.2、1.19.2 的 Forge 支持，以及 26.1.x 的 Fabric 和 NeoForge 支持。
- 使用 ApricityUI 重新设计游戏内界面。
- Forge 和 NeoForge 支持在早期加载阶段安装必要资源；Fabric 在启动后安装必要资源。
- 新增基于 SHA-256 的资源内容匹配，通过 `mode` 选择哈希匹配或文件名匹配。资源安装与移除操作会保留历史记录和备份，以便恢复。
- 新增游戏内管理器与服务端之间的双向 DLC 同步，支持同步 DLC 和预设的启用状态。
- 拓展包新增 Modrinth `.mrpack` 支持。
- 热更新遇到未知参数时，可选择取消更新、忽略参数并继续，或更新 DLC 管理器后重试。后者将在下一次游戏启动时的加载阶段尝试完成更新。
- 新增热更新前的存档备份功能。
- 新增 `[modpack].autoUpdate` 和 `forceUpdate`，默认均为关闭。自动更新会在加载期间完成。客户端的强制更新不能跳过或忽略，但无法更新的版本可以跳过。
- 热更新仓库新增 CNB 支持。
- 支持通过可选的 `_metadata.dc` 文件设置文件夹的本地化显示名称和排序。
- 新增 `[Alarm]` 段，用于设置 DLC 警告页面的多语言提示。`alarm = true` 时，优先显示对应语言的警告内容，再回退至 Tooltip。
- 新增 `[ui].showDependencyReminder`，用于控制启用 DLC 时是否显示前置提醒，默认关闭。
- DLC 管理器入口按钮的锚点新增隐藏选项。
- 新增服务端文件搜索功能。

#### 修复

- 将 DLC Pack 元数据读取移至后台，并显示读取提示，减少预览大型拓展包时的卡顿。
- 修复配置路径中的反斜杠无法被正确识别为路径分隔符的问题。
- 修复跨多个版本更新后，仅显示最终版本的更新日志的问题。

#### 变更

- 修正 `taczRoot` 的含义：它现在指定 TACZ 模组存放枪包的目标目录，相对游戏根目录解析。DLC 内的源枪包通过 `type = "gunpack"` 指定。
- 将 notice.dc 和 version.dc 的默认位置迁移至 config/dlc_manager/。
- 取消单独的DLC版本，并将原来的“base”名称改为“version”。
- ApricityUI 现在是游戏内界面所需的外部前置，需要单独安装。不再依赖 MaLiLib/MaFgLib。
- 必要资源改为自动安装，移除原先的必要资源管理页面。
- ZIP 必要资源改用显式的 `unzip` 参数控制解压，默认值为 `false`，且仅对 `.zip` 文件生效。`appliedTarget` 支持以 `/` 结尾的目录，也支持指定文件名以重命名；不再通过目标文件名判断是否解压。依赖原有隐式解压行为的配置需要显式设置 `unzip = true`。
- DLC 管理连接改为使用 Minecraft 服务器的游戏端口，外部管理器也使用该端口。移除独立管理端口。如遇问题请反馈。已有服务器连接条目需要改用 Minecraft 游戏端口。
- 服务端 DLC 管理功能强制开启，移除服务端的 `serverSupport` 配置；客户端仍保留该配置。
- 将 `servers.dc` 和 DLC Pack 的 `order.dc` 从配置文件目录迁移至 `DLC/tmp`，并迁移已有文件。

#### 废弃与移除

- 移除旧字段别名 `dlc_name` 和 `dlc_identifier`，请改用 `file_name` 和 `identifier`。

### DC 编辑器与语言集成

#### 新增功能

- 新增文件夹元数据文档分类，以及 `_metadata.dc` 的结构化编辑页面。
- 支持编辑资源匹配模式、必要资源 `unzip`、多语言 `[Alarm]` 提示、自动更新与强制更新，以及前置提醒配置。

#### 修复

- 配置文件名为默认值或由 `identifier` 生成时，会随 `identifier` 修改。手动命名后，修改 `identifier` 不再改变配置文件名。

#### 变更

- 必填的 `identifier` 为空时，校验结果由警告提升为错误。
- 将通用 DC 语法与 DLC 管理器的应用定义分离。VS Code 和 IntelliJ 插件保持通用，DLC 专用字段由主项目维护，并通过 DC 分类与字段 API 注册。



## 5.1.0

### DLC Manager

### DLC External Manager

#### Features

- Added a standalone JavaFX manager for managing local instances without starting Minecraft.
- Supports DLC browsing, search, enable/disable operations, dependencies, conflicts, presets, missing-content downloads, required resources, DLC Packs, updates, announcements, and settings.
- Supports remote server management, including DLC, downloads, presets, packs, updates, files, and RCON tools, with DLC management connections using the Minecraft game port.
- Added English and Chinese interfaces and platform-specific distributions. The standalone JAR requires Java 25.

### DLC管理器

### DLC 外部管理器

#### 新增功能

- 新增独立运行的 JavaFX 管理器，无需启动 Minecraft 即可管理本地实例。
- 支持 DLC 浏览、搜索、启用与禁用、前置依赖、冲突检查、预设、缺失内容下载、必要资源、拓展包、热更新、公告和配置管理。
- 支持远程管理服务器的 DLC、下载、预设、拓展包、热更新、文件管理，DLC 管理连接使用 Minecraft 游戏端口。
- 提供中英文界面和适用于不同平台的发行包。独立 JAR 需要 Java 25。
