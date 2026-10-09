# DC Language 开发与维护

[English](DEVELOPMENT.md)

## 第三方许可分发

Gson 的版本、版权和许可来源记录在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，原文保存在 `licenses/gson/`。这些文件随运行库、源码和 Javadoc JAR 一起进入 `META-INF/dclanguage/`；源码包另含本维护说明、README 和语言规范的中英文版本。LSP 和 MPS 分发包从此目录携带同一份许可资料。更新 Gson 时同时核对实际解析版本、上游版权和发行包内的文本，不沿用 Bootstrap 专用的重定位说明。

由 MPS 维护语言定义的 `.dc` 工具链。包名 `com.cozooo.dclanguage`，运行库坐标为 `com.cozooo:dclanguage:0.1.0`。主项目直接从 Maven Central 解析这个坐标。

库提供文档读取、修改、写回及可选的分类／字段校验。它有自己的 Gradle 工程和 Wrapper，运行需要 Java 17，运行时依赖 Gson。Java 与 Kotlin 使用相同 API；Kotlin 标准库仅用于互操作测试，不是本库的运行依赖。

语言本身的权威来源是 [MPS Definition 模型](mps/solutions/definition/models/dc.mps)。它生成 Java 语法表、解析／写回入口及共享 schema 元数据；通用解析和格式保留算法在引擎中实现，并由生成规则驱动。MPS 原生 TextGen 和独立 headless 生成命令均能输出这些产物，不是把手写解析器整段存进模型。

生成的规则同时用于运行库和 [LSP 语言服务](lsp/README.zh.md)。[VS Code 客户端](../editor/vscode-dc-language/README.zh.md) 与 [IntelliJ 客户端](../editor/jetbrains-dc-plugin/README.zh.md) 共享诊断、补全、悬停和格式化；高亮、自动配对等显示规则也从同份元数据派生。修改语言定义需要 MPS，调用运行库不需要。

## 修改语言与生成

1. 在 MPS 打开 `mps/definition-editor`，编辑原生 `Definition` 模型中的 token、字符串、策略、分类、字段或约束。
2. 设置 `MPS_HOME` 指向 MPS 2026.1 SDK，在本目录执行：

```sh
./gradlew -p mps generateLanguage verifyGeneratedLanguage
./gradlew test
./gradlew -p lsp test shadowJar serverDistZip
```

`generateLanguage` 的专用编译单元只依赖 MPS SDK 和 Gson，不依赖核心库，因此修改模型后不会因为旧生成物校验而陷入循环。生成源保存在 `src/generated` 并随仓库分发；普通构建不需要 SDK。核心的 `verifyGeneratedLanguage` 任务检查模型和生成物的 SHA-256，防止修改模型后漏生成，或直接编辑生成文件。

MPS 工程中的同名 `verifyGeneratedLanguage` 会使用真实模型重新生成并逐字比较，覆盖生成器本身的变化。完整验证还包含修改原生节点后编译新运行库，检查解析、写回、补全和必填诊断的实际行为变化。

重新构建 IDE 插件会把最新 LSP 与同一份元数据打包进去；安装说明见各客户端 README。MPS 自带的 `.dc` 结构编辑器是辅助工具，语言定义与生成流程见 [MPS 工程维护说明](mps/DEVELOPMENT.zh.md)。

## 快速开始

```java
import com.cozooo.dclanguage.*;
import java.nio.file.Path;

var language = DcLanguages.create();
var basic = language.registerMainCategory("basic", true);
var identifier = language.addField(basic, DcField.string("identifier"), true);
var mode = language.addField(basic, DcField.enumeration("mode", "hash", "name"), false);

var path = Path.of("example.dc");
var document = DcDocuments.read(path);
var result = language.validate(document);
if (!result.valid()) {
    throw new IllegalArgumentException(result.issues().toString());
}

String name = document.get(basic, identifier);
document.set(basic, mode, "hash");
DcDocuments.write(path, document);
```

对应文档：

```dc
[basic]
identifier = "example"
mode = "hash"
```

`create()` 不需要传语言名。`registerMainCategory(name, required)` 的第二个参数决定该分类是否必须出现。`addField(category, field, required)` 的第三个参数决定该字段是否必须出现。注册行为不读取、创建或修改配置文件。

读取和写入不需要传入 `language`。语言对象只保存校验规则，每种配置可以建立自己的规则集合，互不影响。缺失文件由 `read` 抛出 `IOException`，不会自动创建或填充内容。

