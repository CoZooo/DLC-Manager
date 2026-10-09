# MPS Language Definition and Plugin Development

[简体中文](DEVELOPMENT.zh.md)

`pluginZip` places the original Gson JAR and the DC Language JAR in `dclanguage/lib/`, with `LICENSE`, `THIRD_PARTY_NOTICES.md`, and `licenses/gson/` under `dclanguage/`. License texts come from the [parent module](../THIRD_PARTY_NOTICES.md). The plugin ZIP does not include the `example` project; obtain the sample project and configurations from the repository.

This project defines the `.dc` language in JetBrains MPS. The native `Definition` model at `solutions/definition/models/dc.mps` is authoritative. Structured nodes express syntax tokens, string and escape rules, numeric patterns, value types, parsing policies, and application schemas.

The model generates Java syntax tables and parser/writer entry points for the runtime, plus JSON language metadata for validation and language services. A handwritten general engine implements parsing and formatting preservation using the generated rules. The model does not embed the parser's source code.

## Definitions and outputs

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

`languages/definition/languageModels/structure.mps` is the definition DSL's native Structure model. Its fourteen concepts, including `Definition` and `SyntaxToken`, have projectional editors, validation, and source navigation. Editing `Field.required`, enum values, or descriptions updates the shared schema. Changing nodes such as `SyntaxToken.assignment` updates the runtime syntax table.

The library's `src/generated` directory contains:

- `java/com/cozooo/dclanguage/generated/DcGeneratedSyntax.java`: token constants, lexical rules, and policies.
- `java/com/cozooo/dclanguage/DcParser.java` and `DcWriter.java`: generated entry points that call the general engine.
- `resources/com/cozooo/dclanguage/language-definition.json`: syntax and schema profile metadata.
- `generation-manifest.json`: SHA-256 hashes of the source model and each generated file.

These files are committed. Normal runtime, Minecraft mod, Bootstrap, and JavaFX builds use them without requiring MPS. Language maintainers regenerate them after changing the definition.

## Generation and verification

JDK 21 and the MPS 2026.1 SDK are required. Set `MPS_HOME` to the directory containing `lib/mps-openapi.jar`; the macOS application uses `MPS.app/Contents`.

Run in this directory:

```sh
../gradlew generateLanguage
../gradlew verifyGeneratedLanguage
../gradlew test pluginZip
```

Pass `-PmpsHome="$MPS_HOME"` to select the SDK explicitly. `generateLanguage` creates a real `MpsEnvironment`, loads the language modules and native model, and generates outputs from SNode data. `verifyGeneratedLanguage` regenerates and compares byte for byte without modifying files.

MPS **TextGen** also registers four output units: shared JSON, Java syntax tables, the parser entry point, and the writer entry point. Command-line generation additionally writes the build manifest.

The generator uses a separate `definitionGenerator` source set that depends only on the MPS SDK and Gson. It loads the definition language and authoritative model through `definition-project`. Generation remains available after a model change even if the core outputs are stale; it needs neither a prebuilt bootstrap JAR nor a temporarily disabled consistency check.

`test` and `pluginZip` use the current runtime through `includeBuild("..")`. When developing alongside another Gradle build, `-PdcLibraryJar=...` can select an already-built JAR to avoid writing shared compilation directories. It is not a prerequisite for generation.

Use `-PgeneratedOutput=...` for a temporary output directory, or `-PdefinitionProject=...` to verify a copy of the MPS project.

## Maintain the definition in MPS

After installing the plugin, open `definition-editor`, navigate to `com.cozooo.dclanguage.definitions.dc`, and open the `dc` root. This project loads only the authoritative model Solution and uses the plugin's definition DSL, avoiding a duplicate language declaration with the same UUID.

`definition-project` lets the headless generator load the language and model independently. The complete development project in this directory is used to change the DSL's native Structure and Java backend.

- `SyntaxToken` defines literals for assignments, categories, containers, and comments.
- `StringRule` defines delimiters, multiline behavior, and escape interpretation.
- `EscapeRule` represents characters as `U+XXXX`, including control characters such as backspace that XML cannot store directly.
- `NumberRule` and `NameRule` define regular expressions for numbers and names that can be written without quoting.
- `Policy` controls bare values, empty assignments, trailing commas, newline separators, boolean case, category forms, nesting depth, and related behavior.
- `SchemaProfile` defines filename patterns and categories. Nested `Category` nodes are expanded into `path` arrays during generation.
- `Field` contains its type, `required` flag, description, default, enum values, and constraints. Types may be unions such as `string|array`; `*` represents dynamic fields.

The initial type and field constraints cover existing application files while preserving compatibility with inferred identifiers. `strict-example` demonstrates required categories and fields, plus string-length constraints.

Missing tokens, empty delimiters, conflicts, duplicate fields, invalid types, and invalid regular expressions are reported by MPS validation and the generator, stopping generation.

Gradle compiles the definition DSL's runtime descriptors, projectional editors, and generation backend. Native Structure and DC definitions remain in `.mps` files. `prepareDefinitionLanguage` prepares compiled code for the development project. Rebuild after changing the Java backend; regenerate after changing the DC model.

## Optional structured `.dc` editor

The companion language at `languages/dc` edits configuration files directly. It includes Document, Member, Object, Array, and Scalar concepts and uses the runtime library for reading and saving.

Install `build/distributions/dclanguage-mps-0.1.0.zip` through MPS **Settings → Plugins → Install Plugin from Disk**. The plugin includes both languages and their runtime dependencies, but not the MPS SDK.

After installation, open the repository's `example` directory to use the sample configuration. That project references only the sandbox Solution, avoiding duplicate loading of source languages with the same UUID. A `.dc` file placed in a Solution's model source root is discovered automatically in the project tree.

Saving preserves comments, unknown fields, and unchanged formatting, detects external disk changes, and replaces the file atomically. Invalid configuration or UTF-8 data is not overwritten with an empty model. Fix the error and retry the save. Objects and arrays use standard MPS structural operations and folding.

## Test coverage

Tests against the real MPS SDK cover module loading, native concepts and editors, the Definition model, deterministic generation, stale-output detection, propagation of changes to token and required-field nodes, and all four registered TextGen outputs.

Configuration editing tests cover existing fixtures, Unicode, unknown fields, formatting preservation, creation and editing, conflicts, reloads, retries after errors, and automatic `.dc` discovery. The isolated package test reads the official extension bean from the packaged `plugin.xml`, registers it through IntelliJ ExtensionPoint and MPS `ModelFactoryRegister`, and opens the sample project to read and write files. It does not replace extension loading with a manually constructed factory.

These tests run without a graphical interface and do not cover editor appearance or interactive behavior.
