package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcType;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
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

public class DlcServerMaLiLibScreen extends GuiListBase<DlcServerListRow, DlcServerListEntry, DlcServerListWidget> implements DlcReturnPositionHost {
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
    private int savedScrollOffset;
    private DlcType savedScrollFilter;
    private boolean restoreScrollPending;

    private ButtonGeneric applyButton;
    private ButtonGeneric backButton;
    private ButtonGeneric presetButton;
    private ButtonGeneric enableAllButton;
    private ButtonGeneric disableAllButton;
    private ButtonGeneric configButton;
    private ButtonGeneric serverToolsButton;
    private ButtonGeneric dlcTabButton;
    private ButtonGeneric requiredButton;
    private ButtonGeneric missingButton;

    private ButtonGeneric allFilterButton;
    private ButtonGeneric modsFilterButton;
    private ButtonGeneric taczFilterButton;
    private ButtonGeneric resourceFilterButton;
    private ButtonGeneric dataFilterButton;
    private ButtonGeneric otherFilterButton;
    private final Map<DlcType, ButtonGeneric> filterButtons = new EnumMap<>(DlcType.class);
    private DlcType activeFilter;
    private boolean applying;
    private int remoteMissingCount;
    private int requiredMissingCount;
    private boolean hasRequiredEntries;
    private boolean promptedRemoteDownload;

