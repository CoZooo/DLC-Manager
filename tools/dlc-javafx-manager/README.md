# DLC Manager — JavaFX

JavaFX desktop DLC Manager with full parity to the in-game client DLC manager.

It runs **entirely outside Minecraft** — no running game instance, no mod loader,
no `net.minecraft` dependency. The in-game validation logic (`.dc` parsing,
scanner, applier, config/state), the encrypted CurseForge API, the Modrinth
client, and the self-developed `.dc` / TCP protocol are all reused:

- The `.dc` engine, `DlcScanner`, `DlcApplier`, `DlcConfig`/`DlcStateConfig`,
  download clients and TCP protocol are ported from `common` (Kotlin → Java),
  stripped of Minecraft coupling. The single in-game coupling point
  (`isResourcePackSelected`, which queried `Minecraft.getInstance()`) is
  replaced with a file-existence check.
- The pure-Java `tools/dlc-external-controller` engine (CurseForge key
  obfuscation + decoder, Modrinth client, HTTP/JSON, downloader, hash util,
  `.dc` remote loader, `DlcTcpClient`) is compiled straight into this module
  via `srcDirs` — no copy.

Two operating modes, mirroring the in-game screens:

- **Local mode** — operate on a user-selected game directory: scan DLC, toggle
  enable/disable, apply changes, manage presets, resolve + download missing
  Remote and Required resources.
- **Remote mode** — connect to a server running the DLC Manager mod over the
  JSON-over-TCP protocol (port 29690): DLC management, Remote/Required,
  hot-update, DLC Pack, RCON, and a remote file manager.

## Build

This is a standalone Gradle build (its own `settings.gradle`). Run it through
the repository-root wrapper with `-p`:

```powershell
.\gradlew.bat -p tools\dlc-javafx-manager build
```

Run the app:

```powershell
.\gradlew.bat -p tools\dlc-javafx-manager run
```

## Native packaging

`jpackage` produces a self-contained application image with a bundled JRE
(requires `JAVA_HOME` pointing at JDK 17+ that ships `jpackage`):

```powershell
.\gradlew.bat -p tools\dlc-javafx-manager jpackageImage
.\gradlew.bat -p tools\dlc-javafx-manager jpackageZip
```

Outputs land in `build/native-image` and `build/native-distributions`.

## Layout

```
src/main/java/com/cozooo/dlc_manager/
  app/            JavaFX app entry and shell
  local/          local workspace tabs
  remote/         remote workspace tabs and connection state
  ui/             JavaFX i18n and style helpers
  core/           .dc engine, scanner, applier, config, paths (ported from common)
  client/         download / remote / required / preset clients (ported from common)
  platform/       CrossPlatformRuntime + DlcFxPlatformProvider
../dlc-external-controller/src/main/java/   reused pure-Java engine (CurseForge, TCP, ...)
```
