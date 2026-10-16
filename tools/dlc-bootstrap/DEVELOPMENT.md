# Progress and Troubleshooting

[简体中文](DEVELOPMENT.zh.md)

DLC Manager prepares required resources when Forge or NeoForge starts. The game or dedicated server continues automatically once preparation is complete. See [Installation and use](README.md) for setup instructions.

## View progress

The default loading screen shows download and installation progress. If no progress window is available, check startup log entries prefixed with `[dlc-manager-bootstrap]`. Dedicated servers also show progress in the log.

The first launch may take longer while files download. Wait for preparation to finish; no additional action is needed.

## Installation failed

If a required resource cannot be prepared, startup stops and reports an installation error.

1. Read the startup log to identify the affected resource and reported cause.
2. Check the resource's `.dc` configuration and download address. If the log reports a network or file-access problem, resolve it before retrying.
3. Restart the game or server to try again.

## A required file is missing

Missing required files are restored on the next startup. Existing target files are kept and are not replaced during this preparation step.

If the same file is reported missing again, check its resource configuration and the startup log.

## An update added required resources

Required resources added while the game or server is running are prepared on the next startup. Save your work and restart when you are ready to use them.

Some mods may require another restart after their first installation. Follow the instructions provided by those mods.

## Supported versions

Startup preparation is supported on Forge 1.20.1 and NeoForge 1.21.1, for both clients and dedicated servers. Fabric versions retain their existing required-resource installation flow.

This feature does not load new mods into a running game. See the [main guide](../../README.md) for everyday DLC management, downloads, and updates.

Project licensing and third-party notices are available in [LICENSE](../../LICENSE) and [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
