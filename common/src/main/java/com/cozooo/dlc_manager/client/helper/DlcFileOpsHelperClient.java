package com.cozooo.dlc_manager.client.helper;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.screen.DlcFileOpsHelperDownloadScreen;
import com.cozooo.dlc_manager.client.screen.DlcSingleButtonScreen;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class DlcFileOpsHelperClient {
    public static final String HELPER_MOD_ID = "dlc_fileops_helper";
    public static final String HELPER_API_CLASS = "com.cozooo.dlc_fileops_helper.api.FileOpsHelperApi";
    private static final String HELPER_VERSION = "0.1.0";
    private static final String HELPER_DOWNLOAD_BASE = "https://downloads.cozozjw.cn/files/dlchelper/";

    private DlcFileOpsHelperClient() {
    }

    public enum HelperState {
        NOT_REQUIRED,
        READY,
        MISSING,
        DOWNLOADED_RESTART_REQUIRED,
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
        return isWindows() && "fabric".equalsIgnoreCase(safeTrim(CrossPlatformRuntime.loaderName()));
    }

    public static HelperState detectState() {
        if (!shouldUseHelper()) {
            return HelperState.NOT_REQUIRED;
        }
        if (CrossPlatformRuntime.isModLoaded(HELPER_MOD_ID)) {
            return isApiSupported() ? HelperState.READY : HelperState.UNSUPPORTED;
        }
        return hasDownloadedHelperJar() ? HelperState.DOWNLOADED_RESTART_REQUIRED : HelperState.MISSING;
    }

    public static boolean showInstallPromptIfNeeded(Screen returnScreen, Component title) {
        HelperState state = detectState();
        if (state == HelperState.NOT_REQUIRED || state == HelperState.READY) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (state == HelperState.DOWNLOADED_RESTART_REQUIRED) {
            minecraft.setScreen(new DlcSingleButtonScreen(returnScreen,
                title,
                Component.translatable("dlc_manager.helper.restart_required"),
                Component.translatable("dlc_manager.back")));
            return true;
        }
        if (state == HelperState.UNSUPPORTED) {
            minecraft.setScreen(new DlcSingleButtonScreen(returnScreen,
                title,
                Component.translatable("dlc_manager.helper.unsupported"),
                Component.translatable("dlc_manager.back")));
            return true;
        }
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                minecraft.setScreen(new DlcFileOpsHelperDownloadScreen(returnScreen, title));
            } else {
                minecraft.setScreen(returnScreen);
            }
        }, title,
            Component.translatable("dlc_manager.helper.download_required"),
            Component.translatable("dlc_manager.remote.download"),
            Component.translatable("dlc_manager.back")));
        return true;
    }

    public static String helperDownloadUrl() {
        return HELPER_DOWNLOAD_BASE + helperFileName();
    }

    public static String helperFileName() {
        String loader = normalizeLoaderName(CrossPlatformRuntime.loaderName());
        String mcVersion = safeTrim(CrossPlatformRuntime.mcVersion());
        if (loader.isBlank()) {
            loader = "fabric";
        }
        if (mcVersion.isBlank()) {
            mcVersion = "1.20.1";
        }
        return "DLC-FileOps-Helper-" + loader + "-" + mcVersion + "-" + HELPER_VERSION + ".jar";
    }

    public static Path helperDownloadTarget() {
        return DlcPaths.modsDir().resolve(helperFileName());
    }

    public static Path downloadHelper(DlcDownloadService.ProgressListener listener) throws IOException {
        Path modsDir = DlcPaths.modsDir();
        Files.createDirectories(modsDir);
        return DlcDownloadService.download(helperDownloadUrl(), modsDir, helperFileName(), listener, null);
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
            Class<?> apiClass = Class.forName(HELPER_API_CLASS);
            Method getPending = apiClass.getMethod("getPendingOperations");
            Object rawPending = getPending.invoke(null);
            if (!(rawPending instanceof Map<?, ?> pendingMap) || !asBoolean(pendingMap.get("ok"))) {
                return false;
            }
            if (!hasPendingDelete(pendingMap.get("operations"), normalizedTarget)) {
                return false;
            }
            Method cancel = apiClass.getMethod("cancelPendingOperationsByTarget", List.class);
            Object rawCancel = cancel.invoke(null, List.of(normalizedTarget));
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
        if (state == HelperState.MISSING) {
            return FileOpResult.failure("Failed to " + operation + ": FileOps Helper is missing. Download it to mods and restart Minecraft.");
        }
        if (state == HelperState.DOWNLOADED_RESTART_REQUIRED) {
            return FileOpResult.failure("Failed to " + operation + ": FileOps Helper was downloaded but Minecraft must be restarted before retrying.");
        }
        if (state == HelperState.UNSUPPORTED) {
            return FileOpResult.failure("Failed to " + operation + ": FileOps Helper is not supported on this platform.");
        }
        try {
            Class<?> apiClass = Class.forName(HELPER_API_CLASS);
            Method method = apiClass.getMethod(methodName, parameterTypes);
            Object raw = method.invoke(null, args);
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
            Class<?> apiClass = Class.forName(HELPER_API_CLASS);
            Method method = apiClass.getMethod("isSupported");
            Object result = method.invoke(null);
            return result instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasDownloadedHelperJar() {
        Path modsDir = DlcPaths.modsDir();
        if (!Files.isDirectory(modsDir)) {
            return false;
        }
        String prefix = ("DLC-FileOps-Helper-" + normalizeLoaderName(CrossPlatformRuntime.loaderName())
            + "-" + safeTrim(CrossPlatformRuntime.mcVersion()) + "-").toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.list(modsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName() == null ? "" : path.getFileName().toString())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.endsWith(".jar") && name.startsWith(prefix));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String normalizeLoaderName(String loaderName) {
        String loader = safeTrim(loaderName).toLowerCase(Locale.ROOT);
        if (loader.isBlank()) {
            return "fabric";
        }
        return loader;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasPendingDelete(Object operationsRaw, String normalizedTarget) {
        if (!(operationsRaw instanceof Iterable<?> iterable)) {
            return false;
        }
        for (Object entry : iterable) {
            if (!(entry instanceof Map<?, ?> op)) {
                continue;
            }
            String type = safeTrim(asString(op.get("type"))).toLowerCase(Locale.ROOT);
            if (!"delete".equals(type)) {
                continue;
            }
            String target = normalizePath(asString(op.get("path")));
            if (normalizedTarget.equals(target)) {
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
            return Path.of(path).toAbsolutePath().normalize().toString();
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
