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
- 所有方法都会返回 `ok` 和 `message`
- `ok=true` 表示本次 helper 调用成功
- `ok=false` 表示本次 helper 调用失败，失败原因写在 `message`

返回结构：

- 通用字段：
  - `ok: boolean`
  - `message: String`
- `getCapabilities()` 额外返回：
  - `modId`
  - `modName`
  - `apiVersion`
  - `supported`
  - `os`
  - `pid`
  - `gameDir`
  - `taskRoot`
  - `supportedTypes`
- `scheduleOperations(...)`、`scheduleDelete(...)`、`scheduleMove(...)`、`scheduleCopy(...)`、`scheduleWriteText(...)`、`scheduleWriteBytes(...)` 额外返回：
  - `supported`
  - `operationCount`
  - `taskFile`
  - `indexFile`
- `getPendingOperations()` 额外返回：
  - `supported`
  - `operationCount`
  - `taskFile`
  - `indexFile`
  - `operations`
- `operations` 中每个元素包含：
  - `id`
  - `type`
  - `path`
  - `source`
  - `target`
  - `replaceExisting`
  - `contentBase64`
  - `createdAt`
  - `updatedAt`
- `cancelPendingOperationsByTarget(...)` 额外返回：
  - `supported`
  - `removedCount`
  - `taskFile`
  - `indexFile`