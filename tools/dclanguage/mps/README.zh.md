# 用 MPS 定义 DC 语言

[English](README.md)

本工具用于定义和维护 `.dc` 语言的语法、字段及校验规则。权威定义位于 [原生 MPS 模型](solutions/definition/models/dc.mps)，生成结果供 DC Language 库、语言服务和配套编辑器使用。它同时提供可选的 `.dc` 配置结构编辑器。

## 准备环境

构建需要 JDK 21 和 MPS 2026.1 SDK。将 `MPS_HOME` 指向包含 `lib/mps-openapi.jar` 的目录；macOS 的 MPS 应用使用 `MPS.app/Contents`。也可通过 `-PmpsHome=/path/to/MPS` 指定。

运行 DLC Manager 或调用 DC Language 库不需要安装 MPS。

## 构建插件

在本目录运行：

```sh
../gradlew check pluginZip
```

安装包位于 `build/distributions/dclanguage-mps-0.1.0.zip`。在 MPS 的 **Settings → Plugins → Install Plugin from Disk** 选择此 ZIP，按提示安装并重启。

## 修改语言定义

1. 安装插件后，打开仓库中的 `definition-editor` 项目。
2. 在 `com.cozooo.dclanguage.definitions.dc` 模型中打开 `dc` 根节点。
3. 修改语法标点、字符串、数值规则，或配置类型中的分类、字段和约束。
4. 在本目录生成并验证：

```sh
../gradlew generateLanguage verifyGeneratedLanguage
../gradlew -p .. check
../gradlew -p ../lsp check shadowJar serverDistZip
```

生成结果保存在上级模块的 `src/generated/`，与模型一起提交。多个编辑器共享的字段与规则应维护在此模型中；不要直接修改生成文件，也不要为每个编辑器分别维护语法。

生成器实现、定义概念及 SDK 验证范围见 [维护说明](DEVELOPMENT.zh.md)。

## 编辑配置文件

插件还可以按分类、列表和字段编辑 `.dc` 文件，并在保存时保留注释、未知字段和未修改内容。

示例项目位于[仓库的 `example` 目录](example)，插件 ZIP 不包含示例。获取仓库后，在 MPS 中打开此项目即可使用；也可先查看[示例配置](solutions/sandbox/models/example.dc)。编辑期间发生外部文件修改或配置错误时，先处理问题再保存。

根据[项目许可](../LICENSE)，针对本插件自身收费须事先取得书面授权。
