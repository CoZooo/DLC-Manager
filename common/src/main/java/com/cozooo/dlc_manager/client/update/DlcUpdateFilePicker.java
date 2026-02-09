package com.cozooo.dlc_manager.client.update;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DlcUpdateFilePicker {
    private DlcUpdateFilePicker() {
    }

    public static Path pickUpdateZip(String title) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            PickResult tiny = tryTinyFileDialog(title);
            if (tiny.canceled) {
                return null;
            }
            if (tiny.path != null) {
                return tiny.path;
            }
            if (osName.contains("mac")) {
                PickResult apple = tryAppleScriptFileDialog(title);
                if (apple.canceled) {
                    return null;
                }
                if (apple.path != null) {
                    return apple.path;
                }
                if (!tiny.available && !apple.available) {
                    openUpdateDir();
                } else if (!tiny.canceled && !apple.canceled) {
                    openUpdateDir();
                }
                return null;
            }
            if (!tiny.available) {
                openUpdateDir();
            } else if (!tiny.canceled) {
                openUpdateDir();
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Path pickAnyFile(String title) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            PickResult tiny = tryTinyFileDialogAny(title, "");
            if (tiny.canceled) {
                return null;
            }
            if (tiny.path != null) {
                return tiny.path;
            }
            if (osName.contains("mac")) {
                PickResult apple = tryAppleScriptFileDialogAny(title, null);
                if (apple.canceled) {
                    return null;
                }
                if (apple.path != null) {
                    return apple.path;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Path pickAnyFile(Path defaultDir, String title) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String defaultPath = defaultDir == null ? "" : defaultDir.toString() + File.separator;
            PickResult tiny = tryTinyFileDialogAny(title, defaultPath);
            if (tiny.canceled) {
                return null;
            }
            if (tiny.path != null) {
                return tiny.path;
            }
            if (osName.contains("mac")) {
                PickResult apple = tryAppleScriptFileDialogAny(title, defaultDir);
                if (apple.canceled) {
                    return null;
                }
                if (apple.path != null) {
                    return apple.path;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Path pickSaveFile(String title, String suggestedName) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            PickResult tiny = tryTinySaveDialog(title, suggestedName);
            if (tiny.canceled) {
                return null;
            }
            if (tiny.path != null) {
                return tiny.path;
            }
            if (osName.contains("mac")) {
                PickResult apple = tryAppleScriptSaveDialog(title, suggestedName);
                if (apple.canceled) {
                    return null;
                }
                if (apple.path != null) {
                    return apple.path;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Path pickFolder(String title) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            PickResult tiny = tryTinyFolderDialog(title);
            if (tiny.canceled) {
                return null;
            }
            if (tiny.path != null) {
                return tiny.path;
            }
            if (osName.contains("mac")) {
                PickResult apple = tryAppleScriptFolderDialog(title);
                if (apple.canceled) {
                    return null;
                }
                if (apple.path != null) {
                    return apple.path;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static PickResult tryTinyFileDialog(String title) {
        try {
            Path updateDir = DlcUpdateService.getUpdateDir();
            if (updateDir != null) {
                Files.createDirectories(updateDir);
            }
            String defaultPath = updateDir == null ? "" : updateDir.toString() + File.separator;
            Class<?> stackClass = Class.forName("org.lwjgl.system.MemoryStack");
            Class<?> memUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
            Class<?> pbClass = Class.forName("org.lwjgl.PointerBuffer");
            Class<?> tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Object stack = stackClass.getMethod("stackPush").invoke(null);
            try {
                Object filters = stackClass.getMethod("mallocPointer", int.class).invoke(stack, 1);
                Object utf8Buffer = stackClass.getMethod("UTF8", CharSequence.class).invoke(stack, "*.zip");
                long addr = (long) memUtilClass.getMethod("memAddress", Buffer.class).invoke(null, utf8Buffer);
                pbClass.getMethod("put", long.class).invoke(filters, addr);
                pbClass.getMethod("flip").invoke(filters);
                java.lang.reflect.Method target = null;
                for (java.lang.reflect.Method method : tinyClass.getMethods()) {
                    if (!"tinyfd_openFileDialog".equals(method.getName())) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 5) {
                        continue;
                    }
                    if (CharSequence.class.isAssignableFrom(params[0])
                        && CharSequence.class.isAssignableFrom(params[1])
                        && params[2].isAssignableFrom(pbClass)
                        && CharSequence.class.isAssignableFrom(params[3])) {
                        target = method;
                        break;
                    }
                }
                if (target == null) {
                    return PickResult.unavailable();
                }
                Object allowMulti;
                Class<?> allowType = target.getParameterTypes()[4];
                if (allowType == boolean.class || allowType == Boolean.class) {
                    allowMulti = false;
                } else if (allowType == int.class || allowType == Integer.class) {
                    allowMulti = 0;
                } else {
                    allowMulti = 0;
                }
                Object result = target.invoke(null,
                    title == null ? "" : title,
                    defaultPath,
                    filters,
                    "zip",
                    allowMulti
                );
                if (result instanceof String value && !value.isBlank()) {
                    return PickResult.picked(Path.of(value));
                }
                return PickResult.canceled();
            } finally {
                if (stack instanceof AutoCloseable closeable) {
                    closeable.close();
                } else {
                    stackClass.getMethod("close").invoke(stack);
                }
            }
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryTinyFileDialogAny(String title, String defaultPath) {
        try {
            Class<?> stackClass = Class.forName("org.lwjgl.system.MemoryStack");
            Class<?> pbClass = Class.forName("org.lwjgl.PointerBuffer");
            Class<?> tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Object stack = stackClass.getMethod("stackPush").invoke(null);
            try {
                java.lang.reflect.Method target = null;
                for (java.lang.reflect.Method method : tinyClass.getMethods()) {
                    if (!"tinyfd_openFileDialog".equals(method.getName())) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 5) {
                        continue;
                    }
                    if (CharSequence.class.isAssignableFrom(params[0])
                        && CharSequence.class.isAssignableFrom(params[1])
                        && params[2].isAssignableFrom(pbClass)
                        && CharSequence.class.isAssignableFrom(params[3])) {
                        target = method;
                        break;
                    }
                }
                if (target == null) {
                    return PickResult.unavailable();
                }
                Object allowMulti;
                Class<?> allowType = target.getParameterTypes()[4];
                if (allowType == boolean.class || allowType == Boolean.class) {
                    allowMulti = false;
                } else if (allowType == int.class || allowType == Integer.class) {
                    allowMulti = 0;
                } else {
                    allowMulti = 0;
                }
                Object result = target.invoke(null,
                    title == null ? "" : title,
                    defaultPath == null ? "" : defaultPath,
                    null,
                    null,
                    allowMulti
                );
                if (result instanceof String value && !value.isBlank()) {
                    return PickResult.picked(Path.of(value));
                }
                return PickResult.canceled();
            } finally {
                if (stack instanceof AutoCloseable closeable) {
                    closeable.close();
                } else {
                    stackClass.getMethod("close").invoke(stack);
                }
            }
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryTinySaveDialog(String title, String suggestedName) {
        try {
            String defaultPath = suggestedName == null ? "" : suggestedName;
            Class<?> stackClass = Class.forName("org.lwjgl.system.MemoryStack");
            Class<?> pbClass = Class.forName("org.lwjgl.PointerBuffer");
            Class<?> tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Object stack = stackClass.getMethod("stackPush").invoke(null);
            try {
                java.lang.reflect.Method target = null;
                for (java.lang.reflect.Method method : tinyClass.getMethods()) {
                    if (!"tinyfd_saveFileDialog".equals(method.getName())) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 4) {
                        continue;
                    }
                    if (CharSequence.class.isAssignableFrom(params[0])
                        && CharSequence.class.isAssignableFrom(params[1])
                        && params[2].isAssignableFrom(pbClass)
                        && CharSequence.class.isAssignableFrom(params[3])) {
                        target = method;
                        break;
                    }
                }
                if (target == null) {
                    return PickResult.unavailable();
                }
                Object result = target.invoke(null,
                    title == null ? "" : title,
                    defaultPath,
                    null,
                    null
                );
                if (result instanceof String value && !value.isBlank()) {
                    return PickResult.picked(Path.of(value));
                }
                return PickResult.canceled();
            } finally {
                if (stack instanceof AutoCloseable closeable) {
                    closeable.close();
                } else {
                    stackClass.getMethod("close").invoke(stack);
                }
            }
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryTinyFolderDialog(String title) {
        try {
            Class<?> tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            java.lang.reflect.Method target = null;
            for (java.lang.reflect.Method method : tinyClass.getMethods()) {
                if (!"tinyfd_selectFolderDialog".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }
                if (CharSequence.class.isAssignableFrom(params[0]) && CharSequence.class.isAssignableFrom(params[1])) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                return PickResult.unavailable();
            }
            Object result = target.invoke(null, title == null ? "" : title, "");
            if (result instanceof String value && !value.isBlank()) {
                return PickResult.picked(Path.of(value));
            }
            return PickResult.canceled();
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryAppleScriptFileDialog(String title) {
        try {
            Path updateDir = DlcUpdateService.getUpdateDir();
            String defaultLocation = updateDir == null ? "" : updateDir.toString();
            String safeTitle = escapeAppleScript(title == null ? "Import Update" : title);
            String safeDefault = escapeAppleScript(defaultLocation);
            String script;
            if (safeDefault.isBlank()) {
                script = "POSIX path of (choose file of type {\"zip\"} with prompt \"" + safeTitle + "\")";
            } else {
                script = "POSIX path of (choose file of type {\"zip\"} with prompt \"" + safeTitle
                    + "\" default location POSIX file \"" + safeDefault + "\")";
            }
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exit = process.waitFor();
                if (exit == 0 && line != null && !line.isBlank()) {
                    return PickResult.picked(Path.of(line.trim()));
                }
                return PickResult.canceled();
            }
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryAppleScriptFileDialogAny(String title, Path defaultDir) {
        try {
            String defaultLocation = defaultDir == null ? "" : defaultDir.toString();
            String safeTitle = escapeAppleScript(title == null ? "Select File" : title);
            String safeDefault = escapeAppleScript(defaultLocation);
            String script;
            if (safeDefault.isBlank()) {
                script = "POSIX path of (choose file with prompt \"" + safeTitle + "\")";
            } else {
                script = "POSIX path of (choose file with prompt \"" + safeTitle
                    + "\" default location POSIX file \"" + safeDefault + "\")";
            }
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exit = process.waitFor();
                if (exit == 0 && line != null && !line.isBlank()) {
                    return PickResult.picked(Path.of(line.trim()));
                }
                return PickResult.canceled();
            }
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryAppleScriptSaveDialog(String title, String suggestedName) {
        try {
            String safeTitle = escapeAppleScript(title == null ? "Save File" : title);
            String safeName = escapeAppleScript(suggestedName == null ? "" : suggestedName);
            String script;
            if (safeName.isBlank()) {
                script = "POSIX path of (choose file name with prompt \"" + safeTitle + "\")";
            } else {
                script = "POSIX path of (choose file name with prompt \"" + safeTitle + "\" default name \"" + safeName + "\")";
            }
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return PickResult.picked(Path.of(line.trim()));
                }
            }
            return PickResult.canceled();
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static PickResult tryAppleScriptFolderDialog(String title) {
        try {
            String safeTitle = escapeAppleScript(title == null ? "Select Folder" : title);
            String script = "POSIX path of (choose folder with prompt \"" + safeTitle + "\")";
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exit = process.waitFor();
                if (exit == 0 && line != null && !line.isBlank()) {
                    return PickResult.picked(Path.of(line.trim()));
                }
                return PickResult.canceled();
            }
        } catch (Throwable ignored) {
            return PickResult.unavailable();
        }
    }

    private static String escapeAppleScript(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void openUpdateDir() {
        Path folder = DlcUpdateService.getUpdateDir();
        try {
            if (folder == null) {
                return;
            }
            Files.createDirectories(folder);
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("mac")) {
                new ProcessBuilder("/usr/bin/open", folder.toString()).start();
                return;
            }
            if (osName.contains("win")) {
                new ProcessBuilder("explorer", folder.toString()).start();
                return;
            }
            new ProcessBuilder("xdg-open", folder.toString()).start();
        } catch (Throwable ignored) {
        }
    }

    private static final class PickResult {
        private final Path path;
        private final boolean canceled;
        private final boolean available;

        private PickResult(Path path, boolean canceled, boolean available) {
            this.path = path;
            this.canceled = canceled;
            this.available = available;
        }

        private static PickResult picked(Path path) {
            return new PickResult(path, false, true);
        }

        private static PickResult canceled() {
            return new PickResult(null, true, true);
        }

        private static PickResult unavailable() {
            return new PickResult(null, false, false);
        }
    }
}
