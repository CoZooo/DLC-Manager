# Development

[简体中文](DEVELOPMENT.zh.md)

## Project layout

The main mod maintains corresponding implementations in `src/main/java` and `src/main/kotlin`. StoneCutter generates version-specific sources for the nodes in `versions/`. The active node uses original test sources; other nodes use generated tests alongside their version-specific tests.

Native MPS models define only the generic `.dc` syntax. DLC Manager owns its application fields in `src/main/resources/META-INF/dlc-manager/dlc-definition.json`. `DlcDcDefinition.register(...)` registers a profile through the DC category/field API; editor clients register the complete application definition separately from syntax metadata. The main project, Bootstrap, and external manager resolve `com.cozooo:dclanguage:0.1.1` from Maven Central.

`settings.gradle.kts` includes Bootstrap and the external manager as separate builds. DC Editor is a separate web and desktop application with its own text-editing implementation.

## DC application definitions

DLC field changes only edit `src/main/resources/META-INF/dlc-manager/dlc-definition.json`; they do not require regenerating the DC grammar. Java and Kotlin share that resource via `DlcDcDefinition`. Bootstrap and the external manager package the same file. DC Editor registers the complete definition through the generic TypeScript category/field API. VS Code copies it during `scripts/prepare-server.js`, while the JetBrains client copies it during `processResources`; both send it as `initializationOptions.definition` to the generic server. The library default catalog remains empty.

## Build

Use the repository's Gradle Wrapper with JDK 17, 21, and 25 available. Game nodes select their configured Java versions; the external manager uses Java 25.

```sh
./gradlew build --parallel
```

Distribution files are written to `output/<mod_version>/`. The version is set in root `gradle.properties`.

## Checks and tests

Repository checks:

```sh
./gradlew check --parallel
```

Tests for the Kotlin and Java implementations:

```sh
./gradlew test --parallel
./gradlew -Penable_kotlin=false test --parallel
```

A specific node can be selected directly, for example:

```sh
./gradlew :1.20.1-forge:test
```

## Component guides

- [DC runtime API](tools/dclanguage/DEVELOPMENT.md) and [language specification](tools/dclanguage/SPEC.md).
- [Language server](tools/dclanguage/lsp/README.md) and [MPS](tools/dclanguage/mps/DEVELOPMENT.md).
- [Bootstrap](tools/dlc-bootstrap/README.md) and [FileOps Helper](tools/file-ops-helper/README.md).
- [VS Code](tools/editor/vscode-dc-language/DEVELOPMENT.md), [JetBrains](tools/editor/jetbrains-dc-plugin/DEVELOPMENT.md), and [Quick Look](tools/editor/dc-quicklook/DEVELOPMENT.md).

From `tools/dc-editor`, use `npm run build` for the web application or `npm run tauri:build` for the desktop application.

## Contributing

AI-assisted development is allowed. Every PR description must include the following sections:

```markdown
## Summary
Briefly summarize the contribution.

## New Feature/Issue Solved
Describe the feature or problem addressed, with related issue links when available.

## What Changed
List the main changes.

## Validation
List the checks performed and their results. State which checks were not run.

## Additional Notes (Optional)
```
