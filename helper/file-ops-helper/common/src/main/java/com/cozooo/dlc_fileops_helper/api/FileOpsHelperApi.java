package com.cozooo.dlc_fileops_helper.api;

import com.cozooo.dlc_fileops_helper.FileOpsHelperConstants;
import com.cozooo.dlc_fileops_helper.core.HelperPaths;
import com.cozooo.dlc_fileops_helper.core.PendingFileOpsHelper;
import com.cozooo.dlc_fileops_helper.core.PendingFileOpsHelper.CancelResult;
import com.cozooo.dlc_fileops_helper.core.PendingFileOpsHelper.PendingFileOp;
import com.cozooo.dlc_fileops_helper.core.PendingFileOpsHelper.PendingIndexResult;
import com.cozooo.dlc_fileops_helper.core.PendingFileOpsHelper.ScheduleResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FileOpsHelperApi {
    private FileOpsHelperApi() {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static boolean isSupported() {
        return PendingFileOpsHelper.isSupported();
    }

    public static Map<String, Object> getCapabilities() {
        LinkedHashMap<String, Object> data = baseResult(true, "");
        data.put("modId", FileOpsHelperConstants.MOD_ID);
        data.put("modName", FileOpsHelperConstants.MOD_NAME);
        data.put("apiVersion", FileOpsHelperConstants.API_VERSION);
        data.put("supported", PendingFileOpsHelper.isSupported());
        data.put("os", System.getProperty("os.name", ""));
        data.put("pid", ProcessHandle.current().pid());
        data.put("gameDir", HelperPaths.gameDir().toString());
        data.put("taskRoot", HelperPaths.pendingOpsRoot().toString());
        data.put("supportedTypes", PendingFileOpsHelper.supportedTypes());
        return data;
    }

    public static Map<String, Object> scheduleOperations(List<Map<String, Object>> operations) {
        List<PendingFileOp> mapped = new ArrayList<>();
        if (operations != null) {
            for (Map<String, Object> raw : operations) {
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                PendingFileOp op = new PendingFileOp();
                op.type = asString(raw.get("type"));
                op.path = resolvePathString(raw.get("path"));
                op.source = resolvePathString(raw.get("source"));
                op.target = resolvePathString(raw.get("target"));
                op.replaceExisting = asBoolean(raw.get("replaceExisting"));
                op.contentBase64 = resolveContentBase64(raw);
                mapped.add(op);
            }
        }
        return toResult(PendingFileOpsHelper.schedule(mapped));
    }

    public static Map<String, Object> scheduleDelete(List<String> paths) {
        List<PendingFileOp> operations = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                PendingFileOp op = new PendingFileOp();
                op.type = "delete";
                op.path = resolvePathString(path);
                operations.add(op);
            }
        }
        return toResult(PendingFileOpsHelper.schedule(operations));
    }

    public static Map<String, Object> scheduleMove(String source, String target, boolean replaceExisting) {
        PendingFileOp op = new PendingFileOp();
        op.type = "move";
        op.source = resolvePathString(source);
        op.target = resolvePathString(target);
        op.replaceExisting = replaceExisting;
        return toResult(PendingFileOpsHelper.schedule(List.of(op)));
    }

    public static Map<String, Object> scheduleCopy(String source, String target, boolean replaceExisting) {
        PendingFileOp op = new PendingFileOp();
        op.type = "copy";
        op.source = resolvePathString(source);
        op.target = resolvePathString(target);
        op.replaceExisting = replaceExisting;
        return toResult(PendingFileOpsHelper.schedule(List.of(op)));
    }

    public static Map<String, Object> scheduleWriteText(String target, String content) {
        PendingFileOp op = new PendingFileOp();
        op.type = "write_text";
        op.target = resolvePathString(target);
        op.contentBase64 = Base64.getEncoder().encodeToString(
            (content == null ? "" : content).getBytes(StandardCharsets.UTF_8)
        );
        return toResult(PendingFileOpsHelper.schedule(List.of(op)));
    }

    public static Map<String, Object> scheduleWriteBytes(String target, byte[] content) {
        PendingFileOp op = new PendingFileOp();
        op.type = "write_bytes";
        op.target = resolvePathString(target);
        op.contentBase64 = Base64.getEncoder().encodeToString(content == null ? new byte[0] : content);
        return toResult(PendingFileOpsHelper.schedule(List.of(op)));
    }

    public static Map<String, Object> getPendingOperations() {
        PendingIndexResult result = PendingFileOpsHelper.getPendingOperations();
        LinkedHashMap<String, Object> data = baseResult(true, "");
        data.put("supported", PendingFileOpsHelper.isSupported());
        if (result.indexFile != null) {
            data.put("indexFile", result.indexFile.toString());
            data.put("taskFile", result.indexFile.toString());
        }
        data.put("operationCount", result.operations.size());
        List<Map<String, Object>> operations = new ArrayList<>();
        for (PendingFileOp op : result.operations) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("id", op.id);
            item.put("type", op.type);
            item.put("path", op.path);
            item.put("source", op.source);
            item.put("target", op.target);
            item.put("replaceExisting", op.replaceExisting);
            item.put("contentBase64", op.contentBase64);
            item.put("createdAt", op.createdAt);
            item.put("updatedAt", op.updatedAt);
            operations.add(item);
        }
        data.put("operations", operations);
        return data;
    }

    public static Map<String, Object> cancelPendingOperationsByTarget(List<String> paths) {
        List<Path> resolved = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                try {
                    Path resolvedPath = resolvePath(path);
                    if (resolvedPath != null) {
                        resolved.add(resolvedPath);
                    }
                } catch (InvalidPathException ignored) {
                }
            }
        }
        return toCancelResult(PendingFileOpsHelper.cancelOperationsByTarget(resolved));
    }

    private static Map<String, Object> toResult(ScheduleResult result) {
        LinkedHashMap<String, Object> data = baseResult(result.ok, result.message);
        data.put("supported", PendingFileOpsHelper.isSupported());
        data.put("operationCount", result.operationCount);
        if (result.taskFile != null) {
            data.put("taskFile", result.taskFile.toString());
            data.put("indexFile", result.taskFile.toString());
        }
        return data;
    }

    private static Map<String, Object> toCancelResult(CancelResult result) {
        LinkedHashMap<String, Object> data = baseResult(result.ok, result.message);
        data.put("supported", PendingFileOpsHelper.isSupported());
        data.put("removedCount", result.removedCount);
        if (result.indexFile != null) {
            data.put("taskFile", result.indexFile.toString());
            data.put("indexFile", result.indexFile.toString());
        }
        return data;
    }

    private static LinkedHashMap<String, Object> baseResult(boolean ok, String message) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("ok", ok);
        data.put("message", message == null ? "" : message);
        return data;
    }

    private static String resolveContentBase64(Map<String, Object> raw) {
        String contentBase64 = asString(raw.get("contentBase64"));
        if (contentBase64 != null && !contentBase64.isBlank()) {
            return contentBase64;
        }
        Object content = raw.get("content");
        if (content instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        String text = asString(content);
        if (text == null) {
            return null;
        }
        String type = asString(raw.get("type"));
        if (type != null && type.toLowerCase(Locale.ROOT).contains("bytes")) {
            return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String resolvePathString(Object value) {
        Path path = resolvePath(asString(value));
        return path == null ? null : path.toString();
    }

    private static Path resolvePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(raw);
            if (!path.isAbsolute()) {
                path = HelperPaths.gameDir().resolve(path);
            }
            return path.toAbsolutePath().normalize();
        } catch (InvalidPathException ignored) {
            return null;
        }
    }
}
