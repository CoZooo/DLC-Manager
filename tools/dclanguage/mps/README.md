# Define DC Language with MPS

[简体中文](README.zh.md)

This tool defines and maintains `.dc` syntax, fields, and validation rules. The [native MPS model](solutions/definition/models/dc.mps) is the authoritative definition; its generated outputs are shared by the DC Language library, language server, and editor plugins. The tool also includes an optional structured editor for `.dc` files.

## Requirements

Building requires JDK 21 and the MPS 2026.1 SDK. Set `MPS_HOME` to the directory containing `lib/mps-openapi.jar`; for the macOS application, use `MPS.app/Contents`. You can also pass `-PmpsHome=/path/to/MPS`.

Running DLC Manager or using the DC Language library does not require MPS.

## Build the plugin

Run in this directory:

```sh
../gradlew check pluginZip
```

The package is `build/distributions/dclanguage-mps-0.1.0.zip`. In MPS, choose **Settings → Plugins → Install Plugin from Disk**, select the ZIP, and follow the prompts to install and restart.

## Change the language definition

1. Install the plugin, then open the repository's `definition-editor` project.
2. Open the `dc` root in the `com.cozooo.dclanguage.definitions.dc` model.
3. Change syntax tokens, string or number rules, or the categories, fields, and constraints of a configuration profile.
4. Generate and verify from this directory:

```sh
../gradlew generateLanguage verifyGeneratedLanguage
../gradlew -p .. check
../gradlew -p ../lsp check shadowJar serverDistZip
```

Outputs are stored in the parent module's `src/generated/` directory and committed alongside the model. Keep rules shared by multiple editors in this model. Do not edit generated files directly or maintain separate syntax definitions for each editor.

See [Development](DEVELOPMENT.md) for the generator, definition concepts, and SDK verification coverage.

## Edit configuration files

The plugin also lets you edit `.dc` categories, lists, and fields while preserving comments, unknown fields, and unchanged formatting when saving.

The sample project is in the [repository's `example` directory](example); it is not included in the plugin ZIP. Obtain the repository and open that project in MPS, or first browse the [sample configuration](solutions/sandbox/models/example.dc). Resolve external file changes or configuration errors before saving.

This project uses a [noncommercial license](../LICENSE). Commercial use or sales require prior written permission.
