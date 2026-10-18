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

Installers include Java, so no separate runtime installation is needed: use `.exe` or `.msi` on Windows, `.dmg` or `.pkg` on macOS, and `.deb` or `.rpm` on Linux. Windows installers let you choose whether to create a desktop shortcut.

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

## Build installers

From the repository root, build both installer formats supported by the current system:

```sh
./gradlew --project-dir tools/dlc-javafx-manager jpackageInstallers
```

`jpackageInstaller` builds only the default format (Windows EXE, macOS DMG, Linux DEB). Individual tasks are `jpackageExe`, `jpackageMsi`, `jpackageDmg`, `jpackagePkg`, `jpackageDeb`, and `jpackageRpm`. The root project also exposes `javafxManagerInstaller` and `javafxManagerInstallers`.

Build on the target operating system and processor architecture using JDK 25. Windows also needs WiX 3.14's `bin` directory on PATH; Linux needs `fakeroot` and `rpm`. The version comes from the main project's `mod_version`, and outputs use `output/<version>/DLC-Manager-<version>-<platform>.<format>`. Installer metadata uses the numeric version without a prerelease suffix; filenames and application JARs retain the full version.

The **Build External Manager Installers** GitHub Actions workflow supports manual builds and builds/uploads installers when a Release is published. It covers Windows x64, Linux x64/ARM64, and macOS Intel/Apple Silicon. macOS builds must pass Developer ID signing, notarization, and ticket stapling before upload. Windows signing is not configured.

### macOS signing and notarization

For local signing, set `MAC_SIGNING_IDENTITY` to the name and team portion shared by your Developer ID Application and Developer ID Installer certificates, for example `Your Name (TEAMID)`. Both certificates must have their private keys in Keychain. Optionally set `MAC_SIGNING_KEYCHAIN` to a custom Keychain path, then run:

```sh
./gradlew --project-dir tools/dlc-javafx-manager jpackageInstallers -PmacSign=true
```

This signs the native libraries inside JavaFX JARs in a separate input copy, then signs the application, bundled Java, and installers. Signed output is placed in `output/<version>/signed/`. Without `-PmacSign=true`, local packaging remains unsigned for distribution.

To notarize, store your credentials with `xcrun notarytool store-credentials <profile>`, set `MAC_NOTARY_PROFILE` to that profile name, and run:

```sh
./gradlew --project-dir tools/dlc-javafx-manager notarizeInstallers -PmacSign=true
```

Use `notarizeDmg` or `notarizePkg` for one format, or `javafxManagerNotarizedInstallers` from the root project for both. A custom notarization Keychain can be supplied through `MAC_NOTARY_KEYCHAIN`. Only an `Accepted` response followed by successful stapling, ticket validation, and Gatekeeper assessment produces output in `output/<version>/notarized/`. Submission results and rejection logs are saved under `build/dlc-javafx-manager/notarization/`.

For GitHub Actions, configure these repository secrets before triggering the workflow:

- `MAC_SIGNING_IDENTITY`: Name and team portion shared by both Developer ID certificates.
- `MAC_CERTIFICATES_P12_BASE64`: Base64-encoded PKCS#12 export containing both Developer ID certificates and their private keys.
- `MAC_CERTIFICATES_PASSWORD`: Password for that export.
- `MAC_NOTARY_KEY_BASE64`: Base64-encoded App Store Connect team API private key (`.p8`).
- `MAC_NOTARY_KEY_ID` and `MAC_NOTARY_ISSUER_ID`: API key ID and issuer ID.

The workflow imports credentials into a temporary Keychain, validates them, signs and notarizes both macOS formats, and removes the temporary credentials even if the build fails. Credential command output is suppressed, decoded private-key content is masked, and raw notarization logs are not uploaded as artifacts. Missing credentials or failed notarization stop the macOS job before installer upload.

Charging for this software itself requires prior written permission. Redistribution must retain the license and provide corresponding source. See [licensing](../../LICENSING.md).
