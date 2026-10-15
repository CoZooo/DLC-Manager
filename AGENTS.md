# Development requirements

## Main project

- Maintain equivalent behavior in `src/main/java` and `src/main/kotlin`, including error handling, platform conditions, and configuration compatibility.
- Support every node in `versions/`. Independent Java-only tools do not require Kotlin duplicates.
- Edit the shared sources rather than generated StoneCutter copies. The active node uses original test sources; other nodes use generated test sources. Never include both copies of the same test in one compilation.
- Follow `.editorconfig` and the surrounding code style. Keep English and Chinese interface resources synchronized.

## DC language

- Define generic grammar in `tools/dclanguage/mps/solutions/definition/models/dc.mps`, then regenerate syntax metadata. Do not put application fields in the language library.
- Maintain DLC Manager fields in `src/main/resources/META-INF/dlc-manager/dlc-definition.json` and register them through the DC category/field APIs. Editor integrations receive the complete application definition separately from syntax metadata.
- Keep parsing, validation, completion, and formatting rules consistent across the runtime library and its editor integrations. Do not maintain separate grammar or field tables in consumers.
- The main project, Bootstrap, and external manager resolve `com.cozooo:dclanguage:0.1.0` from Maven Central. Update consumer coordinates together when changing the library version.
- FileOps Helper uses `com.cozooo.dlc_manager:dlc_fileops_helper`; its version is set in root `gradle.properties`.

## Configuration and file operations

- Preserve compatibility with existing `.dc` configurations, legacy presets, and dependency identifiers.
- Preserve comments, unknown fields, and unaffected formatting when editing configuration files.
- Retain validation, backups, recovery, and atomic-save behavior when changing installation, update, migration, or deletion logic.

## Verification

For changes without source modifications:

```sh
./gradlew check --parallel
```

For main-project source changes, test both implementations across all affected nodes:

```sh
./gradlew test --parallel
./gradlew -Penable_kotlin=false test --parallel
```

Use the relevant module's checks for independent tools. Verify archive contents when packaging changes. GUI behavior requires manual acceptance.

## Pull requests

AI-assisted contributions are allowed. Every PR description must include the following sections in this order. Use `None` under Optional Notes when there is nothing to add.

```markdown
## Summary
Briefly summarize the contribution.

## New Feature/Issue Solved
Describe the feature or problem addressed, with related issue links when available.

## What Changed
List the main changes.

## Validation
List the checks performed and their results. State which checks were not run.

## Optional Notes
Additional context, or None.
```
