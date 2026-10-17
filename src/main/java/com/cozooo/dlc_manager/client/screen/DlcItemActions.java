package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;

public final class DlcItemActions {

    private static GroupToggleState getGroupToggleState(java.util.List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return new GroupToggleState("", false, false);
        }
        boolean anyEligibleDisabled = items.stream().anyMatch(item -> item.AllowEnableByAll()
            && !DlcRuntimeState.hasMissingDependencies(item)
            && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
            && !item.isEnabled());
        boolean anyEnabled = items.stream().anyMatch(DlcItem::isEnabled);
        if (anyEligibleDisabled) {
            return new GroupToggleState(DlcTextCompat.translatable("dlc_manager.group.enable").getString(), true, true);
        }
        if (anyEnabled) {
            return new GroupToggleState(DlcTextCompat.translatable("dlc_manager.group.disable").getString(), true, true);
        }
        return new GroupToggleState("", false, false);
    }

    private static java.util.List<String> collectDependencyNamesRecursive(DlcItem item) {
        if (item == null) {
            return java.util.List.of();
        }
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        addDependencyNames(item.getDependencies(), result, queue);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (name == null || name.isBlank()) {
                continue;
            }
            DlcItem depItem = DlcRuntimeState.getItemByIdentifier(name);
            if (depItem == null) {
                continue;
            }
            addDependencyNames(depItem.getDependencies(), result, queue);
        }
        return new java.util.ArrayList<>(result);
    }

    private static void addDependencyNames(java.util.List<String> names, java.util.Set<String> result,
                                           java.util.ArrayDeque<String> queue) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (result.add(name)) {
                queue.add(name);
            }
        }
    }

    private static void continueBatchEnable(java.util.List<DlcItem> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(targets);
        if (!deps.getLines().isEmpty()) {
            Component scopeLabel = DlcTextCompat.translatable("dlc_manager.scope.group");
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel);
            return;
        }
        java.util.List<DlcItem> alarmItems = targets.stream()
            .filter(DlcItem::isAlarm)
            .toList();
        if (!alarmItems.isEmpty()) {
            showBatchAlarmPrompt(alarmItems, () -> enableItems(targets, true));
            return;
        }
        enableItems(targets, true);
    }

    private static boolean isDirty(DlcItem item) {
        return DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled());
    }

    private static void requestEnable(DlcItem item) {
        if (item == null) {
            return;
        }
        if (item.isAlarm()) {
            Screen returnScreen = Minecraft.getInstance().screen;
            DlcReturnPositionHost.remember(returnScreen);
            String tip = resolveLocalized(item.getTooltips());
            Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(returnScreen, tip, () -> {
                attemptEnable(item);
                if (returnScreen instanceof DlcManagerScreen screen) {
                    screen.refreshAfterStateChange();
                }
            }));
            return;
        }
        attemptEnable(item);
    }

    private static void attemptEnable(DlcItem item) {
        ConflictCheck conflicts = getConflicts(item);
        if (!conflicts.items.isEmpty()) {
            showConflictPrompt(item, conflicts);
            return;
        }

        DependencyCheck dependencies = getMissingDependencies(item);
        if (!dependencies.names.isEmpty()) {
            showDependencyPrompt(item, dependencies);
            return;
        }

        item.setEnabled(true);
        DlcRuntimeState.setOverride(item.getId(), true);

        item.setDirty(isDirty(item));
    }

    private static void continueEnableAfterConflictResolution(DlcItem item) {
        int guard = 0;
        while (guard++ < 64) {
            ConflictCheck conflicts = getConflicts(item);
            if (conflicts.items.isEmpty()) {
                break;
            }
            disableItemsWithDependents(conflicts.items);
        }

        DependencyCheck dependencies = getMissingDependencies(item);
        if (!dependencies.names.isEmpty()) {
            showDependencyPrompt(item, dependencies);
            return;
        }

        item.setEnabled(true);
        DlcRuntimeState.setOverride(item.getId(), true);

        item.setDirty(isDirty(item));
    }

    private static String resolveLocalized(java.util.Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String lang = Minecraft.getInstance().options.languageCode;
        if (values.containsKey(lang)) {
            return values.get(lang);
        }
        if (values.containsKey("en_us")) {
            return values.get("en_us");
        }
        return values.values().stream().findFirst().orElse("");
    }

    private static void showBatchAlarmPrompt(java.util.List<DlcItem> alarmItems, Runnable onConfirm) {
        Screen returnScreen = Minecraft.getInstance().screen;
        DlcReturnPositionHost.remember(returnScreen);
        String tip = DlcConfirmUtil.buildBatchAlarmTip(alarmItems);
        Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(returnScreen, tip, () -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            if (returnScreen instanceof DlcManagerScreen screen) {
                screen.refreshAfterStateChange();
            }
        }));
    }

    private static java.util.List<DlcItem> filterConflictTargets(java.util.List<DlcItem> targets, DlcRuntimeState.ConflictBatchResult conflicts) {
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

    private static DependencyCheck getMissingDependencies(DlcItem item) {
        DlcRuntimeState.DependencyBatchResult result = DlcRuntimeState.collectMissingDependencies(java.util.List.of(item));
        if (result.getLines().isEmpty()) {
            return new DependencyCheck(java.util.List.of(), java.util.List.of());
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (DlcRuntimeState.DependencyLine line : result.getLines()) {
            names.add(DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName()));
        }
        return new DependencyCheck(new java.util.ArrayList<>(names), result.getItemsToEnable());
    }

    private static ConflictCheck getConflicts(DlcItem item) {
        if (item == null) {
            return new ConflictCheck(java.util.List.of(), java.util.List.of());
        }
        DlcRuntimeState.ConflictBatchResult batch = DlcRuntimeState.collectConflicts(java.util.List.of(item));
        if (batch.getLines().isEmpty()) {
            return new ConflictCheck(java.util.List.of(), java.util.List.of());
        }
        String rootId = item.getIdentifier();
        java.util.Set<String> conflictIds = new java.util.LinkedHashSet<>();
        for (DlcRuntimeState.ConflictLine line : batch.getLines()) {
            String left = line.getLeftName();
            String right = line.getRightName();
            if (left != null && !left.isBlank() && (rootId == null || !left.equals(rootId))) {
                conflictIds.add(left);
            }
            if (right != null && !right.isBlank() && (rootId == null || !right.equals(rootId))) {
                conflictIds.add(right);
            }
        }
        java.util.List<DlcItem> toDisable = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String identifier : conflictIds) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcItem conflictItem = DlcRuntimeState.getItemByIdentifier(identifier);
            if (conflictItem != null && conflictItem.isEnabled()) {
                toDisable.add(conflictItem);
                names.add(DlcDisplayNameUtil.resolveDisplayName(identifier));
            }
        }
        return new ConflictCheck(names, toDisable);
    }

    private static void enableItems(java.util.Collection<DlcItem> items, boolean enabled) {
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

    private static void disableItemsWithDependents(java.util.Collection<DlcItem> items) {
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

    private static void showBatchConflictPrompt(java.util.List<DlcRuntimeState.ConflictLine> lines, Runnable onSkipConfirm) {
        Component message = DlcConfirmUtil.buildConflictMessage(lines);
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcManagerScreen screen) {
            screen.rememberListScroll();
        }
        Component title = DlcTextCompat.translatable("dlc_manager.batch.conflicts_title")
            .withStyle(net.minecraft.ChatFormatting.RED);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            if (onSkipConfirm != null) {
                onSkipConfirm.run();
            }
            if (returnScreen instanceof DlcManagerScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, title, message,
            DlcTextCompat.translatable("dlc_manager.batch.conflicts_skip_enable"),
            DlcConfirmUtil.confirmNo()));
    }

    private static void showBatchDependencyPrompt(java.util.List<DlcRuntimeState.DependencyLine> lines,
        java.util.List<DlcItem> depsToEnable, java.util.List<DlcItem> targets, Component scopeLabel) {
        if (!DlcConfig.isShowDependencyReminder()) {
            enableBatchDependencies(depsToEnable, targets, null);
            return;
        }
        Component message = DlcConfirmUtil.buildDependencyMessage(lines);
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcManagerScreen screen) {
            screen.rememberListScroll();
        }
        Component title = DlcTextCompat.translatable("dlc_manager.batch.dependencies_title");
        Component enableLabel = DlcTextCompat.translatable("dlc_manager.batch.enable_deps", scopeLabel);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            enableBatchDependencies(depsToEnable, targets, () -> {
                if (returnScreen instanceof DlcManagerScreen screen) {
                    screen.refreshAfterStateChange();
                }
            });
        }, title, message, enableLabel, DlcConfirmUtil.confirmNo()));
    }

    private static void enableBatchDependencies(java.util.List<DlcItem> depsToEnable,
        java.util.List<DlcItem> targets, Runnable onEnabled) {
        java.util.ArrayList<DlcItem> planned = new java.util.ArrayList<>();
        if (depsToEnable != null) {
            planned.addAll(depsToEnable);
        }
        if (targets != null) {
            planned.addAll(targets);
        }
        DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(planned);
        if (!conflicts.getLines().isEmpty()) {
            java.util.List<DlcItem> filtered = filterConflictTargets(planned, conflicts);
            showBatchConflictPrompt(conflicts.getLines(), () -> {
                enableItems(filtered, true);
                if (onEnabled != null) {
                    onEnabled.run();
                }
            });
            return;
        }
        enableItems(planned, true);
        if (onEnabled != null) {
            onEnabled.run();
        }
    }

    private static void showDependencyPrompt(DlcItem item, DependencyCheck dependencies) {
        if (dependencies.names.isEmpty()) {
            return;
        }
        if (!DlcConfig.isShowDependencyReminder()) {
            enableItems(dependencies.items, true);
            enableItems(java.util.List.of(item), true);
            item.setDirty(isDirty(item));
            return;
        }
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcManagerScreen screen) {
            screen.rememberListScroll();
        }
        Component message = DlcConfirmUtil.buildDependencyPromptMessage(dependencies.names);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            for (DlcItem dep : dependencies.items) {
                dep.setEnabled(true);
                DlcRuntimeState.setOverride(dep.getId(), true);
            }
            item.setEnabled(true);
            DlcRuntimeState.setOverride(item.getId(), true);

            if (returnScreen instanceof DlcManagerScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, DlcTextCompat.translatable("dlc_manager.title"), message,
            DlcTextCompat.translatable("dlc_manager.dependency.enable"),
            DlcConfirmUtil.confirmNo()));
    }

    private static void showConflictPrompt(DlcItem item, ConflictCheck conflicts) {
        if (conflicts.items.isEmpty()) {
            return;
        }
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcManagerScreen screen) {
            screen.rememberListScroll();
        }
        Component message = DlcConfirmUtil.buildConflictPromptMessage(conflicts.names);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            continueEnableAfterConflictResolution(item);
            if (returnScreen instanceof DlcManagerScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, DlcTextCompat.translatable("dlc_manager.title"), message,
            DlcTextCompat.translatable("dlc_manager.conflict.resolve"),
            DlcConfirmUtil.confirmNo()));
    }

    public static void toggle(DlcItem item) {
        if (item.isEnabled()) {
            disableItemsWithDependents(java.util.List.of(item));

            item.setDirty(isDirty(item));
            return;
        }

        if (DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())) {
            return;
        }

        requestEnable(item);
    }

    public static void toggleGroup(java.util.List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        GroupToggleState state = getGroupToggleState(items);
        if (!state.show) {
            return;
        }
        boolean anyEligibleDisabled = items.stream().anyMatch(item -> item.AllowEnableByAll()
            && !DlcRuntimeState.hasMissingDependencies(item)
            && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
            && !item.isEnabled());
        boolean target = anyEligibleDisabled;
        if (target) {
            java.util.List<DlcItem> targets = items.stream()
                .filter(item -> item.AllowEnableByAll()
                    && !DlcRuntimeState.hasMissingDependencies(item)
                    && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                    && !item.isEnabled())
                .toList();
            DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(targets);
            if (!conflicts.getLines().isEmpty()) {
                java.util.List<DlcItem> filteredTargets = filterConflictTargets(targets, conflicts);
                showBatchConflictPrompt(conflicts.getLines(), () -> continueBatchEnable(filteredTargets));
                return;
            }
            continueBatchEnable(targets);
        } else {
            disableItemsWithDependents(items);
        }

    }

    private static final class DependencyCheck {
        private final java.util.List<String> names;
        private final java.util.List<DlcItem> items;

        private DependencyCheck(java.util.List<String> names, java.util.List<DlcItem> items) {
            this.names = names;
            this.items = items;
        }
    }

    private static final class ConflictCheck {
        private final java.util.List<String> names;
        private final java.util.List<DlcItem> items;

        private ConflictCheck(java.util.List<String> names, java.util.List<DlcItem> items) {
            this.names = names;
            this.items = items;
        }
    }

    private static final class GroupToggleState {
        private final String label;
        private final boolean enabled;
        private final boolean show;

        private GroupToggleState(String label, boolean enabled, boolean show) {
            this.label = label;
            this.enabled = enabled;
            this.show = show;
        }
    }

    public static String groupToggleLabel(java.util.List<DlcItem> items) { return getGroupToggleState(items).label; }
    public static boolean canToggleGroup(java.util.List<DlcItem> items) { return getGroupToggleState(items).show; }
    public static java.util.List<String> dependencyNames(DlcItem item) { return collectDependencyNamesRecursive(item); }
}
