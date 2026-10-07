package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredProgressItem;
import com.cozooo.dlc_manager.client.server.DlcServerRequiredStatus;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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

public class DlcServerRequiredManagerScreen extends DlcRequiredPageScreen {
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

    private volatile boolean loading;
    private volatile boolean downloading;
    private volatile DlcServerRequiredStatus status;
    private volatile String errorText;
    private volatile String lastError;
    private int scrollIndex;
    private Set<String> activeIds = new HashSet<>();

    public DlcServerRequiredManagerScreen(Screen parent, DlcServerEntry serverEntry) {
        super(parent, Component.translatable("dlc_manager.required.manage_title"));
        this.parent = parent;
        this.serverEntry = serverEntry;
    }

    private void initControls() {
        rebuildAllWidgets();
        loadRequiredList();
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
            if (!item.hidden) {
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
            DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                Component.translatable("dlc_manager.required.ready"), Component.empty());
        } else {
            DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
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

    private void handleBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    private Component buildEntryLabel(DlcServerRequiredItem item) {
        String name = resolveDisplayName(item);
        Component label = DlcTextFormatter.parse(name);
        boolean applied = isApplied(item);
        boolean pending = item != null && item.hasFile && item.needsApply;
        if (applied) {
            label = label.copy().withStyle(ChatFormatting.GREEN);
        } else if (pending) {
            label = DlcTextFormatter.parse(name)
                .copy()
                .append(Component.literal(" "))
                .append(Component.translatable("dlc_manager.required.pending_restart"))
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

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash(visibleItems, entryButtons.values().toArray(), loading);

        var resources = new java.util.ArrayList<ResourceRow>();
        for (var item : visibleItems) resources.add(new ResourceRow(resolveDisplayName(item),
                () -> buildEntryLabel(item).getString(), () -> resolveTip(item), entryButtons.get(item.id),
                () -> progressById.getOrDefault(item.id, isApplied(item) ? 1f : 0f)));
        renderResources(resources, () -> errorText, !loading, applyAllButton,
                () -> loading ? -1 : batchState.progress);

    }
    @Override protected void updatePage() {
        updateEntryButtons(); updateApplyAllAvailability();
        if (pageRevision != java.util.Objects.hash(visibleItems, entryButtons.values().toArray(), loading)) refreshPage();
    }

    @Override public boolean shouldCloseOnEsc() { return !downloading && !loading; }
}
