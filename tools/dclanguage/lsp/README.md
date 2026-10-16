# DC Language Server

[简体中文](README.zh.md)

DC Language Server provides diagnostics, completion, field descriptions, and formatting for `.dc` files in editors that support LSP. It uses the same rules as the DC Language library and preserves comments and configuration data when formatting.

The [VS Code plugin](../../editor/vscode-dc-language/README.md) and [JetBrains plugin](../../editor/jetbrains-dc-plugin/README.md) already include the server. You do not need to install this module separately when using either plugin.

## Build

Java 17 or later is required. Run in this directory:

```sh
../gradlew check shadowJar serverDistZip
```

The runnable JAR is `build/libs/dclanguage-lsp.jar`. The distribution ZIP is written to `build/distributions/`.

## Start and connect

Start the server directly:

```sh
java -jar build/libs/dclanguage-lsp.jar --stdio
```

Alternatively, extract the distribution ZIP and run `dclanguage-lsp` on macOS or Linux, or `dclanguage-lsp.bat` on Windows. The launcher uses `JAVA_HOME` first, then looks for Java on PATH.

In your editor's LSP settings, associate `.dc` files with this command and use standard input/output for communication. Standard output is reserved for the editor protocol; startup and transport errors go to standard error.

The server starts with no application fields. Pass an application-owned catalog as `initializationOptions.definition` (the same profile/category/field format accepted by `DcSchemaCatalog.fromJson`). The server registers it through the DC API, then selects a profile from its file patterns and document values. Set `initializationOptions.profile` to select one explicitly. Without a definition, diagnostics and formatting cover generic DC syntax only. Fix syntax errors before formatting.

## Capabilities

The server supports full and incremental document updates, diagnostics, field and value completion, hover descriptions, formatting, and request cancellation. Unknown fields are preserved rather than removed because they are unregistered.

It implements LSP 3.17 with UTF-16 document positions. Normal editing only processes text sent by the client; it does not read files from document URIs or execute paths or commands in a configuration. Document versions must increase. Stale background results are discarded or return `ContentModified`.

Transport uses UTF-8 JSON and `Content-Length` headers. Each message is limited to 8 MiB. Up to 128 documents may be open, with at most 2,000,000 UTF-16 units per document and 8,000,000 units in total.

The distribution ZIP includes `LICENSE`, `THIRD_PARTY_NOTICES.md`, and `licenses/gson/`. The combined JAR also includes the relevant notices. See [DC Language](../README.md) for library usage and the [LSP 3.17 specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/) for protocol details.

Charging for this software itself requires prior written permission under the [project license](../LICENSE).
