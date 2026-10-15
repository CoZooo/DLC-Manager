# DC Language Development and API Reference

[简体中文](DEVELOPMENT.zh.md)

DC Language is a `.dc` toolchain whose language definition is maintained in MPS. Its Java package is `com.cozooo.dclanguage`, and the runtime library coordinates are `com.cozooo:dclanguage:0.1.1`. The main project resolves that coordinate directly from Maven Central.

The library reads, edits, and writes documents, with optional category and field validation. It has its own Gradle project and wrapper, requires Java 17, and depends on Gson at runtime. Java and Kotlin use the same API. The Kotlin standard library is used only in interoperability tests, not as a runtime dependency.

The [MPS Definition model](mps/solutions/definition/models/dc.mps) is the authoritative language source. It generates Java syntax tables, parser and writer entry points, and shared schema metadata. A general parsing and formatting-preservation engine applies those generated rules. Native MPS TextGen and the standalone headless generator both produce these outputs; the model does not contain an embedded copy of the parser implementation.

The runtime library and [language server](lsp/README.md) use the same generated rules. The [VS Code client](../editor/vscode-dc-language/README.md) and [IntelliJ client](../editor/jetbrains-dc-plugin/README.md) share diagnostics, completion, hover, and formatting. Highlighting and automatic pairing rules are also derived from the same metadata. Editing the language definition requires MPS; using the runtime library does not.

## Third-party distribution

[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) records the Gson version, copyright, and license sources. Original texts are stored in `licenses/gson/` and included under `META-INF/dclanguage/` in the runtime, source, and Javadoc JARs. The source JAR also includes this guide, the README, and the language specification in English and Chinese. LSP and MPS distributions reuse these license files. When updating Gson, verify the resolved version, upstream notices, and packaged texts. Do not reuse the Bootstrap-specific relocation notice.

## Change and generate the language

1. Open `mps/definition-editor` in MPS and edit generic tokens, strings, literals, or policies in the native `Definition` model. Application fields belong to the consuming project.
2. Set `MPS_HOME` to an MPS 2026.1 SDK and run from this directory:

```sh
./gradlew -p mps generateLanguage verifyGeneratedLanguage
./gradlew test
./gradlew -p lsp test shadowJar serverDistZip
```

The dedicated compilation unit for `generateLanguage` depends only on the MPS SDK and Gson. It does not depend on the core library, so stale generated-output checks cannot prevent regeneration after a model change. Generated sources are committed under `src/generated`; normal builds do not require the SDK. The core `verifyGeneratedLanguage` task checks SHA-256 hashes of the model and outputs to catch missed regeneration or direct edits to generated files.

The MPS project's task of the same name regenerates from the real model and compares the outputs byte for byte, covering generator changes as well. Full verification also changes native nodes, compiles a new runtime, and checks that parsing, writing, completion, and required-field diagnostics change accordingly.

Rebuilding an IDE plugin bundles the updated language server and metadata. Installation instructions are in each client's README. The optional MPS `.dc` structured editor is a companion tool; see [MPS Development](mps/DEVELOPMENT.md) for the definition and generation workflow.

## Quick start

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

The corresponding document:

```dc
[basic]
identifier = "example"
mode = "hash"
```

`create()` does not take a language name. The second argument to `registerMainCategory(name, required)` determines whether the category must appear. The third argument to `addField(category, field, required)` does the same for the field. Registration does not read, create, or modify configuration files.

Reading and writing do not require a `language` argument. A language object stores validation rules only; each configuration type can have its own independent rule set. `read` throws `IOException` for a missing file rather than creating or filling it.

These registration APIs let applications own their runtime constraints. The library default has no application profiles or fields: `DcSchemaCatalog.loadDefault()` returns an empty catalog. Applications can register categories and `DcField` rules directly, or maintain their own catalog definition and call `DcSchemaCatalog.fromJson(definition).language(profile)`; the latter uses the same `registerCategory` / `addField` API. Definition metadata is supplied explicitly to editor services, rather than bundled into the generic syntax. The toolchain does not infer arbitrary Java predicates. The TypeScript metadata adapter in `js/registry.ts` uses the same category/field registration model and has no built-in fields.

Call the same API from Kotlin:

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

## Work without predefined rules

```java
var document = DcDocuments.readText("[basic]\nidentifier = \"example\"\n");
String name = document.getString("basic", "identifier");
document.set("basic", "identifier", "changed");
document.remove("basic", "mode");
String text = DcDocuments.writeText(document);
```

`DcDocuments.create()` creates an empty document. `fromJson(JsonObject)` copies existing data. `document.root()` and `category(...)` return mutable Gson objects; `toJson()` returns an independent copy. All edits through these APIs, including changes to unregistered fields, participate in write-back.

Pass category paths as separate segments so dots within names are not treated as separators:

```java
document.category("resources", "resource.with.dots", "targets");
document.get(java.util.List.of("resources", "resource.with.dots"), "source");
```

## Types and validation

| Field type | Creation |
| --- | --- |
| String | `DcField.string("name")` |
| Boolean | `DcField.bool("enabled")` |
| Number | `DcField.number("count")`, using `BigDecimal` |
| Enum | `DcField.enumeration("mode", "hash", "name")` |
| List | `DcField.list("names", DcTypes.STRING)` |
| Object | `DcField.object("metadata")` |
| Any value | `DcField.any("extension")` |
| Nullable | `new DcField<>("label", DcTypes.nullable(DcTypes.STRING))` |
| Custom type | `new DcType<>(name, accepts, decode, encode)` |

```java
var count = language.addField(
    basic,
    DcField.number("count").check(value -> value.signum() >= 0, "Count must not be negative"),
    false
);
var enabled = language.addField(basic, DcField.bool("enabled").defaultValue(true), false);
```

