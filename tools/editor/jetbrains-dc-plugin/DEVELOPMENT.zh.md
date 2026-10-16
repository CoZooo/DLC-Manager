# JetBrains 插件开发

[English](DEVELOPMENT.md)

## 语言功能

插件使用 IntelliJ Platform 公共扩展点以及内嵌的 Java 17 stdio 语言服务，适用于普通 IntelliJ 系 IDE，包括 Community 版本；不依赖 MPS 安装或 Ultimate LSP API。

诊断、分类与字段补全、快速文档和显式格式化请求使用共享语言服务。词法分析器与补全触发符读取 `processResources` 从同一服务 JAR 中提取的生成定义。语法配色、颜色补全、颜色选择与行内色块由插件提供。

服务优先使用 `JAVA_HOME/bin/java`，随后尝试 IDE Java 运行时；要求 Java 17 及以上。每个项目管理自己的服务进程，项目关闭时停止。请求在事件派发线程外执行，支持取消与超时；过期文档版本的诊断会被丢弃。格式化由 IDE 请求执行。

## 构建与测试

从仓库根目录开始：

```sh
./gradlew -p tools/dclanguage/lsp shadowJar
cd tools/editor/jetbrains-dc-plugin
./gradlew test buildPlugin
```

插件使用自己的 Gradle wrapper，默认以 IntelliJ IDEA Community 2023.3.8 SDK 构建；`sinceBuild` 为 `233`，目前未设置版本上界。构建采用 Java 25 toolchain，输出 Java 17 字节码。可用 `-PintellijHome=<IDE home>` 指定本地 IDE，或用 `-PdcLanguageServerJar=<built JAR>` 选择其他已构建的共享服务。

输出为 `tools/editor/jetbrains-dc-plugin-2.0.0.zip`，其中服务位于 `jetbrains-dc-plugin/server/dclanguage-lsp.jar`。

分发包同时包含中英文用户说明、项目许可证、第三方说明以及 `licenses/gson/`。许可证正文来自 `tools/dclanguage/licenses/`，项目许可证来自 `tools/dclanguage/LICENSE`。

测试启动模拟与真实 stdio 服务，覆盖 Unicode 帧、超时、异常与关闭、诊断、补全、文档及格式化。测试需要共享服务 JAR，但不启动桌面 IDE。
