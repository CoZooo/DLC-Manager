# DLC FileOps Helper

独立附属模组工程，放在主项目仓库内，但不接入根 `settings.gradle`，因此不会被主项目构建或打包。

用途：

- 提供供主项目反射调用的静态 Java API
- 在 Windows 上将失败后需要重启才能完成的文件操作转交给 PowerShell helper
- 所有 pending file ops 统一存放在 `DLC/tmp/pending_file_ops/index.json`
- 新操作会按目标路径合并，后来的操作会覆盖同目标的旧 pending 操作

反射入口类：

- `com.cozooo.dlc_fileops_helper.api.FileOpsHelperApi`

静态方法：

- `isAvailable()`
- `isSupported()`
- `getCapabilities()`
- `scheduleOperations(List<Map<String, Object>> operations)`
- `scheduleDelete(List<String> paths)`
- `scheduleMove(String source, String target, boolean replaceExisting)`
- `scheduleCopy(String source, String target, boolean replaceExisting)`
- `scheduleWriteText(String target, String content)`
- `scheduleWriteBytes(String target, byte[] content)`
- `getPendingOperations()`
- `cancelPendingOperationsByTarget(List<String> paths)`

返回值：

- 使用 `Map<String, Object>`，便于主项目通过反射直接读取
- 成功时包含 `ok=true`
- 失败时包含 `ok=false` 和 `message`

统一构建命令：

```bash
./helper/file-ops-helper/build.sh build
./helper/file-ops-helper/build.sh check
./helper/file-ops-helper/build.sh clean
```

根目录转调命令：

```bash
./build.sh helperBuild
./build.sh helperCheck
./build.sh helperClean
```
