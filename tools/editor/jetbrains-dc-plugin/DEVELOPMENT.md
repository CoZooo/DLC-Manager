# JetBrains plugin development

[简体中文](DEVELOPMENT.zh.md)

## Language features

The plugin uses public IntelliJ Platform extension points and a bundled Java 17 stdio language server. It works in ordinary IntelliJ-based IDEs, including Community editions, without an MPS installation or the Ultimate LSP API.

Diagnostics, section and field completion, Quick Documentation, and explicit formatting use the shared language service. The lexer and completion triggers read the generated definition extracted from the same server JAR during `processResources`. The plugin provides syntax colors, color completion, the picker, and inline swatches.

The server tries `JAVA_HOME/bin/java`, then the IDE runtime. Java 17 or later is required. Each project owns its server process and stops it when closing. Requests run outside the event dispatch thread with cancellation and timeouts; diagnostics for older document versions are discarded. Formatting runs when requested by the IDE.

The plugin bundles only generic DC syntax and does not read or package application field definitions. Integrations can explicitly pass a `definition` and optional `profile` through `DCStdioClient.initialize(rootUri, initializationOptions)`; ordinary IDE startup supplies no application schema.

## Build and test

Start at the repository root:

```sh
./gradlew -p tools/dclanguage/lsp shadowJar
cd tools/editor/jetbrains-dc-plugin
./gradlew test buildPlugin
```

The plugin has its own Gradle wrapper and defaults to the IntelliJ IDEA Community 2023.3.8 SDK. `sinceBuild` is `233`, with no upper bound currently set. A Java 25 toolchain produces Java 17 bytecode. Use `-PintellijHome=<IDE home>` for a local IDE, or `-PdcLanguageServerJar=<built JAR>` for another built server.

The output is `tools/editor/jetbrains-dc-plugin-2.0.0.zip`. The server is stored at `jetbrains-dc-plugin/server/dclanguage-lsp.jar` inside the ZIP.

The distribution includes English and Chinese user guides, the project license, third-party notices, and `licenses/gson/`. License files come from `tools/dclanguage/licenses/` and `tools/dclanguage/LICENSE`.

Tests start simulated and real stdio servers and cover Unicode framing, timeouts, failure and shutdown, diagnostics, completion, documentation, and formatting. They require the shared server JAR and do not launch a desktop IDE.
