# DLC FileOps Helper

[简体中文](README.zh.md)

DLC FileOps Helper schedules file operations on Windows for DLC Manager and other Java applications. It deletes, moves, copies, writes files, or creates directories after the current process exits, making it useful for files locked by the game. DLC Manager includes the helper, so players do not need to install it separately.

## Build

JDK 17 is required. Run from the repository root:

```sh
./tools/file-ops-helper/build.sh build
```

The JAR is written to `tools/file-ops-helper/build/libs/`. To run checks only, use `./tools/file-ops-helper/build.sh check`.

## Use

Configure the instance and temporary directories before calling the API:

```java
import com.cozooo.dlc_fileops_helper.FileOpsHelperBootstrap;
import com.cozooo.dlc_fileops_helper.api.FileOpsHelperApi;
import java.nio.file.Path;

Path gameDir = Path.of("example-instance").toAbsolutePath();
FileOpsHelperBootstrap.initialize(gameDir, gameDir.resolve(".dlc-manager/tmp"));

if (FileOpsHelperApi.isSupported()) {
    var result = FileOpsHelperApi.scheduleWriteText("config/example.txt", "updated");
    if (!Boolean.TRUE.equals(result.get("ok"))) {
        throw new IllegalStateException(String.valueOf(result.get("message")));
    }
}
```

Relative paths are resolved from `gameDir`. Operations run after the current Java process exits: `ok=true` means scheduling succeeded, not that the file has already changed. Windows and an available `powershell.exe` are required.

Common calls:

| Operation | API |
| --- | --- |
| Check platform support | `isSupported()` |
| Inspect capabilities and environment | `getCapabilities()` |
| Delete files | `scheduleDelete(List<String> paths)` |
| Move a file | `scheduleMove(source, target, replaceExisting)` |
| Copy a file | `scheduleCopy(source, target, replaceExisting)` |
| Write text or bytes | `scheduleWriteText(target, content)` / `scheduleWriteBytes(target, content)` |
| Schedule a batch | `scheduleOperations(List<Map<String, Object>> operations)` |
| List pending operations | `getPendingOperations()` |
| Cancel operations by target | `cancelPendingOperationsByTarget(List<String> paths)` |

`isAvailable()` and `isSupported()` return booleans. Other calls return a result with `ok` and `message`. Scheduling results also include `supported`, `operationCount`, `taskFile`, and `indexFile`. Read `operations` from a pending-operation query and `removedCount` from a cancellation result.

`getCapabilities()` also returns `modId`, `modName`, `apiVersion`, `supported`, `os`, `pid`, `gameDir`, `taskRoot`, and `supportedTypes`. Each operation from `getPendingOperations()` includes `id`, `type`, `path`, `source`, `target`, `replaceExisting`, `contentBase64`, `createdAt`, and `updatedAt`; the result also contains `supported`, `operationCount`, `taskFile`, and `indexFile`. Cancellation results also include `supported`, `taskFile`, and `indexFile`.

Batch operation `type` values are `delete`, `move`, `copy`, `write_text`, `write_bytes`, and `mkdir`. Deletion uses `path`; directory creation uses `target`; moves and copies use `source`, `target`, and `replaceExisting`; writes use `target` and Base64-encoded `contentBase64`. Prefer the convenience methods for individual operations.

Charging for this software itself requires prior written permission under the [project license](../../LICENSE).
