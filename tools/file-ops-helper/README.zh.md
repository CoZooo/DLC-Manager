# DLC FileOps Helper

[English](README.md)

DLC FileOps Helper 为 DLC Manager 及其他 Java 应用提供 Windows 文件延后处理：当前进程退出后执行删除、移动、复制、写入或创建目录，适合处理被游戏占用的文件。DLC Manager 已包含此组件，玩家无需单独安装。

## 构建

需要 JDK 17。在仓库根目录执行：

```sh
./tools/file-ops-helper/build.sh build
```

生成的 JAR 位于 `tools/file-ops-helper/build/libs/`。仅检查时使用 `./tools/file-ops-helper/build.sh check`。

## 使用

先配置实例目录与临时目录，再调用 API：

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

相对路径从 `gameDir` 解析。操作在当前 Java 进程退出后执行；`ok=true` 只表示已成功安排，不表示文件已经修改。使用此功能需要 Windows 和可用的 `powershell.exe`。

常用调用：

| 操作 | API |
| --- | --- |
| 检查支持情况 | `isSupported()` |
| 查看能力与环境 | `getCapabilities()` |
| 删除文件 | `scheduleDelete(List<String> paths)` |
| 移动文件 | `scheduleMove(source, target, replaceExisting)` |
| 复制文件 | `scheduleCopy(source, target, replaceExisting)` |
| 写入文本或字节 | `scheduleWriteText(target, content)` / `scheduleWriteBytes(target, content)` |
| 批量安排操作 | `scheduleOperations(List<Map<String, Object>> operations)` |
| 查看待执行操作 | `getPendingOperations()` |
| 按目标取消操作 | `cancelPendingOperationsByTarget(List<String> paths)` |

`isAvailable()` 和 `isSupported()` 返回布尔值。其他调用返回包含 `ok`、`message` 的结果对象；安排操作的结果另有 `supported`、`operationCount`、`taskFile` 和 `indexFile`。查询待执行操作时读取 `operations`，取消操作时读取 `removedCount`。

`getCapabilities()` 还返回 `modId`、`modName`、`apiVersion`、`supported`、`os`、`pid`、`gameDir`、`taskRoot` 和 `supportedTypes`。`getPendingOperations()` 返回的每项操作包括 `id`、`type`、`path`、`source`、`target`、`replaceExisting`、`contentBase64`、`createdAt` 和 `updatedAt`；其结果同时包含 `supported`、`operationCount`、`taskFile` 和 `indexFile`。取消结果也包含 `supported`、`taskFile` 和 `indexFile`。

批量操作的 `type` 支持 `delete`、`move`、`copy`、`write_text`、`write_bytes` 和 `mkdir`。删除使用 `path`，创建目录使用 `target`，移动和复制使用 `source`、`target`、`replaceExisting`，写入使用 `target` 和 Base64 编码的 `contentBase64`。单项操作建议优先使用对应的便捷方法。

使用、修改和再分发须遵守 [PolyForm Shield License 1.0.0](../../LICENSE)。许可允许在范围内进行使用，但禁止提供竞争产品或服务。
