package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem;
import com.cozooo.dlc_manager.client.server.DlcServerRemoteStatus;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DlcServerRemoteDownloadScreen extends DlcScreenCompat.MouseScroll {
    private static final int LINE_HEIGHT = 10;
    private static final int CHECKBOX_SIZE = 8;
    private static final int CHECKBOX_X = 12;
    private static final String ALL_MARKER = "__ALL__";

    private final Screen parent;
    private final DlcServerEntry serverEntry;
    private final Set<String> selectedKeys = new HashSet<>(Set.of(ALL_MARKER));
    private final Map<String, Float> progressByKey = new ConcurrentHashMap<>();
    private volatile List<DlcServerRemoteItem> items = List.of();

    private Button downloadButton;
    private Button backButton;

    private volatile boolean loading = true;
    private volatile boolean downloading;
    private volatile DlcServerRemoteStatus status;
    private volatile String errorText;
    private int scrollIndex;
    private boolean draggingScrollBar;
    private int scrollBarDragOffset;
    private float lastTotalProgress;

    public DlcServerRemoteDownloadScreen(Screen parent, DlcServerEntry serverEntry) {
        super(Component.translatable("dlc_manager.remote.title"));
        this.parent = parent;
        this.serverEntry = serverEntry;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        downloadButton = Button.builder(Component.translatable("dlc_manager.remote.download"), button -> handleDownload())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        downloadButton.active = false;
        backButton = Button.builder(Component.translatable("dlc_manager.required.skip"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(backButton);
        loadMissingList();
    }

    private void loadMissingList() {
        loading = true;
        CompletableFuture
            .supplyAsync(this::fetchMissing)
            .whenComplete((list, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (list == null) {
                    errorText = Component.translatable("dlc_manager.server.remote_missing_failed").getString();
                    return;
                }
                items = list;
                if (items.isEmpty()) {
                    Minecraft.getInstance().setScreen(parent);
                    return;
                }
                downloadButton.active = true;
            }));
    }

    private List<DlcServerRemoteItem> fetchMissing() {
        try {
            return DlcServerConnection.fetchRemoteMissingList(serverEntry, 8000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void handleDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        downloadButton.active = false;
        CompletableFuture
            .supplyAsync(() -> startDownload())
            .whenComplete((started, throwable) -> {
                if (started == null) {
                    return;
                }
                startPolling();
            });
    }

    private DlcServerRemoteStatus startDownload() {
        try {
            List<String> keys = resolveSelectionKeys();
            return DlcServerConnection.startRemoteDownload(serverEntry, 8000, keys);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void startPolling() {
        new Thread(() -> {
            while (downloading) {
                DlcServerRemoteStatus next = fetchStatus();
                if (next != null) {
                    updateStatus(next);
                    if (next.finished) {
                        downloading = false;
                        boolean ok = isStatusOk(next);
                        Minecraft.getInstance().execute(() -> {
                            if (ok) {
                                DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                                    Component.translatable("dlc_manager.server.remote_missing_success"), Component.empty());
                            } else {
                                DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                                    Component.translatable("dlc_manager.server.remote_missing_failed"), Component.empty());
                            }
                            if (parent instanceof DlcServerManagerScreen manager) {
                                manager.refreshServerInfo();
                            } else if (parent instanceof DlcServerMaLiLibScreen mali) {
                                mali.refreshServerInfo();
                            }
                            Minecraft.getInstance().setScreen(parent);
                        });
                        break;
                    }
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }, "dlc-server-remote-poll").start();
    }

    private boolean isStatusOk(DlcServerRemoteStatus next) {
        if (next == null) {
            return false;
        }
        if (next.items != null) {
            for (DlcServerRemoteItem item : next.items) {
                if (item != null && item.done && !item.success) {
                    return false;
                }
            }
        }
        return next.success;
    }

    private DlcServerRemoteStatus fetchStatus() {
        try {
            return DlcServerConnection.fetchRemoteDownloadStatus(serverEntry, 3000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateStatus(DlcServerRemoteStatus next) {
        status = next;
        if (next == null || next.items == null) {
            return;
        }
        for (DlcServerRemoteItem item : next.items) {
            if (item == null || item.key == null) {
                continue;
            }
            float progress = 0f;
            if (item.totalBytes > 0) {
                progress = item.downloadedBytes / (float) item.totalBytes;
            } else if (item.done) {
                progress = 1f;
            }
            progressByKey.put(item.key, progress);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.remote.desc"), centerX, y, 0xC8C8C8);
        y += 16;
        if (loading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.remote.detecting"), centerX, y, 0xE6E6E6);
            y += 12;
        }
        if (errorText != null && !errorText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, DlcTextFormatter.parse(errorText), centerX, y, 0xE6E6E6);
            y += 12;
        }

        int listTop = y;
        drawCheckbox(guiGraphics, CHECKBOX_X, listTop + 1, CHECKBOX_SIZE, isAllSelected());
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.remote.select_all"),
            CHECKBOX_X + CHECKBOX_SIZE + 6, listTop, 0xE6E6E6, false);
        int listStart = listTop + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listStart) / LINE_HEIGHT);
        int totalLines = items.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcServerRemoteItem item = items.get(i);
            int rowY = listStart + (i - scrollIndex) * LINE_HEIGHT;
            boolean checked = isAllSelected() || selectedKeys.contains(item.key);
            drawCheckbox(guiGraphics, CHECKBOX_X, rowY + 1, CHECKBOX_SIZE, checked);
            String name = resolveDisplayName(item);
            guiGraphics.drawString(this.font, DlcTextFormatter.parse(name), CHECKBOX_X + CHECKBOX_SIZE + 6, rowY, 0xE6E6E6, false);
            if (downloading) {
                Float progress = progressByKey.get(item.key);
                if (progress != null) {
                    int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
                    String progressText = percent + "%";
                    int textWidth = this.font.width(progressText);
                    int percentBoxWidth = this.font.width("100%");
                    int barWidth = 36;
                    int barHeight = 6;
                    int percentRight = this.width - 18;
                    int percentLeft = percentRight - percentBoxWidth;
                    int barRight = percentLeft - 6;
                    int barLeft = barRight - barWidth;
                    int barY = rowY + 2;
                    drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress);
                    guiGraphics.drawString(this.font, progressText, percentRight - textWidth, rowY, 0xC8C8C8, false);
                }
            }
        }
        drawScrollBar(guiGraphics, listStart, listBottom, totalLines, maxVisible, maxScroll);

        if (downloading && status != null) {
            y = this.height - 70;
            float totalProgress = resolveTotalProgress(status);
            Component statusText = "selecting_source".equals(status.statusKey)
                ? Component.translatable("dlc_manager.remote.selecting_source")
                : Component.translatable("dlc_manager.remote.downloading_multi");
            guiGraphics.drawCenteredString(this.font, statusText, centerX, y, 0xE6E6E6);
            y += 12;
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, totalProgress);
            int percent = Math.round(totalProgress * 100f);
            guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 10, 0xC8C8C8);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private String resolveDisplayName(DlcServerRemoteItem item) {
        if (item == null) {
            return "";
        }
        if (item.displayNames != null && !item.displayNames.isEmpty()) {
            String lang = DlcDcConfigIO.normalizeLangKey(Minecraft.getInstance().options.languageCode);
            String name = lang == null ? null : item.displayNames.get(lang);
            if (name == null || name.isBlank()) {
                name = item.displayNames.get("en_us");
            }
            if (name == null || name.isBlank()) {
                name = item.displayNames.values().stream().findFirst().orElse(null);
            }
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (item.name != null && !item.name.isBlank()) {
            return item.name;
        }
        return item.key == null ? "" : item.key;
    }

    private float resolveTotalProgress(DlcServerRemoteStatus status) {
        if (status == null) {
            return 0f;
        }
        if (status.items != null && !status.items.isEmpty()) {
            float sum = 0f;
            int count = 0;
            for (DlcServerRemoteItem item : status.items) {
                if (item == null) {
                    continue;
                }
                float progress;
                if (item.totalBytes > 0) {
                    progress = item.downloadedBytes / (float) item.totalBytes;
                } else if (item.done) {
                    progress = 1f;
                } else {
                    progress = 0f;
                }
                sum += Math.max(0f, Math.min(1f, progress));
                count++;
            }
            if (count > 0) {
                float computed = sum / (float) count;
                float clamped = Math.max(0f, Math.min(1f, computed));
                lastTotalProgress = Math.max(lastTotalProgress, clamped);
                return lastTotalProgress;
            }
        }
        if (status.totalBytes > 0) {
            float computed = Math.max(0f, Math.min(1f, status.downloadedBytes / (float) status.totalBytes));
            lastTotalProgress = Math.max(lastTotalProgress, computed);
            return lastTotalProgress;
        }
        if (status.totalCount > 0) {
            float computed = Math.max(0f, Math.min(1f, status.completedCount / (float) status.totalCount));
            lastTotalProgress = Math.max(lastTotalProgress, computed);
            return lastTotalProgress;
        }
        return 0f;
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(deltaY);
    }

    private boolean handleMouseScrolled(double deltaY) {
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listTop) / LINE_HEIGHT);
        int totalLines = items.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (!downloading) {
            int listTop = getListTop();
            int listBottom = this.height - 70;
            if (mouseY >= listTop && mouseY < listTop + LINE_HEIGHT) {
                int boxX = CHECKBOX_X;
                int boxY = listTop + 1;
                if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE
                    && mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE) {
                    toggleSelectAll();
                    return true;
                }
            }
            int listStart = listTop + LINE_HEIGHT;
            if (mouseY >= listStart && mouseY < listBottom) {
                int row = (int) ((mouseY - listStart) / LINE_HEIGHT);
                int index = scrollIndex + row;
                if (index >= 0 && index < items.size()) {
                    int boxX = CHECKBOX_X;
                    int boxY = listStart + row * LINE_HEIGHT + 1;
                    if (mouseX >= boxX && mouseX <= boxX + CHECKBOX_SIZE
                        && mouseY >= boxY && mouseY <= boxY + CHECKBOX_SIZE) {
                        toggleSelection(items.get(index));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollBar) {
            updateScrollFromDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollBar) {
            draggingScrollBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private boolean beginScrollDrag(double mouseX, double mouseY) {
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listTop) / LINE_HEIGHT);
        int totalLines = items.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return false;
        }
        int barX = this.width - 12;
        int barWidth = 4;
        int barHeight = Math.max(12, (listBottom - listTop) * maxVisible / totalLines);
        int trackHeight = listBottom - listTop;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = listTop + (int) ((scrollIndex / (float) maxScroll) * maxTrack);
        if (mouseX < barX || mouseX > barX + barWidth || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        if (mouseY >= barY && mouseY <= barY + barHeight) {
            draggingScrollBar = true;
            scrollBarDragOffset = (int) mouseY - barY;
            return true;
        }
        int targetBarY = (int) mouseY - barHeight / 2;
        targetBarY = Math.max(listTop, Math.min(listTop + maxTrack, targetBarY));
        int newIndex = Math.round(((targetBarY - listTop) / (float) maxTrack) * maxScroll);
        scrollIndex = Math.max(0, Math.min(maxScroll, newIndex));
        return true;
    }

    private void updateScrollFromDrag(double mouseY) {
        int listTop = getListTop() + LINE_HEIGHT;
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listTop) / LINE_HEIGHT);
        int totalLines = items.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return;
        }
        int barHeight = Math.max(12, (listBottom - listTop) * maxVisible / totalLines);
        int trackHeight = listBottom - listTop;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int targetBarY = (int) mouseY - scrollBarDragOffset;
        targetBarY = Math.max(listTop, Math.min(listTop + maxTrack, targetBarY));
        int newIndex = Math.round(((targetBarY - listTop) / (float) maxTrack) * maxScroll);
        scrollIndex = Math.max(0, Math.min(maxScroll, newIndex));
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int bg = 0xFF2B2B2B;
        int fg = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + filled, y + height, fg);
    }

    private void drawCheckbox(GuiGraphics guiGraphics, int x, int y, int size, boolean checked) {
        int bg = 0xFF2B2B2B;
        int outline = 0xFF6B6B6B;
        int fill = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + size, y + size, outline);
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, bg);
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, fill);
        }
    }

    private int getListTop() {
        return 20 + 18 + 16;
    }

    private boolean isAllSelected() {
        return selectedKeys.contains(ALL_MARKER);
    }

    private void toggleSelectAll() {
        if (isAllSelected()) {
            selectedKeys.clear();
            return;
        }
        selectedKeys.clear();
        selectedKeys.add(ALL_MARKER);
    }

    private void toggleSelection(DlcServerRemoteItem item) {
        if (item == null || item.key == null || item.key.isBlank()) {
            return;
        }
        if (isAllSelected()) {
            selectedKeys.clear();
            for (DlcServerRemoteItem entry : items) {
                if (entry != null && entry.key != null && !entry.key.equals(item.key)) {
                    selectedKeys.add(entry.key);
                }
            }
            return;
        }
        if (selectedKeys.contains(item.key)) {
            selectedKeys.remove(item.key);
        } else {
            selectedKeys.add(item.key);
        }
        if (!items.isEmpty() && selectedKeys.size() == items.size()) {
            selectedKeys.clear();
            selectedKeys.add(ALL_MARKER);
        }
    }

    private List<String> resolveSelectionKeys() {
        if (isAllSelected()) {
            return List.of();
        }
        return new ArrayList<>(selectedKeys);
    }
}
