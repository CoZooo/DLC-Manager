# Automatic Required Resource Setup

[简体中文](README.zh.md)

DLC Manager downloads and installs configured required resources during startup on Forge 1.20.1 and NeoForge 1.21.1. The game continues after ordinary mods, resource packs, and other files are ready. This works for clients and dedicated servers.

## Build

Run from the repository root:

```sh
./gradlew build --parallel
```

Distribution files for each loader are written to the root `output/<version>/` directory. The root project packages this component and the main mod into one JAR; do not distribute this directory's internal JAR as a separate mod. See [Development](DEVELOPMENT.md) for the two implementations and early loading checks.

## Install

Place the DLC Manager mod file for your Minecraft version and loader in the instance's `mods` directory, and install the dependencies listed in the [main guide](../../README.md).

This feature is included in DLC Manager. No extra Bootstrap installation or launch arguments are needed.

## Use

Modpack authors first configure the DLC roots in `config/dlc_manager/config.dc`, then add required-resource `.dc` files to each root's `required` directory. Players can start the instance normally. The first launch may wait for downloads; later launches check for missing files and restore them.

- Wait for required resources to finish installing before startup continues.
- Existing target files are not replaced at this stage.
- Startup log entries prefixed with `[dlc-manager-bootstrap]` show progress.
- Configuration, download, or installation failures stop startup with a required-resource installation error. Fix the reported issue and restart the instance to retry.
- Required resources added by an update while the game is running are prepared on the next launch.

## Scope

Fabric versions retain their existing required-resource installation flow. Special mods with additional startup requirements may still need another restart after the first installation; follow their own installation instructions.

This component does not load new mods into a running game. See [DLC Manager](../../README.md) for normal management, downloads, and updates.

Charging for this software itself requires prior written permission under the [project license](../../LICENSE).
