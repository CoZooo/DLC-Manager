# 开发

[English](DEVELOPMENT.md)

## 项目结构

主模组在 `src/main/java` 和 `src/main/kotlin` 中维护对应实现，StoneCutter 按 `versions/` 中的节点生成各版本源码。活动节点使用原始测试源码，其他节点使用生成测试和版本专属测试。

`.dc` 语言由 MPS 原生模型定义，并生成运行库与编辑工具共享的规则。主项目、Bootstrap 和外部管理器从 Maven Central 使用 `com.cozooo:dclanguage:0.1.0`。

`settings.gradle.kts` 将 Bootstrap 和外部管理器作为独立构建纳入。DC Editor 是独立的网页与桌面应用，使用自己的文本编辑实现。

## 构建

使用仓库自带的 Gradle Wrapper，并准备 JDK 17、21、25。游戏节点使用各自配置的 Java 版本，外部管理器使用 Java 25。

```sh
./gradlew build --parallel
```

分发文件输出到 `output/<mod_version>/`，版本在根目录 `gradle.properties` 中设置。

## 检查与测试

仓库检查：

```sh
./gradlew check --parallel
```

分别测试 Kotlin 和 Java 实现：

```sh
./gradlew test --parallel
./gradlew -Penable_kotlin=false test --parallel
```

也可以直接选择特定节点，例如：

```sh
./gradlew :1.20.1-forge:test
```

## 组件说明

- [DC 运行库 API](tools/dclanguage/DEVELOPMENT.zh.md)与[语言规范](tools/dclanguage/SPEC.zh.md)。
- [语言服务](tools/dclanguage/lsp/README.zh.md)与 [MPS](tools/dclanguage/mps/DEVELOPMENT.zh.md)。
- [Bootstrap](tools/dlc-bootstrap/README.zh.md)与 [FileOps Helper](tools/file-ops-helper/README.zh.md)。
- [VS Code](tools/editor/vscode-dc-language/DEVELOPMENT.zh.md)、[JetBrains](tools/editor/jetbrains-dc-plugin/DEVELOPMENT.zh.md)与 [Quick Look](tools/editor/dc-quicklook/DEVELOPMENT.zh.md)。

在 `tools/dc-editor` 中运行 `npm run build` 构建网页应用，或运行 `npm run tauri:build` 构建桌面应用。

## 贡献与 Pull Request

本项目允许使用 AI 辅助开发。每个 PR 的描述请依次包含以下内容：

```markdown
## Summary
简要概括此 PR 的目的。

## New Feature/Issue Solved
说明新增功能或解决的问题，并关联相关 Issue（如有）。

## What Changed
- 列出主要改动。

## Validation
- 写明验证方法与结果；未执行的验证请说明原因。

## Additional Notes (Optional)
```
