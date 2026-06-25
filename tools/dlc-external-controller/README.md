# DLC External Controller

Pure Java external controller for DLC-Manager native mod synchronization.

The tool implements scheme B only:

- Reads `DLC/remote/**/*.dc` as the modpack truth source.
- Compares remote `file_name` entries with the local `mods` directory.
- Resolves downloads from CurseForge, Modrinth, and direct URLs using DLC-Manager-compatible priority order.
- Talks to the DLC-Manager server over the same JSON line TCP protocol.
- Uploads local mod changes with `file_upload_*`, and calls `set` plus `apply` when requested.

## Build

```powershell
.\gradlew.bat --settings-file "$PWD\tools\dlc-external-controller\settings.gradle" --project-dir "$PWD\tools\dlc-external-controller" build
```

or through the repository root task:

```powershell
.\gradlew.bat externalControllerBuild
```

## Usage

Preview only:

```powershell
.\gradlew.bat --settings-file "$PWD\tools\dlc-external-controller\settings.gradle" --project-dir "$PWD\tools\dlc-external-controller" run --args="preview --game-dir C:\\path\\to\\minecraft"
```

Apply downloads and server sync:

```powershell
.\gradlew.bat --settings-file "$PWD\tools\dlc-external-controller\settings.gradle" --project-dir "$PWD\tools\dlc-external-controller" run --args="apply --game-dir C:\\path\\to\\minecraft --host 127.0.0.1 --port 29690 --password secret"
```

Connection test:

```powershell
.\gradlew.bat --settings-file "$PWD\tools\dlc-external-controller\settings.gradle" --project-dir "$PWD\tools\dlc-external-controller" run --args="test-server --host 127.0.0.1 --port 29690 --password secret"
```

Deletion is never automatic. To delete a server file, pass one or more explicit remote paths:

```powershell
.\gradlew.bat --settings-file "$PWD\tools\dlc-external-controller\settings.gradle" --project-dir "$PWD\tools\dlc-external-controller" run --args="apply --game-dir C:\\path\\to\\minecraft --host 127.0.0.1 --delete mods/old.jar"
```
