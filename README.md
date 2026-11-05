[简体中文](README.zh.md)

# DLC Manager

Manage optional Minecraft content in-game or from a separate desktop window. Choose mods, resource packs, data packs, and TACZ gun packs, apply presets, download missing content, and keep your modpack up to date.

## Features

- Browse DLC by category, group, or search, and enable or disable individual items or groups.
- Check missing dependencies, conflicting content, and actions that need confirmation.
- Switch between content combinations using modpack presets.
- Download optional and required resources, and import and manage expansion packs.
- Read notices, descriptions, project links, and update notes.
- Manage configured servers from the game or the external manager.
- Edit `.dc` configuration with companion tools, or preview files in macOS Finder.

Available content and controls depend on the modpack configuration. Follow the prompts to restart the game or reload resources after applying changes.

## Supported Minecraft versions

| Minecraft | Loaders |
| --- | --- |
| 1.18.2 | Forge |
| 1.19.2 | Forge |
| 1.20.1 | Fabric, Forge |
| 1.21.1 | Fabric, NeoForge |
| 26.1.x | Fabric, NeoForge |

Choose a download matching both your Minecraft version and loader.

## Install and get started

1. Download the matching mod from [Releases](https://github.com/CoZooo/DLC-Manager/releases). Install Architectury API, ApricityUI on the client, and Fabric API for Fabric. Default builds also need the appropriate Kotlin support for their loader; check the release notes for the exact dependencies.
2. Put the mod file in your instance's `mods` folder and start the game.
3. Open **DLC Manager** from the main menu.
4. Select the content you want, resolve dependency or conflict prompts, and click **Apply**.
5. Restart or reload resources when prompted.

If your modpack already includes DLC Manager, start at step 3. You can also open the manager in-game with `/dlcmanager`. TACZ gun pack management requires TACZ.

## Add your own content

Create a `DLC` folder in your instance directory and place optional mod files in it, then open the manager. The instance directory is the folder containing `mods`, `config`, and your other game files.

Modpack authors can add `.dc` files for descriptions, download sources, presets, and notices. Use [DC Editor](tools/dc-editor/README.md) to create configurations, or edit existing files with one of the plugins below.

## Companion tools

- [DLC External Manager](tools/dlc-javafx-manager/README.md): manage local instances and servers in a separate window.
- [DC Editor](tools/dc-editor/README.md): edit configurations using forms or text, preview notices, and export files.
- [VS Code extension](tools/editor/vscode-dc-language/README.md): highlighting, completion, error hints, formatting, and color picking.
- [JetBrains plugin](tools/editor/jetbrains-dc-plugin/README.md): edit `.dc` files in compatible JetBrains IDEs.
- [DC Quick Look](tools/editor/dc-quicklook/README.md): select a `.dc` file in Finder and press Space to preview it.

See each tool's guide for installation. Available downloads are listed in Releases.

## Common questions

**The list is empty.** Check that you opened the correct game instance and that its `DLC` folder contains content. If the modpack uses a different DLC path, follow its configuration.

**An item cannot be enabled.** Resolve the dependency and conflict prompts, or check whether the current preset restricts your selection.

**Nothing changed after applying.** Check for a pending restart or resource reload. Mod changes normally require a restart.

**A preset, notice, or download control is missing.** These features need corresponding modpack content and may be disabled by its configuration.

**I want to manage content without starting the game.** Open [DLC External Manager](tools/dlc-javafx-manager/README.md) and select the instance directory.

## Feedback

Report problems in [Issues](https://github.com/CoZooo/DLC-Manager/issues). Include the DLC Manager version, Minecraft version, loader, reproduction steps, and relevant screenshots or logs. Remove passwords and tokens before sharing files.

## License

Use, modification, and redistribution are subject to the [PolyForm Shield License 1.0.0](LICENSE). The license allows use for permitted purposes, but competing products and services are prohibited. Independently supplied content retains its own terms.