A default only affects the value returned by typed `document.get(category, field)` calls; it is not written into the document. A required field must still appear explicitly, even if it has a default. Unknown categories and fields are neither validated nor removed, and there are no special cleanup rules for legacy fields.

Use `language.registerCategory(parent, name, required)` to register a child category. When an optional parent is absent, its child fields are not required. When the parent exists, its child categories and fields are validated. Register root fields on `language.rootCategory()`.

Declared names are matched case-insensitively by default while the document preserves their original spelling. For exact matching, call `language.caseSensitive(true)` before registering rules. Typed access through `DcCategory` and `DcField` follows the registry's policy. Generic access using string names is controlled by `document.caseSensitive(true)`. Case-insensitive reads reject ambiguous matches that differ only by case instead of choosing an arbitrary entry.

`validate` returns all detected issues without modifying the document. Each `DcIssue` has `path()` and `message()`. Reading invalid syntax throws `DcParseException`, whose `line()` and `column()` are one-based. Writing does not implicitly validate against any registry; the application must call validation when needed.

## Write-back behavior

The default is `DcWriteOptions.PRESERVE_FORMATTING`:

- An unchanged document is preserved byte for byte, including comments, blank lines, BOM, line endings, and the presence or absence of a final newline.
- Editing a field replaces only its value. Other fields, unknown content, names, and trailing comments are preserved, along with compatible string quotation styles.
- New fields are inserted into their category. New categories, deleted fields or categories, and direct changes to nested objects can all be written back.
- Replacing an entire list or inline object serializes that value again. Its internal comments move before the field as standalone comments. Deleting fields or categories preserves their comments.
- Before writing, the result is parsed again and checked for semantic equivalence. If it cannot represent the data without loss, the operation fails without overwriting the existing file.

```java
String normalized = DcDocuments.writeText(document, DcWriteOptions.NORMALIZE);
DcDocuments.write(path, document, DcWriteOptions.NORMALIZE);
```

Use `document.asSection("basic")` to explicitly render an inline or newly added object as a bracketed category. Pass nested paths segment by segment, for example `document.asSection("resources", "entry.with.dots")`. The method is chainable and changes only the selected object's layout. Existing bracketed categories remain unchanged, and other fields and comments are preserved. The path must identify an existing object; missing paths or non-object values cause an error. This is useful when migrating old fields to a new category layout.

```java
document.set("basic", "identifier", "example");
document.asSection("basic");
DcDocuments.write(path, document);
```

`NORMALIZE` emits a consistent format, discarding comments and original layout while retaining data, unknown fields, and types. It uses full category paths with independently quoted segments, including names containing dots, slashes, or quotation marks.

File writes first use a temporary file in the same directory, then replace the target. If atomic moves are unavailable, writing falls back to a normal replacement. Existing POSIX or ACL permissions are retained when available. Writing through a symbolic link updates its real target while preserving the link; broken links cause an error. Temporary files are cleaned up after failure.

Document objects are not thread-safe. Saving computes changes from the original read snapshot and current in-memory values; it does not merge concurrent changes made to the file by another program. Applications must lock or reread the file when necessary.

## Relationship to DLC configuration

The library preserves the generic `.dc` structure: categories such as `[basic]` and `[download]` remain objects, and bare entries map names to empty objects. It does not perform DLC-specific flattening, downloading, or installation. DLC Manager's Java and Kotlin implementations, JavaFX, and Bootstrap use this library. The main project's `DlcDcFormatUtil` retains DLC field expansion and relationship conversion. Update configurations use the same parser, then convert bare `del` entries to application lists. `dc-editor` remains a separate TypeScript implementation.

For imports from older dialects, `DcDocuments.categoryHeaders(text)` exposes category name segments, character ranges, indentation, and bracket or colon style. The scan skips multiline strings and other complete values. It does not construct an application data tree or require fields awaiting migration to satisfy uniqueness rules. After conversion, the importer should use `readText` for strict parsing.

See [SPEC.md](SPEC.md) for syntax boundaries and examples. [MPS Development](mps/DEVELOPMENT.md) covers the authoritative definition DSL, generator, and optional `.dc` structured editor. The MPS plugin is built separately; its SDK dependencies do not enter this library or the game runtime.

## Build and test

Run in this directory:

```sh
./gradlew test
```

Package with `./gradlew jar sourcesJar`. The project does not require DLC Manager, Minecraft, StoneCutter, or ApricityUI. The Gradle wrapper requires Java 17 or later, and compilation and tests use a Java 17 toolchain.

The project uses [DLC Manager Source License 1.0](LICENSE). Its copy must match the root `LICENSE`, while third-party licenses remain separate.

Tests cover existing Remote, dependency/conflict, history, state, update, and announcement syntax, plus lossless edits, error positions, required fields, types, unknown fields, defaults, Kotlin calls, and atomic writes.

## Main project integration

The root project, Bootstrap, and JavaFX resolve `com.cozooo:dclanguage:0.1.1` from Maven Central without a local composite-build replacement.

- Fabric, Forge, and NeoForge distributions embed the library. Development environments resolve the same dependency.
- Bootstrap relocates the library and Gson in its independent early-loading package so it does not rely on the game's runtime libraries being loaded.
- JavaFX includes the library through the runtime dependencies of its distribution fat JAR.
- Run library tests separately with `./gradlew test` in `tools/dclanguage`. MPS SDK verification belongs to the separate MPS project.
- MPS and LSP development builds can still use a local composite build to develop and verify language changes.

Generic document writing uses the caller's complete data tree, so field and state-record deletions take effect. Structured DLC writing uses the application adapter to preserve existing category and field positions. Invalid existing `.dc` files are not silently replaced with empty objects.
