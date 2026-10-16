# VS Code 插件开发

[English](DEVELOPMENT.md)

## 语言功能

扩展通过 stdio 启动随包提供的 Java 语言服务。诊断、字段补全、悬浮说明和格式化使用与 DC 运行时及其他编辑器集成相同的生成定义。格式化由编辑器请求执行。

Java 查找顺序为 `dc.javaPath`、`JAVA_HOME/bin/java`、`PATH` 中的 `java`；显式设置 `dc.javaPath` 时仅使用指定程序。要求 Java 17 及以上。`dc.profile` 默认自动检测，也可将指定 profile 传入服务。这些设置在扩展激活时读取。日志输出到 **DC Language Server**。

## 构建

在仓库根目录构建共享服务，再打包扩展：

```sh
./gradlew -p tools/dclanguage/lsp shadowJar
cd tools/editor/vscode-dc-language
npm ci
npm run prepare-server
npm test
npm run package
```

`prepare-server` 将 `tools/dclanguage/lsp/build/libs/dclanguage-lsp.jar` 复制到扩展中，并依据该 JAR 内的定义生成 TextMate 高亮、括号与引号配对、片段以及补全触发符。需要使用其他已构建的服务时，通过 `DC_LANGUAGE_SERVER_JAR` 指定 JAR。

输出文件为 `tools/editor/vscode-dc-language-2.0.0.vsix`。

用户包包含中英文用户说明与 `THIRD_PARTY_NOTICES.md`；Gson 的许可证和版权声明保存在同包服务 JAR 的 `META-INF/dclanguage/licenses/gson/`。两种语言的维护文档都通过 `.vscodeignore` 排除出 VSIX。

打包命令使用 `--baseContentUrl` 指向插件在仓库中的目录，确保扩展页面的中文说明链接正确。

## 测试

测试覆盖 Unicode 消息帧、超时、服务异常与关闭、文档同步、过期诊断丢弃、用户原文保留以及真实服务会话。真实服务测试依赖已构建的 JAR。
