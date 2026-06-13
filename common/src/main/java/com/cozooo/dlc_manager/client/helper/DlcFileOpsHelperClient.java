package com.cozooo.dlc_manager.client.helper;

import com.cozooo.dlc_fileops_helper.FileOpsHelperBootstrap;
import com.cozooo.dlc_fileops_helper.api.FileOpsHelperApi;
import com.cozooo.dlc_manager.core.DlcPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DlcFileOpsHelperClient {
    static {
        FileOpsHelperBootstrap.initialize(DlcPaths.gameDir(), DlcPaths.tmpRoot());
    }

    private DlcFileOpsHelperClient() {
    }

    public enum HelperState {
        NOT_REQUIRED,
        READY,
        UNSUPPORTED
    }

    public record FileOpResult(boolean ok, boolean helperScheduled, String message) {
        public static FileOpResult success(boolean helperScheduled) {
            return new FileOpResult(true, helperScheduled, "");
        }

        public static FileOpResult failure(String message) {
            return new FileOpResult(false, false, message == null ? "" : message);
        }
    }

    public static boolean shouldUseHelper() {
        return isWindows();
    }

    public static HelperState detectState() {
        if (!shouldUseHelper()) {
            return HelperState.NOT_REQUIRED;
        }
        return isApiSupported() ? HelperState.READY : HelperState.UNSUPPORTED;
    }

    public static FileOpResult scheduleDelete(Path path) {
        if (path == null) {
            return FileOpResult.failure("Failed to delete: missing target path");
        }
        return invokeSchedule("scheduleDelete", new Class<?>[] {List.class}, List.of(path.toString()), "delete");
    }

    public static FileOpResult scheduleCopy(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return FileOpResult.failure("Failed to copy: missing source or target path");
        }
        return invokeSchedule("scheduleCopy",
            new Class<?>[] {String.class, String.class, boolean.class},
            source.toString(), target.toString(), replaceExisting, "copy");
    }

    public static FileOpResult scheduleMove(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return FileOpResult.failure("Failed to move: missing source or target path");
        }
        return invokeSchedule("scheduleMove",
            new Class<?>[] {String.class, String.class, boolean.class},
            source.toString(), target.toString(), replaceExisting, "move");
    }

    public static FileOpResult scheduleWriteText(Path target, String content) {
        if (target == null) {
            return FileOpResult.failure("Failed to write: missing target path");
        }
        return invokeSchedule("scheduleWriteText",
            new Class<?>[] {String.class, String.class},
            target.toString(), content == null ? "" : content, "write");
    }

    public static boolean cancelPendingDelete(Path target) {
        return cancelPendingOperation(target, "delete");
    }

    public static boolean cancelPendingOperation(Path target, String requiredType) {
        if (target == null) {
            return false;
        }
        HelperState state = detectState();
        if (state != HelperState.READY) {
            return false;
        }
        String normalizedTarget = normalizePath(target);
        if (normalizedTarget.isBlank()) {
            return false;
        }
        try {
            Object rawPending = FileOpsHelperApi.getPendingOperations();
            if (!(rawPending instanceof Map<?, ?> pendingMap) || !asBoolean(pendingMap.get("ok"))) {
                return false;
            }
            if (!hasPendingOperation(pendingMap.get("operations"), normalizedTarget, requiredType)) {
                return false;
            }
            Object rawCancel = FileOpsHelperApi.cancelPendingOperationsByTarget(List.of(normalizedTarget));
            if (!(rawCancel instanceof Map<?, ?> cancelMap) || !asBoolean(cancelMap.get("ok"))) {
                return false;
            }
            Object removedCount = cancelMap.get("removedCount");
            if (removedCount instanceof Number number) {
                return number.intValue() > 0;
            }
            return asInt(removedCount) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static FileOpResult invokeSchedule(String methodName, Class<?>[] parameterTypes, Object[] args, String operation) {
        HelperState state = detectState();
        if (state == HelperState.NOT_REQUIRED) {
            return FileOpResult.failure("Failed to " + operation + ": helper is not enabled on this platform");
        }
        if (state == HelperState.UNSUPPORTED) {
            return FileOpResult.failure("Failed to " + operation + ": FileOps Helper is not supported on this platform.");
        }
        try {
            Object raw = switch (methodName) {
                case "scheduleDelete" -> FileOpsHelperApi.scheduleDelete(asStringList(args[0]));
                case "scheduleCopy" -> FileOpsHelperApi.scheduleCopy((String) args[0], (String) args[1], (Boolean) args[2]);
                case "scheduleMove" -> FileOpsHelperApi.scheduleMove((String) args[0], (String) args[1], (Boolean) args[2]);
                case "scheduleWriteText" -> FileOpsHelperApi.scheduleWriteText((String) args[0], (String) args[1]);
                default -> null;
            };
            if (!(raw instanceof Map<?, ?> map)) {
                return FileOpResult.failure("Failed to " + operation + ": invalid helper response");
            }
            boolean ok = asBoolean(map.get("ok"));
            String message = asString(map.get("message"));
            if (ok) {
                return FileOpResult.success(true);
            }
            if (message == null || message.isBlank()) {
                message = "Failed to " + operation + ": helper scheduling failed";
            } else if (!message.startsWith("Failed to ")) {
                message = "Failed to " + operation + ": " + message;
            }
            return FileOpResult.failure(message);
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            if (message == null || message.isBlank()) {
                message = "helper API unavailable";
            }
            return FileOpResult.failure("Failed to " + operation + ": " + message);
        }
    }

    private static FileOpResult invokeSchedule(String methodName, Class<?>[] parameterTypes,
                                               Object arg1, String operation) {
        return invokeSchedule(methodName, parameterTypes, new Object[] {arg1}, operation);
    }

    private static FileOpResult invokeSchedule(String methodName, Class<?>[] parameterTypes,
                                               Object arg1, Object arg2, boolean arg3, String operation) {
        return invokeSchedule(methodName, parameterTypes, new Object[] {arg1, arg2, arg3}, operation);
    }

    private static FileOpResult invokeSchedule(String methodName, Class<?>[] parameterTypes,
                                               Object arg1, Object arg2, String operation) {
        return invokeSchedule(methodName, parameterTypes, new Object[] {arg1, arg2}, operation);
    }

    private static boolean isApiSupported() {
        try {
            return FileOpsHelperApi.isSupported();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasPendingOperation(Object operationsRaw, String normalizedTarget, String requiredType) {
        if (!(operationsRaw instanceof Iterable<?> iterable)) {
            return false;
        }
        String normalizedType = safeTrim(requiredType).toLowerCase(Locale.ROOT);
        for (Object entry : iterable) {
            if (!(entry instanceof Map<?, ?> op)) {
                continue;
            }
            String type = safeTrim(asString(op.get("type"))).toLowerCase(Locale.ROOT);
            if (!normalizedType.isBlank() && !normalizedType.equals(type)) {
                continue;
            }
            String path = normalizePath(asString(op.get("path")));
            String source = normalizePath(asString(op.get("source")));
            String target = normalizePath(asString(op.get("target")));
            if (normalizedTarget.equals(path) || normalizedTarget.equals(source) || normalizedTarget.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(Path path) {
        if (path == null) {
            return "";
        }
        try {
            return path.toAbsolutePath().normalize().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            Path normalized = Path.of(path);
            if (!normalized.isAbsolute()) {
                normalized = DlcPaths.gameDir().resolve(normalized);
            }
            return normalized.toAbsolutePath().normalize().toString();
        } catch (Throwable ignored) {
            return safeTrim(path);
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(asString(item));
        }
        return result;
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

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
