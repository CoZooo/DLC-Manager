package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.required.DlcRequiredNotice;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredProgressItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DlcServerRequiredManagerScreen extends Screen {
    private static final class InstallState {
        private volatile boolean running;
        private volatile float progress;
    }

    private final Screen parent;
    private final DlcServerEntry serverEntry;
    private final List<DlcServerRequiredItem> allItems = new ArrayList<>();
    private final List<DlcServerRequiredItem> visibleItems = new ArrayList<>();
    private final Map<String, DlcServerRequiredItem> index = new HashMap<>();
    private Map<String, Set<String>> dependents = new HashMap<>();
    private final Map<String, Button> entryButtons = new HashMap<>();
    private final Map<String, Float> progressById = new ConcurrentHashMap<>();

    private Button applyAllButton;
    private Button backButton;
    private final InstallState batchState = new InstallState();
    private List<String> noticeLines = List.of();

    private volatile boolean loading;
    private volatile boolean downloading;
    private volatile DlcServerRequiredStatus status;
    private volatile String errorText;
    private volatile String lastError;
    private int scrollIndex;
    private Set<String> activeIds = new HashSet<>();

    public DlcServerRequiredManagerScreen(Screen parent, DlcServerEntry serverEntry) {
        super(Component.translatable("dlc_manager.required.manage_title"));
        this.parent = parent;
        this.serverEntry = serverEntry;
    }

    @Override
    protected void init() {
        noticeLines = splitLines(DlcRequiredNotice.load().resolveManageDesc());
        rebuildAllWidgets();
        loadRequiredList();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        init();
    }

    private void rebuildAllWidgets() {
        clearWidgets();
        entryButtons.clear();
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        applyAllButton = Button.builder(Component.translatable("dlc_manager.required.apply_all"), button -> applyAll())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("dlc_manager.back"), button -> handleBack())
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(applyAllButton);
        addRenderableWidget(backButton);
        buildEntryButtons();
    }

    private void buildEntryButtons() {
        int buttonWidth = 72;
        int buttonHeight = 16;
        int x = this.width - 20 - buttonWidth;
        for (DlcServerRequiredItem item : visibleItems) {
            if (item == null || item.id == null || item.id.isBlank()) {
                continue;
            }
            String label = getEntryButtonLabel(item);
            Button button = Button.builder(Component.literal(label), btn -> onEntryAction(item))
                .bounds(x, 0, buttonWidth, buttonHeight)
                .build();
            entryButtons.put(item.id, button);
            addRenderableWidget(button);
        }
        updateEntryButtons();
    }

    private void loadRequiredList() {
        loading = true;
        CompletableFuture
            .supplyAsync(this::fetchRequiredList)
            .whenComplete((list, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (list == null) {
                    if (lastError != null && !lastError.isBlank()) {
                        errorText = lastError;
                    } else {
                        errorText = Component.translatable("dlc_manager.required.error", "").getString();
                    }
                    return;
                }
                errorText = null;
                setItems(list);
            }));
    }

    private void setItems(List<DlcServerRequiredItem> list) {
        allItems.clear();
        allItems.addAll(list);
        index.clear();
        visibleItems.clear();
        for (DlcServerRequiredItem item : allItems) {
            if (item == null || item.id == null || item.id.isBlank()) {
                continue;
            }
            index.put(item.id, item);
            if (item.inList && !item.hidden) {
                visibleItems.add(item);
            }
        }
        dependents = buildDependents(allItems);
        rebuildAllWidgets();
    }

    private List<DlcServerRequiredItem> fetchRequiredList() {
        try {
            lastError = null;
            return DlcServerConnection.fetchRequiredList(serverEntry, 8000);
        } catch (Exception ex) {
            lastError = ex.getMessage();
            return null;
        }
    }

    private void onEntryAction(DlcServerRequiredItem item) {
        if (item == null || downloading) {
            return;
        }
        if (isApplied(item)) {
            uninstallEntry(item);
            return;
        }
        startDownload(List.of(item.id), false);
    }

    private void applyAll() {
        if (downloading) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (DlcServerRequiredItem item : visibleItems) {
            if (item != null && item.id != null && !item.id.isBlank()) {
                ids.add(item.id);
            }
        }
        startDownload(ids, true);
    }

    private void startDownload(List<String> ids, boolean batch) {
        if (downloading) {
            return;
        }
        downloading = true;
        activeIds = new HashSet<>(ids);
        batchState.running = batch;
        batchState.progress = 0f;
        updateEntryButtons();
        updateApplyAllButton(batch, true);
        CompletableFuture
            .supplyAsync(() -> startDownloadRequest(ids))
            .whenComplete((started, throwable) -> {
                if (started != null) {
                    Minecraft.getInstance().execute(() -> {
                        updateStatus(started);
                        if (started.finished) {
                            finishDownload(started);
                        } else {
                            startPolling();
                        }
                    });
                } else {
                    Minecraft.getInstance().execute(() -> {
                        downloading = false;
                        if (lastError != null && !lastError.isBlank()) {
                            errorText = lastError;
                        } else {
                            errorText = Component.translatable("dlc_manager.required.error", "").getString();
                        }
                        updateEntryButtons();
                        updateApplyAllButton(false, false);
                    });
                }
            });
    }

    private DlcServerRequiredStatus startDownloadRequest(List<String> ids) {
        try {
            lastError = null;
            return DlcServerConnection.startRequiredDownload(serverEntry, 8000, ids);
        } catch (Exception ex) {
            lastError = ex.getMessage();
            return null;
        }
    }

    private void uninstallEntry(DlcServerRequiredItem root) {
        Set<String> ids = collectUninstallTargets(root);
        CompletableFuture
            .runAsync(() -> {
                try {
                    DlcServerConnection.uninstallRequired(serverEntry, 8000, new ArrayList<>(ids));
                } catch (Exception ignored) {
                }
            })
            .whenComplete((v, t) -> Minecraft.getInstance().execute(this::loadRequiredList));
    }

    private void startPolling() {
        new Thread(() -> {
            while (downloading) {
                DlcServerRequiredStatus next = fetchStatus();
                if (next != null) {
                    updateStatus(next);
                    if (next.finished) {
                        downloading = false;
                        boolean ok = isStatusOk(next);
                        String reason = getStatusError(next);
                    Minecraft.getInstance().execute(() -> {
                        finishDownload(next);
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
        }, "dlc-server-required-poll").start();
    }

    private DlcServerRequiredStatus fetchStatus() {
        try {
            return DlcServerConnection.fetchRequiredDownloadStatus(serverEntry, 3000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateStatus(DlcServerRequiredStatus next) {
        status = next;
        if (next == null || next.items == null) {
            return;
        }
        String statusError = next.error;
        for (DlcServerRequiredProgressItem item : next.items) {
            if (item == null || item.id == null) {
                continue;
            }
            if ((statusError == null || statusError.isBlank()) && item.error != null && !item.error.isBlank()) {
                statusError = item.error;
            }
            float progress = 0f;
            if (item.totalBytes > 0) {
                progress = item.downloadedBytes / (float) item.totalBytes;
            } else if (item.done) {
                progress = 1f;
            }
            progressById.put(item.id, progress);
        }
        if (next.totalBytes > 0) {
            batchState.progress = next.downloadedBytes / (float) next.totalBytes;
        } else if (next.totalCount > 0) {
            batchState.progress = next.completedCount / (float) next.totalCount;
        } else {
            batchState.progress = 0f;
        }
        if (next.finished && statusError != null && !statusError.isBlank()) {
            errorText = statusError;
        }
    }

    private void finishDownload(DlcServerRequiredStatus next) {
        downloading = false;
        boolean ok = isStatusOk(next);
        String reason = getStatusError(next);
        if (ok) {
            SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.required.ready"), Component.empty());
        } else {
            SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.required.error", ""),
                (reason == null || reason.isBlank()) ? Component.empty() : Component.literal(reason));
        }
        updateEntryButtons();
        updateApplyAllButton(false, false);
        activeIds = new HashSet<>();
        loadRequiredList();
    }

    private boolean isStatusOk(DlcServerRequiredStatus next) {
        if (next == null) {
            return false;
        }
        if (next.items != null) {
            for (DlcServerRequiredProgressItem item : next.items) {
                if (item != null && item.done && !item.success) {
                    return false;
                }
            }
        }
        return next.success;
    }

    private String getStatusError(DlcServerRequiredStatus next) {
        if (next == null) {
            return "";
        }
        if (next.error != null && !next.error.isBlank()) {
            return next.error;
        }
        if (next.items != null) {
            for (DlcServerRequiredProgressItem item : next.items) {
                if (item != null && item.error != null && !item.error.isBlank()) {
                    return item.error;
                }
            }
        }
        return "";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        for (String line : getManageDescLines()) {
            guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xC8C8C8);
            y += this.font.lineHeight + 2;
        }
        if (errorText != null && !errorText.isBlank()) {
            y += 4;
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xE6E6E6);
            y += this.font.lineHeight + 2;
        }
        y += 8;

        int listTop = y;
        int listBottom = this.height - 70;
        int lineHeight = 18;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = visibleItems.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }

        int rowY = listTop;
        for (int i = 0; i < visibleItems.size(); i++) {
            DlcServerRequiredItem item = visibleItems.get(i);
            Button button = item == null ? null : entryButtons.get(item.id);
            boolean visible = i >= scrollIndex && i < scrollIndex + maxVisible;
            if (!visible) {
                if (button != null) {
                    button.visible = false;
                }
                continue;
            }
            if (button != null) {
                button.visible = true;
                button.setY(rowY - 2);
            }
            Component label = buildEntryLabel(item);
            guiGraphics.drawString(this.font, label, 20, rowY, 0xE6E6E6, false);
            if (item != null && item.id != null) {
                Float progress = progressById.get(item.id);
                if (progress != null) {
                    drawRightProgress(guiGraphics, rowY, button, progress);
                }
            }
            if (isMouseOverRow(mouseX, mouseY, rowY, lineHeight) && !isMouseOverButton(button, mouseX, mouseY)) {
                renderTooltip(guiGraphics, item, mouseX, mouseY);
            }
            rowY += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
        renderInstallOverlays(guiGraphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int listTop = getListTop();
        int listBottom = this.height - 70;
        int lineHeight = 18;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = visibleItems.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = vertical > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private int getListTop() {
        int y = 20 + 18;
        y += getManageDescLines().size() * (this.font.lineHeight + 2);
        if (errorText != null && !errorText.isBlank()) {
            y += this.font.lineHeight + 6;
        }
        y += 8;
        return y;
    }

    private List<String> getManageDescLines() {
        return noticeLines == null ? List.of() : noticeLines;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        if (text.contains("\n")) {
            String[] parts = text.split("\\n");
            List<String> lines = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    lines.add(part);
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        return new ArrayList<>(List.of(text));
    }

    private void handleBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    private Component buildEntryLabel(DlcServerRequiredItem item) {
        String name = resolveDisplayName(item);
        Component label = Component.literal(name);
        boolean applied = isApplied(item);
        boolean pending = item != null && item.hasFile && item.needsApply;
        if (applied) {
            label = label.copy().withStyle(ChatFormatting.GREEN);
        } else if (pending) {
            label = Component.literal(name + " " + Component.translatable("dlc_manager.required.pending_restart").getString())
                .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC);
        }
        return label;
    }

    private String getEntryButtonLabel(DlcServerRequiredItem item) {
        return isApplied(item)
            ? Component.translatable("dlc_manager.required.uninstall").getString()
            : Component.translatable("dlc_manager.required.download").getString();
    }

    private void updateEntryButtons() {
        for (DlcServerRequiredItem item : visibleItems) {
            if (item == null || item.id == null) {
                continue;
            }
            Button button = entryButtons.get(item.id);
            if (button == null) {
                continue;
            }
            if (downloading) {
                button.active = false;
                if (activeIds.contains(item.id) || progressById.containsKey(item.id)) {
                    button.setMessage(Component.literal(""));
                } else {
                    button.setMessage(Component.literal(getEntryButtonLabel(item)));
                }
            } else {
                button.active = true;
                button.setMessage(Component.literal(getEntryButtonLabel(item)));
            }
        }
        updateApplyAllAvailability();
    }

    private void updateApplyAllButton(boolean batch, boolean active) {
        if (applyAllButton == null) {
            return;
        }
        if (!active) {
            applyAllButton.active = true;
            applyAllButton.setMessage(Component.translatable("dlc_manager.required.apply_all"));
            batchState.running = false;
            batchState.progress = 0f;
            return;
        }
        applyAllButton.active = false;
        if (batch) {
            applyAllButton.setMessage(Component.literal(""));
        }
    }

    private void updateApplyAllAvailability() {
        if (applyAllButton == null) {
            return;
        }
        if (downloading || batchState.running) {
            return;
        }
        boolean allApplied = true;
        for (DlcServerRequiredItem item : visibleItems) {
            if (item == null || !isApplied(item)) {
                allApplied = false;
                break;
            }
        }
        applyAllButton.active = !allApplied;
        applyAllButton.setMessage(Component.translatable("dlc_manager.required.apply_all"));
    }

    private void renderInstallOverlays(GuiGraphics guiGraphics) {
        if (batchState.running && applyAllButton != null) {
            drawProgressButton(guiGraphics, applyAllButton.getX(), applyAllButton.getY(),
                applyAllButton.getWidth(), applyAllButton.getHeight(), batchState.progress);
        }
    }

    private void drawProgressButton(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int bg = 0xFF2B2B2B;
        int fg = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + filled, y + height, fg);
        int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
        String label = percent + "%";
        int textX = x + (width - this.font.width(label)) / 2;
        int textY = y + (height - this.font.lineHeight) / 2 + 1;
        guiGraphics.drawString(this.font, label, textX, textY, 0xFFFFFF, false);
    }

    private void drawRightProgress(GuiGraphics guiGraphics, int rowY, Button button, float progress) {
        int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
        String progressText = percent + "%";
        int textWidth = this.font.width(progressText);
        int percentBoxWidth = this.font.width("100%");
        int barWidth = 36;
        int barHeight = 6;
        int percentRight = button != null ? button.getX() - 6 : this.width - 18;
        int percentLeft = percentRight - percentBoxWidth;
        int barRight = percentLeft - 6;
        int barLeft = barRight - barWidth;
        int barY = rowY + 2;
        drawProgressBar(guiGraphics, barLeft, barY, barWidth, barHeight, progress);
        guiGraphics.drawString(this.font, progressText, percentRight - textWidth, rowY, 0xC8C8C8, false);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int bg = 0xFF2B2B2B;
        int fg = 0xFF4CAF50;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + filled, y + height, fg);
    }

    private void renderTooltip(GuiGraphics guiGraphics, DlcServerRequiredItem item, int mouseX, int mouseY) {
        if (item == null || item.tips == null || item.tips.isEmpty()) {
            return;
        }
        String tip = resolveTip(item);
        if (tip == null || tip.isBlank()) {
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        int maxWidth = Math.min(260, this.width - 40);
        for (String line : tip.split("\\n")) {
            lines.addAll(this.font.split(Component.literal(line), maxWidth));
        }
        guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    private String resolveTip(DlcServerRequiredItem item) {
        if (item == null || item.tips == null || item.tips.isEmpty()) {
            return "";
        }
        String lang = Minecraft.getInstance().options.languageCode;
        String value = item.tips.get(lang);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = item.tips.get("en_us");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return item.tips.values().stream().findFirst().orElse("");
    }

    private boolean isApplied(DlcServerRequiredItem item) {
        return item != null && item.applied;
    }

    private String resolveDisplayName(DlcServerRequiredItem item) {
        if (item == null) {
            return "";
        }
        if (item.displayNames != null) {
            String lang = Minecraft.getInstance().getLanguageManager().getSelected();
            String value = item.displayNames.get(lang);
            if (value != null && !value.isBlank()) {
                return value;
            }
            value = item.displayNames.get("en_us");
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        if (item.name != null && !item.name.isBlank()) {
            return item.name;
        }
        return item.id == null ? "" : item.id;
    }

    private boolean isMouseOverRow(int mouseX, int mouseY, int y, int height) {
        return mouseX >= 16 && mouseX <= this.width - 16 && mouseY >= y && mouseY <= y + height;
    }

    private boolean isMouseOverButton(Button button, int mouseX, int mouseY) {
        if (button == null || !button.visible) {
            return false;
        }
        return button.isMouseOver(mouseX, mouseY);
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

    private Map<String, Set<String>> buildDependents(List<DlcServerRequiredItem> entries) {
        Map<String, Set<String>> map = new HashMap<>();
        for (DlcServerRequiredItem entry : entries) {
            if (entry == null || entry.id == null) {
                continue;
            }
            for (String dep : entry.dependencies) {
                if (dep == null || dep.isBlank()) {
                    continue;
                }
                map.computeIfAbsent(dep, key -> new HashSet<>()).add(entry.id);
            }
        }
        return map;
    }

    private Set<String> collectUninstallTargets(DlcServerRequiredItem root) {
        Set<String> remove = new HashSet<>();
        if (root == null || root.id == null) {
            return remove;
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(root.id);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!remove.add(id)) {
                continue;
            }
            DlcServerRequiredItem entry = index.get(id);
            if (entry == null) {
                continue;
            }
            for (String depId : entry.dependencies) {
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                DlcServerRequiredItem depEntry = index.get(depId);
                if (depEntry != null && !isEnabled(depEntry)) {
                    continue;
                }
                if (hasOtherDependents(depId, remove)) {
                    continue;
                }
                queue.add(depId);
            }
            Set<String> ups = dependents.get(id);
            if (ups != null) {
                for (String up : ups) {
                    if (remove.contains(up)) {
                        continue;
                    }
                    DlcServerRequiredItem upEntry = index.get(up);
                    if (upEntry != null && !isEnabled(upEntry)) {
                        continue;
                    }
                    queue.add(up);
                }
            }
        }
        return remove;
    }

    private boolean hasOtherDependents(String id, Set<String> removing) {
        Set<String> deps = dependents.get(id);
        if (deps == null || deps.isEmpty()) {
            return false;
        }
        for (String dep : deps) {
            if (removing.contains(dep)) {
                continue;
            }
            DlcServerRequiredItem entry = index.get(dep);
            if (entry != null && isEnabled(entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnabled(DlcServerRequiredItem item) {
        return item != null && (item.applied || item.hasFile);
    }
}
