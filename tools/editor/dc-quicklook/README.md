# DC Quick Look

[简体中文](README.zh.md)

Select a `.dc` file in macOS Finder and press Space to preview it with syntax highlighting.

Use is subject to the license supplied with the release. Charging for this plugin itself requires prior written permission.

## Features

- Highlight sections, fields, strings, numbers, booleans, and comments.
- Preview nested configurations, arrays, and multiline text.
- Use light or dark appearance and select or copy preview text.
- Keep the original file unchanged and continue using your preferred editor.

## Requirements

macOS 13 or later, on Apple Silicon or Intel Macs.

## Install

1. Download `DCQuickLookHost-2.0.0-distribution.zip` from [Releases](https://github.com/CoZooo/DLC-Manager/releases).
2. Extract the package, then open `DCQuickLookHost-2.0.0-macos-notarized.zip` inside it. Move the extracted `DCQuickLookHost.app` to Applications.
3. Open the app once. It closes automatically after setup; this is expected.
4. Select a `.dc` file in Finder and press Space.

If previews are not enabled, search System Settings for Quick Look extensions and enable DC Quick Look.

## Use

Select a file in Finder and press Space to open its preview. Press Space or Esc to close it. Open the file in your usual editor when you want to make changes.

Files larger than 2 MiB show only the beginning, with a notice. Open the file in an editor to read the rest.

## Troubleshooting

**The app opens without a window:** This is expected. The app enables previews; everyday use starts in Finder with the Space key.

**Previews do not appear:** Check that the app is in Applications and has been opened once, then check that its Quick Look extension is enabled. Close and reopen the preview. If needed, sign out of macOS and sign in again.

**The file cannot be read:** Check that it opens in an editor. Download cloud-stored files to your Mac before previewing them.

**Only part of the file appears:** The file exceeds the preview limit. Use an editor to read it in full.

To uninstall, disable the extension in System Settings and move the app to the Trash.

For other problems, [open an issue](https://github.com/CoZooo/DLC-Manager/issues) with your app and macOS versions and a small file that reproduces the problem. Remove passwords, tokens, and private information before sharing it.
