# DC Language Tools for VS Code

[简体中文](README.zh.md)

Edit `.dc` configuration files in VS Code with highlighting, completion, error hints, field documentation, formatting, and color picking.

Use is subject to the included license. Charging for this plugin itself requires prior written permission.

## Features

- Highlight sections, fields, strings, numbers, and comments.
- Get section and field suggestions, with hints for configuration problems.
- Hover over supported fields to read their documentation.
- Use **Format Document** to tidy up a file.
- Preview `&#RRGGBB` colors and choose preset or custom colors.
- Insert common `.dc` snippets and automatically pair brackets and quotes.

## Requirements

- VS Code 1.90 or later.
- Java 17 or later.

## Install

1. Find the VS Code package in [Releases](https://github.com/CoZooo/DLC-Manager/releases). The 2.0.0 file is `vscode-dc-language-2.0.0.vsix`.
2. Open the Extensions view in VS Code and choose **Install from VSIX…** from its menu.
3. Select the downloaded `.vsix` file and reload the window if prompted.
4. Open a `.dc` file and check that the language mode in the status bar is **DC**.

## Use

Open a `.dc` file and start editing. Hover over a highlighted problem to see its message, or open the Problems panel for the full list. Run **Format Document** from the Command Palette when you want to format the file.

Type `&` or `&#` where you want a color, then choose a preset or **Pick custom color...**. You can also run **DC: Pick Hex Color** from the Command Palette, or adjust an existing `&#RRGGBB` value with the editor's color controls.

## Settings

Search for `dc` in VS Code settings:

| Setting | Purpose |
| --- | --- |
| `dc.javaPath` | Path to the Java executable. Leave empty to find Java automatically. |
| `dc.profile` | Configuration type used for validation. The default, `auto`, detects it automatically and normally needs no change. |

Reload the VS Code window after changing either setting.

## Troubleshooting

**No highlighting:** Check that the extension is enabled, the file ends in `.dc`, and its language mode is **DC**.

**Highlighting works, but completion or error hints do not:** Check that Java 17 or later is installed. Set `dc.javaPath` if needed, then reload the window. Select **DC Language Server** in the Output panel for details.

**The color picker does not open:** Open a `.dc` file before running **DC: Pick Hex Color**. If an input box appears, enter `RRGGBB`, `#RRGGBB`, or `&#RRGGBB`.

For other problems, [open an issue](https://github.com/CoZooo/DLC-Manager/issues) with your extension and VS Code versions and a small configuration that reproduces the problem. Remove passwords, tokens, and private information before sharing it.
