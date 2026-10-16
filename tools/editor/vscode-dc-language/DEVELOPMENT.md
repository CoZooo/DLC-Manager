# VS Code plugin development

[简体中文](DEVELOPMENT.zh.md)

## Language features

The extension starts its bundled Java language server over stdio. Diagnostics, field completion, hover documentation, and formatting use the same generated definition as the DC runtime and other editor integrations. Formatting runs when requested by the editor.

Java is selected from `dc.javaPath`, `JAVA_HOME/bin/java`, then `java` on `PATH`. When `dc.javaPath` is set, only that executable is used. Java 17 or later is required. These settings are read when the extension activates. Logs appear in **DC Language Server**.

The extension bundles only generic DC syntax and does not read or package application field definitions. Integrations can explicitly pass their own `definition` and optional `profile` through `LspClient.start(rootUri, initializationOptions)`.

## Build

Build the shared server from the repository root, then package the extension:

```sh
./gradlew -p tools/dclanguage/lsp shadowJar
cd tools/editor/vscode-dc-language
npm ci
npm run prepare-server
npm test
npm run package
```

`prepare-server` copies `tools/dclanguage/lsp/build/libs/dclanguage-lsp.jar` into the extension and generates TextMate highlighting, bracket and quote pairs, snippets, and completion triggers from that JAR's definition. Set `DC_LANGUAGE_SERVER_JAR` to use another built server JAR.

The output is `tools/editor/vscode-dc-language-2.0.0.vsix`.

The package includes English and Chinese user guides and `THIRD_PARTY_NOTICES.md`. Gson's license and copyright notices are inside the server JAR under `META-INF/dclanguage/licenses/gson/`. `.vscodeignore` excludes maintenance documents in both languages.

The packaging command sets `--baseContentUrl` to this plugin's repository directory so the Chinese guide link resolves correctly on the extension page.

## Tests

Tests cover Unicode framing, timeouts, process failure and shutdown, document synchronization, stale diagnostics, preservation of user text, and a real server session. Real-server tests require the built JAR.