这些注册 API 用于应用自行增加运行时约束。需要在多个 IDE 中共享的字段和规则应维护在 MPS 的 `SchemaProfile` 中；工具链不会扫描任意 Java 代码来推导自定义谓词。可用 `DcSchemaCatalog.loadDefault()` 访问生成的配置类型。

Kotlin 直接调用：

```kotlin
val language = DcLanguages.create()
val basic = language.registerMainCategory("basic", true)
val identifier = language.addField(basic, DcField.string("identifier"), true)
val mode = language.addField(basic, DcField.enumeration("mode", "hash", "name"), false)

val document = DcDocuments.read(Path.of("example.dc"))
check(language.validate(document).valid())
val name: String? = document.get(basic, identifier)
document.set(basic, mode, "hash")
DcDocuments.write(Path.of("example.dc"), document)
```

## 不需要预先声明规则的操作

```java
var document = DcDocuments.readText("[basic]\nidentifier = \"example\"\n");
String name = document.getString("basic", "identifier");
document.set("basic", "identifier", "changed");
document.remove("basic", "mode");
String text = DcDocuments.writeText(document);
```

`DcDocuments.create()` 创建空文档。`fromJson(JsonObject)` 接受已有数据并复制它。`document.root()` 与 `category(...)` 返回可修改的 Gson 对象；`toJson()` 返回独立副本。任何通过这些接口做出的改动都参与写回，包括未注册字段。

分类路径逐段传入，避免把名称中的点误认为分隔符：

```java
document.category("resources", "resource.with.dots", "targets");
document.get(java.util.List.of("resources", "resource.with.dots"), "source");
```

## 类型与校验

| 字段 | 创建方式 |
| --- | --- |
| 字符串 | `DcField.string("name")` |
| 布尔值 | `DcField.bool("enabled")` |
| 数字 | `DcField.number("count")`，使用 `BigDecimal` |
| 枚举 | `DcField.enumeration("mode", "hash", "name")` |
| 列表 | `DcField.list("names", DcTypes.STRING)` |
| 对象 | `DcField.object("metadata")` |
| 任意值 | `DcField.any("extension")` |
| 可为 null | `new DcField<>("label", DcTypes.nullable(DcTypes.STRING))` |
| 自定义类型 | `new DcType<>(name, accepts, decode, encode)` |

```java
var count = language.addField(
    basic,
    DcField.number("count").check(value -> value.signum() >= 0, "Count must not be negative"),
    false
);
var enabled = language.addField(basic, DcField.bool("enabled").defaultValue(true), false);
```

默认值只影响类型化 `document.get(category, field)` 的返回值，不写进文档。如果字段被声明为必填，有默认值也仍需在文件中显式出现。未知分类和字段不参与校验、不被删除，也不会为旧字段建立专门的清理规则。

可通过 `language.registerCategory(parent, name, required)` 注册子分类。可选父分类不存在时，不要求其子字段出现；父分类存在时，会校验子分类和字段。根级字段注册到 `language.rootCategory()`。

默认查找已声明名称时不区分大小写；文档保留原始拼写。需要精确匹配时，在注册之前调用 `language.caseSensitive(true)`。通过 `DcCategory`／`DcField` 的类型化读写沿用注册表的策略；使用字符串名称的通用读写由 `document.caseSensitive(true)` 控制。存在仅大小写不同的多个匹配项时，忽略大小写的读取会拒绝歧义，而不是选中任意一个。

`validate` 返回所有发现的问题，每个 `DcIssue` 包含 `path()` 和 `message()`。它不修改文件。语法损坏由读取操作抛出 `DcParseException`，可通过 `line()`／`column()` 获取从 1 开始的位置。写回不会隐式调用任何注册表的校验；需要时由应用显式调用。

## 写回行为

默认使用 `DcWriteOptions.PRESERVE_FORMATTING`：

- 未修改文档逐字保留，包括注释、空行、BOM、换行符和末尾是否换行。
- 修改字段时只替换其值，保留其他字段、未知内容、名称和行尾注释；可兼容的字符串引号也保留。
- 新字段插入所属分类。新增分类、删除字段、删除分类、直接修改嵌套对象均可写回。
- 修改整个列表／内联对象时重新序列化该值；其内部注释移到字段前作为独立注释。删除字段或分类时保留其中的注释。
- 写出前重新解析结果并检查语义一致性；无法无损表示时抛错，不覆盖已有文件。