    public DlcServerMaLiLibScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info) {
        this(parent, entry, info, parent instanceof PauseScreen);
    }

    public DlcServerMaLiLibScreen(Screen parent, DlcServerEntry entry, DlcServerInfo info, boolean openedFromIngame) {
        super(10, 34);
        this.parent = parent;
        this.entry = entry;
        this.openedFromIngame = openedFromIngame;
        setTitle("");
        setParent(parent);
        setListPosition(18, 34);
        setInfo(info);
    }

    @Override
    protected DlcServerListWidget createListWidget(int x, int y) {
        return new DlcServerListWidget(x, y, getBrowserWidth(), getBrowserHeight(), rows, this) {
            @Override
            protected DlcServerListEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, DlcServerListRow entry) {
                return new DlcServerListEntry(x, y, this.browserEntryWidth, getBrowserEntryHeightFor(entry), isOdd, entry, listIndex,
                    DlcServerMaLiLibScreen.this);
            }
        };
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 36;
    }

    @Override
    protected int getBrowserHeight() {
        int listTop = getListTop();
        int listBottom = getFooterTop();
        return Math.max(80, listBottom - listTop);
    }

    @Override
    public void initGui() {
        promptRemoteDownloadIfMissing();
        rebuildRows();
        capturePresetSnapshot();
        reCreateListWidget();
        super.initGui();
        addHeaderButtons();
        addFilterButtons();
        addFooterButtons();
        updateListLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcTooltipContext.clear();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTitle(guiGraphics);
        DlcTooltipContext.render(guiGraphics, Minecraft.getInstance().font);
    }

    private void setInfo(DlcServerInfo info) {
        items.clear();
        itemsByIdentifier.clear();
        baseline.clear();
        remoteMissingCount = info != null ? info.remoteMissing : 0;
        requiredMissingCount = info != null ? info.requiredMissing : 0;
        hasRequiredEntries = info != null && info.hasRequired;
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
            grouped.computeIfAbsent(type, key -> new ArrayList<>()).add(item);
        }
        if (activeFilter == null) {
            for (DlcType type : DlcType.values()) {
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

    private void addHeaderButtons() {
        int right = this.width - 2;
        int y = 0;
        int h = 14;
        int gap = 6;

        int configWidth = Math.max(60, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.server.config")) + 12);
        int configX = right - configWidth;
        configButton = new ButtonGeneric(configX, y, configWidth, h, StringUtils.translate("dlc_manager.server.config"));
        addButton(configButton, new SimpleButtonListener(this::openConfig));

        int serverWidth = Math.max(60, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.server.tab.servers")) + 12);
        int serverX = configX - gap - serverWidth;
        serverToolsButton = new ButtonGeneric(serverX, y, serverWidth, h, StringUtils.translate("dlc_manager.server.tab.servers"));
        addButton(serverToolsButton, new SimpleButtonListener(this::openServerTools));

        int dlcWidth = Math.max(48, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.server.tab.dlc")) + 12);
        int dlcX = serverX - gap - dlcWidth;
        dlcTabButton = new ButtonGeneric(dlcX, y, dlcWidth, h, StringUtils.translate("dlc_manager.server.tab.dlc"));
        dlcTabButton.setEnabled(false);
        addButton(dlcTabButton, new SimpleButtonListener(() -> {}));

        String requiredLabel = StringUtils.translate("dlc_manager.required.manage_title");
        int requiredWidth = Math.max(60, StringUtils.getStringWidth(requiredLabel) + 12);
        requiredButton = new ButtonGeneric(8, 2, requiredWidth, h, requiredLabel);
        addButton(requiredButton, new SimpleButtonListener(this::openRequiredManager));
        if (!shouldShowRequiredButton()) {
            requiredButton.setEnabled(false);
            requiredButton.setX(-9999);
            requiredButton.setY(-9999);
        }
        if (remoteMissingCount > 0) {
            String missingLabel = StringUtils.translate("dlc_manager.server.remote_missing_button");
            int missingWidth = Math.max(60, StringUtils.getStringWidth(missingLabel) + 12);
            missingButton = new ButtonGeneric(8, 2, missingWidth, h, missingLabel);
            addButton(missingButton, new SimpleButtonListener(this::startRemoteDownload));
        } else {
            missingButton = null;
        }
    }

    private void addFilterButtons() {
        int panelLeft = 20;
        int panelRight = this.width - 20;
        int y = 19;
        int h = 18;
        int gap = this.width < 420 ? 4 : 6;
        int count = 6;
        int available = panelRight - panelLeft;
        int totalGap = gap * (count - 1);
        int w = Math.max(40, (available - totalGap) / count);
        if (w * count + totalGap > available) {
            gap = 2;
            totalGap = gap * (count - 1);
            w = Math.max(32, (available - totalGap) / count);
        }
        int x = panelLeft;

        allFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(null));
        addButton(allFilterButton, new FilterButtonListener(null));
        x += w + gap;
        modsFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.MOD));
        addButton(modsFilterButton, new FilterButtonListener(DlcType.MOD));
        x += w + gap;
        taczFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.TACZ_PACK));
        addButton(taczFilterButton, new FilterButtonListener(DlcType.TACZ_PACK));
        x += w + gap;
        resourceFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.RESOURCE_PACK));
        addButton(resourceFilterButton, new FilterButtonListener(DlcType.RESOURCE_PACK));
        x += w + gap;
        dataFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.DATA_PACK));
        addButton(dataFilterButton, new FilterButtonListener(DlcType.DATA_PACK));
        x += w + gap;
        otherFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.UNKNOWN));
        addButton(otherFilterButton, new FilterButtonListener(DlcType.UNKNOWN));

        filterButtons.put(DlcType.MOD, modsFilterButton);
        filterButtons.put(DlcType.TACZ_PACK, taczFilterButton);
        filterButtons.put(DlcType.RESOURCE_PACK, resourceFilterButton);
        filterButtons.put(DlcType.DATA_PACK, dataFilterButton);
        filterButtons.put(DlcType.UNKNOWN, otherFilterButton);
        updateFilterButtons();
    }

    private void addFooterButtons() {
        int gap = 4;
        int rightGap = 2;
        int totalButtons = 5;
        int buttonWidth = Math.min(80, (this.width - 16 - gap * (totalButtons - 1)) / totalButtons);
        int buttonHeight = 20;
        int buttonY = this.height - 28;
        int leftX = 6;
        int rightX = this.width - 6;
        enableAllButton = new ButtonGeneric(leftX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.enable_all"));
        disableAllButton = new ButtonGeneric(leftX + buttonWidth + gap, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.disable_all"));
        addButton(enableAllButton, new SimpleButtonListener(() -> setAllEnabled(true)));
        addButton(disableAllButton, new SimpleButtonListener(() -> setAllEnabled(false)));
        backButton = new ButtonGeneric(rightX - buttonWidth, buttonY, buttonWidth, buttonHeight, StringUtils.translate("gui.back"));
        applyButton = new ButtonGeneric(rightX - buttonWidth * 2 - rightGap, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.apply"));
        presetButton = new ButtonGeneric(applyButton.getX() - rightGap - buttonWidth, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.preset.manager"));
        addButton(presetButton, new SimpleButtonListener(this::openPreset));
        addButton(applyButton, new SimpleButtonListener(this::applyChanges));
        addButton(backButton, new SimpleButtonListener(this::openServerList));
        updateApplyState();
    }

    private void updateFilterButtons() {
        if (allFilterButton != null) {
            allFilterButton.setDisplayString(getFilterLabel(null));
            allFilterButton.setEnabled(activeFilter != null);
        }
        if (modsFilterButton != null) {
            modsFilterButton.setDisplayString(getFilterLabel(DlcType.MOD));
        }
        if (taczFilterButton != null) {
            taczFilterButton.setDisplayString(getFilterLabel(DlcType.TACZ_PACK));
        }
        if (resourceFilterButton != null) {
            resourceFilterButton.setDisplayString(getFilterLabel(DlcType.RESOURCE_PACK));
        }
        if (dataFilterButton != null) {
            dataFilterButton.setDisplayString(getFilterLabel(DlcType.DATA_PACK));
        }
        if (otherFilterButton != null) {
            otherFilterButton.setDisplayString(getFilterLabel(DlcType.UNKNOWN));
        }
        for (Map.Entry<DlcType, ButtonGeneric> entry : filterButtons.entrySet()) {
            ButtonGeneric button = entry.getValue();
            if (button != null) {
                button.setEnabled(activeFilter != entry.getKey());
            }
        }
    }

    private void openConfig() {
        Minecraft.getInstance().setScreen(new DlcServerConfigMaLiLibScreen(this, entry));
    }

    private void openServerTools() {
        Minecraft.getInstance().setScreen(new DlcServerManagerScreen(this, entry, buildInfoSnapshot(), DlcServerManagerScreen.Tab.SERVERS, openedFromIngame));
    }

    private void openRequiredManager() {
        Minecraft.getInstance().setScreen(new DlcServerRequiredManagerScreen(this, entry));
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
        info.baseVersion = serverBaseVersion;
        info.dlcVersion = serverDlcVersion;
        return info;
    }

    private void openPreset() {
        capturePresetSnapshot();
        Minecraft.getInstance().setScreen(new DlcPresetManagerScreen(this) {
            @Override
            public void onClose() {
                refreshAfterPresetChange();
                super.onClose();
            }
        });
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void setAllEnabled(boolean enabled) {
        for (DlcServerItem item : items) {
            if (item == null) {
                continue;
            }
            if (activeFilter != null && mapType(item.type) != activeFilter) {
                continue;
            }
            item.enabled = enabled;
        }
        updateApplyState();
    }

    void handleToggle(DlcServerItem item, ButtonGeneric button) {
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
            Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    continueEnableAfterConflictResolution(item);
                }
            }, Component.translatable("dlc_manager.conflict.word"), message, Component.translatable("dlc_manager.conflict.resolve"), Component.translatable("gui.cancel")));
            return;
        }
        DependencyCheck deps = checkDependencies(item);
        if (!deps.missing.isEmpty()) {
            Component message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames);
            rememberListScroll();
            Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    for (DlcServerItem dep : deps.missingItems) {
                        dep.enabled = true;
                    }
                    item.enabled = true;
                    updateApplyState();
                }
            }, Component.translatable("dlc_manager.dependency.enable"), message, Component.translatable("dlc_manager.dependency.enable"), Component.translatable("gui.cancel")));
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
            Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    for (DlcServerItem dep : deps.missingItems) {
                        dep.enabled = true;
                    }
                    item.enabled = true;
                    updateApplyState();
                }
            }, Component.translatable("dlc_manager.dependency.enable"), message, Component.translatable("dlc_manager.dependency.enable"), Component.translatable("gui.cancel")));
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
            backButton.setEnabled(!dirty && !applying && requiredMissingCount <= 0);
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
            return StringUtils.translate("dlc_manager.pending.restart");
        }
        return StringUtils.translate("dlc_manager.pending.reload");
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
                Component line = Component.translatable("dlc_manager.preset.conflict", blocker)
                    .withStyle(ChatFormatting.DARK_RED);
                lines.addAll(font.split(line, maxWidth));
            }
        }
        if (!missing.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component missingLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.missing_dependencies").withStyle(ChatFormatting.RED))
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
            Component depLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.dependencies").withStyle(ChatFormatting.GOLD))
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
            Component conflictLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.conflicts").withStyle(ChatFormatting.DARK_RED))
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
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(result -> {
            if (result) {
                CompletableFuture.runAsync(() -> {
                    try {
                        DlcServerConnection.restartServer(entry, 8000);
                    } catch (Exception ignored) {
                    }
                });
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.translatable("dlc_manager.server.restart_title"),
            Component.translatable("dlc_manager.server.restart_prompt")));
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
        reCreateListWidget();
        updateListLayout();
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
                    reCreateListWidget();
                    updateListLayout();
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

    private void updateListLayout() {
        setListPosition(18, getListTop());
        if (getListWidget() != null) {
            getListWidget().setSize(getBrowserWidth(), getBrowserHeight());
            getListWidget().initGui();
        }
        restoreListScroll();
    }

    private int getListTop() {
        return 34;
    }

    private int getFooterTop() {
        return this.height - 36;
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

    private int maxTextWidth(String a, String b) {
        return Math.max(StringUtils.getStringWidth(a), StringUtils.getStringWidth(b));
    }

    @Override
    public void rememberReturnPosition() {
        rememberListScroll();
    }

    private void rememberListScroll() {
        if (getListWidget() == null || getListWidget().getScrollbar() == null) {
            return;
        }
        savedScrollOffset = getListWidget().getScrollbar().getValue();
        savedScrollFilter = activeFilter;
        restoreScrollPending = true;
    }

    private void restoreListScroll() {
        if (!restoreScrollPending) {
            return;
        }
        if (getListWidget() == null || getListWidget().getScrollbar() == null) {
            return;
        }
        if (savedScrollFilter != activeFilter) {
            restoreScrollPending = false;
            return;
        }
        var scrollBar = getListWidget().getScrollbar();
        scrollBar.setValue(Math.min(savedScrollOffset, scrollBar.getMaxValue()));
        restoreScrollPending = false;
    }

    private void renderTitle(GuiGraphics guiGraphics) {
        String title = StringUtils.translate("dlc_manager.server.title");
        String base = serverBaseVersion == null ? "" : serverBaseVersion;
        String dlc = serverDlcVersion == null ? "" : serverDlcVersion;
        String version = "";
        if (!base.isBlank() || !dlc.isBlank()) {
            version = dlc.isBlank() ? base : (base.isBlank() ? dlc : base + "+" + dlc);
        }
        int titleX = 8;
        int titleY = 6;
        guiGraphics.drawString(Minecraft.getInstance().font, title, titleX, titleY, 0xFFFFFF, false);
        if (!version.isBlank()) {
            int titleWidth = Minecraft.getInstance().font.width(title);
            guiGraphics.drawString(Minecraft.getInstance().font, version, titleX + titleWidth + 6, titleY, 0xC8C8C8, false);
        }
        int textWidth = Minecraft.getInstance().font.width(title)
            + (version.isBlank() ? 0 : 6 + Minecraft.getInstance().font.width(version));
        int nextX = titleX + textWidth + 8;
        boolean showRequired = shouldShowRequiredButton();
        if (requiredButton != null) {
            if (!showRequired) {
                requiredButton.setEnabled(false);
                requiredButton.setX(-9999);
                requiredButton.setY(-9999);
            } else {
                requiredButton.setEnabled(true);
                int maxX = this.width - 10 - requiredButton.getWidth() - (missingButton != null ? (missingButton.getWidth() + 6) : 0);
                requiredButton.setX(Math.min(nextX, Math.max(titleX, maxX)));
                requiredButton.setY(2);
            }
        }
        if (missingButton != null) {
            int baseX = (showRequired && requiredButton != null)
                ? requiredButton.getX() + requiredButton.getWidth() + 6
                : nextX;
            int missMax = this.width - 10 - missingButton.getWidth();
            missingButton.setX(Math.min(baseX, Math.max(titleX, missMax)));
            missingButton.setY(2);
        }
    }

    private boolean shouldShowRequiredButton() {
        return hasRequiredEntries || requiredMissingCount > 0;
    }

    private String getFilterLabel(DlcType type) {
        boolean active = (type == null && activeFilter == null) || (type != null && type == activeFilter);
        String base = type == null
            ? StringUtils.translate("dlc_manager.filter.all")
            : StringUtils.translate(type.getDisplayKey());
        return active ? "[" + base + "]" : base;
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
            return StringUtils.translate("dlc_manager.group.root");
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

    private static class SimpleButtonListener implements IButtonActionListener {
        private final Runnable action;

        private SimpleButtonListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            action.run();
        }
    }

    private class FilterButtonListener implements IButtonActionListener {
        private final DlcType filter;

        private FilterButtonListener(DlcType filter) {
            this.filter = filter;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            activeFilter = filter;
            rebuildRows();
            reCreateListWidget();
            updateFilterButtons();
            updateListLayout();
            if (getListWidget() != null && getListWidget().getScrollbar() != null) {
                getListWidget().getScrollbar().setValue(0);
            }
        }
    }
}
