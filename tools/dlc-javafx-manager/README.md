# DLC External Manager

[简体中文](README.zh.md)

Manage DLC in a standalone window without starting Minecraft. Work with local instances or connect to a server that has DLC Manager's management service enabled.

## Features

- Browse, search, group, enable, and disable local DLC.
- Download missing content, apply presets, and manage DLC Packs and updates.
- Read announcements and adjust an instance's DLC Manager settings.
- Manage remote DLC, downloads, presets, DLC Packs, updates, and files.
- Choose an English or Chinese interface.

## Download and launch

Choose an External Manager download for your operating system and processor from [Releases](https://github.com/CoZooo/DLC-Manager/releases). Available platforms depend on the release.

For an app bundle, extract it and open the app. For a `.jar`, install Java 25 and open the file with Java. If double-clicking does not work, run this in the download directory:

```sh
java -jar DLC-Manager-<version>-<platform>.jar
```

Replace the filename with the one you downloaded. The manager runs separately; it does not go in the `mods` folder.

## Manage a local instance

1. Open the manager and select the game instance directory, or drag the directory into the window.
2. Select the instance root containing folders such as `mods` and `config`, rather than only its `DLC` subfolder.
3. Browse the DLC list and change what is enabled or apply a preset.
4. Apply the changes and follow any dependency, conflict, or confirmation prompts.
5. Start the game. If it is already running, restart it or reload resources as prompted.

Avoid changing the same instance in the in-game manager and External Manager at the same time.

## Manage a server

On the server page, enter the server address, DLC Manager management port, and password, then connect. The server must have DLC Manager installed and its management service enabled. Ask its administrator for the management port.

Available actions depend on the server settings. Before updating, deleting files, or restarting, check that you are connected to the intended server.

## Troubleshooting

**Local content is missing:** Select the instance root again and check that instance's configured DLC path.

**The server connection fails:** Check the address, management port, password, and whether the server accepts connections. The game port and management port may be different.

**The download will not open:** Check that it matches your operating system and processor. The `.jar` version requires Java 25.

[Back to DLC Manager](../../README.md)

Charging for this software itself requires prior written permission. Redistribution must retain the license and provide corresponding source. See [licensing](../../LICENSING.md).
