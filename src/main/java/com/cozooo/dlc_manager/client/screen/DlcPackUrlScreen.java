package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class DlcPackUrlScreen extends DlcUrlPageScreen {
    private final DlcPackManagerScreen parent;
    private EditBox urlField;
    private Button downloadButton;
    private Button pasteButton;
    private Button backButton;
    private volatile boolean downloading;
    private volatile boolean cancelRequested;
    private volatile float progress;
    private volatile String errorText;
    private volatile String statusText;
    private volatile long totalBytes;
    private volatile long downloadedBytes;
    private String urlValue = "";

    public DlcPackUrlScreen(DlcPackManagerScreen parent) {
        super(DlcUiPage.LOCAL_PACK_URL, parent, Component.translatable("dlc_manager.pack.import_url_title"));
        this.parent = parent;
    }

    private void initControls() {
        int fieldWidth = Math.min(320, this.width - 40);
        int fieldHeight = 18;
        int fieldX = this.width / 2 - fieldWidth / 2;
        int fieldY = this.height / 2 - 20;
        urlField = new EditBox(this.font, fieldX, fieldY, fieldWidth, fieldHeight, Component.empty());
        urlField.setMaxLength(Integer.MAX_VALUE);
        urlField.setFocused(true);
        urlField.setValue(urlValue);
        addRenderableWidget(urlField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = fieldY + 30;
        int leftX = this.width / 2 - buttonWidth - 10 - buttonWidth / 2;
        int centerX = this.width / 2 - buttonWidth / 2;
        int rightX = this.width / 2 + buttonWidth / 2 + 10;

        int downloadX = downloading ? centerX : leftX;
        downloadButton = Button.builder(Component.translatable("dlc_manager.update.url_download"), btn -> {
                if (downloading) {
                    cancelDownload();
                } else {
                    startDownload();
                }
            })
            .bounds(downloadX, buttonY, buttonWidth, buttonHeight)
            .build();
        pasteButton = Button.builder(Component.translatable("dlc_manager.update.url_paste"), btn -> pasteClipboard())
            .bounds(centerX, buttonY, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("dlc_manager.pack.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(rightX, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(pasteButton);
        addRenderableWidget(backButton);
        if (downloading) {
            setButtonsForDownload();
        }
    }

    private void pasteClipboard() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard != null) {
            urlField.setValue(clipboard.trim());
        }
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        String url = urlField.getValue() == null ? "" : urlField.getValue().trim();
        if (url.isBlank()) {
            errorText = Component.translatable("dlc_manager.update.url_empty").getString();
            return;
        }
        downloading = true;
        cancelRequested = false;
        progress = 0f;
        downloadedBytes = 0L;
        totalBytes = 0L;
        errorText = null;
        statusText = Component.translatable("dlc_manager.pack.url_downloading").getString();
        setButtonsForDownload();

        new Thread(() -> {
            Path downloaded = null;
            try {
                downloaded = downloadToDir(url, DlcPaths.dlcRoot().resolve("pack"));
                Path finalDownloaded = downloaded;
                Minecraft.getInstance().execute(() -> {
                    downloading = false;
                    Minecraft.getInstance().setScreen(parent);
                    parent.importDownloadedPack(finalDownloaded);
                });
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded);
                    } catch (IOException ignored) {
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    errorText = Component.translatable("dlc_manager.pack.url_canceled").getString();
                    downloading = false;
                    cancelRequested = false;
                    setButtonsActive(true);
                });
            } catch (Exception ex) {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded);
                    } catch (IOException ignored) {
                    }
                }
                String msg = ex.getMessage() == null ? "download failed" : ex.getMessage();
                Minecraft.getInstance().execute(() -> {
                    errorText = Component.translatable("dlc_manager.update.url_failed", msg).getString();
                    downloading = false;
                    cancelRequested = false;
                    setButtonsActive(true);
                });
            }
        }, "dlc-pack-url-download").start();
    }

    private void cancelDownload() {
        if (!downloading) {
            return;
        }
        cancelRequested = true;
        statusText = Component.translatable("dlc_manager.pack.canceling").getString();
        if (downloadButton != null) {
            downloadButton.active = false;
            downloadButton.setMessage(Component.translatable("dlc_manager.pack.canceling"));
        }
    }

    private Path downloadToDir(String url, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        String fileName = DlcDownloadService.suggestFileName(url);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(url));
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "pack.zip";
        } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        fileName = uniqueFileName(outputDir, fileName);
        totalBytes = 0L;
        downloadedBytes = 0L;
        return DlcDownloadService.download(url, outputDir, fileName, (done, total) -> {
            statusText = Component.translatable("dlc_manager.pack.url_downloading").getString();
            downloadedBytes = done;
            totalBytes = total;
            progress = total > 0 ? (done / (float) total) : 0f;
        }, () -> cancelRequested);
    }

    private String uniqueFileName(Path dir, String fileName) {
        if (!Files.exists(dir.resolve(fileName))) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            String candidate = base + "-" + i + ext;
            if (!Files.exists(dir.resolve(candidate))) {
                return candidate;
            }
        }
        return base + "-" + System.nanoTime() + ext;
    }

    private void setButtonsActive(boolean active) {
        if (downloadButton != null) {
            downloadButton.active = active;
            downloadButton.setMessage(Component.translatable("dlc_manager.update.url_download"));
            downloadButton.visible = true;
        }
        if (pasteButton != null) {
            pasteButton.active = active;
            pasteButton.visible = true;
        }
        if (backButton != null) {
            backButton.active = active;
            backButton.visible = true;
        }
        if (urlField != null) {
            urlField.visible = true;
        }
    }

    private void setButtonsForDownload() {
        if (downloadButton != null) {
            downloadButton.active = !cancelRequested;
            downloadButton.setMessage(cancelRequested ? Component.translatable("dlc_manager.pack.canceling") : Component.translatable("dlc_manager.pack.cancel"));
            downloadButton.visible = true;
        }
        if (pasteButton != null) {
            pasteButton.active = false;
            pasteButton.visible = false;
        }
        if (backButton != null) {
            backButton.active = false;
            backButton.visible = true;
        }
        if (urlField != null) {
            urlField.visible = false;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !downloading;
    }

    @Override
    public void onClose() {
        if (!downloading) {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override protected void buildPage() {
        if (urlField != null) urlValue = urlField.getValue();
        initControls();
        renderUrlForm("dlc_manager.pack.url_prompt", urlField, pasteButton, downloadButton,
                () -> statusText, () -> errorText, () -> progress, () -> downloading ? Math.round(progress * 100) + "%" : "", () -> downloading);
    }
    @Override protected void updatePage() { if (urlField != null) urlField.active = !downloading; }

}
