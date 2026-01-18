package com.cozooo.dlc_manager.client.update;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class DlcUpdateFilePicker {
    private DlcUpdateFilePicker() {
    }

    public static Path pickUpdateZip(String title) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("mac")) {
                String picked = tryAppleScriptFileDialog(title);
                if (picked != null && !picked.isBlank()) {
                    return Path.of(picked);
                }
                return null;
            }
            return tryAwtFileDialog(title);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path tryAwtFileDialog(String title) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return null;
            }
            AtomicReference<Path> result = new AtomicReference<>();
            Runnable task = () -> {
                FileDialog dialog = new FileDialog((java.awt.Frame) null, title == null ? "" : title, FileDialog.LOAD);
                dialog.setFilenameFilter((dir, name) -> name != null && name.toLowerCase().endsWith(".zip"));
                Path updateDir = DlcUpdateService.getUpdateDir();
                if (updateDir != null) {
                    dialog.setDirectory(updateDir.toString());
                }
                dialog.setVisible(true);
                String file = dialog.getFile();
                if (file != null && !file.isBlank()) {
                    String dir = dialog.getDirectory();
                    if (dir == null || dir.isBlank()) {
                        result.set(Path.of(file));
                    } else {
                        result.set(Path.of(dir, file));
                    }
                }
                dialog.dispose();
            };
            if (EventQueue.isDispatchThread()) {
                task.run();
            } else {
                EventQueue.invokeAndWait(task);
            }
            return result.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String tryAppleScriptFileDialog(String title) {
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
                    return line.trim();
                }
            }
        } catch (Throwable ignored) {
            // Best-effort.
        }
        return null;
    }

    private static String escapeAppleScript(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void openFolder(Path folder) {
        try {
            if (folder == null) {
                return;
            }
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }
}
