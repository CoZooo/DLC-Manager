# DC Quick Look

[English](README.md)

在 macOS Finder 中选中 `.dc` 文件，按空格即可预览带语法高亮的内容。

使用、修改和再分发须遵守随发布提供的 PolyForm Shield License 1.0.0。许可允许在范围内进行使用，但禁止提供竞争产品或服务。

## 功能

- 高亮分类、字段、字符串、数字、布尔值和注释。
- 预览嵌套配置、数组、多行文本等 `.dc` 内容。
- 支持浅色和深色外观，可以选择和复制预览中的文本。
- 保留原文件内容，继续使用你已有的默认编辑器。

## 适用版本

支持 macOS 13 或更高版本，适用于 Apple Silicon 和 Intel Mac。

## 安装

1. 从 [项目 Releases](https://github.com/CoZooo/DLC-Manager/releases) 下载 `DCQuickLookHost-2.0.0-distribution.zip`。
2. 解压套件，再打开其中的 `DCQuickLookHost-2.0.0-macos-notarized.zip`，将解压得到的 `DCQuickLookHost.app` 拖入“应用程序”文件夹。
3. 打开该应用一次。应用完成设置后会自动退出，这是正常行为。
4. 在 Finder 中选中一个 `.dc` 文件，按空格查看预览。

如果预览未启用，请在“系统设置”中搜索 Quick Look／快速查看扩展，并启用 DC Quick Look。

## 使用

在 Finder 中选中文件后按空格打开预览，再按空格或 Esc 关闭。需要编辑时，使用平常的编辑器打开文件即可。

超过 2 MiB 的文件只预览开头部分，并显示提示；使用编辑器可查看完整文件。

## 常见问题

**打开应用后没有窗口**：这是正常行为。应用用于启用预览功能，日常使用从 Finder 中按空格开始。

**预览没有生效**：确认应用已放入“应用程序”并至少打开过一次，再检查系统设置中的 Quick Look 扩展是否启用。关闭当前预览窗口后重新打开；仍未生效时，可重新登录 macOS 后再试。

**提示无法读取文件**：确认文件可以在编辑器中正常打开；云盘文件请先下载到本机。

**只显示了部分内容**：文件超过预览大小限制，请在编辑器中查看完整内容。

卸载时，先在系统设置中停用扩展，再将应用移到废纸篓。

仍有问题时，请在 [Issues](https://github.com/CoZooo/DLC-Manager/issues) 提供应用版本、macOS 版本和可复现的文件片段。分享前请移除密码、令牌和其他私人信息。
