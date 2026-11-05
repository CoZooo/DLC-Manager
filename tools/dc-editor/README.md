# DC Editor

[简体中文](README.zh.md)

Create and edit `.dc` configurations for DLC Manager. Fill in forms or edit the text directly, then review any problems before exporting.

## Features

- Create content descriptions, remote downloads, required-resource lists, presets, announcements, redirects, updates, version information, main settings, and DLC Pack configurations.
- Switch between forms and text editing.
- Keep several configurations open and edit them separately.
- Preview announcements and find missing or invalid values.
- Export one configuration, or bundle several in a ZIP.
- Choose an English or Chinese interface.

## Get started

Use the web editor linked in the release notes, or get an available desktop version from [Releases](https://github.com/CoZooo/DLC-Manager/releases).

1. Select a configuration type and choose **New Config**, or use **Import .dc config** to open a file.
2. Fill in the form or switch to the text view to make changes directly.
3. Open the validation view and resolve any reported problems. For announcements, use the preview to check the content.
4. Export the `.dc` file. With several files open, you can export the current file or all files.
5. Put the exported file in the appropriate configuration location in your target instance, then use it in DLC Manager.

Browser exports may go to your Downloads folder. They do not automatically replace files in a game instance, so check the filename and save location.

## Troubleshooting

**The file opens as the wrong configuration type:** Select the correct type in the sidebar and check its fields.

**An announcement preview is empty:** Check that it has content or an accessible content link. Online content may also be affected by network or website restrictions.

**Changes do not appear in the game:** Export the file and put it in the correct instance directory. Unexported edits are not applied to the game.

To edit `.dc` files in your usual code editor, try the [VS Code plugin](../editor/vscode-dc-language/README.md) or [JetBrains plugin](../editor/jetbrains-dc-plugin/README.md).

[Back to DLC Manager](../../README.md)

Use, modification, and redistribution of this software are subject to the [PolyForm Shield License 1.0.0](../../LICENSE). The license allows use for permitted purposes, but competing products and services are prohibited.
