# MPS DC 语言定义与插件维护

[English](DEVELOPMENT.md)

`pluginZip` 将原版 Gson JAR 与 DC Language JAR 放入 `dclanguage/lib/`，并在 `dclanguage/` 下附带 `LICENSE`、`THIRD_PARTY_NOTICES.md` 和 `licenses/gson/`。许可资料复用[上级模块的维护原文](../THIRD_PARTY_NOTICES.md)。插件 ZIP 不含 `example` 项目；需要示例的用户应获取仓库中的项目与配置文件。

本工程用 JetBrains MPS 定义 `.dc` 语言本身。权威定义是 `solutions/definition/models/dc.mps` 中的原生 `Definition` 模型；语法标点、字符串规则、转义、数值正则、值类型、解析策略和业务 schema 都由结构化节点表达。

模型生成两类共享输出：Java 语法表及解析／写回入口供运行库使用，JSON 语言元数据供校验和语言服务使用。通用解析和格式保留算法保留为手写引擎，读取生成的规则。模型中没有嵌入整段解析器源码。

## 定义与输出

```text
MPS Definition
  ├─ SyntaxToken / LiteralRule / StringRule / EscapeRule
  ├─ NumberRule / NameRule / ValueType / Policy
  └─ SchemaProfile → Category → Field → Constraint
                  ↓ MPS TextGen / headless generator
  DcGeneratedSyntax.java + DcParser.java + DcWriter.java
  language-definition.json + generation-manifest.json
                  ↓
  DC runtime / schema validation / language services
```

`languages/definition/languageModels/structure.mps` 是定义 DSL 的原生 Structure 模型。`Definition`、`SyntaxToken` 等十四种概念均有实际投影编辑器、校验及源码导航。编辑 `Field.required`、枚举值或描述会更新同一份共享 schema；修改 `SyntaxToken.assignment` 等节点会更新运行库读取的语法表。

核心生成文件位于独立库的 `src/generated`：

- `java/com/cozooo/dclanguage/generated/DcGeneratedSyntax.java`：token 常量、词法规则和策略。
- `java/com/cozooo/dclanguage/DcParser.java`、`DcWriter.java`：生成入口，调用通用引擎。
- `resources/com/cozooo/dclanguage/language-definition.json`：语法及 schema profiles 元数据。
- `generation-manifest.json`：源模型及每个生成文件的 SHA-256。

这些生成文件提交到仓库。普通运行库、Minecraft 模组、Bootstrap 和 JavaFX 构建使用它们，不要求安装 MPS。修改语言定义后，语言维护者使用下述命令重新生成。

## 生成和验证

需要 JDK 21 和 MPS 2026.1 SDK。设置 `MPS_HOME` 指向含 `lib/mps-openapi.jar` 的目录；macOS 应用对应 `MPS.app/Contents`。

在本目录执行：

```sh
../gradlew generateLanguage
../gradlew verifyGeneratedLanguage
../gradlew test pluginZip
```

可用 `-PmpsHome="$MPS_HOME"` 显式指定 SDK。`generateLanguage` 创建真正的 `MpsEnvironment`，加载语言模块及原生模型，再从 SNode 生成输出。`verifyGeneratedLanguage` 重新生成并逐字比较，不修改文件。

MPS 的 **TextGen** 同样注册了四个输出单元：共享 JSON、Java 语法表、解析入口及写回入口。命令行生成还写入构建使用的 manifest。

生成器使用独立的 `definitionGenerator` source set，只依赖 MPS SDK 和 Gson；它通过 `definition-project` 加载定义语言及权威模型。即使模型刚改过、核心生成物尚未更新，也能直接执行生成，不需要预存 bootstrap JAR 或临时关闭一致性校验。

`test` 和 `pluginZip` 通过 `includeBuild("..")` 使用当前运行库。只在与另一个 Gradle 构建并行开发时，可用 `-PdcLibraryJar=...` 指定已构建 JAR，避免改写共享编译目录。这个选项不是生成语言的前提。

`-PgeneratedOutput=...` 可选择临时生成目录；`-PdefinitionProject=...` 可验证整个 MPS 工程的副本。

## 在 MPS 中维护定义

安装本工程插件后，打开 `definition-editor` 项目，进入 `com.cozooo.dclanguage.definitions.dc` 模型并打开 `dc` 根节点。这个入口只加载权威模型 Solution，直接使用插件提供的定义 DSL，不会重复声明同 UUID 的语言。

`definition-project` 供无界面生成器独立加载语言与模型。本目录的完整开发工程用于修改 DSL 的原生 Structure 和 Java 后端。

- `SyntaxToken` 定义赋值、分类、容器、注释等字面量。
- `StringRule` 定义定界符、是否多行及是否解释转义。
- `EscapeRule` 以 `U+XXXX` 表示字符，避免 XML 无法保存退格等控制字符。
- `NumberRule`、`NameRule` 定义数值与可直接写出的名称正则。
- `Policy` 定义裸值、空赋值、尾逗号、换行分隔、布尔大小写、分类形式、嵌套深度等策略。
- `SchemaProfile` 定义文件匹配模式和分类；`Category` 可嵌套，生成时展开为 `path` 数组。
- `Field` 包含类型、`required`、描述、默认值、枚举和约束。类型可使用 `string|array` 等联合类型；`*` 表示动态字段。

类型与字段约束的初始定义覆盖既有业务文件，并保留允许推断标识符的兼容行为。`strict-example` 展示必填分类、必填字段和字符串长度约束。

语法定义出现缺失 token、空定界符、冲突、重复字段、非法类型或正则时，MPS 校验和生成器会报告错误，停止生成。

定义 DSL 的运行时描述器、投影编辑器和生成后端通过外部 Gradle 编译；原生 Structure 和 DC 定义内容保存在 `.mps` 文件中。`prepareDefinitionLanguage` 准备开发工程的编译结果。修改 Java 后端后重新构建，修改 DC 模型后重新生成。

## 可选的 `.dc` 结构编辑插件

本工程还保留直接编辑配置文件的辅助语言，位于 `languages/dc`。它包含 Document、Member、Object、Array、Scalar，读取和保存都复用运行库。

安装包为 `build/distributions/dclanguage-mps-0.1.0.zip`，可在 MPS **Settings → Plugins → Install Plugin from Disk** 安装。插件包含两种语言及所需运行库，不包含 MPS SDK。

安装后打开 `example` 目录即可使用配置示例；这个项目只引用 sandbox Solution，避免与安装包重复加载同 UUID 的源码语言。把 `.dc` 放入 Solution 的 model source root 后，项目树会自动发现它。

保存会保留注释、未知字段及未修改内容的格式，检测磁盘上的外部改动，并原子替换文件。非法配置或非法 UTF-8 不会被空模型覆盖；修复错误后可以重试保存。对象、数组使用标准 MPS 结构操作和折叠。

## 测试范围

真实 MPS SDK 测试覆盖语言模块加载、原生概念和编辑器、Definition 模型、确定性生成、生成物过期检测、token/required 节点修改传播及注册 TextGen 的四个输出。

配置编辑测试覆盖原有文件夹具、Unicode、未知字段、格式保留、创建和修改、冲突、重载、错误后重试及 `.dc` 自动发现。隔离安装包测试从包内 `plugin.xml` 读取官方 extension bean，经 IntelliJ ExtensionPoint 和 MPS `ModelFactoryRegister` 注册，再打开示例项目读写文件；没有用直接手工构造工厂替代扩展加载。

这些测试不启动图形界面，不覆盖编辑器外观或交互行为。
