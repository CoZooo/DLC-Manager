package com.cozooo.dlc_manager.author;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.stage.FileChooser;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;

/** Hosts the embedded DC Editor frontend and its native file operations. */
public final class DlcFxDcEditorLauncher {
    private static final Gson GSON = new Gson();

    private DlcFxDcEditorLauncher() {
    }

    public static String indexUrl(String locale) {
        var index = DlcFxDcEditorLauncher.class.getResource("/author-tools/dc-editor/index.html");
        if (index == null) {
            return null;
        }
        String normalized = "en_us".equalsIgnoreCase(locale) ? "en_us" : "zh_cn";
        return index.toExternalForm() + "?embedded=1&locale=" + normalized;
    }

    @SuppressWarnings("removal")
    public static void installBridge(WebEngine engine, java.util.function.Supplier<Scene> scene, String locale) {
        engine.getLoadWorker().stateProperty().addListener((observable, previous, state) -> {
            if (state != javafx.concurrent.Worker.State.SUCCEEDED) {
                return;
            }
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("dlcEditorHost", new FileHost(scene));
            engine.executeScript("document.documentElement.classList.add('embedded-editor')");
            setLocale(engine, locale);
        });
    }

    public static void setLocale(WebEngine engine, String locale) {
        String normalized = "en_us".equalsIgnoreCase(locale) ? "en_us" : "zh_cn";
        Runnable apply = () -> {
            String script = "(() => {"
                    + "const locale = '" + normalized + "';"
                    + "const apply = () => {"
                    + "const setter = window.__dlcEditorSetLocale;"
                    + "if (typeof setter === 'function') setter(locale);"
                    + "window.dispatchEvent(new CustomEvent('dlc-editor-locale', { detail: locale }));"
                    + "return typeof setter === 'function';"
                    + "};"
                    + "if (apply()) return;"
                    + "let attempts = 0;"
                    + "const timer = window.setInterval(() => {"
                    + "if (apply() || ++attempts >= 40) window.clearInterval(timer);"
                    + "}, 50);"
                    + "})();";
            try {
                engine.executeScript(script);
            } catch (RuntimeException error) {
                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                    throw error;
                }
                return;
            }
        };
        if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED
                || engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.RUNNING) {
            apply.run();
            return;
        }
        javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State> listener = new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends javafx.concurrent.Worker.State> observable,
                                 javafx.concurrent.Worker.State previous,
                                 javafx.concurrent.Worker.State current) {
                if (current != javafx.concurrent.Worker.State.SUCCEEDED) {
                    return;
                }
                engine.getLoadWorker().stateProperty().removeListener(this);
                apply.run();
            }
        };
        engine.getLoadWorker().stateProperty().addListener(listener);
    }

    public static final class FileHost {
        private final java.util.function.Supplier<Scene> scene;

        public FileHost(java.util.function.Supplier<Scene> scene) {
            this.scene = scene;
        }

        public String openFiles() throws IOException {
            FileChooser chooser = chooser("打开 .dc 配置");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DC Config", "*.dc"));
            List<java.io.File> files = chooser.showOpenMultipleDialog(scene.get().getWindow());
            JsonArray output = new JsonArray();
            if (files != null) {
                for (java.io.File file : files) {
                    JsonObject result = new JsonObject();
                    result.addProperty("name", file.getName());
                    result.addProperty("path", file.toPath().toAbsolutePath().toString());
                    result.addProperty("text", Files.readString(file.toPath()));
                    output.add(result);
                }
            }
            return GSON.toJson(output);
        }

        public String pickFile() {
            FileChooser chooser = chooser("选择文件");
            java.io.File file = chooser.showOpenDialog(scene.get().getWindow());
            if (file == null) {
                return "null";
            }
            JsonObject result = new JsonObject();
            result.addProperty("name", file.getName());
            result.addProperty("path", file.toPath().toAbsolutePath().toString());
            return GSON.toJson(result);
        }

        public String saveFile(String suggestedName, String text, String path) throws IOException {
            FileChooser chooser = chooser("导出 .dc 配置");
            chooser.setInitialFileName(suggestedName);
            if (path != null && !path.isBlank()) {
                Path requested = Path.of(path).toAbsolutePath().normalize();
                if (Files.isDirectory(requested.getParent())) {
                    chooser.setInitialDirectory(requested.getParent().toFile());
                }
                chooser.setInitialFileName(requested.getFileName().toString());
            }
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DC Config", "*.dc"));
            java.io.File file = chooser.showSaveDialog(scene.get().getWindow());
            if (file == null) {
                throw new IOException("用户取消了导出。");
            }
            atomicWrite(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
            return result(file.toPath());
        }

        public String saveArchive(String suggestedName, String base64) throws IOException {
            FileChooser chooser = chooser("导出 ZIP 压缩包");
            chooser.setInitialFileName(suggestedName);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Archive", "*.zip"));
            java.io.File file = chooser.showSaveDialog(scene.get().getWindow());
            if (file == null) {
                throw new IOException("用户取消了导出。");
            }
            atomicWrite(file.toPath(), Base64.getDecoder().decode(base64));
            return result(file.toPath());
        }

        private static FileChooser chooser(String title) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            return chooser;
        }

        private static String result(Path path) {
            JsonObject result = new JsonObject();
            result.addProperty("name", path.getFileName().toString());
            result.addProperty("path", path.toAbsolutePath().toString());
            return GSON.toJson(result);
        }

        private static void atomicWrite(Path target, byte[] content) throws IOException {
            Path absolute = target.toAbsolutePath().normalize();
            Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp-" + System.nanoTime());
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
