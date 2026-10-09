[English](README.md)

# DLC Manager

在 Minecraft 游戏内或独立窗口中管理整合包的可选内容：选择想玩的模组、资源包、数据包和 TACZ 枪包，应用预设，下载缺失内容，并获取整合包更新。

## 可以做什么

- 按分类、分组和搜索结果查看 DLC，单独或批量启用、停用。
- 提示缺少的前置内容、内容冲突和需要确认的操作。
- 使用整合包作者提供的预设，快速切换内容组合。
- 下载可选内容与必要资源，导入、启用和管理拓展包。
- 查看公告、内容说明、项目链接和更新日志。
- 通过游戏内入口或外部管理器管理已配置的服务器。
- 用配套编辑器编写 `.dc` 配置，在 VS Code、JetBrains IDE 中获得编辑提示，在 macOS Finder 中直接预览。

实际显示的入口和可用内容由整合包配置决定。涉及模组启停时，请按提示重启游戏；资源包等内容按提示重新加载。

## 支持的游戏版本

| Minecraft | 加载器 |
| --- | --- |
| 1.20.1 | Fabric、Forge |
| 1.21.1 | Fabric、NeoForge |

请选择同时匹配游戏版本和加载器的文件。

## 安装与开始使用

1. 从项目的 [Releases](https://github.com/CoZooo/DLC-Manager/releases) 获取适合当前实例的 DLC Manager 模组文件，并安装对应前置：Architectury API、客户端的 ApricityUI，以及 Fabric 版的 Fabric API。默认发行版还需要对应加载器的 Kotlin 支持，具体见下载说明。
2. 将模组文件放入该 Minecraft 实例的 `mods` 文件夹，启动游戏。
3. 在主菜单打开 **DLC 管理器**，浏览整合包已经提供的内容。
4. 选择要启用或停用的项目，确认前置或冲突提示，然后点击 **应用**。
5. 按界面提示重启或重新加载资源。

如果使用的是已经配好 DLC Manager 的整合包，可以直接从第 3 步开始。游戏中也可以通过 `/dlcmanager` 打开管理器。TACZ 枪包管理需要安装 TACZ。

## 添加自己的内容

在实例目录创建 `DLC` 文件夹，将可选模组文件放入其中，再打开管理器查看。这里的“实例目录”是包含 `mods`、`config` 等文件夹的游戏目录。

整合包作者可以用 `.dc` 配置补充内容说明、下载来源、预设和公告。推荐使用 [DC Editor](tools/dc-editor/README.zh.md) 创建配置，或用下面的编辑器插件编辑已有文件。

## 配套工具

- [DLC 外部管理器](tools/dlc-javafx-manager/README.zh.md)：在独立窗口中管理本地实例或服务器。
- [DC Editor](tools/dc-editor/README.zh.md)：通过表单和文本编辑 `.dc`，预览公告并导出配置。
- [VS Code 插件](tools/editor/vscode-dc-language/README.zh.md)：高亮、补全、错误提示、格式化和颜色选择。
- [JetBrains 插件](tools/editor/jetbrains-dc-plugin/README.zh.md)：在支持的 JetBrains IDE 中编辑 `.dc`。
- [DC Quick Look](tools/editor/dc-quicklook/README.zh.md)：在 macOS Finder 中选中文件后按空格预览。

各工具的安装说明以对应页面为准；可下载文件以 Releases 中实际提供的附件为准。

## 常见问题

**列表为空。** 确认当前启动的是正确的实例，并检查实例的 `DLC` 文件夹是否有内容。如果整合包修改了 DLC 路径，以它的设置为准。

**内容无法启用。** 先按提示补全前置、处理冲突，或检查当前预设是否限制了选择。

**应用后没有变化。** 查看是否还有重启或资源重载提示。模组变更通常需要重启游戏。

**找不到预设、公告或下载入口。** 这些功能需要整合包作者提供相应内容，也可能在整合包设置中关闭。

**不想启动游戏也要管理内容。** 使用 [DLC 外部管理器](tools/dlc-javafx-manager/README.zh.md)，选择对应实例目录。

## 反馈

遇到问题可以提交 [Issue](https://github.com/CoZooo/DLC-Manager/issues)，附上 DLC Manager 版本、Minecraft 版本、加载器、操作步骤，以及相关截图或日志。发送日志和配置前，请移除密码与令牌。

## 许可

对本模组本身收费须事先获得书面授权；再分发须保留许可并提供对应源码。详见[许可说明](LICENSING.zh.md)。独立提供的外部内容适用其各自条款。
