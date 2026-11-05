# DC Language Server

[English](README.md)

DC Language Server 为 `.dc` 文件提供错误检查、自动补全、字段说明和格式化，可接入支持 LSP 的编辑器。它使用与 DC Language 库相同的规则，格式化时保留注释和配置内容。

[VS Code 插件](../../editor/vscode-dc-language/README.zh.md)和 [JetBrains 插件](../../editor/jetbrains-dc-plugin/README.zh.md)已包含语言服务，使用这些插件时无需单独安装本模块。

## 构建

需要 Java 17 或更新版本。在本目录执行：

```sh
../gradlew check shadowJar serverDistZip
```

可直接运行的 JAR 位于 `build/libs/dclanguage-lsp.jar`，分发 ZIP 位于 `build/distributions/`。

## 启动与接入

直接启动：

```sh
java -jar build/libs/dclanguage-lsp.jar --stdio
```

也可以解压分发 ZIP，使用其中的 `dclanguage-lsp`（macOS / Linux）或 `dclanguage-lsp.bat`（Windows）。启动器优先使用 `JAVA_HOME`，否则查找 PATH 中的 Java。

在编辑器的 LSP 设置中，将 `.dc` 文件关联到上述启动命令，使用标准输入输出连接。语言服务输出用于与编辑器通信，启动及传输错误写入标准错误输出。

服务默认不注册业务字段。通过 `initializationOptions.definition` 传入应用自有的 catalog（格式与 `DcSchemaCatalog.fromJson` 接收的 profile／category／field 定义相同），由服务通过 DC API 注册，再根据文件匹配模式和文档值选择类型。可用 `initializationOptions.profile` 固定类型。不传 definition 时，只提供通用 DC 语法诊断和格式化。语法错误应先修复，再执行格式化。

## 支持能力

支持完整和增量文档更新、错误诊断、字段及取值补全、悬停说明、格式化和请求取消。未知字段会保留，不会仅因没有注册而被删除。

语言服务支持 LSP 3.17，文档位置按 UTF-16 计算。普通编辑只处理客户端发送的文本，不会读取 URI 对应的文件或执行配置中的路径、命令。文档版本必须递增，过期的后台结果会被丢弃或返回 `ContentModified`。

传输使用 UTF-8 JSON 和 `Content-Length` 标头。单条消息上限为 8 MiB；最多同时打开 128 个文档，单文档最多 2,000,000 个 UTF-16 单元，总文本最多 8,000,000 个单元。

分发 ZIP 带有 `LICENSE`、`THIRD_PARTY_NOTICES.md` 和 `licenses/gson/`，合并后的 JAR 也包含相应第三方资料。共享库的用法见 [DC Language](../README.zh.md)，协议依据 [LSP 3.17 规范](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)。

使用、修改和再分发须遵守 [PolyForm Shield License 1.0.0](../LICENSE)。许可允许在范围内进行使用，但禁止提供竞争产品或服务。
