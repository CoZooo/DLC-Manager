package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DlcRemoteDownloadScreen extends Screen {
    private final Screen parent;
    private final List<DlcRemoteEntry> missing;
    private List<DlcRemoteEntry> previewQueue = List.of();

    private Button downloadButton;
    private Button skipButton;

    private volatile boolean downloading;
    private volatile boolean detecting = true;
    private volatile String statusText;
    private volatile String errorText;
    private volatile int totalCount;
    private volatile int completedCount;
    private volatile float currentProgress;
    private int scrollIndex;

    public DlcRemoteDownloadScreen(Screen parent, List<DlcRemoteEntry> missing) {
        super(Component.translatable("dlc_manager.remote.title"));
        this.parent = parent;
        this.missing = missing == null ? List.of() : missing;
    }

    @Override
    protected void init() {
        if (missing.isEmpty()) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        downloadButton = Button.builder(Component.translatable("dlc_manager.remote.download"), button -> startDownload())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        downloadButton.active = false;
        downloadButton.visible = false;
        skipButton = Button.builder(Component.translatable("dlc_manager.remote.skip"), button -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight).build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(skipButton);
        detecting = true;
        statusText = Component.translatable("dlc_manager.remote.preparing").getString();
        new Thread(this::buildPreviewQueue, "dlc-remote-preview").start();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.remote.desc"), centerX, y, 0xC8C8C8);
        y += 16;
        if (detecting && !downloading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.remote.detecting"), centerX, y, 0xE6E6E6);
            y += 12;
        }

        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = y;
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcRemoteEntry entry = showList.get(i);
            String name = entry.resolveDisplayName();
            guiGraphics.drawString(this.font, "- " + name, 24, y, 0xE6E6E6, false);
            y += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);

        if (downloading) {
            y = this.height - 70;
            String status = statusText == null ? "" : statusText;
            guiGraphics.drawCenteredString(this.font, status, centerX, y, 0xE6E6E6);
            y += 12;
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, currentProgress);
            int percent = Math.round(Math.max(0f, Math.min(1f, currentProgress)) * 100f);
            guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
        } else if (errorText != null && !errorText.isBlank()) {
            y = this.height - 70;
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xFF6B6B);
        }

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        List<DlcRemoteEntry> showList = previewQueue.isEmpty() ? missing : previewQueue;
        int listTop = 20 + 18 + 16 + (detecting && !downloading ? 12 : 0);
        int listBottom = this.height - 70;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = showList.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 12;
        int barWidth = 4;
        int barHeight = Math.max(12, (bottom - top) * maxVisible / totalLines);
        int trackHeight = bottom - top;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = top + (int) ((scrollIndex / (float) maxScroll) * maxTrack);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int bg = 0xFF2B2B2B;
        int fg = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + filled, y + height, fg);
    }

    private void buildPreviewQueue() {
        List<DlcRemoteEntry> queue = new ArrayList<>();
        ArrayDeque<DlcRemoteEntry> work = new ArrayDeque<>(missing);
        Set<String> seen = new HashSet<>();
        while (!work.isEmpty()) {
            DlcRemoteEntry entry = work.poll();
            if (entry == null) {
                continue;
            }
            String key = entry.getUniqueKey();
            if (!seen.add(key)) {
                continue;
            }
            queue.add(entry);
            for (DlcRemoteEntry dep : entry.getRemoteDependencies()) {
                work.add(dep);
            }
            for (DlcRemoteEntry dep : fetchModrinthRequired(entry)) {
                work.add(dep);
            }
        }
        previewQueue = queue;
        detecting = false;
        statusText = Component.translatable("dlc_manager.remote.ready").getString();
        scrollIndex = 0;
        Minecraft.getInstance().execute(() -> {
            if (downloadButton != null) {
                downloadButton.visible = true;
                downloadButton.active = true;
            }
        });
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        errorText = null;
        downloadButton.active = false;
        skipButton.active = false;
        new Thread(this::downloadAll, "dlc-remote-download").start();
    }

    private void downloadAll() {
        ArrayDeque<DlcRemoteEntry> queue = new ArrayDeque<>(missing);
        Set<String> processed = new HashSet<>();
        Set<String> scheduled = new HashSet<>();
        for (DlcRemoteEntry entry : missing) {
            scheduled.add(entry.getUniqueKey());
        }
        totalCount = scheduled.size();
        completedCount = 0;
        while (!queue.isEmpty()) {
            DlcRemoteEntry entry = queue.pollFirst();
            if (entry == null) {
                continue;
            }
            String key = entry.getUniqueKey();
            if (processed.contains(key)) {
                continue;
            }
            List<DlcRemoteEntry> deps = new ArrayList<>(entry.getRemoteDependencies());
            deps.addAll(fetchModrinthRequired(entry));
            boolean addedDeps = false;
            for (DlcRemoteEntry dep : deps) {
                if (dep == null) {
                    continue;
                }
                String depKey = dep.getUniqueKey();
                if (processed.contains(depKey) || scheduled.contains(depKey)) {
                    continue;
                }
                scheduled.add(depKey);
                totalCount = scheduled.size();
                queue.addFirst(dep);
                addedDeps = true;
            }
            if (addedDeps) {
                queue.addLast(entry);
                continue;
            }
            statusText = Component.translatable("dlc_manager.remote.downloading", entry.resolveDisplayName(), completedCount + 1, totalCount).getString();
            try {
                downloadEntry(entry);
            } catch (Exception e) {
                errorText = Component.translatable("dlc_manager.remote.error", e.getMessage()).getString();
                downloading = false;
                Minecraft.getInstance().execute(() -> {
                    downloadButton.active = true;
                    skipButton.active = true;
                });
                return;
            }
            processed.add(key);
            completedCount++;
        }
        downloading = false;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(parent));
    }

    private List<DlcRemoteEntry> fetchModrinthRequired(DlcRemoteEntry entry) {
        List<DlcRemoteEntry> result = new ArrayList<>();
        String versionId = entry.getModrinthVersionId();
        if (versionId == null || versionId.isBlank()) {
            return result;
        }
        try {
            ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
            if (version == null) {
                return result;
            }
            for (ModrinthClient.ModrinthDependency dep : version.requiredDependencies) {
                if (dep == null || dep.versionId == null || dep.versionId.isBlank()) {
                    continue;
                }
                DlcRemoteEntry depEntry = new DlcRemoteEntry(null, entry.getDlcRoot(), entry.getBaseDir(), null);
                depEntry.setModrinthProjectId(dep.projectId == null ? "" : dep.projectId);
                depEntry.setModrinthVersionId(dep.versionId);
                depEntry.setType(entry.getType());
                depEntry.setInList(true);
                depEntry.setHidden(false);
                depEntry.setAllowEnableByAll(true);
                try {
                    ModrinthClient.ModrinthVersion depVersion = ModrinthClient.fetchVersion(dep.versionId);
                    if (depVersion != null && depVersion.fileName != null && !depVersion.fileName.isBlank()) {
                        depEntry.setFileNameHint(depVersion.fileName);
                        if (depEntry.getDlcIdentifier() == null || depEntry.getDlcIdentifier().isBlank()) {
                            depEntry.setDlcIdentifier(depVersion.fileName);
                        }
                    }
                } catch (Exception ignored) {
                    // Best-effort.
                }
                if (depEntry.getDlcIdentifier() == null || depEntry.getDlcIdentifier().isBlank()) {
                    if (dep.projectId != null && !dep.projectId.isBlank()) {
                        depEntry.setDlcIdentifier(dep.projectId);
                    }
                }
                result.add(depEntry);
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    private void downloadEntry(DlcRemoteEntry entry) throws IOException, InterruptedException {
        if (entry == null) {
            return;
        }
        currentProgress = 0f;
        String directUrl = entry.getDirectUrl();
        String url = null;
        String fileName = entry.getFileNameHint();
        if (directUrl != null && !directUrl.isBlank()) {
            url = directUrl;
        } else if (entry.getModrinthVersionId() != null && !entry.getModrinthVersionId().isBlank()) {
            ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(entry.getModrinthVersionId());
            if (version != null) {
                url = version.url;
                fileName = version.fileName != null && !version.fileName.isBlank() ? version.fileName : fileName;
            }
        }
        if (url == null || url.isBlank()) {
            throw new IOException("missing download url");
        }
        DownloadInfo downloadInfo = openDownload(url);
        String headerName = DlcDownloadUtil.sanitizeFileName(downloadInfo.fileName);
        if (directUrl != null && !directUrl.isBlank()) {
            if (headerName != null) {
                fileName = headerName;
            }
        }
        if (!DlcDownloadUtil.isLikelyFileName(fileName) && headerName != null) {
            fileName = headerName;
        }
        fileName = finalizeFileName(fileName, entry, url);
        Path target = entry.resolveTargetPath(fileName);
        if (target == null) {
            throw new IOException("invalid target path");
        }
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            closeQuietly(downloadInfo.connection);
            return;
        }
        Path tempFile = Files.createTempFile(target.getParent(), "dlc-download", ".tmp");
        downloadToFile(downloadInfo.connection, tempFile);
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

        entry.setFileNameHint(fileName);
        DlcRemoteManager.updateFileNameKey(entry, fileName);
        DlcRemoteManager.writeInfoFile(entry.getDlcRoot(), entry, fileName);
    }

    private void downloadToFile(HttpURLConnection connection, Path target) throws IOException {
        if (connection == null) {
            throw new IOException("missing download url");
        }
        int total = connection.getContentLength();
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int len;
            int read = 0;
            while ((len = in.read(buffer)) >= 0) {
                out.write(buffer, 0, len);
                read += len;
                if (total > 0) {
                    currentProgress = Math.min(1f, (float) read / (float) total);
                }
            }
        } finally {
            connection.disconnect();
        }
        currentProgress = 1f;
    }

    private DownloadInfo openDownload(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "DLC-Manager");
        connection.connect();
        String fileName = DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.extractFileNameFromUrl(connection.getURL().toString());
        }
        return new DownloadInfo(connection, fileName);
    }

    private String finalizeFileName(String fileName, DlcRemoteEntry entry, String url) {
        String name = fileName;
        if (name == null || name.isBlank()) {
            String fallback = entry.getFileNameHint();
            if (fallback == null || fallback.isBlank()) {
                fallback = entry.getDlcIdentifier();
            }
            if (fallback == null || fallback.isBlank()) {
                fallback = "dlc-download";
            }
            name = fallback;
        }
        if (!name.contains(".")) {
            String extension = guessExtension(entry);
            if (extension != null) {
                name = name + extension;
            }
        }
        return name;
    }

    private String guessExtension(DlcRemoteEntry entry) {
        if (entry == null || entry.getType() == null) {
            return null;
        }
        return switch (entry.getType()) {
            case MOD -> ".jar";
            case RESOURCE_PACK, DATA_PACK, TACZ_PACK, UNKNOWN -> ".zip";
        };
    }

    private void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // Best-effort.
            }
        }
    }

    private static final class DownloadInfo {
        private final HttpURLConnection connection;
        private final String fileName;

        private DownloadInfo(HttpURLConnection connection, String fileName) {
            this.connection = connection;
            this.fileName = fileName;
        }
    }

    private String stripJson(String name) {
        if (name != null && name.endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }
}
