# DC Language Support for JetBrains IDEs

[简体中文](README.zh.md)

Edit `.dc` configuration files in JetBrains IDEs with highlighting, completion, error hints, field documentation, formatting, and color picking.

Use is subject to the included license. Charging for this plugin itself requires prior written permission.

## Features

- Highlight sections, fields, strings, numbers, and comments.
- Get section and field suggestions and hints for configuration problems.
- Use **Quick Documentation** to read about supported fields.
- Use **Reformat Code** to tidy up a file.
- Complete `&#RRGGBB` colors and preview or change them with inline color swatches.
- Customize DC syntax colors in the IDE settings.

## Requirements

For IntelliJ IDEA 2023.3 or later, including Community editions, and compatible JetBrains IDEs. Check compatibility with your IDE on the plugin page when installing.

Java 17 or later is required. The IDE's bundled Java can usually be used without extra setup.

## Install

1. Find the JetBrains package in [Releases](https://github.com/CoZooo/DLC-Manager/releases). The 2.0.0 file is `jetbrains-dc-plugin-2.0.0.zip`.
2. Open **Settings → Plugins** in your IDE.
3. In the plugin settings menu, choose **Install Plugin from Disk…**.
4. Select the downloaded `.zip`, restart the IDE if prompted, and open a `.dc` file.

## Use

Open a `.dc` file to use highlighting and completion. Hover over a problem marker to read its message, or invoke **Quick Documentation** on a supported field. Choose **Code → Reformat Code** to format the file.

Type `&` or `&#` to choose a preset color or **Pick custom color...**. Click the swatch beside an existing `&#RRGGBB` value to replace it with another color.

## Settings

- Adjust syntax colors under **Editor → Color Scheme → DC**.
- Search settings for **DC inline color** to manage inline color hints.
- Java normally needs no extra setup. If you set `JAVA_HOME`, make sure it points to Java 17 or later, then restart the IDE.

## Troubleshooting

**The plugin is incompatible:** Check your IDE version and choose a matching plugin package.

**The file is not recognized as DC:** Check that the plugin is enabled and `.dc` is associated with **DC** in the IDE's file type settings.

**Highlighting works, but completion or diagnostics do not:** Read any **DC Language Server** notification, check the Java version, and reopen the project.

**Color swatches are missing:** Enable **DC inline color** in settings and use the complete `&#RRGGBB` format.

For other problems, [open an issue](https://github.com/CoZooo/DLC-Manager/issues) with the plugin version, IDE name and version, and a small configuration that reproduces the problem. Remove passwords, tokens, and private information before sharing it.
