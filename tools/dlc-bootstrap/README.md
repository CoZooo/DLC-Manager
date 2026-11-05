# Automatic Required Resource Setup

[简体中文](README.zh.md)

DLC Manager downloads and installs configured required resources during startup on Forge 1.20.1 and NeoForge 1.21.1. The game continues after ordinary mods, resource packs, and other files are ready. This works for clients and dedicated servers.

## Build

Run from the repository root:

```sh
./gradlew build --parallel
```

Use the file matching your Minecraft version and loader from `output/<version>/`. See [Progress and troubleshooting](DEVELOPMENT.md) for help with resource preparation.

## Install

Place the DLC Manager mod file for your Minecraft version and loader in the instance's `mods` directory, and install the dependencies listed in the [main guide](../../README.md).

This feature is included in DLC Manager. No extra Bootstrap installation or launch arguments are needed.

## Use

Modpack authors first configure the DLC roots in `config/dlc_manager/config.dc`, then add required-resource `.dc` files to each root's `required` directory. Players can start the instance normally. The first launch may wait for downloads; later launches check for missing files and restore them.

- Wait for required resources to finish installing before startup continues.
- Existing target files are not replaced at this stage.
- On Forge and NeoForge clients, the default loading screen shows download and installation progress.
- If no progress window is available, check startup log entries prefixed with `[dlc-manager-bootstrap]`. Dedicated servers also show progress in the log.
- Configuration, download, or installation failures stop startup with a required-resource installation error. Fix the reported issue and restart the instance to retry.
- Required resources added by an update while the game is running are prepared on the next launch.

## Scope

Fabric versions retain their existing required-resource installation flow. Special mods with additional startup requirements may still need another restart after the first installation; follow their own installation instructions.

This component does not load new mods into a running game. See [DLC Manager](../../README.md) for normal management, downloads, and updates.

Use, modification, and redistribution are subject to the [PolyForm Shield License 1.0.0](../../LICENSE). The license allows use for permitted purposes, but competing products and services are prohibited.
