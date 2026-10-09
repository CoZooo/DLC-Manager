package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcConfigTypeSupport;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;
import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

public class DlcManagerScreen extends DlcManagerPageScreen implements DlcReturnPositionHost {
    private final Screen parent;
    private final List<DlcListRow> rows = new ArrayList<>();
    private final List<DlcItem> items = new ArrayList<>();
    private final List<DlcItem> allItems = new ArrayList<>();
    private DlcApplyMode applyMode = DlcApplyMode.RESTART_ONLY;
    private boolean hasModItems, hasModChanges, userSelectedDlcApplyMode, noticeChecked;
    private DlcType activeFilter;
    private final boolean hasTaczMod = DlcPlatformServices.isModLoaded("tacz");
    private final Set<String> presetSnapshot = new LinkedHashSet<>();
    private boolean presetSnapshotCaptured;
    private DlcUiButton modeButton, backButton, enableAllButton, disableAllButton;
    private boolean pendingScrollRestore;
    private double savedScrollOffset;
    private DlcType savedScrollFilter;

    private boolean canShowServerButton() {
        return DlcConfig.isServerSupportOn();
    }

    private void rebuildRows() {
        rows.clear();
        items.clear();
        allItems.clear();
        hasModItems = false;
        hasModChanges = false;
        userSelectedDlcApplyMode = false;

        if (DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.reload();
        } else {
            DlcPresetRuntimeState.clearAll();
        }

        if (activeFilter == null) {
            appendRedirectRows();
        }

        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
        Map<String, Boolean> enabledById = new HashMap<>();
        for (DlcType type : DlcType.values()) {
            if (type == DlcType.TACZ_PACK && !hasTaczMod) {
                continue;
            }
            List<DlcItem> typeItems = grouped.get(type);
            if (typeItems == null || typeItems.isEmpty()) {
                continue;
            }
            if (type == DlcType.MOD) {
                hasModItems = true;
            }
            for (DlcItem item : typeItems) {
                boolean enabled = item.isEnabled();
                enabledById.put(item.getId(), enabled);
                DlcRuntimeState.ensureBaseline(item.getId(), enabled);
                Boolean override = DlcRuntimeState.getOverride(item.getId());
                if (override != null) {
                    item.setEnabled(override);
                }
                allItems.add(item);
            }
            if (activeFilter != null && type != activeFilter) {
                continue;
            }
            List<DlcItem> visibleItems = typeItems.stream()
                .filter(item -> !item.isHidden())
                .toList();
            if (visibleItems.isEmpty()) {
                continue;
            }
            if (activeFilter == null) {
                rows.add(DlcListRow.groupHeader(type));
            }
            Map<String, List<DlcItem>> byPath = groupByRelativeFolder(visibleItems);
            boolean hasSubDir = byPath.keySet().stream().anyMatch(key -> !"__root__".equals(key));
            boolean firstPathHeader = true;
            for (Map.Entry<String, List<DlcItem>> entry : byPath.entrySet()) {
                if ("__root__".equals(entry.getKey())) {
                    if (hasSubDir) {
                        rows.add(DlcListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), true, firstPathHeader));
                        firstPathHeader = false;
                    }
                } else {
                    rows.add(DlcListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), false, firstPathHeader));
                    firstPathHeader = false;
                }
                for (DlcItem item : entry.getValue()) {
                    boolean enabled = enabledById.getOrDefault(item.getId(), item.isEnabled());
                    DlcRuntimeState.ensureBaseline(item.getId(), enabled);
                    Boolean override = DlcRuntimeState.getOverride(item.getId());
                    if (override != null) {
                        item.setEnabled(override);
                    }
                    item.setDirty(isItemModified(item));
                    rows.add(DlcListRow.item(item));
                    items.add(item);
                }
            }
        }
        DlcRuntimeState.updateItemIndex(allItems);
        DlcPresetRuntimeState.applyOverridesToRuntime();
        hasModChanges = computeModReloadChanges();
        updateHeaderButtonsState();
    }

    private void appendRedirectRows() {
        List<DlcRedirectEntry> redirectEntries = new ArrayList<>();
        for (Path dlcRoot : DlcConfig.getDlcRoots()) {
            redirectEntries.addAll(DlcRedirectLoader.load(dlcRoot));
        }
        if (redirectEntries.isEmpty()) {
            return;
        }
        String lang = Minecraft.getInstance().options.languageCode;
        List<DlcRedirectEntry> visible = redirectEntries.stream()
            .filter(entry -> entry != null && !entry.isHidden() && entry.isAvailableFor(lang))
            .toList();
        if (visible.isEmpty()) {
            return;
        }
        rows.add(DlcListRow.redirectHeader(translate("dlc_manager.redirect.header")));
        for (DlcRedirectEntry entry : visible) {
            rows.add(DlcListRow.redirectItem(entry));
        }
    }

    private Map<String, List<DlcItem>> groupByRelativeFolder(List<DlcItem> items) {
        Map<String, List<DlcItem>> grouped = new TreeMap<>();
        for (DlcItem item : items) {
            String label = resolveGroupLabel(item);
            grouped.computeIfAbsent(label, key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private String resolveGroupLabel(DlcItem item) {
        try {
            Path source = item.getSourcePath();
            String relative = null;
            for (Path dlcRoot : DlcConfig.getDlcRoots()) {
                if (source.startsWith(dlcRoot)) {
                    relative = dlcRoot.relativize(source).toString().replace('\\', '/');
                    break;
                }
            }
            if (relative == null) {
                relative = DlcPaths.gameDir().relativize(source).toString().replace('\\', '/');
            }
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash <= 0) {
                return "__root__";
            }
            return relative.substring(0, lastSlash);
        } catch (Exception ignored) {
            return "__root__";
        }
    }

    private String resolvePathLabel(String raw) {
        if ("__root__".equals(raw)) {
            return translate("dlc_manager.group.root");
        }
        return raw;
    }

    private String getDlcApplyModeLabel() {
        String modeName = translate(applyMode.getLabelKey());
        return translate("dlc_manager.apply_mode_label", modeName);
    }

    private void syncVisibleItemOverrides() {
        for (DlcItem item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            DlcRuntimeState.setOverride(item.getId(), item.isEnabled());
        }
    }

    private void applyChanges() {
        boolean applyNeeded = computeApplyChanges();
        boolean modReloadNeeded = computeModReloadChanges();
        boolean otherReloadNeeded = computeNonModReloadChanges();
        boolean requiredPending = DlcRequiredRuntimeState.hasPendingChanges();
        boolean presetChanged = DlcConfig.isPresetFuncOn() && hasPresetChanges();
        if (!applyNeeded && !modReloadNeeded && !otherReloadNeeded) {
            if (presetChanged) {
                DlcPresetRuntimeState.syncStatusFromRuntime();
            }
            if (requiredPending) {
                minecraft.setScreen(new DlcConfirmScreen(accepted -> {
                    if (accepted) {
                        minecraft.stop();
                    } else {
                        minecraft.setScreen(parent);
                    }
                }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.required.restart_prompt"),
                    Component.translatable("dlc_manager.restart_now"),
                    Component.translatable("dlc_manager.restart_later")));
            } else {
                minecraft.setScreen(parent);
            }
            return;
        }

        DlcApplier.ApplyResult applyResult = null;
        if (applyNeeded) {
            applyResult = DlcApplier.apply(allItems, applyMode);
            if (applyResult != null && applyResult.requiresRestart()) {
                modReloadNeeded = true;
            }
            if (applyResult != null && applyResult.helperScheduled()) {
                modReloadNeeded = true;
                otherReloadNeeded = false;
            }
            updateResourcePackSelection();
            if (shouldShowApplyFailureScreen(applyResult)) {
                showApplyFailureScreen(applyResult);
                return;
            }
        }
        if (DlcConfig.isPresetFuncOn() && (presetChanged || applyNeeded)) {
            DlcPresetRuntimeState.syncStatusFromRuntime();
        }

        if (!modReloadNeeded && !otherReloadNeeded) {
            if (requiredPending) {
                minecraft.setScreen(new DlcConfirmScreen(accepted -> {
                    if (accepted) {
                        minecraft.stop();
                    } else {
                        minecraft.setScreen(parent);
                    }
                }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.required.restart_prompt"),
                    Component.translatable("dlc_manager.restart_now"),
                    Component.translatable("dlc_manager.restart_later")));
            } else {
                minecraft.setScreen(parent);
            }
            return;
        }

        boolean helperScheduled = applyResult != null && applyResult.helperScheduled();
        String message;
        if (helperScheduled) {
            message = "dlc_manager.apply.deferred_restart";
        } else if (modReloadNeeded) {
            message = "dlc_manager.message.restart";
        } else if (otherReloadNeeded && applyMode == DlcApplyMode.TRY_HOT_RELOAD) {
            message = "dlc_manager.message.reload_prompt";
        } else {
            message = "dlc_manager.message.reopen_world";
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (applyMode == DlcApplyMode.RESTART_ONLY) {
            minecraft.setScreen(new DlcConfirmScreen(accepted -> {
                if (accepted) {
                    minecraft.stop();
                } else {
                    minecraft.setScreen(parent);
                }
            }, Component.translatable("dlc_manager.title"), Component.translatable(message),
                Component.translatable("dlc_manager.restart_now"),
                Component.translatable("dlc_manager.restart_later")));
        } else if (applyMode == DlcApplyMode.TRY_HOT_RELOAD) {
            minecraft.setScreen(new DlcConfirmScreen(accepted -> {
                if (accepted) {
                    var future = tryReloadResources();
                    future.thenRun(() -> minecraft.execute(() -> {
                        updateBaselineAfterReload();
                        minecraft.setScreen(parent);
                    }));
                } else {
                    minecraft.setScreen(parent);
                }
            }, Component.translatable("dlc_manager.title"), Component.translatable(message),
                Component.translatable("dlc_manager.reload_now"),
                Component.translatable("dlc_manager.reload_later")));
        } else {
            minecraft.setScreen(new DlcConfirmScreen(accepted -> minecraft.setScreen(parent),
                Component.translatable("dlc_manager.title"), Component.translatable(message)));
        }
    }

    private boolean shouldShowApplyFailureScreen(DlcApplier.ApplyResult result) {
        return hasFailureMessage(result);
    }

    private boolean hasFailureMessage(DlcApplier.ApplyResult result) {
        if (result == null || result.messages() == null) {
            return false;
        }
        for (String message : result.messages()) {
            if (message != null && message.startsWith("Failed to ")) {
                return true;
            }
        }
        return false;
    }

    private void showApplyFailureScreen(DlcApplier.ApplyResult result) {
        minecraft.setScreen(new DlcSingleButtonScreen(parent,
            Component.translatable("dlc_manager.server.apply_failed"),
            Component.literal(joinFailureMessages(result)),
            Component.translatable("dlc_manager.back")));
    }

    private String joinFailureMessages(DlcApplier.ApplyResult result) {
        if (result == null || result.messages() == null) {
            return "";
        }
        List<String> failureMessages = new ArrayList<>();
        for (String message : result.messages()) {
            if (message != null && message.startsWith("Failed to ")) {
                failureMessages.add(message);
            }
        }
        return String.join("\n", failureMessages);
    }

    private void updateModeAvailability() {
        boolean modChanges = computeModReloadChanges();
        boolean otherChanges = computeNonModReloadChanges();
        hasModChanges = modChanges;
        if (modChanges) {
            applyMode = DlcApplyMode.RESTART_ONLY;
            userSelectedDlcApplyMode = false;
        } else if (otherChanges) {
            if (!userSelectedDlcApplyMode) {
                applyMode = DlcApplyMode.TRY_HOT_RELOAD;
            }
        } else {
            if (!userSelectedDlcApplyMode) {
                applyMode = DlcApplyMode.RESTART_ONLY;
            }
        }
        if (modeButton != null) {
            modeButton.setEnabled(!modChanges);
            modeButton.setDisplayString(getDlcApplyModeLabel());
        }
        updateBackButtonLabel();
    }

    private String getBackButtonLabel() {
        boolean hasChanges = computeApplyChanges() || hasPresetChanges();
        return hasChanges
            ? translate("dlc_manager.back_discard")
            : translate("dlc_manager.back");
    }

    private void updateBackButtonLabel() {
        if (backButton == null) {
            return;
        }
        backButton.setDisplayString(getBackButtonLabel());
        backButton.setEnabled(true);
    }

    private boolean computeModReloadChanges() {
        for (DlcItem item : allItems) {
            if (item.getType() != DlcType.MOD && !DlcConfigTypeSupport.isConfigType(item)) {
                continue;
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true;
            }
        }
        return false;
    }

    private boolean computeNonModReloadChanges() {
        for (DlcItem item : allItems) {
            if (item.getType() == DlcType.MOD || DlcConfigTypeSupport.isConfigType(item)) {
                continue;
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true;
            }
        }
        return false;
    }

    private boolean isItemModified(DlcItem item) {
        return hasPendingApplyChange(item);
    }

    private boolean computeApplyChanges() {
        if (DlcPresetRuntimeState.hasPendingFileConflictChanges()) {
            return true;
        }
        for (DlcItem item : allItems) {
            if (hasPendingApplyChange(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPendingApplyChange(DlcItem item) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            return false;
        }
        DlcStateConfig.DlcState state = DlcStateConfig.getState(item.getId());
        boolean appliedEnabled = state != null && state.enabled;
        return appliedEnabled != item.isEnabled();
    }

    private java.util.concurrent.CompletableFuture<Void> tryReloadResources() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.getToasts(),
                Component.translatable("dlc_manager.toast.reload_title"),
                Component.translatable("dlc_manager.toast.reload_start"));
            var future = minecraft.reloadResourcePacks();
            future.thenRun(() -> minecraft.execute(() ->
                DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.getToasts(),
                    Component.translatable("dlc_manager.toast.reload_title"),
                    Component.translatable("dlc_manager.toast.reload_done"))));
            return future;
        } catch (Throwable ignored) {
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    private void updateBaselineAfterReload() {
        for (DlcItem item : items) {
            if (item.getType() == DlcType.MOD) {
                continue;
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled());
        }
    }

    private void updateResourcePackSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repo = minecraft.getResourcePackRepository();
        try {
            repo.reload();
            Set<String> selected = new LinkedHashSet<>(repo.getSelectedIds());

            for (DlcItem item : items) {
                if (item.getType() != DlcType.RESOURCE_PACK) {
                    continue;
                }
                String fileName = item.getSourcePath().getFileName().toString();
                String packId = resolvePackId(repo, fileName);
                if (item.isEnabled() && packId == null) {
                    Path target = minecraft.getResourcePackDirectory().resolve(fileName);
                    copyRecursive(item.getSourcePath(), target);
                    repo.reload();
                    packId = resolvePackId(repo, fileName);
                }
                if (packId == null) {
                    continue;
                }
                if (item.isEnabled()) {
                    selected.add(packId);
                } else {
                    selected.remove(packId);
                }
            }

            repo.setSelected(selected);
            minecraft.options.resourcePacks = new java.util.ArrayList<>(selected);
            minecraft.options.save();
        } catch (Throwable ignored) {
        }
    }

    private String resolvePackId(PackRepository repo, String fileName) {
        String fileId = "file/" + fileName;
        if (repo.isAvailable(fileId)) {
            return fileId;
        }
        if (repo.isAvailable(fileName)) {
            return fileName;
        }
        for (String id : repo.getAvailableIds()) {
            if (id.endsWith("/" + fileName) || id.endsWith(fileName)) {
                return id;
            }
        }
        return null;
    }

    private void copyRecursive(Path source, Path target) throws java.io.IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                    Path relative = source.relativize(dir);
                    Files.createDirectories(target.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Path relative = source.relativize(file);
                    Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void updateHeaderButtonsState() {
        if (disableAllButton == null) {
            return;
        }
        java.util.List<DlcItem> actionItems = getActionItems();
        boolean any = !actionItems.isEmpty();
        boolean anyEligible = false;
        boolean allEligibleEnabled = true;
        boolean allDisabled = true;
        for (DlcItem item : actionItems) {
            if (item.isEnabled()) {
                allDisabled = false;
            }
            if (item.AllowEnableByAll() && !DlcRuntimeState.hasMissingDependencies(item)) {
                anyEligible = true;
                if (!item.isEnabled()) {
                    allEligibleEnabled = false;
                }
            }
            if (!allDisabled && !allEligibleEnabled && anyEligible) {
                break;
            }
        }
        HeaderLabels labels = getHeaderLabels();

        if (!any) {
            if (enableAllButton != null) {
                enableAllButton.setEnabled(false);
                enableAllButton.setDisplayString(labels.enableLabel);
            }
            disableAllButton.setEnabled(false);
            disableAllButton.setDisplayString(labels.disableLabel);
            return;
        }
        if (enableAllButton != null) {
            if (!anyEligible) {
                enableAllButton.setEnabled(false);
                enableAllButton.setDisplayString(labels.enableLabel);
            } else if (allEligibleEnabled) {
                enableAllButton.setEnabled(false);
                enableAllButton.setDisplayString(labels.enableDone);
            } else {
                enableAllButton.setEnabled(true);
                enableAllButton.setDisplayString(labels.enableLabel);
            }
        }
        if (allDisabled) {
            disableAllButton.setEnabled(false);
            disableAllButton.setDisplayString(labels.disableDone);
        } else {
            disableAllButton.setEnabled(true);
            disableAllButton.setDisplayString(labels.disableLabel);
        }
    }

    private java.util.List<DlcItem> getActionItems() {
        return allItems.stream()
            .filter(item -> activeFilter == null || item.getType() == activeFilter)
            .filter(item -> searchQuery.isBlank() || (!item.isHidden() && matchesItem(item)))
            .toList();
    }

    private void handleBatchEnable(java.util.List<DlcItem> targets, Component scopeLabel) {
        DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(targets);
        if (!conflicts.getLines().isEmpty()) {
            java.util.List<DlcItem> filteredTargets = filterConflictTargets(targets, conflicts);
            showBatchConflictSkipPrompt(conflicts.getLines(), () -> {
                if (filteredTargets.isEmpty()) {
                    updateModeAvailability();
                    return;
                }
                continueBatchEnable(filteredTargets, scopeLabel);
            });
            return;
        }
        continueBatchEnable(targets, scopeLabel);
    }

    private void continueBatchEnable(java.util.List<DlcItem> targets, Component scopeLabel) {
        if (targets == null || targets.isEmpty()) {
            updateModeAvailability();
            return;
        }
        DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(targets);
        if (!deps.getLines().isEmpty()) {
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel);
            return;
        }
        java.util.List<DlcItem> alarmItems = targets.stream()
            .filter(DlcItem::isAlarm)
            .toList();
        if (!alarmItems.isEmpty()) {
            rememberListScroll();
            String tip = DlcConfirmUtil.buildBatchAlarmTip(alarmItems);
            Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(this, tip, () -> {
                enableItems(targets, true);
                refreshAfterStateChange();
            }));
            return;
        }
        enableItems(targets, true);
        updateModeAvailability();
    }

    private java.util.List<DlcItem> filterConflictTargets(java.util.List<DlcItem> targets, DlcRuntimeState.ConflictBatchResult conflicts) {
        if (targets == null || targets.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Set<String> targetIds = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item != null && item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                targetIds.add(item.getIdentifier());
            }
        }
        if (targetIds.isEmpty()) {
            return java.util.List.of();
        }
        if (conflicts == null || conflicts.getLines().isEmpty()) {
            return targets;
        }
        java.util.Set<String> skipped = new java.util.HashSet<>();
        for (DlcRuntimeState.ConflictLine line : conflicts.getLines()) {
            String left = line.getLeftName();
            String right = line.getRightName();
            if (left != null && targetIds.contains(left)) {
                skipped.add(left);
            }
            if (right != null && targetIds.contains(right)) {
                skipped.add(right);
            }
        }
        if (skipped.isEmpty()) {
            return targets;
        }
        return targets.stream()
            .filter(item -> item != null && (item.getIdentifier() == null || !skipped.contains(item.getIdentifier())))
            .toList();
    }

    private void showBatchConflictSkipPrompt(java.util.List<DlcRuntimeState.ConflictLine> lines, Runnable onSkipConfirm) {
        Component message = DlcConfirmUtil.buildConflictMessage(lines);
        Component title = Component.translatable("dlc_manager.batch.conflicts_title")
            .withStyle(net.minecraft.ChatFormatting.DARK_RED);
        rememberListScroll();
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (!accepted) {
                return;
            }
            if (onSkipConfirm != null) {
                onSkipConfirm.run();
            }
        }, title, message,
            Component.translatable("dlc_manager.batch.conflicts_skip_enable"),
            DlcConfirmUtil.confirmNo()));
    }

    private void enableItems(java.util.Collection<DlcItem> items, boolean enabled) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            item.setEnabled(enabled);
            DlcRuntimeState.setOverride(item.getId(), enabled);
        }
    }

    private void disableItemsWithDependents(java.util.Collection<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<DlcItem> toDisable = new java.util.LinkedHashSet<>();
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            toDisable.addAll(DlcRuntimeState.collectDependentsRecursive(item));
        }
        enableItems(toDisable, false);
        java.util.Set<DlcItem> hiddenDeps = DlcRuntimeState.collectHiddenDependenciesToDisable(toDisable);
        enableItems(hiddenDeps, false);
    }

    private void showBatchDependencyPrompt(java.util.List<DlcRuntimeState.DependencyLine> lines,
        java.util.List<DlcItem> depsToEnable, java.util.List<DlcItem> targets, Component scopeLabel) {
        Component message = DlcConfirmUtil.buildDependencyMessage(lines);
        Component title = Component.translatable("dlc_manager.batch.dependencies_title");
        Component enableLabel = Component.translatable("dlc_manager.batch.enable_deps", scopeLabel);
        rememberListScroll();
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (!accepted) {
                return;
            }
            java.util.ArrayList<DlcItem> planned = new java.util.ArrayList<>();
            if (depsToEnable != null) {
                planned.addAll(depsToEnable);
            }
            if (targets != null) {
                planned.addAll(targets);
            }
            DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(planned);
            if (!conflicts.getLines().isEmpty()) {
                java.util.List<DlcItem> filteredTargets = filterConflictTargets(planned, conflicts);
                if (filteredTargets.isEmpty()) {
                    refreshAfterStateChange();
                    return;
                }
                showBatchConflictSkipPrompt(conflicts.getLines(), () -> {
                    enableItems(filteredTargets, true);
                    refreshAfterStateChange();
                });
                return;
            }
            enableItems(planned, true);
            refreshAfterStateChange();
        }, title, message, enableLabel, DlcConfirmUtil.confirmNo()));
    }

    private HeaderLabels getHeaderLabels() {
        if (activeFilter == null && searchQuery.isBlank()) {
            return new HeaderLabels(
                translate("dlc_manager.enable_all"),
                translate("dlc_manager.disable_all"),
                translate("dlc_manager.all_enabled"),
                translate("dlc_manager.all_disabled"));
        }
        String typeName = searchQuery.isBlank() ? translate(activeFilter.getDisplayKey()) : translate("dlc_manager.scope.search");
        return new HeaderLabels(
            translate("dlc_manager.enable_all_type", typeName),
            translate("dlc_manager.disable_all_type", typeName),
            translate("dlc_manager.all_enabled_type", typeName),
            translate("dlc_manager.all_disabled_type", typeName));
    }

    private void returnToMainMenu() {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot);
        }
        DlcRuntimeState.clearOverrides();
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    private void capturePresetSnapshot() {
        if (presetSnapshotCaptured) {
            return;
        }
        if (!DlcConfig.isPresetFuncOn()) {
            presetSnapshot.clear();
            presetSnapshotCaptured = true;
            return;
        }
        presetSnapshot.clear();
        presetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds());
        presetSnapshotCaptured = true;
    }

    private boolean hasPresetChanges() {
        if (!DlcConfig.isPresetFuncOn()) {
            return false;
        }
        if (!presetSnapshotCaptured) {
            return false;
        }
        return !DlcPresetRuntimeState.getEnabledPresetIds().equals(presetSnapshot);
    }

    private static final class HeaderLabels {
        private final String enableLabel;
        private final String disableLabel;
        private final String enableDone;
        private final String disableDone;

        private HeaderLabels(String enableLabel, String disableLabel, String enableDone, String disableDone) {
            this.enableLabel = enableLabel;
            this.disableLabel = disableLabel;
            this.enableDone = enableDone;
            this.disableDone = disableDone;
        }
    }

    public static String getPendingSuffix(DlcItem item) {
        if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
            return "";
        }
        if (item.getType() == DlcType.MOD || DlcConfigTypeSupport.isConfigType(item)) {
            return translate("dlc_manager.pending.restart");
        }
        return translate("dlc_manager.pending.reload");
    }

    public static void refreshCurrentOpenScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !(minecraft.screen instanceof DlcManagerScreen screen)) {
            return;
        }
        screen.refreshAfterStateChange();
    }

    private void handleBatchToggle(boolean enable) {
        Component activeFilterLabel = !searchQuery.isBlank() ? Component.translatable("dlc_manager.scope.search")
            : activeFilter == null ? null : Component.translatable(activeFilter.getDisplayKey());
        Component message = DlcConfirmUtil.buildHeaderConfirmMessage(enable, activeFilterLabel);
        rememberListScroll();
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(DlcManagerScreen.this);
            if (!accepted) {
                return;
            }
            java.util.List<DlcItem> actionItems = getActionItems();
            if (enable) {
                java.util.List<DlcItem> targets = actionItems.stream()
                    .filter(item -> item.AllowEnableByAll()
                        && !DlcRuntimeState.hasMissingDependencies(item)
                        && !item.isEnabled())
                    .toList();
                if (!targets.isEmpty()) {
                    Component scopeLabel = !searchQuery.isBlank() ? Component.translatable("dlc_manager.scope.search") : activeFilter == null
                        ? Component.translatable("dlc_manager.scope.all")
                        : Component.translatable("dlc_manager.scope.group");
                    handleBatchEnable(targets, scopeLabel);
                }
            } else {
                disableItemsWithDependents(actionItems);
                updateModeAvailability();
            }
        }, Component.translatable("dlc_manager.title"),
            message,
            DlcConfirmUtil.confirmYes(),
            DlcConfirmUtil.confirmNo()));
    }

    public DlcManagerScreen(Screen parent) {
        super(DlcUiPage.MANAGER);
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (DlcRemoteManager.tryShowFromManager(this)) return;
        if (!noticeChecked) {
            noticeChecked = true;
            if (DlcNoticeManager.tryShowFromManager(this)) return;
        }
        rebuildRows();
        capturePresetSnapshot();
        super.init();
    }

    @Override
    protected void bindDocument(Document document) {
        bindManagerLayout(document, translate("dlc_manager.title"));
        Element tools = document.getElementById("manager-tools");
        clearUiContent(tools);
        if (DlcConfig.isUpdateManagerOn() && !DlcConfig.isUButtomShown())
            button(document, tools, translate("dlc_manager.update.manager"), () -> open(() -> DlcUpdateClient.openUpdateManager(this)));
        if (DlcConfig.isNoticeDisplayModeManagerButton() && hasNoticeContent())
            button(document, tools, translate("dlc_manager.notice"), () -> open(() -> DlcNoticeManager.forceShow(this)));
        if (DlcConfig.isPresetFuncOn())
            button(document, tools, translate("dlc_manager.preset.manager"), () -> open(() -> Minecraft.getInstance().setScreen(new DlcPresetManagerScreen(this))));
        if (DlcConfig.isPackFuncOn() && !DlcConfig.isPButtomShown())
            button(document, tools, translate("dlc_manager.pack.manager"), () -> open(() -> Minecraft.getInstance().setScreen(new DlcPackManagerScreen(this))));
        if (canShowServerButton())
            button(document, tools, translate("dlc_manager.server.manage"), () -> open(() -> Minecraft.getInstance().setScreen(new DlcServerListScreen(this))));
        Element context = document.getElementById("manager-context");
        clearUiContent(context);
        if (DlcConfig.isConfigButtonShown())
            button(document, tools, translate("dlc_manager.config"), () -> open(() -> Minecraft.getInstance().setScreen(new DlcConfigScreen(this))));

        for (Element tool : tools.getChildren()) tool.setAttribute("class", "button nav-button");
        Element batch = document.getElementById("batch-actions");
        clearUiContent(batch);
        if (DlcRemoteManager.hasMissing())
            button(document, batch, translate("dlc_manager.remote.complete"), () -> open(() -> Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(this, DlcRemoteManager.collectMissing()))));
        enableAllButton = DlcConfig.isEnableAllButtonShown() ? button(document, batch, "", () -> handleBatchToggle(true)) : null;
        disableAllButton = button(document, batch, "", () -> handleBatchToggle(false));
        Element footer = document.getElementById("manager-actions");
        clearUiContent(footer);
        clearUiContent(document.getElementById("manager-mode"));
        modeButton = button(document, document.getElementById("manager-mode"), getDlcApplyModeLabel(), this::toggleDlcApplyMode);
        modeButton.getElement().setAttribute("class", "button mode-button");
        backButton = button(document, footer, getBackButtonLabel(), this::returnToMainMenu);
        button(document, footer, translate("dlc_manager.apply"), this::applyChanges).getElement().setAttribute("class", "button primary");
        renderFilters(document);
        renderRows(document);
        updateModeAvailability();
        updateHeaderButtonsState();
        restoreListScroll();
        if (!searchQuery.isBlank()) onManagerSearch(document, searchQuery);
    }

    private boolean hasNoticeContent() {
        DlcNoticeConfig notice = DlcNoticeConfig.load();
        return notice.isNoticeEnabled() && notice.hasContent(DlcPlatformServices.languageCode());
    }

    private java.util.Set<String> searchMatches = java.util.Set.of();
    @Override protected void onManagerSearch(Document document, String query) {
        searchQuery = query == null ? "" : query;
        searchMatches = java.util.Set.of();
        savedScrollOffset = 0;
        var list = document.getElementById("manager-list");
        list.setScrollTop(0); list.setAttribute("style", "pointer-events:none"); list.setAttribute("aria-busy", "true");
        searchClearButton.setEnabled(!searchQuery.isBlank());
        java.util.function.Consumer<java.util.Set<String>> apply = matches -> {
            searchMatches = matches; list.removeAttribute("style"); list.removeAttribute("aria-busy");
            renderRows(document); updateHeaderButtonsState();
        };
        if (searchQuery.isBlank()) {
            filterAsync("", java.util.List.of(), apply);
            return;
        }
        var entries = new ArrayList<DlcUiAsyncSearch.Entry<String>>();
        for (var item : allItems) entries.add(new DlcUiAsyncSearch.Entry<>("item:" + item.getId(), plain(DlcDisplayNameUtil.resolveDisplayName(item)), item.getIdentifier(), item.getSourcePath() == null ? "" : item.getSourcePath().toString()));
        for (var row : rows) if (row.getRedirectEntry() != null) {
            var redirect = row.getRedirectEntry();
            entries.add(new DlcUiAsyncSearch.Entry<>(redirectSearchKey(redirect), plain(redirect.resolveDisplayName(DlcPlatformServices.languageCode())), redirect.getRedirectUrl()));
        }
        updateHeaderButtonsState();
        filterAsync(searchQuery, entries, apply);
    }
    private static String redirectSearchKey(DlcRedirectEntry redirect) {
        return "redirect:" + plain(redirect.resolveDisplayName(DlcPlatformServices.languageCode())) + "\n" + redirect.getRedirectUrl();
    }
    private boolean matchesItem(DlcItem item) { return searchQuery.isBlank() || searchMatches.contains("item:" + item.getId()); }
    private boolean matchesRedirect(DlcRedirectEntry redirect) { return redirect != null && (searchQuery.isBlank() || searchMatches.contains(redirectSearchKey(redirect))); }

    private boolean matchesRow(DlcListRow row) {
        return switch (row.getType()) {
            case ITEM -> matchesItem(row.getItem());
            case PATH_HEADER -> row.getItems().stream().anyMatch(this::matchesItem);
            case GROUP_HEADER -> items.stream().anyMatch(item -> item.getType() == row.getGroupType() && matchesItem(item));
            case REDIRECT_ITEM -> matchesRedirect(row.getRedirectEntry());
            case REDIRECT_HEADER -> rows.stream().anyMatch(candidate -> candidate.getType() == DlcListRow.RowType.REDIRECT_ITEM && matchesRedirect(candidate.getRedirectEntry()));
        };
    }

    private void updatePendingStatus(Document document) {
        long count = allItems.stream().filter(item -> DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())).count();
        boolean other = hasPresetChanges() || DlcRequiredRuntimeState.hasPendingChanges() || DlcPresetRuntimeState.hasPendingFileConflictChanges();
        Element status = document.getElementById("pending-status");
        status.setTextContent(translate(count > 0 ? "dlc_manager.ui.pending" : other ? "dlc_manager.ui.pending_other" : "dlc_manager.ui.clean", count));
        status.setAttribute("class", count > 0 || other ? "pending" : "");
    }

    private void open(Runnable action) {
        rememberListScroll();
        action.run();
    }

    private void renderFilters(Document document) {
        Element filters = document.getElementById("manager-filters");
        clearUiContent(filters);
        addFilter(document, filters, null);
        for (DlcType type : DlcType.values()) {
            if (type == DlcType.DLC_PACK || (type == DlcType.TACZ_PACK && !hasTaczMod)) continue;
            addFilter(document, filters, type);
        }
    }

    private void addFilter(Document document, Element filters, DlcType type) {
        String label = translate(type == null ? "dlc_manager.filter.all" : type.getDisplayKey());
        DlcUiButton filter = button(document, filters, label, () -> setFilter(type));
        filter.getElement().setAttribute("class", type == activeFilter ? "button filter active" : "button filter");
        filter.getElement().setAttribute("aria-pressed", Boolean.toString(type == activeFilter));
        filter.getElement().setAttribute("aria-selected", Boolean.toString(type == activeFilter));
    }

    private void setFilter(DlcType type) {
        syncVisibleItemOverrides();
        activeFilter = type;
        savedScrollOffset = 0;
        savedScrollFilter = type;
        rebuildRows();
        Document document = getLinkedDocument();
        if (document != null) {
            renderFilters(document);
            renderRows(document);
            restoreListScroll();
        }
        updateModeAvailability();
        updateHeaderButtonsState();
    }

    private void toggleDlcApplyMode() {
        if (hasModChanges) return;
        applyMode = applyMode == DlcApplyMode.RESTART_ONLY ? DlcApplyMode.TRY_HOT_RELOAD : DlcApplyMode.RESTART_ONLY;
        userSelectedDlcApplyMode = true;
        updateModeAvailability();
    }

    private void renderRows(Document document) {
        if (tooltip != null) tooltip.hide();
        Element list = document.getElementById("manager-list");
        double scroll = list.getTargetScrollTop();
        clearUiContent(list);
        clearManagerGroups();
        Element rowParent = list;
        int renderedRows = 0;
        int renderedDlcCount = 0;
        int enabledDlcCount = 0;
        for (DlcListRow row : rows) {
            if (!matchesRow(row)) continue;
            renderedRows++;
            if (row.getType() != DlcListRow.RowType.ITEM) rowParent = list;
            Element entry = rowParent.appendChild(document.createElement("div"));
            switch (row.getType()) {
                case GROUP_HEADER, REDIRECT_HEADER -> {
                    entry.setAttribute("class", "group-heading");
                    entry.setTextContent(row.getGroupType() == null ? plain(row.getLabel()) : translate(row.getGroupType().getDisplayKey()));
                }
                case PATH_HEADER -> {
                    entry.setAttribute("class", "path-heading");
                    managerText(document, entry, "span", row.getLabel(), "path-label");
                    List<DlcItem> groupItems = row.getItems().stream().filter(this::matchesItem).toList();
                    if (!row.isRootPath() && DlcItemActions.canToggleGroup(groupItems))
                        button(document, entry, DlcItemActions.groupToggleLabel(groupItems), () -> {
                            rememberListScroll();
                            DlcItemActions.toggleGroup(groupItems);
                            refreshAfterStateChange();
                        });
                    String key = groupItems.get(0).getType().name() + ":" + row.getLabel();
                    rowParent = addManagerGroup(document, list, entry, key, groupItems.size());
                }
                case REDIRECT_ITEM -> {
                    entry.setAttribute("class", "dlc-row redirect-row");
                    DlcRedirectEntry redirect = row.getRedirectEntry();
                    managerText(document, entry, "span", redirect.resolveDisplayName(DlcPlatformServices.languageCode()), "item-name");
                    button(document, entry, translate("dlc_manager.redirect.open"), () -> open(() -> Minecraft.getInstance().setScreen(
                        new DlcRedirectConfirmScreen(this, redirect.getRedirectUrl(), redirect.resolveNotice(DlcPlatformServices.languageCode())))));
                }
                case ITEM -> {
                    DlcItem item = row.getItem();
                    renderedDlcCount++;
                    if (item.isEnabled()) enabledDlcCount++;
                    boolean dirty = DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled());
                    Element name = styleManagerRow(document, entry, DlcDisplayNameUtil.resolveDisplayName(item), item.isEnabled(), dirty);
                    tooltip.bind(entry, () -> itemTooltip(item));
                    if (item.getRedirectUrl() != null && !item.getRedirectUrl().isBlank()) {
                        long generation = document.getRefreshGeneration();
                        name.addEventListener("dblclick", event -> {
                            if (Minecraft.getInstance().screen == this && document.isCurrentGeneration(generation))
                                open(() -> Minecraft.getInstance().setScreen(new DlcRedirectConfirmScreen(this, item.getRedirectUrl(), "")));
                        });
                    }
                    if (dirty) managerText(document, entry, "span", getPendingSuffix(item), "pending-label");
                    DlcUiButton toggle = managerToggle(document, entry, item.isEnabled(), () -> {
                        rememberListScroll();
                        DlcItemActions.toggle(item);
                        refreshAfterStateChange();
                    });
                    toggle.setEnabled(!DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier()) && (item.isEnabled() || !DlcRuntimeState.hasMissingDependencies(item)));
                }
            }
        }
        if (renderedRows == 0) managerText(document, list, "p", translate(searchQuery.isBlank() ? "dlc_manager.ui.empty" : "dlc_manager.ui.no_results"), "empty-state");
        document.getElementById("manager-summary").setTextContent(translate("dlc_manager.ui.summary", renderedDlcCount, enabledDlcCount));
        updatePendingStatus(document);
        updateAnimations(document, System.nanoTime());
        list.setScrollTop(scroll);
    }

    private static DlcUiTooltip.Content itemTooltip(DlcItem item) {
        List<DlcUiTooltip.Detail> details = new ArrayList<>();
        Map<String, String> tips = item.getTooltips();
        String description = tips.isEmpty() ? "" : tips.getOrDefault(DlcPlatformServices.languageCode(), tips.getOrDefault("en_us", tips.values().iterator().next()));
        var missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item);
        if (!missing.isEmpty()) details.add(new DlcUiTooltip.Detail(translate("dlc_manager.tooltip.dependencies_missing"),
            String.join("&r, ", missing.stream().map(DlcDisplayNameUtil::resolveDisplayName).toList()), true));
        var dependencies = DlcItemActions.dependencyNames(item);
        if (!dependencies.isEmpty()) details.add(new DlcUiTooltip.Detail(translate("dlc_manager.tooltip.dependencies"),
            String.join("&r, ", dependencies.stream().map(DlcDisplayNameUtil::resolveDisplayName).toList()), false));
        if (!item.getConflicts().isEmpty()) details.add(new DlcUiTooltip.Detail(translate("dlc_manager.tooltip.conflicts"),
            String.join("&r, ", item.getConflicts().stream().map(DlcDisplayNameUtil::resolveDisplayName).toList()), true));
        String hint = item.getRedirectUrl() != null && !item.getRedirectUrl().isBlank() ? translate("dlc_manager.tooltip.double_click_redirect") : "";
        return new DlcUiTooltip.Content(DlcDisplayNameUtil.resolveDisplayName(item), description, details, hint);
    }

    public void refreshAfterStateChange() {
        for (DlcItem item : allItems) {
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) item.setEnabled(override);
            item.setDirty(isItemModified(item));
        }
        Document document = getLinkedDocument();
        if (document != null && Minecraft.getInstance().screen == this) renderRows(document);
        updateHeaderButtonsState();
        updateModeAvailability();
    }

    @Override
    public void rememberReturnPosition() { rememberListScroll(); }

    @Override
    public Component getTitle() { return Component.translatable("dlc_manager.title"); }

    @Override
    public void tick() {
        super.tick();
        rememberListScroll();
        updateHeaderButtonsState();
        updateModeAvailability();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        rememberListScroll();
        super.resize(minecraft, width, height);
    }

    void rememberListScroll() {
        Document document = getLinkedDocument();
        if (document == null || pendingScrollRestore) return;
        savedScrollOffset = document.getElementById("manager-list").getTargetScrollTop();
        savedScrollFilter = activeFilter;
    }

    private void restoreListScroll() {
        pendingScrollRestore = savedScrollFilter == activeFilter;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Document document = getLinkedDocument();
        if (!pendingScrollRestore || document == null || document.isInitialCommitSlicing()) return;
        Element list = document.getElementById("manager-list");
        if (list.getClientHeight() > 0 && "auto".equals(list.getComputedStyle().overflowY)) {
            Document.runWithContext(document, () -> list.setScrollTop(savedScrollOffset));
            pendingScrollRestore = false;
        }
    }

    @Override
    public void onClose() {
        if (tooltip != null) tooltip.hide();
        if (hasPresetChanges()) DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot);
        DlcRuntimeState.clearOverrides();
        super.onClose();
        Minecraft.getInstance().setScreen(parent);
    }

    private static String translate(String key, Object... args) { return plain(Component.translatable(key, args).getString()); }
    private static String plain(String value) { return value == null ? "" : DlcTextFormatter.parse(value).getString(); }
}
