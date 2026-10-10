package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if <1.20.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class DlcServerScreen extends DlcManagerPageScreen implements DlcReturnPositionHost {
    private static final String PRESET_REF_PREFIX = "preset:";
    private static final Set<String> REMOTE_PROMPTED = new HashSet<>();
    private final Screen parent;
    private final DlcServerEntry entry;
    private final boolean openedFromIngame;
    private final List<DlcServerListRow> rows = new ArrayList<>();
    private final List<DlcServerItem> items = new ArrayList<>();
    private final Map<String, DlcServerItem> itemsByIdentifier = new LinkedHashMap<>();
    private final Map<String, Boolean> baseline = new HashMap<>();
    private String serverBaseVersion = "";
    private String serverDlcVersion = "";
    private final Set<String> presetSnapshot = new LinkedHashSet<>();
    private boolean presetSnapshotCaptured;
    private final Set<String> presetAppliedIdentifiers = new LinkedHashSet<>();
    private final Map<String, DlcPresetStatus> presetStatusById = new LinkedHashMap<>();
    private double savedScrollOffset;
    private DlcType savedScrollFilter;
    private boolean restoreScrollPending;

    private DlcUiButton applyButton;
    private DlcUiButton backButton;

    private DlcType activeFilter;
    private boolean applying;
    private int remoteMissingCount;
    private int requiredMissingCount;
    private boolean hasRequiredEntries;
    private boolean promptedRemoteDownload;
    private boolean hasServerTaczMod;

    public DlcServerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info) {
        this(parent, entry, info, parent instanceof PauseScreen);
    }

    public DlcServerScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info, boolean openedFromIngame) {
        super(DlcUiPage.SERVER_MANAGER);
        this.parent = parent;
        this.entry = entry;
        this.openedFromIngame = openedFromIngame;
        setInfo(info);
    }

    private void setInfo(DlcServerInfo info) {
        items.clear();
        itemsByIdentifier.clear();
        baseline.clear();
        remoteMissingCount = info != null ? info.remoteMissing : 0;
        requiredMissingCount = info != null ? info.requiredMissing : 0;
        hasRequiredEntries = info != null && info.hasRequired;
        hasServerTaczMod = info != null && info.hasTaczMod;
        if (!hasServerTaczMod && activeFilter == DlcType.TACZ_PACK) {
            activeFilter = null;
        }
        serverBaseVersion = info != null && info.baseVersion != null ? info.baseVersion : "";
        serverDlcVersion = info != null && info.dlcVersion != null ? info.dlcVersion : "";
        if (info != null && info.items != null) {
            for (DlcServerItem item : info.items) {
                if (item == null) {
                    continue;
                }
                items.add(item);
                baseline.put(item.id, item.enabled);
                if (item.identifier != null && !item.identifier.isBlank()) {
                    String key = normalizeIdentifier(item.identifier);
                    if (!key.isBlank()) {
                        itemsByIdentifier.put(key, item);
                    }
                }
            }
        }
        applyPresetOverrides();
    }

    private void rebuildRows() {
        rows.clear();
        Map<DlcType, List<DlcServerItem>> grouped = new EnumMap<>(DlcType.class);
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            DlcType type = mapType(item.type);
            if (type == null) {
                type = DlcType.UNKNOWN;
            }
            if (!isServerTypeVisible(type)) {
                continue;
            }
            grouped.computeIfAbsent(type, key -> new ArrayList<>()).add(item);
        }
        if (activeFilter == null) {
            for (DlcType type : DlcType.values()) {
                if (!isServerTypeVisible(type)) {
                    continue;
                }
                List<DlcServerItem> group = grouped.get(type);
                if (group == null || group.isEmpty()) {
                    continue;
                }
                rows.add(DlcServerListRow.groupHeader(type));
                Map<String, List<DlcServerItem>> byPath = groupByRelativeFolder(group);
                boolean hasSubDir = byPath.keySet().stream().anyMatch(key -> !"__root__".equals(key));
                boolean firstPathHeader = true;
                for (Map.Entry<String, List<DlcServerItem>> entry : byPath.entrySet()) {
                    if ("__root__".equals(entry.getKey())) {
                        if (hasSubDir) {
                            rows.add(DlcServerListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), true, firstPathHeader));
                            firstPathHeader = false;
                        }
                    } else {
                        rows.add(DlcServerListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), false, firstPathHeader));
                        firstPathHeader = false;
                    }
                    for (DlcServerItem item : entry.getValue()) {
                        rows.add(DlcServerListRow.item(item));
                    }
                }
            }
        } else {
            List<DlcServerItem> group = grouped.get(activeFilter);
            if (group != null && !group.isEmpty()) {
                Map<String, List<DlcServerItem>> byPath = groupByRelativeFolder(group);
                boolean hasSubDir = byPath.keySet().stream().anyMatch(key -> !"__root__".equals(key));
                boolean firstPathHeader = true;
                for (Map.Entry<String, List<DlcServerItem>> entry : byPath.entrySet()) {
                    if ("__root__".equals(entry.getKey())) {
                        if (hasSubDir) {
                            rows.add(DlcServerListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), true, firstPathHeader));
                            firstPathHeader = false;
                        }
                    } else {
                        rows.add(DlcServerListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), false, firstPathHeader));
                        firstPathHeader = false;
                    }
                    for (DlcServerItem item : entry.getValue()) {
                        rows.add(DlcServerListRow.item(item));
                    }
                }
            }
        }
    }

    private void openConfig() {
        if (applying) return;
        Minecraft.getInstance().setScreen(new DlcServerConfigScreen(this, entry));
    }

    private void openServerTools() {
        if (applying) return;
        Minecraft.getInstance().setScreen(new DlcServerManagerScreen(this, entry, buildInfoSnapshot(), DlcServerManagerScreen.Tab.SERVERS, openedFromIngame));
    }

    private void openServerList() {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot);
            presetAppliedIdentifiers.clear();
            applyPresetOverrides();
        }
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        if (openedFromIngame) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        Minecraft.getInstance().setScreen(new DlcServerListScreen(null));
    }

    Screen getRootParent() {
        return parent;
    }

    DlcServerItem getItemByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return itemsByIdentifier.get(normalizeIdentifier(identifier));
    }

    List<DlcServerItem> getItemsSnapshot() {
        return new ArrayList<>(items);
    }

    private DlcServerInfo buildInfoSnapshot() {
        DlcServerInfo info = new DlcServerInfo();
        info.items = new ArrayList<>(items);
        info.remoteMissing = remoteMissingCount;
        info.requiredMissing = requiredMissingCount;
        info.hasRequired = hasRequiredEntries;
        info.hasTaczMod = hasServerTaczMod;
        info.baseVersion = serverBaseVersion;
        info.dlcVersion = serverDlcVersion;
        return info;
    }

    private void openPreset() {
        if (applying) return;
        capturePresetSnapshot();
        Minecraft.getInstance().setScreen(new DlcPresetManagerScreen(DlcUiPage.SERVER_PRESETS, this) {
            @Override
            public void onClose() {
                refreshAfterPresetChange();
                super.onClose();
            }
        });
    }

    private void setAllEnabled(boolean enabled) {
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            if (!matchesVisibleItem(item)) {
                continue;
            }
            item.enabled = enabled;
        }
        updateApplyState();
    }

    void handleToggle(DlcServerItem item) {
        if (item == null) {
            return;
        }
        if (item.enabled) {
            disableWithDependents(item);
            updateApplyState();
            return;
        }
        if (item.alarm) {
            String tip = getTooltipText(item);
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(this, tip, () -> continueEnableWithChecks(item)));
            return;
        }
        continueEnableWithChecks(item);
    }

    private void continueEnableWithChecks(DlcServerItem item) {
        if (item == null) {
            return;
        }
        ConflictCheck conflicts = checkConflicts(item);
        if (!conflicts.conflicts.isEmpty()) {
            Component message = DlcConfirmUtil.buildConflictPromptMessage(conflicts.conflictNames);
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    continueEnableAfterConflictResolution(item);
                }
            }, DlcTextCompat.translatable("dlc_manager.conflict.word"), message, DlcTextCompat.translatable("dlc_manager.conflict.resolve"), DlcTextCompat.translatable("gui.cancel")));
            return;
        }
        DependencyCheck deps = checkDependencies(item);
        if (!deps.missing.isEmpty()) {
            Component message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames);
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    for (DlcServerItem dep : deps.missingItems) {
                        dep.enabled = true;
                    }
                    item.enabled = true;
                    updateApplyState();
                }
            }, DlcTextCompat.translatable("dlc_manager.dependency.enable"), message, DlcTextCompat.translatable("dlc_manager.dependency.enable"), DlcTextCompat.translatable("gui.cancel")));
            return;
        }
        item.enabled = true;
        updateApplyState();
    }

    private void continueEnableAfterConflictResolution(DlcServerItem item) {
        int guard = 0;
        while (guard++ < 64) {
            ConflictCheck conflicts = checkConflicts(item);
            if (conflicts.conflicts.isEmpty() || conflicts.conflictItems.isEmpty()) {
                break;
            }
            disableItemsWithDependents(conflicts.conflictItems);
        }

        DependencyCheck deps = checkDependencies(item);
        if (!deps.missing.isEmpty()) {
            Component message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames);
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    for (DlcServerItem dep : deps.missingItems) {
                        dep.enabled = true;
                    }
                    item.enabled = true;
                    updateApplyState();
                }
            }, DlcTextCompat.translatable("dlc_manager.dependency.enable"), message, DlcTextCompat.translatable("dlc_manager.dependency.enable"), DlcTextCompat.translatable("gui.cancel")));
            return;
        }

        item.enabled = true;
        updateApplyState();
    }

    private void disableWithDependents(DlcServerItem item) {
        if (item == null) {
            return;
        }
        disableItemsWithDependents(List.of(item));
    }

    void disableItemsWithDependents(java.util.Collection<DlcServerItem> itemsToDisable) {
        if (itemsToDisable == null || itemsToDisable.isEmpty()) {
            return;
        }
        Set<DlcServerItem> toDisable = new LinkedHashSet<>();
        for (DlcServerItem item : itemsToDisable) {
            if (item != null) {
                toDisable.addAll(collectDependentsRecursive(item));
            }
        }
        for (DlcServerItem target : toDisable) {
            if (target != null) {
                target.enabled = false;
            }
        }
    }

    private Set<DlcServerItem> collectDependentsRecursive(DlcServerItem root) {
        Set<DlcServerItem> result = new LinkedHashSet<>();
        if (root == null) {
            return result;
        }
        ArrayDeque<DlcServerItem> stack = new ArrayDeque<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            DlcServerItem current = stack.removeFirst();
            if (current == null || !result.add(current)) {
                continue;
            }
            String currentId = normalizeIdentifier(current.identifier);
            if (currentId.isBlank()) {
                continue;
            }
            for (DlcServerItem item : items) {
                if (item == null || item.dependencies == null || item.dependencies.isEmpty()) {
                    continue;
                }
                for (String dep : item.dependencies) {
                    if (dep == null || dep.isBlank()) {
                        continue;
                    }
                    if (normalizeIdentifier(dep).equals(currentId)) {
                        stack.add(item);
                        break;
                    }
                }
            }
        }
        return result;
    }

    void updateApplyState() {
        if (applyButton == null) {
            return;
        }
        boolean dirty = false;
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            Boolean baselineValue = baseline.get(item.id);
            if (baselineValue == null) {
                baselineValue = false;
            }
            if (baselineValue != item.enabled) {
                dirty = true;
                break;
            }
        }
        applyButton.setEnabled(dirty && !applying);
        if (backButton != null) {
            backButton.setEnabled(!applying && requiredMissingCount <= 0);
        }
    }

    boolean isDirty(DlcServerItem item) {
        if (item == null) {
            return false;
        }
        Boolean baselineValue = baseline.get(item.id);
        if (baselineValue == null) {
            baselineValue = false;
        }
        return baselineValue != item.enabled;
    }

    String getPendingSuffix(DlcServerItem item) {
        if (!isDirty(item)) {
            return "";
        }
        if (mapType(item.type) == DlcType.MOD) {
            return tr("dlc_manager.pending.restart");
        }
        return tr("dlc_manager.pending.reload");
    }

    String resolveDisplayName(DlcServerItem item) {
        if (item == null) {
            return "";
        }
        if (item.displayNames != null && !item.displayNames.isEmpty()) {
            String lang = Minecraft.getInstance().options.languageCode;
            if (item.displayNames.containsKey(lang)) {
                return item.displayNames.get(lang);
            }
            if (item.displayNames.containsKey("en_us")) {
                return item.displayNames.get("en_us");
            }
            return item.displayNames.values().stream().findFirst().orElse(item.identifier);
        }
        if (item.name != null && !item.name.isBlank()) {
            return item.name;
        }
        return item.identifier != null ? item.identifier : "";
    }

    java.util.List<net.minecraft.util.FormattedCharSequence> buildTooltipLines(net.minecraft.client.gui.Font font, DlcServerItem item, int maxWidth) {
        java.util.ArrayList<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        String tooltip = getTooltipText(item);
        if (tooltip != null && !tooltip.isBlank()) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, tooltip, maxWidth));
        }
        List<String> deps = collectDependencyNamesRecursive(item);
        List<String> conflicts = item != null ? item.conflicts : List.of();
        Set<String> missing = collectMissingDependencyIdentifiers(item);
        List<String> presetBlockers = DlcPresetRuntimeState.getDlcBlockerNames(item.identifier,
            Minecraft.getInstance().options.languageCode);
        if (!presetBlockers.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            for (String blocker : presetBlockers) {
                Component line = DlcTextCompat.translatable("dlc_manager.preset.conflict", blocker)
                    .withStyle(ChatFormatting.DARK_RED);
                lines.addAll(font.split(line, maxWidth));
            }
        }
        if (!missing.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component missingLabel = DlcTextCompat.empty()
                .append(DlcTextCompat.translatable("dlc_manager.tooltip.missing_dependencies").withStyle(ChatFormatting.RED))
                .append(":");
            lines.addAll(font.split(missingLabel, maxWidth));
            for (String dep : missing) {
                lines.addAll(font.split(DlcTextFormatter.parse(resolveDisplayName(resolveDependencyItem(dep))), maxWidth));
            }
        }
        if (deps != null && !deps.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component depLabel = DlcTextCompat.empty()
                .append(DlcTextCompat.translatable("dlc_manager.tooltip.dependencies").withStyle(ChatFormatting.GOLD))
                .append(":");
            lines.addAll(font.split(depLabel, maxWidth));
            for (String dep : deps) {
                lines.addAll(font.split(DlcTextFormatter.parse(resolveDisplayName(resolveDependencyItem(dep))), maxWidth));
            }
        }
        if (conflicts != null && !conflicts.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component conflictLabel = DlcTextCompat.empty()
                .append(DlcTextCompat.translatable("dlc_manager.tooltip.conflicts").withStyle(ChatFormatting.DARK_RED))
                .append(":");
            lines.addAll(font.split(conflictLabel, maxWidth));
            for (String conflict : conflicts) {
                lines.addAll(font.split(DlcTextFormatter.parse(resolveDisplayName(resolveDependencyItem(conflict))), maxWidth));
            }
        }
        return lines;
    }

    private String getTooltipText(DlcServerItem item) {
        if (item == null || item.tips == null || item.tips.isEmpty()) {
            return null;
        }
        String lang = Minecraft.getInstance().options.languageCode;
        if (item.tips.containsKey(lang)) {
            return item.tips.get(lang);
        }
        if (item.tips.containsKey("en_us")) {
            return item.tips.get("en_us");
        }
        return item.tips.values().stream().findFirst().orElse(null);
    }

    private List<String> collectDependencyNamesRecursive(DlcServerItem item) {
        if (item == null || item.dependencies == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(item.dependencies);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (result.add(id)) {
                DlcServerItem dep = resolveDependencyItem(id);
                if (dep != null && dep.dependencies != null) {
                    queue.addAll(dep.dependencies);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private Set<String> collectMissingDependencyIdentifiers(DlcServerItem item) {
        if (item == null || item.dependencies == null || item.dependencies.isEmpty()) {
            return Set.of();
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String depId : item.dependencies) {
            if (depId == null || depId.isBlank()) {
                continue;
            }
            DlcServerItem dep = resolveDependencyItem(depId);
            if (dep == null) {
                missing.add(depId);
            }
        }
        return missing;
    }

    private DependencyCheck checkDependencies(DlcServerItem item) {
        DependencyCheck check = new DependencyCheck();
        if (item == null || item.dependencies == null) {
            return check;
        }
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<DlcServerItem> queue = new ArrayDeque<>();
        queue.add(item);
        while (!queue.isEmpty()) {
            DlcServerItem current = queue.poll();
            if (current == null || current.identifier == null || current.identifier.isBlank()) {
                continue;
            }
            String currentId = normalizeIdentifier(current.identifier);
            if (currentId.isBlank() || !visited.add(currentId)) {
                continue;
            }
            if (current.dependencies == null || current.dependencies.isEmpty()) {
                continue;
            }
            for (String depId : current.dependencies) {
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                DlcServerItem dep = resolveDependencyItem(depId);
                if (dep == null || !dep.enabled) {
                    String depKey = normalizeIdentifier(depId);
                    if (!check.missing.contains(depKey)) {
                        check.missing.add(depKey);
                        check.missingNames.add(resolveDisplayName(dep));
                    }
                    if (dep != null && !check.missingItems.contains(dep)) {
                        check.missingItems.add(dep);
                    }
                }
                if (dep != null) {
                    queue.add(dep);
                }
            }
        }
        return check;
    }

    private ConflictCheck checkConflicts(DlcServerItem item) {
        ConflictCheck check = new ConflictCheck();
        if (item == null) {
            return check;
        }
        Set<String> plannedIds = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<DlcServerItem> queue = new ArrayDeque<>();
        queue.add(item);
        while (!queue.isEmpty()) {
            DlcServerItem current = queue.poll();
            if (current == null || current.identifier == null || current.identifier.isBlank()) {
                continue;
            }
            String currentId = normalizeIdentifier(current.identifier);
            if (currentId.isBlank() || !visited.add(currentId)) {
                continue;
            }
            plannedIds.add(currentId);
            if (current.dependencies != null) {
                for (String depId : current.dependencies) {
                    if (depId == null || depId.isBlank()) {
                        continue;
                    }
                    DlcServerItem dep = resolveDependencyItem(depId);
                    if (dep != null) {
                        queue.add(dep);
                    }
                }
            }
        }

        for (String plannedId : plannedIds) {
            DlcServerItem planned = itemsByIdentifier.get(plannedId);
            if (planned == null || planned.conflicts == null || planned.conflicts.isEmpty()) {
                continue;
            }
            for (String conflictId : planned.conflicts) {
                if (conflictId == null || conflictId.isBlank()) {
                    continue;
                }
                String conflictKey = normalizeIdentifier(conflictId);
                if (conflictKey.isBlank() || plannedIds.contains(conflictKey)) {
                    continue;
                }
                DlcServerItem conflict = resolveDependencyItem(conflictId);
                if (conflict != null && conflict.enabled && !check.conflicts.contains(conflictKey)) {
                    check.conflicts.add(conflictKey);
                    check.conflictItems.add(conflict);
                    check.conflictNames.add(resolveDisplayName(conflict));
                }
            }
        }

        for (DlcServerItem existing : items) {
            if (existing == null || existing.identifier == null || existing.identifier.isBlank() || !existing.enabled) {
                continue;
            }
            String existingKey = normalizeIdentifier(existing.identifier);
            if (existingKey.isBlank() || plannedIds.contains(existingKey)) {
                continue;
            }
            if (existing.conflicts == null || existing.conflicts.isEmpty()) {
                continue;
            }
            boolean hit = false;
            for (String conflictId : existing.conflicts) {
                if (conflictId == null || conflictId.isBlank()) {
                    continue;
                }
                String conflictKey = normalizeIdentifier(conflictId);
                if (!conflictKey.isBlank() && plannedIds.contains(conflictKey)) {
                    hit = true;
                    break;
                }
            }
            if (hit && !check.conflicts.contains(existingKey)) {
                check.conflicts.add(existingKey);
                check.conflictItems.add(existing);
                check.conflictNames.add(resolveDisplayName(existing));
            }
        }
        return check;
    }

    private DlcServerItem resolveDependencyItem(String depId) {
        if (depId == null || depId.isBlank()) {
            return null;
        }
        String key = normalizeIdentifier(depId);
        if (key.isBlank()) {
            return null;
        }
        return itemsByIdentifier.get(key);
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private void applyChanges() {
        if (applying) {
            return;
        }
        applying = true;
        pageError = null;
        updateApplyState();
        List<DlcServerItem> changed = new ArrayList<>();
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            Boolean baselineValue = baseline.get(item.id);
            if (baselineValue == null) {
                baselineValue = false;
            }
            if (baselineValue != item.enabled) {
                DlcServerItem copy = new DlcServerItem();
                copy.id = item.id;
                copy.enabled = item.enabled;
                changed.add(copy);
            }
        }
        CompletableFuture
            .supplyAsync(() -> applyRemoteChanges(changed))
            .whenComplete((response, throwable) -> Minecraft.getInstance().execute(() -> {
                applying = false;
                if (response == null || !response.ok) pageError = tr("dlc_manager.server.apply_failed");
                if (response != null && response.ok) {
                    for (DlcServerItem item : items) {
                        baseline.put(item.id, item.enabled);
                    }
                    if (DlcConfig.isPresetFuncOn()) {
                        syncPresetStatusFromItems();
                        updatePresetSnapshot();
                    }
                }
                updateApplyState();
                if (response != null && response.ok && response.requiresRestart) {
                    promptServerRestart();
                }
            }));
    }

    private DlcServerConnection.ApplyResponse applyRemoteChanges(List<DlcServerItem> changes) {
        try {
            return DlcServerConnection.applyChangesWithResponse(entry, 8000, changes);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void promptServerRestart() {
        Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcConfirmScreen(result -> {
            if (result) {
                CompletableFuture.runAsync(() -> {
                    try {
                        DlcServerConnection.restartServer(entry, 8000);
                    } catch (Exception ignored) {
                    }
                });
            }
            Minecraft.getInstance().setScreen(this);
        }, DlcTextCompat.translatable("dlc_manager.server.restart_title"),
            DlcTextCompat.translatable("dlc_manager.server.restart_prompt")));
    }

    private void applyPresetOverrides() {
        if (!DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.clearAll();
            presetAppliedIdentifiers.clear();
            presetStatusById.clear();
            return;
        }
        if (DlcPresetRuntimeState.getPresets().isEmpty()) {
            DlcPresetRuntimeState.reload();
        }
        List<DlcPresetEntry> entries = DlcPresetRuntimeState.getPresets();
        Set<String> enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds();
        updatePresetStatusFromItems();
        if (enabledPresets.isEmpty() || entries.isEmpty()) {
            if (!presetAppliedIdentifiers.isEmpty()) {
                for (String identifier : presetAppliedIdentifiers) {
                    DlcServerItem item = getItemByIdentifier(identifier);
                    if (item != null) {
                        item.enabled = false;
                    }
                }
                presetAppliedIdentifiers.clear();
            }
            return;
        }
        Map<String, DlcPresetEntry> byId = new HashMap<>();
        for (DlcPresetEntry entry : entries) {
            if (entry != null && entry.getId() != null) {
                byId.put(entry.getId(), entry);
            }
        }
        Set<String> desiredEnable = new LinkedHashSet<>();
        Set<String> desiredDisable = new LinkedHashSet<>();
        Map<String, Set<String>> enableCache = new LinkedHashMap<>();
        Map<String, Set<String>> conflictCache = new LinkedHashMap<>();
        for (String id : enabledPresets) {
            DlcPresetEntry preset = byId.get(id);
            if (preset == null) {
                continue;
            }
            boolean partial = preset.getStatus() == DlcPresetStatus.PART;
            for (String enable : resolvePresetEnableIdentifiers(preset.getId(), byId, enableCache, new LinkedHashSet<>())) {
                String key = normalizeIdentifier(enable);
                if (key.isBlank()) {
                    continue;
                }
                if (partial) {
                    DlcServerItem item = getItemByIdentifier(key);
                    if (item != null && item.enabled) {
                        desiredEnable.add(key);
                    }
                } else {
                    desiredEnable.add(key);
                }
            }
            for (String conflict : resolvePresetConflictIdentifiers(preset.getId(), byId, enableCache, conflictCache, new LinkedHashSet<>(), new LinkedHashSet<>())) {
                String key = normalizeIdentifier(conflict);
                if (!key.isBlank()) {
                    desiredDisable.add(key);
                }
            }
        }
        desiredDisable.addAll(collectInfoConflicts(desiredEnable));
        desiredDisable = expandDisabledByDependents(desiredDisable);
        desiredEnable.removeAll(desiredDisable);
        Set<String> desiredAll = new LinkedHashSet<>(desiredEnable);
        desiredAll.addAll(desiredDisable);

        Set<String> toDisable = new LinkedHashSet<>(presetAppliedIdentifiers);
        toDisable.removeAll(desiredAll);
        for (String identifier : desiredEnable) {
            DlcServerItem item = getItemByIdentifier(identifier);
            if (item != null) {
                item.enabled = true;
            }
        }
        for (String identifier : desiredDisable) {
            DlcServerItem item = getItemByIdentifier(identifier);
            if (item != null) {
                item.enabled = false;
            }
        }
        for (String identifier : toDisable) {
            DlcServerItem item = getItemByIdentifier(identifier);
            if (item != null) {
                Boolean base = baseline.get(item.id);
                item.enabled = base != null ? base : false;
            }
        }
        presetAppliedIdentifiers.clear();
        presetAppliedIdentifiers.addAll(desiredAll);
    }

    private Set<String> collectInfoConflicts(Set<String> enabledIdentifiers) {
        Set<String> result = new LinkedHashSet<>();
        if (enabledIdentifiers == null || enabledIdentifiers.isEmpty()) {
            return result;
        }
        for (String identifier : enabledIdentifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcServerItem item = getItemByIdentifier(identifier);
            if (item == null || item.conflicts == null || item.conflicts.isEmpty()) {
                continue;
            }
            for (String conflict : item.conflicts) {
                String key = normalizeIdentifier(conflict);
                if (key.isBlank()) {
                    continue;
                }
                if (enabledIdentifiers.contains(key)) {
                    continue;
                }
                result.add(key);
            }
        }
        return result;
    }

    private Set<String> expandDisabledByDependents(Set<String> disabledIdentifiers) {
        Set<String> expanded = new LinkedHashSet<>();
        if (disabledIdentifiers == null || disabledIdentifiers.isEmpty()) {
            return expanded;
        }
        ArrayDeque<DlcServerItem> queue = new ArrayDeque<>();
        for (String identifier : disabledIdentifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            expanded.add(identifier);
            DlcServerItem root = getItemByIdentifier(identifier);
            if (root != null) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty()) {
            DlcServerItem current = queue.poll();
            if (current == null || current.identifier == null || current.identifier.isBlank()) {
                continue;
            }
            for (DlcServerItem dependent : collectDependentsRecursive(current)) {
                if (dependent == null || dependent.identifier == null || dependent.identifier.isBlank()) {
                    continue;
                }
                String key = normalizeIdentifier(dependent.identifier);
                if (!key.isBlank() && expanded.add(key)) {
                    queue.add(dependent);
                }
            }
        }
        return expanded;
    }

    void refreshAfterPresetChange() {
        applyPresetOverrides();
        rebuildRows();
        refreshManagerPage();
        
        updateApplyState();
    }

    DlcPresetStatus getPresetStatus(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return DlcPresetStatus.OFF;
        }
        updatePresetStatusFromItems();
        return presetStatusById.getOrDefault(presetId, DlcPresetStatus.OFF);
    }

    private Map<String, DlcPresetStatus> updatePresetStatusFromItems() {
        presetStatusById.clear();
        if (!DlcConfig.isPresetFuncOn()) {
            return presetStatusById;
        }
        List<DlcPresetEntry> entries = DlcPresetRuntimeState.getPresets();
        Set<String> enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds();
        Map<String, DlcPresetEntry> byId = new LinkedHashMap<>();
        for (DlcPresetEntry entry : entries) {
            if (entry != null && entry.getId() != null && !entry.getId().isBlank()) {
                byId.put(entry.getId(), entry);
            }
        }
        Map<String, Set<String>> enableCache = new LinkedHashMap<>();
        for (DlcPresetEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            DlcPresetStatus status;
            if (!enabledPresets.contains(entry.getId())) {
                status = DlcPresetStatus.OFF;
            } else {
                boolean allEnabled = true;
                for (String identifier : resolvePresetEnableIdentifiers(entry.getId(), byId, enableCache, new LinkedHashSet<>())) {
                    if (identifier == null || identifier.isBlank()) {
                        continue;
                    }
                    DlcServerItem item = resolveDependencyItem(identifier);
                    if (item == null || !item.enabled) {
                        allEnabled = false;
                        break;
                    }
                }
                status = allEnabled ? DlcPresetStatus.ON : DlcPresetStatus.PART;
            }
            presetStatusById.put(entry.getId(), status);
        }
        return presetStatusById;
    }

    private Set<String> resolvePresetEnableIdentifiers(String presetId,
                                                       Map<String, DlcPresetEntry> byId,
                                                       Map<String, Set<String>> cache,
                                                       Set<String> stack) {
        if (presetId == null || presetId.isBlank()) {
            return Set.of();
        }
        Set<String> cached = cache.get(presetId);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(presetId)) {
            return Set.of();
        }
        DlcPresetEntry preset = byId.get(presetId);
        if (preset == null) {
            stack.remove(presetId);
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : preset.getEnable()) {
            String value = token == null ? "" : token.trim();
            if (value.isBlank()) {
                continue;
            }
            String refPresetId = resolvePresetReference(value, byId);
            if (refPresetId != null) {
                result.addAll(resolvePresetEnableIdentifiers(refPresetId, byId, cache, stack));
            } else {
                result.add(value);
            }
        }
        stack.remove(presetId);
        Set<String> unmodifiable = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(result));
        cache.put(presetId, unmodifiable);
        return unmodifiable;
    }

    private Set<String> resolvePresetConflictIdentifiers(String presetId,
                                                         Map<String, DlcPresetEntry> byId,
                                                         Map<String, Set<String>> enableCache,
                                                         Map<String, Set<String>> conflictCache,
                                                         Set<String> stack,
                                                         Set<String> enableStack) {
        if (presetId == null || presetId.isBlank()) {
            return Set.of();
        }
        Set<String> cached = conflictCache.get(presetId);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(presetId)) {
            return Set.of();
        }
        DlcPresetEntry preset = byId.get(presetId);
        if (preset == null) {
            stack.remove(presetId);
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : preset.getConflicts()) {
            String value = token == null ? "" : token.trim();
            if (value.isBlank()) {
                continue;
            }
            String refPresetId = resolvePresetReference(value, byId);
            if (refPresetId != null) {
                result.addAll(resolvePresetEnableIdentifiers(refPresetId, byId, enableCache, enableStack));
            } else {
                result.add(value);
            }
        }
        stack.remove(presetId);
        Set<String> unmodifiable = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(result));
        conflictCache.put(presetId, unmodifiable);
        return unmodifiable;
    }

    private String resolvePresetReference(String token, Map<String, DlcPresetEntry> byId) {
        if (token == null || token.isBlank()) {
            return null;
        }
        boolean forcedPreset = false;
        String value = token.trim();
        if (value.regionMatches(true, 0, PRESET_REF_PREFIX, 0, PRESET_REF_PREFIX.length())) {
            forcedPreset = true;
            value = value.substring(PRESET_REF_PREFIX.length()).trim();
            if (value.isBlank()) {
                return null;
            }
        }
        String presetId = findPresetId(value, byId);
        if (presetId == null) {
            return null;
        }
        if (!forcedPreset && getItemByIdentifier(value) != null) {
            return null;
        }
        return presetId;
    }

    private String findPresetId(String value, Map<String, DlcPresetEntry> byId) {
        DlcPresetEntry exact = byId.get(value);
        if (exact != null && exact.getId() != null && !exact.getId().isBlank()) {
            return exact.getId();
        }
        for (DlcPresetEntry entry : byId.values()) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            if (entry.getId().equalsIgnoreCase(value)) {
                return entry.getId();
            }
            if (entry.getFileName() != null && !entry.getFileName().isBlank() && entry.getFileName().equalsIgnoreCase(value)) {
                return entry.getId();
            }
        }
        return null;
    }

    private void syncPresetStatusFromItems() {
        Map<String, DlcPresetStatus> statuses = updatePresetStatusFromItems();
        DlcPresetRuntimeState.syncStatusFromExternal(statuses);
    }

    private void capturePresetSnapshot() {
        if (presetSnapshotCaptured) {
            return;
        }
        updatePresetSnapshot();
    }

    private void updatePresetSnapshot() {
        presetSnapshot.clear();
        if (DlcConfig.isPresetFuncOn()) {
            presetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds());
        }
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

    private void promptRemoteDownloadIfMissing() {
        if (remoteMissingCount <= 0) {
            REMOTE_PROMPTED.remove(getPromptKey());
            return;
        }
        if (promptedRemoteDownload || REMOTE_PROMPTED.contains(getPromptKey())) {
            return;
        }
        promptedRemoteDownload = true;
        REMOTE_PROMPTED.add(getPromptKey());
        startRemoteDownload();
    }

    private String getPromptKey() {
        return entry == null ? "" : entry.getKey();
    }

    private void startRemoteDownload() {
        Minecraft.getInstance().setScreen(new DlcServerRemoteDownloadScreen(this, entry));
    }

    void refreshServerInfo() {
        CompletableFuture
            .supplyAsync(() -> fetchInfo(entry))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                if (info != null) {
                    setInfo(info);
                    rebuildRows();
                    refreshManagerPage();
                    
                }
            }));
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static DlcType mapType(String type) {
        if (type == null) {
            return null;
        }
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains("datapack") || lower.equals("data_pack") || lower.equals("data")) {
            return DlcType.DATA_PACK;
        }
        if (lower.contains("resourcepack") || lower.equals("resource_pack") || lower.equals("resource")) {
            return DlcType.RESOURCE_PACK;
        }
        if (lower.contains("tacz")) {
            return DlcType.TACZ_PACK;
        }
        if (lower.equals("mods") || lower.equals("mod")) {
            return DlcType.MOD;
        }
        for (DlcType dlcType : DlcType.values()) {
            if (typeId(dlcType).equalsIgnoreCase(type) || dlcType.name().equalsIgnoreCase(type)) {
                return dlcType;
            }
        }
        return DlcType.UNKNOWN;
    }

    private static String typeId(DlcType type) {
        return switch (type) {
            case MOD -> "mod";
            case RESOURCE_PACK -> "resource";
            case DATA_PACK -> "data";
            case TACZ_PACK -> "tacz";
            case DLC_PACK -> "dlcpack";
            case UNKNOWN -> "other";
        };
    }

    private List<DlcType> visibleFilterTypes() {
        List<DlcType> types = new ArrayList<>();
        for (DlcType type : DlcType.values()) {
            if (type == DlcType.DLC_PACK) {
                continue;
            }
            if (isServerTypeVisible(type)) {
                types.add(type);
            }
        }
        return types;
    }

    private boolean isServerTypeVisible(DlcType type) {
        return type != DlcType.TACZ_PACK || hasServerTaczMod;
    }

    @Override
    public void rememberReturnPosition() {
        rememberListScroll();
    }

    private Map<String, List<DlcServerItem>> groupByRelativeFolder(List<DlcServerItem> items) {
        Map<String, List<DlcServerItem>> grouped = new java.util.TreeMap<>();
        for (DlcServerItem item : items) {
            String label = (item.path == null || item.path.isBlank()) ? "__root__" : item.path;
            grouped.computeIfAbsent(label, key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private String resolvePathLabel(String raw) {
        if ("__root__".equals(raw)) {
            return tr("dlc_manager.group.root");
        }
        return raw;
    }
    private static class DependencyCheck {
        private final List<String> missing = new ArrayList<>();
        private final List<String> missingNames = new ArrayList<>();
        private final List<DlcServerItem> missingItems = new ArrayList<>();
    }

    private static class ConflictCheck {
        private final List<String> conflicts = new ArrayList<>();
        private final List<String> conflictNames = new ArrayList<>();
        private final List<DlcServerItem> conflictItems = new ArrayList<>();
    }

    private boolean prepared;
    private DlcUiButton enableAllButton, disableAllButton;
    private String pageError;
    private Boolean controlsLocked;
    private List<Boolean> renderedState = List.of();

    @Override public Component getTitle() { return DlcTextCompat.translatable("dlc_manager.server.title"); }

    @Override protected void init() {
        capturePresetSnapshot();
        super.init();
        if (!prepared) {
            prepared = true;
            promptRemoteDownloadIfMissing();
        }
    }

    @Override protected void bindDocument(Document document) {
        bindManagerLayout(document, tr("dlc_manager.server.title"));
        document.getElementById("manager-header").setAttribute("class", "workspace-header connected");
        Element connection = document.getElementById("manager-connection");
        clearUiContent(connection);
        connection.setAttribute("style", "display:flex");
        managerText(document, connection, "span", entry == null ? "" : entry.getDisplayName(), "connection-name");
        managerText(document, connection, "span", String.join(" · ", java.util.stream.Stream.of(serverBaseVersion, serverDlcVersion).filter(v -> !v.isBlank()).toList()), "connection-version");
        Element context = document.getElementById("manager-context");
        clearUiContent(context);
        if (entry != null) button(document, context, tr("dlc_manager.server.config"), this::openConfig);
        Element tools = document.getElementById("manager-tools");
        clearUiContent(tools);
        if (DlcConfig.isPresetFuncOn()) button(document, tools, tr("dlc_manager.preset.manager"), this::openPreset);
        button(document, tools, tr("dlc_manager.server.tab.servers"), this::openServerTools);
        for (Element tool : tools.children) tool.setAttribute("class", "button nav-button");
        Element batch = document.getElementById("batch-actions");
        clearUiContent(batch);
        if (remoteMissingCount > 0) button(document, batch, tr("dlc_manager.server.remote_missing_button"), this::startRemoteDownload);
        enableAllButton = DlcConfig.isEnableAllButtonShown() ? button(document, batch, "", () -> confirmBatchToggle(true)) : null;
        disableAllButton = button(document, batch, "", () -> confirmBatchToggle(false));
        Element mode = document.getElementById("manager-mode");
        clearUiContent(mode);
        mode.setAttribute("style", "display:none");
        Element actions = document.getElementById("manager-actions");
        clearUiContent(actions);
        backButton = button(document, actions, tr("gui.back"), this::onClose);
        applyButton = button(document, actions, tr("dlc_manager.apply"), this::applyChanges);
        applyButton.getElement().setAttribute("class", "button primary");
        renderFilters(document);
        renderServerRows(document);
        restoreScrollPending = savedScrollFilter == activeFilter;
        if (!searchQuery.isBlank()) onManagerSearch(document, searchQuery);
    }

    private void refreshManagerPage() {
        Document document = getLinkedDocument();
        if (document != null && document.isActive()) Document.runWithContext(document, () -> bindDocument(document));
    }

    private void renderFilters(Document document) {
        Element filters = document.getElementById("manager-filters");
        clearUiContent(filters);
        List<DlcType> types = new ArrayList<>();
        types.add(null);
        types.addAll(visibleFilterTypes());
        for (DlcType type : types) {
            DlcUiButton filter = button(document, filters, tr(type == null ? "dlc_manager.filter.all" : type.getDisplayKey()), () -> {
                if (!applying && activeFilter != type) {
                    captureUiChange(document.getElementById("manager-list"));
                    activeFilter = type;
                    savedScrollOffset = 0;
                    savedScrollFilter = type;
                    document.getElementById("manager-list").setScrollTop(0);
                    renderFilters(document);
                    renderServerRows(document);
                }
            });
            filter.getElement().setAttribute("class", "button filter" + (activeFilter == type ? " active" : ""));
            filter.getElement().setAttribute("aria-pressed", Boolean.toString(activeFilter == type));
            filter.getElement().setAttribute("aria-selected", Boolean.toString(activeFilter == type));
        }
    }

    private java.util.Set<String> searchMatches = java.util.Set.of();
    @Override protected void onManagerSearch(Document document, String query) {
        if (applying) return;
        searchQuery = query == null ? "" : query;
        searchMatches = java.util.Set.of();
        savedScrollOffset = 0;
        var list = document.getElementById("manager-list");
        list.setScrollTop(0); list.getStyle().setProperty("pointer-events", "none"); list.setAttribute("aria-busy", "true");
        java.util.function.Consumer<java.util.Set<String>> apply = matches -> {
            searchMatches = matches; list.getStyle().removeProperty("pointer-events"); list.removeAttribute("aria-busy");
            renderServerRows(document); updateManagerControls(document);
        };
        if (searchQuery.isBlank()) {
            filterAsync("", java.util.List.of(), apply);
            return;
        }
        var entries = items.stream().map(item -> new DlcUiAsyncSearch.Entry<>(item.id, DlcTextFormatter.parse(resolveDisplayName(item)).getString(), item.identifier, item.path)).toList();
        updateManagerControls(document);
        filterAsync(searchQuery, entries, apply);
    }
    private boolean matchesItem(DlcServerItem item) { return !item.hidden && (searchQuery.isBlank() || searchMatches.contains(item.id)); }

    private boolean matchesVisibleItem(DlcServerItem item) {
        DlcType type = mapType(item.type);
        return isServerTypeVisible(type == null ? DlcType.UNKNOWN : type) && (activeFilter == null || type == activeFilter) && matchesItem(item);
    }

    private void renderServerRows(Document document) {
        if (tooltip != null) tooltip.hide();
        Element list = document.getElementById("manager-list");
        double scroll = list.getTargetScrollTop();
        clearUiContent(list);
        clearManagerGroups();
        rebuildRows();
        Element parent = list;
        int count = 0, enabled = 0;
        for (DlcServerListRow row : rows) {
            if (row.getType() != DlcServerListRow.RowType.ITEM) parent = list;
            switch (row.getType()) {
                case GROUP_HEADER -> {
                    if (items.stream().anyMatch(item -> mapType(item.type) == row.getGroupType() && matchesItem(item)))
                        managerText(document, list, "div", tr(row.getGroupType().getDisplayKey()), "group-heading");
                }
                case PATH_HEADER -> {
                    List<DlcServerItem> groupItems = row.getItems().stream().filter(this::matchesItem).toList();
                    if (groupItems.isEmpty()) continue;
                    Element heading = managerText(document, list, "div", "", "path-heading");
                    managerText(document, heading, "span", row.getLabel(), "path-label");
                    if (!row.isRootPath()) button(document, heading, tr(groupItems.stream().allMatch(item -> item.enabled) ? "dlc_manager.disable_all" : "dlc_manager.enable_all"), () -> {
                        if (!applying) {
                            boolean enable = groupItems.stream().anyMatch(item -> !item.enabled);
                            groupItems.forEach(item -> item.enabled = enable);
                            renderServerRows(document);
                        }
                    });
                    parent = addManagerGroup(document, list, heading, (groupItems.get(0).type == null ? "" : groupItems.get(0).type) + ":" + row.getLabel(), groupItems.size());
                }
                case ITEM -> {
                    DlcServerItem item = row.getItem();
                    if (item == null || !matchesItem(item)) continue;
                    count++;
                    if (item.enabled) enabled++;
                    Element line = parent.appendChild(document.createElement("div"));
                    Element name = styleManagerRow(document, line, resolveDisplayName(item), item.enabled, isDirty(item));
                    if (item.redirectUrl != null && !item.redirectUrl.isBlank()) {
                        long generation = document.getRefreshGeneration();
                        name.addEventListener("dblclick", event -> {
                            if (!applying && Minecraft.getInstance().screen == this && document.isCurrentGeneration(generation)) {
                                rememberListScroll();
                                Minecraft.getInstance().setScreen(new DlcRedirectConfirmScreen(this, item.redirectUrl, ""));
                            }
                        });
                    }
                    tooltip.bind(line, () -> serverTooltip(item));
                    if (isDirty(item)) managerText(document, line, "span", getPendingSuffix(item), "pending-label");
                    managerToggle(document, line, item.enabled, () -> { if (!applying) handleToggle(item); }).setEnabled(!applying);
                }
            }
        }
        if (count == 0) managerText(document, list, "p", tr(searchQuery.isBlank() ? "dlc_manager.ui.empty" : "dlc_manager.ui.no_results"), "empty-state");
        document.getElementById("manager-summary").setTextContent(tr("dlc_manager.ui.summary", count, enabled));
        renderedState = itemState();
        controlsLocked = null;
        updateManagerControls(document);
        updateAnimations(document, System.nanoTime());
        list.setScrollTop(scroll);
    }

    private DlcUiTooltip.Content serverTooltip(DlcServerItem item) {
        var details = new ArrayList<DlcUiTooltip.Detail>();
        var blockers = DlcPresetRuntimeState.getDlcBlockerNames(item.identifier, Minecraft.getInstance().options.languageCode);
        if (!blockers.isEmpty()) details.add(new DlcUiTooltip.Detail("", blockers.stream().map(name -> tr("dlc_manager.preset.conflict", name)).collect(java.util.stream.Collectors.joining("\n")), true));
        var missing = collectMissingDependencyIdentifiers(item); if (!missing.isEmpty()) details.add(new DlcUiTooltip.Detail(tr("dlc_manager.tooltip.missing_dependencies"), String.join("\n", missing.stream().map(id -> resolveDisplayName(resolveDependencyItem(id))).toList()), true));
        var deps = collectDependencyNamesRecursive(item); if (!deps.isEmpty()) details.add(new DlcUiTooltip.Detail(tr("dlc_manager.tooltip.dependencies"), String.join("\n", deps.stream().map(id -> resolveDisplayName(resolveDependencyItem(id))).toList()), false));
        if (!item.conflicts.isEmpty()) details.add(new DlcUiTooltip.Detail(tr("dlc_manager.tooltip.conflicts"), String.join("\n", item.conflicts.stream().map(id -> resolveDisplayName(resolveDependencyItem(id))).toList()), true));
        return new DlcUiTooltip.Content(resolveDisplayName(item), getTooltipText(item), details, item.redirectUrl != null && !item.redirectUrl.isBlank() ? tr("dlc_manager.tooltip.double_click_redirect") : getPendingSuffix(item));
    }
    private List<Boolean> itemState() {
        List<Boolean> state = new ArrayList<>();
        for (DlcServerItem item : items) { state.add(item.enabled); state.add(isDirty(item)); }
        return state;
    }

    private void updateManagerControls(Document document) {
        if (!java.util.Objects.equals(controlsLocked, applying)) {
            controlsLocked = applying;
            var managed = java.util.Arrays.asList(applyButton, backButton, enableAllButton, disableAllButton, searchClearButton).stream().filter(java.util.Objects::nonNull).map(DlcUiButton::getElement).toList();
            for (Element element : document.querySelectorAll("#manager button, #manager input")) {
                if (managed.contains(element)) continue;
                if (applying) element.setAttribute("disabled", ""); else element.removeAttribute("disabled");
            }
        }
        updateApplyState();
        List<DlcServerItem> visible = items.stream().filter(this::matchesVisibleItem).toList();
        String scope = !searchQuery.isBlank() ? tr("dlc_manager.scope.search") : activeFilter == null ? null : tr(activeFilter.getDisplayKey());
        boolean allEnabled = !visible.isEmpty() && visible.stream().allMatch(item -> item.enabled);
        boolean allDisabled = !visible.isEmpty() && visible.stream().noneMatch(item -> item.enabled);
        if (enableAllButton != null) { enableAllButton.setDisplayString(batchLabel(true, allEnabled, scope)); enableAllButton.setEnabled(!applying && !visible.isEmpty() && !allEnabled); }
        disableAllButton.setDisplayString(batchLabel(false, allDisabled, scope));
        disableAllButton.setEnabled(!applying && !visible.isEmpty() && !allDisabled);
        searchClearButton.setEnabled(!applying && !searchQuery.isBlank());
        long count = items.stream().filter(this::isDirty).count();
        Element status = document.getElementById("pending-status");
        String message = applying ? tr("dlc_manager.server.applying") : pageError != null ? pageError : tr(count > 0 ? "dlc_manager.ui.pending" : "dlc_manager.ui.clean", count);
        if (!message.equals(status.getTextContent())) status.setTextContent(message);
        String classes = count > 0 || applying || pageError != null ? "pending" : "";
        if (!classes.equals(status.getAttribute("class"))) status.setAttribute("class", classes);
    }

    private String batchLabel(boolean enable, boolean done, String scope) {
        String key = enable ? (done ? "dlc_manager.all_enabled" : "dlc_manager.enable_all") : (done ? "dlc_manager.all_disabled" : "dlc_manager.disable_all");
        return scope == null ? tr(key) : tr(key + "_type", scope);
    }

    private void confirmBatchToggle(boolean enable) {
        if (applying) return;
        rememberListScroll();
        Component scope = !searchQuery.isBlank() ? DlcTextCompat.translatable("dlc_manager.scope.search") : activeFilter == null ? null : DlcTextCompat.translatable(activeFilter.getDisplayKey());
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) setAllEnabled(enable);
        }, getTitle(), DlcConfirmUtil.buildHeaderConfirmMessage(enable, scope), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    @Override public void tick() {
        super.tick();
        Document document = getLinkedDocument();
        if (document == null || !document.isActive()) return;
        Document.runWithContext(document, () -> {
            rememberListScroll();
            if (!renderedState.equals(itemState()) || (Boolean.TRUE.equals(controlsLocked) && !applying)) renderServerRows(document);
            else updateManagerControls(document);
        });
    }

    private void rememberListScroll() {
        if (restoreScrollPending) return;
        Document document = getLinkedDocument();
        if (document != null) savedScrollOffset = document.getElementById("manager-list").getTargetScrollTop();
        savedScrollFilter = activeFilter;
    }

    //? if <26.1 {
    @Override public void resize(Minecraft minecraft, int width, int height) {
        rememberListScroll();
        restoreScrollPending = savedScrollFilter == activeFilter;
        super.resize(minecraft, width, height);
    }
    //?}

    //? if >=26.1 {
    /*@Override public void resize(int width, int height) {
        rememberListScroll();
        restoreScrollPending = savedScrollFilter == activeFilter;
        super.resize(width, height);
    }
    *///?}

    @Override protected boolean isUiContentReady() { return !restoreScrollPending; }

    //? if >=1.20.1 <26.1 {
    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        super.render(graphics, x, y, delta);
        Document document = getLinkedDocument();
        if (!restoreScrollPending || document == null || document.needsInitialFullCommit() || document.isInitialCommitSlicing()) return;
        Element list = document.getElementById("manager-list");
        if (list.getClientHeight() > 0 && ("auto".equals(list.getComputedStyle().overflowY)
            || "scroll".equals(list.getComputedStyle().overflowY))) {
            Document.runWithContext(document, () -> list.setScrollTop(savedScrollOffset));
        }
        restoreScrollPending = false;
    }
    //?}

    //? if <1.20.1 {
    /*@Override public void render(PoseStack graphics, int x, int y, float delta) {
        super.render(graphics, x, y, delta);
        Document document = getLinkedDocument();
        if (!restoreScrollPending || document == null || document.needsInitialFullCommit() || document.isInitialCommitSlicing()) return;
        Element list = document.getElementById("manager-list");
        if (list.getClientHeight() > 0 && ("auto".equals(list.getComputedStyle().overflowY)
            || "scroll".equals(list.getComputedStyle().overflowY))) {
            Document.runWithContext(document, () -> list.setScrollTop(savedScrollOffset));
        }
        restoreScrollPending = false;
    }
    *///?}

    //? if >=26.1 {
    /*@Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        Document document = getLinkedDocument();
        if (!restoreScrollPending || document == null || document.needsInitialFullCommit() || document.isInitialCommitSlicing()) return;
        Element list = document.getElementById("manager-list");
        if (list.getClientHeight() > 0 && ("auto".equals(list.getComputedStyle().overflowY)
            || "scroll".equals(list.getComputedStyle().overflowY))) {
            Document.runWithContext(document, () -> list.setScrollTop(savedScrollOffset));
        }
        restoreScrollPending = false;
    }
    *///?}

    private static String tr(String key, Object... args) { return DlcTextCompat.translatable(key, args).getString(); }

    @Override public void onClose() {
        if (applying || requiredMissingCount > 0) return;
        if (items.stream().anyMatch(this::isDirty)) Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> { if (accepted) { for (var item : items) item.enabled = baseline.getOrDefault(item.id, false); openServerList(); } else Minecraft.getInstance().setScreen(this); }, DlcTextCompat.translatable("dlc_manager.config.cancel_title"), DlcTextCompat.translatable("dlc_manager.config.cancel_prompt")));
        else openServerList();
    }
    @Override public boolean shouldCloseOnEsc() { return !applying && requiredMissingCount <= 0; }
}
