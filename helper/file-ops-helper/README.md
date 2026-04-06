# DLC FileOps Helper

主入口类：

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

- 使用 `Map<String, Object>`，便于主项目统一处理结果
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
