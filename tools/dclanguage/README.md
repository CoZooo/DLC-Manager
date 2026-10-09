# DC Language

[简体中文](README.zh.md)

DC Language is a Java 17 library for reading, validating, and editing `.dc` configuration files from Java or Kotlin. It preserves comments and formatting when writing changes and provides shared configuration support for DLC Manager, editor plugins, and other tools.

## Add the library

Add the dependency from Maven Central:

```kotlin
dependencies {
    implementation("com.cozooo:dclanguage:0.1.0")
}
```

MPS is not required for normal use or builds.

## Read and edit a configuration

```java
import com.cozooo.dclanguage.DcDocuments;
import java.nio.file.Path;

var path = Path.of("example.dc");
var document = DcDocuments.read(path);
String identifier = document.getString("basic", "identifier");
document.set("basic", "identifier", "example");
DcDocuments.write(path, document);
```

The corresponding file:

```dc
[basic]
identifier = "example"
```

Saving preserves comments, unknown fields, and unchanged content by default. `read` does not create missing files; use `DcDocuments.create()` for an empty document. Use `DcDocuments.readText(text)` and `DcDocuments.writeText(document)` to work with text in memory.

Applications can register required fields, types, and value constraints, then call `language.validate(document)`. See the [API and maintenance guide](DEVELOPMENT.md) for complete examples, typed access, categories, and save behavior, or the [format specification](SPEC.md) for the file syntax.

## Build and verify

With JDK 17 or later, run in this directory:

```sh
./gradlew check
./gradlew jar sourcesJar
```

JAR files are written to `build/libs/`.

The [MPS tool](mps/README.md) maintains `.dc` language rules. Change the MPS model and regenerate its outputs before verifying the library and affected editors. Do not edit generated files directly.

## Companion tools

- [Language server](lsp/README.md): diagnostics, completion, hover, and formatting for other editors.
- [VS Code plugin](../editor/vscode-dc-language/README.md) and [JetBrains plugin](../editor/jetbrains-dc-plugin/README.md): edit `.dc` in your usual editor.
- [macOS Quick Look](../editor/dc-quicklook/README.md): preview `.dc` files in Finder.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency notices.

Charging for this software itself requires prior written permission under the [project license](LICENSE).
