# DC Language Support for JetBrains IDEs

[English](README.md)

在 JetBrains IDE 中编辑 `.dc` 配置文件，获得语法高亮、语法检查、格式化和颜色选择。

使用本插件须遵守随包许可证；针对本插件自身收费须事先取得书面授权。

## 功能

- 高亮分类、字段、字符串、数字和注释。
- 检查通用 DC 语法，不限制应用字段及其取值。
- 使用 **Reformat Code（重新格式化代码）** 整理排版。
- 补全 `&#RRGGBB` 颜色，通过行内色块预览并修改颜色。
- 在 IDE 设置中调整 DC 语法配色。

## 适用版本

适用于 IntelliJ IDEA 2023.3 及以上版本（含 Community），以及兼容的 JetBrains IDE。安装时可在插件页面确认与当前 IDE 的兼容性。

需要 Java 17 或更高版本；通常可直接使用 IDE 自带的 Java，无需单独配置。

## 安装

1. 在 [项目 Releases](https://github.com/CoZooo/DLC-Manager/releases) 中选择 JetBrains 安装包，2.0.0 的文件名为 `jetbrains-dc-plugin-2.0.0.zip`。
2. 打开 IDE 的 **Settings（设置）→ Plugins（插件）**。
3. 在插件设置的齿轮菜单中选择 **Install Plugin from Disk…（从磁盘安装插件…）**。
4. 选择下载的 `.zip`，按提示重启 IDE，然后打开 `.dc` 文件。

## 使用

打开 `.dc` 文件后即可使用高亮和语法检查。将鼠标停在错误标记上查看提示。

需要整理排版时，执行 **Code → Reformat Code**。

输入 `&` 或 `&#` 可选择预设色或 **Pick custom color...**。点击已有 `&#RRGGBB` 旁的色块，可以选择并替换颜色。

## 设置

- 在 **Editor → Color Scheme → DC** 中调整语法颜色。
- 在设置中搜索 **DC inline color**，可管理行内颜色提示。
- 一般无需单独选择 Java。如果已设置 `JAVA_HOME`，请确认它指向 Java 17 或更高版本；修改后重启 IDE。

## 常见问题

**插件显示不兼容**：检查 IDE 版本，并选择与其匹配的插件安装包。

**文件没有被识别为 DC**：确认插件已启用，并在 IDE 的文件类型设置中检查 `.dc` 是否关联到 **DC**。

**有高亮，但补全或诊断不可用**：检查 IDE 通知中的 **DC Language Server** 提示，确认 Java 版本满足要求，再重新打开项目。

**看不到颜色色块**：在设置中检查 **DC inline color** 是否启用，并确认颜色使用完整的 `&#RRGGBB` 格式。

仍有问题时，请在 [Issues](https://github.com/CoZooo/DLC-Manager/issues) 提供插件版本、IDE 名称与版本，以及可复现的配置片段。分享前请移除密码、令牌和其他私人信息。
