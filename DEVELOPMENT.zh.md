# 开发

[English](DEVELOPMENT.md)

## 项目结构

主模组在 `src/main/java` 和 `src/main/kotlin` 中维护对应实现，StoneCutter 按 `versions/` 中的节点生成各版本源码。活动节点使用原始测试源码，其他节点使用生成测试和版本专属测试。

MPS 原生模型只定义通用 `.dc` 语法。DLC Manager 的业务字段维护在 `src/main/resources/META-INF/dlc-manager/dlc-definition.json`；`DlcDcDefinition.register(...)` 通过 DC 分类／字段 API 注册对应类型。编辑器单独注册整份项目定义，与语法元数据分开。主项目、Bootstrap 和外部管理器从 Maven Central 使用 `com.cozooo:dclanguage:0.1.0`。

`settings.gradle.kts` 将 Bootstrap 和外部管理器作为独立构建纳入。DC Editor 是独立的网页与桌面应用，使用自己的文本编辑实现。

## DC 应用定义

DLC 字段变更只修改 `src/main/resources/META-INF/dlc-manager/dlc-definition.json`，不需要重新生成 DC 语法。Java 和 Kotlin 通过 `DlcDcDefinition` 共用该资源；Bootstrap 和外部管理器也打包同一文件。DC Editor 通过通用 TypeScript 分类／字段 API 注册整份定义。VS Code 在 `scripts/prepare-server.js` 中复制它，JetBrains 在 `processResources` 中复制它；两者都通过 `initializationOptions.definition` 传给通用服务。语言库默认 catalog 保持为空。

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
