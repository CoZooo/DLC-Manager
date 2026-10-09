# DC Language

[English](README.md)

DC Language 是用于读取、检查和修改 `.dc` 配置文件的 Java 17 库，Java 和 Kotlin 均可使用。它支持保留注释与原有排版的写回，并为 DLC Manager、编辑器插件和其他工具提供共享配置支持。

## 接入

从 Maven Central 添加依赖：

```kotlin
dependencies {
    implementation("com.cozooo:dclanguage:0.1.0")
}
```

普通使用和构建不需要安装 MPS。

## 读取和修改配置

```java
import com.cozooo.dclanguage.DcDocuments;
import java.nio.file.Path;

var path = Path.of("example.dc");
var document = DcDocuments.read(path);
String identifier = document.getString("basic", "identifier");
document.set("basic", "identifier", "example");
DcDocuments.write(path, document);
```

对应配置：

```dc
[basic]
identifier = "example"
```

默认保存会保留注释、未知字段和未修改内容。`read` 不会自动创建缺失文件；创建空文档使用 `DcDocuments.create()`。需要编辑内存文本时，使用 `DcDocuments.readText(text)` 和 `DcDocuments.writeText(document)`。

可以为应用注册必填字段、类型和取值约束，再调用 `language.validate(document)` 检查配置。完整示例、类型化 API、分类操作和保存行为见 [API 与维护说明](DEVELOPMENT.zh.md)，文件格式见 [语言规范](SPEC.zh.md)。

## 构建和验证

在本目录使用 JDK 17 或更新版本运行：

```sh
./gradlew check
./gradlew jar sourcesJar
```

生成的 JAR 位于 `build/libs/`。

`.dc` 语言规则由 [MPS 工具](mps/README.zh.md)维护。修改规则时先修改 MPS 模型并生成，再验证库及相关编辑器。不要直接修改生成文件。

## 配套工具

- [语言服务](lsp/README.zh.md)：为其他编辑器提供诊断、补全、悬停和格式化。
- [VS Code 插件](../editor/vscode-dc-language/README.zh.md)与 [JetBrains 插件](../editor/jetbrains-dc-plugin/README.zh.md)：在常用编辑器中编辑 `.dc`。
- [macOS 快速预览](../editor/dc-quicklook/README.zh.md)：在 Finder 中预览 `.dc`。

第三方组件声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

本项目采用[非商业许可](LICENSE)，商用或售卖须事先取得书面授权。