```java
String normalized = DcDocuments.writeText(document, DcWriteOptions.NORMALIZE);
DcDocuments.write(path, document, DcWriteOptions.NORMALIZE);
```

需要显式把内联对象或新增对象写成方括号分类时，可使用 `document.asSection("basic")`。嵌套路径逐段传入，例如 `document.asSection("resources", "entry.with.dots")`。该方法支持链式调用，仅调整指定对象的排版；已有方括号分类保持原样，其他字段与注释继续保留。路径必须指向已有对象，缺失或非对象路径会报错。适合将旧配置字段迁移到新的分类布局。

```java
document.set("basic", "identifier", "example");
document.asSection("basic");
DcDocuments.write(path, document);
```

`NORMALIZE` 输出统一格式，舍弃注释和原始排版，但保留数据、未知字段和类型。它使用逐段引用的完整分类路径，支持包含点、斜杠或引号的名称。

文件写入先写入同目录临时文件，再替换目标；文件系统不支持原子移动时退回普通替换。已有文件的可用 POSIX／ACL 权限会被保留。通过符号链接写入时更新真实目标并保留链接；损坏链接会报错。失败时清理临时文件。

文档不是线程安全对象。保存基于读取时的快照及当前内存值计算修改，不会合并其他程序同时对磁盘文件做出的改动；这类场景应在应用层加锁或重新读取。

## 与现有 DLC 配置的关系

库保留 `.dc` 的通用结构：`[basic]`、`[download]` 等分类仍是对象，裸条目映射为名称对应的空对象。它不执行 DLC 专属的平铺、下载或安装逻辑。DLC Manager 的 Java/Kotlin 实现、JavaFX 和 Bootstrap 已接入本库。主项目的 `DlcDcFormatUtil` 保留 DLC 字段展开和关系转换；更新配置通过同一解析器读取，再把 `del` 裸条目转成业务列表。`dc-editor` 仍是独立的 TypeScript 实现。

需要导入旧方言时，`DcDocuments.categoryHeaders(text)` 可提供分类声明的名称分段、字符范围、缩进和方括号／冒号样式。扫描会跳过多行字符串及其他完整值，不构造业务数据树，也不要求尚待迁移的字段已满足唯一性约束；导入器完成转换后再用 `readText` 严格解析。

语法边界与示例见 [SPEC.md](SPEC.zh.md)。MPS 工程位于 [MPS 维护说明](mps/DEVELOPMENT.zh.md)，包含权威语言定义 DSL、生成器和可选的 `.dc` 结构编辑器。它是单独构建的插件，MPS 依赖不会进入本库或游戏运行时。

## 构建与测试

在当前目录运行：

```sh
./gradlew test
```

打包时使用 `./gradlew jar sourcesJar`。本工程无需 DLC Manager、Minecraft、StoneCutter 或 ApricityUI。Gradle Wrapper 启动需要 Java 17 及以上，编译和测试使用 Java 17 工具链。

项目许可为 [DLC Manager Noncommercial Source License 1.0](LICENSE)，副本须与根 `LICENSE` 保持一致；第三方许可继续独立保留。

测试覆盖现有 Remote、依赖／冲突、history、state、更新和公告语法；同时验证无损编辑、错误位置、必填检查、类型、未知字段、默认值、Kotlin 调用和原子写入。

## 主项目接入

根项目以及 Bootstrap、JavaFX 从 Maven Central 解析 `com.cozooo:dclanguage:0.1.0`，不使用本地复合构建替换。

- Fabric、Forge、NeoForge 的模组包内嵌本库，开发环境也能解析相同依赖。
- Bootstrap 的独立早期加载包重定位本库与 Gson，避免依赖游戏尚未加载的运行库。
- JavaFX 使用发行 fat jar 的运行时依赖打包本库。
- 本库测试在 `tools/dclanguage` 下通过 `./gradlew test` 单独运行；MPS SDK 验证由单独的 MPS 工程执行。
- MPS 和 LSP 的库开发构建仍可使用本地复合构建，用于开发和验证语言变更。

泛用文档写回采用调用者提供的完整数据树，删除字段和状态记录会真实生效。DLC 结构化写回则通过业务适配层维护已有分类和字段位置。原有不合法的 `.dc` 文件不会被空对象静默覆盖。
