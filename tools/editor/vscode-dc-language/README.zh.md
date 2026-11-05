# DC Language Tools for VS Code

[English](https://github.com/CoZooo/DLC-Manager/blob/main/tools/editor/vscode-dc-language/README.md)

在 VS Code 中编辑 `.dc` 配置文件，获得语法高亮、语法检查、格式化和颜色选择。

使用、修改和再分发须遵守随包提供的 PolyForm Shield License 1.0.0。许可允许在范围内进行使用，但禁止提供竞争产品或服务。

## 功能

- 为分类、字段、字符串、数字、注释等内容着色。
- 检查通用 DC 语法，不限制应用字段及其取值。
- 使用 **Format Document（格式化文档）** 整理排版。
- 预览 `&#RRGGBB` 颜色，选择预设色或自定义颜色。
- 提供常用 `.dc` 片段与括号、引号配对。

## 适用版本

- VS Code 1.90 或更高版本。
- Java 17 或更高版本。

## 安装

1. 在 [项目 Releases](https://github.com/CoZooo/DLC-Manager/releases) 中选择 VS Code 安装包，2.0.0 的文件名为 `vscode-dc-language-2.0.0.vsix`。
2. 打开 VS Code 的扩展视图，在右上角菜单中选择 **Install from VSIX…（从 VSIX 安装…）**。
3. 选择下载的 `.vsix` 文件，并按提示重新加载窗口。
4. 打开一个 `.dc` 文件，确认右下角语言模式为 **DC**。

## 使用

直接打开现有 `.dc` 文件即可。编辑时查看补全建议；将鼠标停在标记处查看错误或警告，在“问题”面板中查看完整列表。

需要调整排版时，从命令面板执行 **Format Document**。

在需要颜色的位置输入 `&` 或 `&#`，从补全列表中选择预设色或 **Pick custom color...**。也可以通过命令面板执行 **DC: Pick Hex Color**；已有的 `&#RRGGBB` 颜色可通过编辑器的颜色控件调整。

## 设置

在 VS Code 设置中搜索 `dc`：

| 设置 | 用途 |
| --- | --- |
| `dc.javaPath` | 指定 Java 可执行文件。留空时自动查找已安装的 Java。 |

修改此设置后，请重新加载 VS Code 窗口。

## 常见问题

**文件没有高亮**：确认扩展已启用、文件扩展名为 `.dc`，并将右下角语言模式切换为 **DC**。

**有高亮，但没有补全或错误提示**：确认安装了 Java 17 或更高版本；必要时填写 `dc.javaPath`，然后重新加载窗口。在“输出”面板选择 **DC Language Server** 查看具体提示。

**颜色选择未弹出**：先打开 `.dc` 文件，再运行 **DC: Pick Hex Color**。如果编辑器显示颜色输入框，可填写 `RRGGBB`、`#RRGGBB` 或 `&#RRGGBB`。

仍有问题时，请在 [Issues](https://github.com/CoZooo/DLC-Manager/issues) 提供扩展版本、VS Code 版本和可复现的配置片段。分享前请移除密码、令牌和其他私人信息。
