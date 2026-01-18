package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.ApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
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

public class DlcMaLiLibScreen extends GuiListBase<DlcListRow, DlcListEntry, DlcListWidget> {
    private final Screen parent;
    private final List<DlcListRow> rows = new ArrayList<>();
    private final List<DlcItem> items = new ArrayList<>();
    private final List<DlcItem> allItems = new ArrayList<>();
    private ApplyMode applyMode;
    private boolean hasModItems;
    private boolean hasModChanges;
    private boolean userSelectedApplyMode;
    private int savedScrollOffset;
    private DlcType savedScrollFilter;
    private boolean restoreScrollPending;

    private ButtonGeneric applyButton;
    private ButtonGeneric backButton;
    private ButtonGeneric modeButton;
    private ButtonGeneric configButton;
    private ButtonGeneric updateButton;
    private ButtonGeneric enableAllButton;
    private ButtonGeneric disableAllButton;
    private ButtonGeneric noticeButton;
    private ButtonGeneric remoteButton;
    private ButtonGeneric requiredButton;
    private boolean noticeChecked;
    private ButtonGeneric allFilterButton;
    private ButtonGeneric modsFilterButton;
    private ButtonGeneric taczFilterButton;
    private ButtonGeneric resourceFilterButton;
    private ButtonGeneric dataFilterButton;
    private ButtonGeneric otherFilterButton;

    private DlcType activeFilter;
    private boolean footerTwoRows;
    private final boolean hasTaczMod = ModList.get().isLoaded("tacz");

    public DlcMaLiLibScreen(Screen parent) {
        super(10, 34);
        this.parent = parent;
        this.applyMode = ApplyMode.RESTART_ONLY;
        this.userSelectedApplyMode = false;
        setTitle(StringUtils.translate("dlc_manager.title"));
        setParent(parent);
        setListPosition(18, 34);
    }

    @Override
    protected DlcListWidget createListWidget(int x, int y) {
        return new DlcListWidget(x, y, getBrowserWidth(), getBrowserHeight(), rows);
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
        if (DlcRemoteManager.maybeShowFromManager(this)) {
            return;
        }
        if (!noticeChecked) {
            noticeChecked = true;
            if (DlcNoticeManager.maybeShowFromManager(this)) {
                return;
            }
        }
        rebuildRows();
        reCreateListWidget();
        super.initGui();
        addHeaderButtons();
        addNoticeButton();
        addRemoteButton();
        addRequiredButton();
        addFilterButtons();
        addFooterButtons();
        updateListLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcTooltipContext.clear();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        DlcTooltipContext.render(guiGraphics, Minecraft.getInstance().font);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (isOverAnyButton(mouseX, mouseY)) {
            return true;
        }
        return this.onMouseScrolled((int) mouseX, (int) mouseY, deltaX, deltaY);
    }

    private boolean isOverAnyButton(double mouseX, double mouseY) {
        return isOverButton(enableAllButton, mouseX, mouseY)
            || isOverButton(disableAllButton, mouseX, mouseY)
            || isOverButton(noticeButton, mouseX, mouseY)
            || isOverButton(remoteButton, mouseX, mouseY)
            || isOverButton(requiredButton, mouseX, mouseY)
            || isOverButton(allFilterButton, mouseX, mouseY)
            || isOverButton(modsFilterButton, mouseX, mouseY)
            || isOverButton(taczFilterButton, mouseX, mouseY)
            || isOverButton(resourceFilterButton, mouseX, mouseY)
            || isOverButton(dataFilterButton, mouseX, mouseY)
            || isOverButton(otherFilterButton, mouseX, mouseY)
            || isOverButton(modeButton, mouseX, mouseY)
            || isOverButton(updateButton, mouseX, mouseY)
            || isOverButton(configButton, mouseX, mouseY)
            || isOverButton(applyButton, mouseX, mouseY)
            || isOverButton(backButton, mouseX, mouseY);
    }

    private boolean isOverButton(ButtonGeneric button, double mouseX, double mouseY) {
        if (button == null) {
            return false;
        }
        int x = button.getX();
        int y = button.getY();
        return mouseX >= x && mouseX < x + button.getWidth()
            && mouseY >= y && mouseY < y + button.getHeight();
    }

    private void addHeaderButtons() {
        HeaderLabels labels = getHeaderLabels();
        boolean showEnable = DlcPathConfig.isEnableAllButtonShown();
        int right = this.width - 18;
        int y = 0;
        int h = 12;
        int gap = 6;
        int enableWidth = Math.max(60, maxTextWidth(labels.enableLabel, labels.enableDone) + 4);
        int disableWidth = Math.max(60, maxTextWidth(labels.disableLabel, labels.disableDone) + 4);

        int disableX = right - disableWidth;
        int enableX = disableX - gap - enableWidth;
        if (showEnable) {
            if (enableX < 18) {
                enableX = 18;
                disableX = enableX + enableWidth + gap;
            }
        } else {
            enableX = -1000;
        }

        enableAllButton = null;
        if (showEnable) {
            enableAllButton = new ButtonGeneric(enableX, y, enableWidth, h, labels.enableLabel);
            addButton(enableAllButton, new HeaderButtonListener(true));
        }
        disableAllButton = new ButtonGeneric(disableX, y, disableWidth, h, labels.disableLabel);
        addButton(disableAllButton, new HeaderButtonListener(false));
        updateHeaderButtonsState();
    }

    private void addNoticeButton() {
        if (!DlcNoticeManager.isNoticeEnabled()) {
            noticeButton = null;
            return;
        }
        String label = StringUtils.translate("dlc_manager.notice");
        int buttonWidth = Math.max(36, this.textRenderer.width(label) + 8);
        int buttonHeight = 12;
        int titleWidth = this.textRenderer.width(getTitleString());
        int x = 18 + titleWidth + 6;
        int y = 1;
        int rightLimit = this.width - 18;
        if (enableAllButton != null) {
            rightLimit = Math.min(rightLimit, enableAllButton.getX() - 6);
        } else if (disableAllButton != null) {
            rightLimit = Math.min(rightLimit, disableAllButton.getX() - 6);
        }
        if (x + buttonWidth > rightLimit) {
            x = rightLimit - buttonWidth;
        }
        if (x < 18) {
            x = 18;
        }
        noticeButton = new ButtonGeneric(x, y, buttonWidth, buttonHeight, label);
        addButton(noticeButton, new NoticeButtonListener());
    }

    private void addRemoteButton() {
        if (!DlcRemoteManager.hasMissing()) {
            remoteButton = null;
            return;
        }
        String label = StringUtils.translate("dlc_manager.remote.complete");
        int buttonWidth = Math.max(50, this.textRenderer.width(label) + 8);
        int buttonHeight = 12;
        int x = 18 + this.textRenderer.width(getTitleString()) + 6;
        int y = 1;
        if (noticeButton != null) {
            x = noticeButton.getX() + noticeButton.getWidth() + 4;
        }
        int rightLimit = this.width - 18;
        if (enableAllButton != null) {
            rightLimit = Math.min(rightLimit, enableAllButton.getX() - 6);
        } else if (disableAllButton != null) {
            rightLimit = Math.min(rightLimit, disableAllButton.getX() - 6);
        }
        if (x + buttonWidth > rightLimit) {
            x = rightLimit - buttonWidth;
        }
        if (x < 18) {
            x = 18;
        }
        remoteButton = new ButtonGeneric(x, y, buttonWidth, buttonHeight, label);
        addButton(remoteButton, new RemoteButtonListener());
    }

    private void addRequiredButton() {
        String label = StringUtils.translate("dlc_manager.required.manage_button");
        int buttonWidth = Math.max(50, this.textRenderer.width(label) + 8);
        int buttonHeight = 12;
        int x = 18 + this.textRenderer.width(getTitleString()) + 6;
        int y = 1;
        if (remoteButton != null) {
            x = remoteButton.getX() + remoteButton.getWidth() + 4;
        } else if (noticeButton != null) {
            x = noticeButton.getX() + noticeButton.getWidth() + 4;
        }
        int rightLimit = this.width - 18;
        if (enableAllButton != null) {
            rightLimit = Math.min(rightLimit, enableAllButton.getX() - 6);
        } else if (disableAllButton != null) {
            rightLimit = Math.min(rightLimit, disableAllButton.getX() - 6);
        }
        if (rightLimit - x < buttonWidth) {
            requiredButton = new ButtonGeneric(-9999, -9999, 0, 0, label);
            requiredButton.setEnabled(false);
            addButton(requiredButton, new RequiredButtonListener());
            return;
        }
        requiredButton = new ButtonGeneric(x, y, buttonWidth, buttonHeight, label);
        addButton(requiredButton, new RequiredButtonListener());
    }

    private void addFilterButtons() {
        int panelLeft = 20;
        int panelRight = this.width - 20;
        int y = 19;
        int h = 18;
        int gap = this.width < 420 ? 4 : 6;
        int count = hasTaczMod ? 6 : 5;
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

        if (hasTaczMod) {
            taczFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.TACZ_PACK));
            addButton(taczFilterButton, new FilterButtonListener(DlcType.TACZ_PACK));
            x += w + gap;
        }

        resourceFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.RESOURCE_PACK));
        addButton(resourceFilterButton, new FilterButtonListener(DlcType.RESOURCE_PACK));
        x += w + gap;

        dataFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.DATA_PACK));
        addButton(dataFilterButton, new FilterButtonListener(DlcType.DATA_PACK));
        x += w + gap;

        otherFilterButton = new ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.UNKNOWN));
        addButton(otherFilterButton, new FilterButtonListener(DlcType.UNKNOWN));
    }

    private void addFooterButtons() {
        int y = this.height - 30;
        int margin = 8;
        int gap = 2;
        int left = margin;
        int right = this.width - margin;

        modeButton = new ButtonGeneric(left, y, 150, 20, getApplyModeLabel());
        addButton(modeButton, new FooterButtonListener());

        updateButton = new ButtonGeneric(left + modeButton.getWidth() + gap, y, 90, 20, StringUtils.translate("dlc_manager.update.manager"));
        addButton(updateButton, new FooterButtonListener());

        configButton = null;
        if (DlcPathConfig.isConfigButtonShown()) {
            configButton = new ButtonGeneric(left + modeButton.getWidth() + gap + updateButton.getWidth() + gap, y, 48, 20, StringUtils.translate("dlc_manager.config"));
            addButton(configButton, new FooterButtonListener());
        }

        int tailWidth = 70;
        backButton = new ButtonGeneric(right - tailWidth, y, tailWidth, 20, getBackButtonLabel());
        addButton(backButton, new FooterButtonListener());

        applyButton = new ButtonGeneric(backButton.getX() - gap - tailWidth, y, tailWidth, 20, StringUtils.translate("dlc_manager.apply"));
        addButton(applyButton, new FooterButtonListener());

        int leftSpanEnd;
        if (configButton != null) {
            leftSpanEnd = configButton.getX() + configButton.getWidth();
        } else if (updateButton != null) {
            leftSpanEnd = updateButton.getX() + updateButton.getWidth();
        } else {
            leftSpanEnd = modeButton.getX() + modeButton.getWidth();
        }
        footerTwoRows = leftSpanEnd + gap > applyButton.getX();
        if (footerTwoRows) {
            int row1Y = this.height - 52;
            int row2Y = this.height - 30;
            modeButton.setY(row1Y);
            if (updateButton != null) {
                updateButton.setY(row1Y);
            }
            if (configButton != null) {
                configButton.setY(row1Y);
            }
            applyButton.setY(row2Y);
            backButton.setY(row2Y);
        }
        updateModeAvailability();
        ensureFooterNoOverlap();
    }

    private void rebuildRows() {
        rows.clear();
        items.clear();
        allItems.clear();
        hasModItems = false;
        hasModChanges = false;
        userSelectedApplyMode = false;

        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
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
                DlcRuntimeState.ensureBaseline(item.getId(), isEnabledInTargets(item));
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
            for (Map.Entry<String, List<DlcItem>> entry : byPath.entrySet()) {
                if ("__root__".equals(entry.getKey())) {
                    if (hasSubDir) {
                        rows.add(DlcListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), true));
                    }
                } else {
                    rows.add(DlcListRow.pathHeader(resolvePathLabel(entry.getKey()), entry.getValue(), false));
                }
                for (DlcItem item : entry.getValue()) {
                    DlcRuntimeState.ensureBaseline(item.getId(), isEnabledInTargets(item));
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
        hasModChanges = computeModReloadChanges();
        updateHeaderButtonsState();
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
            for (Path dlcRoot : DlcPathConfig.getDlcRoots()) {
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
            return StringUtils.translate("dlc_manager.group.root");
        }
        return raw;
    }

    private String getApplyModeLabel() {
        String modeName = StringUtils.translate(applyMode.getLabelKey());
        return StringUtils.translate("dlc_manager.apply_mode_label", modeName);
    }

    private String getFilterLabel(DlcType type) {
        boolean active = (type == null && activeFilter == null) || (type != null && type == activeFilter);
        String base = type == null
            ? StringUtils.translate("dlc_manager.filter.all")
            : StringUtils.translate(type.getDisplayKey());
        return active ? "[" + base + "]" : base;
    }

    private void toggleApplyMode() {
        if (hasModChanges) {
            applyMode = ApplyMode.RESTART_ONLY;
            updateModeAvailability();
            return;
        }
        applyMode = applyMode == ApplyMode.RESTART_ONLY ? ApplyMode.TRY_HOT_RELOAD : ApplyMode.RESTART_ONLY;
        userSelectedApplyMode = true;
        if (modeButton != null) {
            modeButton.setDisplayString(getApplyModeLabel());
        }
    }

    private void setFilter(DlcType type) {
        activeFilter = type;
        if (allFilterButton != null) {
            allFilterButton.setDisplayString(getFilterLabel(null));
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
        rebuildRows();
        reCreateListWidget();
        updateListLayout();
        updateHeaderButtonsLayout();
        updateNoticeButtonLayout();
        updateRemoteButtonLayout();
        updateRequiredButtonLayout();
        updateModeAvailability();
    }

    private void applyChanges() {
        boolean applyNeeded = computeApplyChanges();
        boolean modReloadNeeded = computeModReloadChanges();
        boolean otherReloadNeeded = computeNonModReloadChanges();
        boolean requiredPending = DlcRequiredRuntimeState.hasPendingChanges();
        if (!applyNeeded && !modReloadNeeded && !otherReloadNeeded) {
            if (requiredPending) {
                minecraft.setScreen(new ConfirmScreen(accepted -> {
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

        if (applyNeeded) {
            DlcApplier.apply(allItems, applyMode);
            updateResourcePackSelection();
        }

        if (!modReloadNeeded && !otherReloadNeeded) {
            if (requiredPending) {
                minecraft.setScreen(new ConfirmScreen(accepted -> {
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

        String message;
        if (modReloadNeeded) {
            message = "dlc_manager.message.restart";
        } else if (otherReloadNeeded && applyMode == ApplyMode.TRY_HOT_RELOAD) {
            message = "dlc_manager.message.reload_prompt";
        } else {
            message = "dlc_manager.message.reopen_world";
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (applyMode == ApplyMode.RESTART_ONLY) {
            minecraft.setScreen(new ConfirmScreen(accepted -> {
                if (accepted) {
                    minecraft.stop();
                } else {
                    minecraft.setScreen(parent);
                }
            }, Component.translatable("dlc_manager.title"), Component.translatable(message),
                Component.translatable("dlc_manager.restart_now"),
                Component.translatable("dlc_manager.restart_later")));
        } else if (applyMode == ApplyMode.TRY_HOT_RELOAD) {
            minecraft.setScreen(new ConfirmScreen(accepted -> {
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
            minecraft.setScreen(new ConfirmScreen(accepted -> minecraft.setScreen(parent),
                Component.translatable("dlc_manager.title"), Component.translatable(message)));
        }
    }

    private class FooterButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
        if (button == backButton) {
            closeGui(true);
        } else if (button == applyButton) {
            applyChanges();
        } else if (button == updateButton) {
            rememberListScroll();
            DlcUpdateClient.openUpdateManager(DlcMaLiLibScreen.this);
        } else if (button == configButton) {
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcPathConfigScreen(DlcMaLiLibScreen.this));
        } else if (button == modeButton) {
            toggleApplyMode();
        }
        }
    }

    @Override
    public void drawContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int panelLeft = 16;
        int panelRight = this.width - 16;
        int panelTop = 16;
        int headerHeight = 22;
        int panelBottom = this.height - 16;
        int footerTop = getFooterTop();
        int footerHeight = Math.max(0, panelBottom - footerTop);

        guiGraphics.fill(panelLeft, panelTop, panelRight, footerTop, 0xCC0F1114);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, footerTop - 1, panelRight, footerTop, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, footerTop, 0xFF2A2F36);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, footerTop, 0xFF2A2F36);
        guiGraphics.fill(panelLeft, panelTop + headerHeight, panelRight, panelTop + headerHeight + 1, 0xFF1D2229);
        guiGraphics.fill(panelLeft, footerTop, panelRight, footerTop + 1, 0xFF1D2229);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 120, panelTop + 2, 0xFF4C84FF);

        super.drawContents(guiGraphics, mouseX, mouseY, partialTick);
        drawTitle(guiGraphics, mouseX, mouseY, partialTick);
        updateModeAvailability();
        updateHeaderButtonsState();
    }

    private int getFooterTop() {
        int panelBottom = this.height - 16;
        int footerTop = panelBottom - (footerTwoRows ? 38 : 16);
        int buttonTop = Integer.MAX_VALUE;
        if (modeButton != null) {
            buttonTop = Math.min(buttonTop, modeButton.getY());
        }
        if (updateButton != null) {
            buttonTop = Math.min(buttonTop, updateButton.getY());
        }
        if (configButton != null) {
            buttonTop = Math.min(buttonTop, configButton.getY());
        }
        if (applyButton != null) {
            buttonTop = Math.min(buttonTop, applyButton.getY());
        }
        if (backButton != null) {
            buttonTop = Math.min(buttonTop, backButton.getY());
        }
        if (buttonTop != Integer.MAX_VALUE) {
            footerTop = Math.min(footerTop, buttonTop - 2);
        }
        return footerTop;
    }

    @Override
    protected void drawTitle(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int titleY = 6;
        guiGraphics.drawString(this.textRenderer, getTitleString(), 18, titleY, 0xFFFFFF, false);
    }

    private int getListTop() {
        int panelTop = 16;
        int headerHeight = 22;
        int filterHeight = 20;
        int base = panelTop + headerHeight + filterHeight - 8;
        return base - 8;
    }

    private void updateModeAvailability() {
        boolean modChanges = computeModReloadChanges();
        boolean otherChanges = computeNonModReloadChanges();
        hasModChanges = modChanges;
        if (modChanges) {
            applyMode = ApplyMode.RESTART_ONLY;
            userSelectedApplyMode = false;
        } else if (otherChanges) {
            if (!userSelectedApplyMode) {
                applyMode = ApplyMode.TRY_HOT_RELOAD;
            }
        } else {
            if (!userSelectedApplyMode) {
                applyMode = ApplyMode.RESTART_ONLY;
            }
        }
        if (modeButton != null) {
            modeButton.setEnabled(!modChanges);
            modeButton.setDisplayString(getApplyModeLabel());
        }
        updateBackButtonLabel();
    }

    private String getBackButtonLabel() {
        boolean hasChanges = computeApplyChanges() || computeModReloadChanges() || computeNonModReloadChanges();
        return hasChanges
            ? StringUtils.translate("dlc_manager.back_discard")
            : StringUtils.translate("dlc_manager.back");
    }

    private void updateBackButtonLabel() {
        if (backButton == null) {
            return;
        }
        backButton.setDisplayString(getBackButtonLabel());
        backButton.setEnabled(!DlcRequiredRuntimeState.hasPendingChanges());
    }

    private boolean computeModReloadChanges() {
        for (DlcItem item : allItems) {
            if (item.getType() != DlcType.MOD) {
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
            if (item.getType() == DlcType.MOD) {
                continue;
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnabledInTargets(DlcItem item) {
        String name = item.getSourcePath().getFileName().toString();
        List<Path> targetDirs = switch (item.getType()) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> List.of();
            case DATA_PACK -> DlcPathConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> DlcPathConfig.getOtherRoots();
        };
        if (item.getType() == DlcType.RESOURCE_PACK) {
            return isResourcePackSelected(name);
        }
        if (targetDirs.isEmpty()) {
            return false;
        }
        for (Path dir : targetDirs) {
            if (!java.nio.file.Files.exists(dir.resolve(name))) {
                return false;
            }
        }
        return true;
    }

    private boolean isResourcePackSelected(String fileName) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            PackRepository repo = minecraft.getResourcePackRepository();
            String packId = resolvePackId(repo, fileName);
            if (packId == null) {
                return false;
            }
            return repo.getSelectedIds().contains(packId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isItemModified(DlcItem item) {
        return DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled());
    }

    public static String getPendingSuffix(DlcItem item) {
        if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
            return "";
        }
        if (item.getType() == DlcType.MOD) {
            return StringUtils.translate("dlc_manager.pending.restart");
        }
        return StringUtils.translate("dlc_manager.pending.reload");
    }

    private boolean computeApplyChanges() {
        for (DlcItem item : allItems) {
            boolean current = isEnabledInTargets(item);
            if (item.isEnabled() != current) {
                return true;
            }
        }
        return false;
    }


    private void updateListLayout() {
        setListPosition(18, getListTop());
        if (getListWidget() != null) {
            getListWidget().setSize(getBrowserWidth(), getBrowserHeight());
            getListWidget().initGui();
        }
        restoreListScroll();
    }

    void refreshAfterStateChange() {
        for (DlcItem item : allItems) {
            if (item == null) {
                continue;
            }
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override != null) {
                item.setEnabled(override);
            }
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            item.setDirty(isItemModified(item));
        }
        updateHeaderButtonsState();
        updateModeAvailability();
        updateListLayout();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        clearButtons();
        addHeaderButtons();
        addNoticeButton();
        addRemoteButton();
        addRequiredButton();
        addFilterButtons();
        addFooterButtons();
        updateListLayout();
    }

    private void ensureFooterNoOverlap() {
        int minGap = 6;
        int leftEnd;
        if (configButton != null) {
            leftEnd = configButton.getX() + configButton.getWidth();
        } else if (updateButton != null) {
            leftEnd = updateButton.getX() + updateButton.getWidth();
        } else {
            leftEnd = modeButton.getX() + modeButton.getWidth();
        }
        int rightStart = applyButton.getX();
        if (rightStart - leftEnd < minGap) {
            int row1Y = this.height - 52;
            int row2Y = this.height - 30;
            modeButton.setY(row1Y);
            if (updateButton != null) {
                updateButton.setY(row1Y);
            }
            if (configButton != null) {
                configButton.setY(row1Y);
            }
            applyButton.setY(row2Y);
            backButton.setY(row2Y);
            footerTwoRows = true;
        }
    }

    private java.util.concurrent.CompletableFuture<Void> tryReloadResources() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.toast.reload_title"),
                Component.translatable("dlc_manager.toast.reload_start"));
            var future = minecraft.reloadResourcePacks();
            future.thenRun(() -> minecraft.execute(() ->
                SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("dlc_manager.toast.reload_title"),
                    Component.translatable("dlc_manager.toast.reload_done"))));
            return future;
        } catch (Throwable ignored) {
            // Best effort.
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
            // Best effort.
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

    private class FilterButtonListener implements IButtonActionListener {
        private final DlcType filter;

        private FilterButtonListener(DlcType filter) {
            this.filter = filter;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            setFilter(filter);
        }
    }

    private class NoticeButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            rememberListScroll();
            DlcNoticeManager.forceShow(DlcMaLiLibScreen.this);
        }
    }

    private class RemoteButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            java.util.List<com.cozooo.dlc_manager.client.remote.DlcRemoteEntry> missing = DlcRemoteManager.collectMissing();
            if (missing.isEmpty()) {
                return;
            }
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(DlcMaLiLibScreen.this, missing));
        }
    }

    private class RequiredButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            rememberListScroll();
            Minecraft.getInstance().setScreen(new DlcRequiredManagerScreen(DlcMaLiLibScreen.this));
        }
    }

    private class HeaderButtonListener implements IButtonActionListener {
        private final boolean enable;

        private HeaderButtonListener(boolean enable) {
            this.enable = enable;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            Component message = getHeaderConfirmMessage(enable);
            rememberListScroll();
            Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(DlcMaLiLibScreen.this);
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
                        Component scopeLabel = activeFilter == null
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
                Component.translatable("dlc_manager.confirm_yes"),
                Component.translatable("dlc_manager.confirm_no")));
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
        if (activeFilter == null) {
            return new java.util.ArrayList<>(allItems);
        }
        return allItems.stream()
            .filter(item -> item.getType() == activeFilter)
            .toList();
    }

    private void updateHeaderButtonsLayout() {
        if (disableAllButton == null) {
            return;
        }
        HeaderLabels labels = getHeaderLabels();
        boolean showEnable = DlcPathConfig.isEnableAllButtonShown();
        int right = this.width - 18;
        int y = 0;
        int h = 12;
        int gap = 6;
        int enableWidth = Math.max(60, maxTextWidth(labels.enableLabel, labels.enableDone) + 4);
        int disableWidth = Math.max(60, maxTextWidth(labels.disableLabel, labels.disableDone) + 4);
        int disableX = right - disableWidth;
        int enableX = disableX - gap - enableWidth;
        if (showEnable) {
            if (enableX < 18) {
                enableX = 18;
                disableX = enableX + enableWidth + gap;
            }
        } else {
            enableX = -1000;
        }
        if (enableAllButton != null) {
            enableAllButton.setX(enableX);
            enableAllButton.setY(y);
            enableAllButton.setWidth(enableWidth);
            enableAllButton.setHeight(h);
        }
        disableAllButton.setX(disableX);
        disableAllButton.setY(y);
        disableAllButton.setWidth(disableWidth);
        disableAllButton.setHeight(h);
    }

    private void updateNoticeButtonLayout() {
        if (noticeButton == null) {
            return;
        }
        String label = StringUtils.translate("dlc_manager.notice");
        int buttonWidth = Math.max(36, this.textRenderer.width(label) + 8);
        int buttonHeight = 12;
        int titleWidth = this.textRenderer.width(getTitleString());
        int x = 18 + titleWidth + 6;
        int y = 1;
        int rightLimit = this.width - 18;
        if (enableAllButton != null) {
            rightLimit = Math.min(rightLimit, enableAllButton.getX() - 6);
        } else if (disableAllButton != null) {
            rightLimit = Math.min(rightLimit, disableAllButton.getX() - 6);
        }
        if (x + buttonWidth > rightLimit) {
            x = rightLimit - buttonWidth;
        }
        if (x < 18) {
            x = 18;
        }
        noticeButton.setX(x);
        noticeButton.setY(y);
        noticeButton.setWidth(buttonWidth);
        noticeButton.setHeight(buttonHeight);
    }

    private void updateRemoteButtonLayout() {
        if (remoteButton == null) {
            return;
        }
        String label = StringUtils.translate("dlc_manager.remote.complete");
        int buttonWidth = Math.max(50, this.textRenderer.width(label) + 8);
        int buttonHeight = 12;
        int x = 18 + this.textRenderer.width(getTitleString()) + 6;
        int y = 1;
        if (noticeButton != null) {
            x = noticeButton.getX() + noticeButton.getWidth() + 4;
        }
        int rightLimit = this.width - 18;
        if (enableAllButton != null) {
            rightLimit = Math.min(rightLimit, enableAllButton.getX() - 6);
        } else if (disableAllButton != null) {
            rightLimit = Math.min(rightLimit, disableAllButton.getX() - 6);
        }
        if (x + buttonWidth > rightLimit) {
            x = rightLimit - buttonWidth;
        }
        if (x < 18) {
            x = 18;
        }
        remoteButton.setX(x);
        remoteButton.setY(y);
        remoteButton.setWidth(buttonWidth);
        remoteButton.setHeight(buttonHeight);
    }

    private void updateRequiredButtonLayout() {
        if (requiredButton == null) {
            return;
        }
        String label = StringUtils.translate("dlc_manager.required.manage_button");
        int buttonWidth = Math.max(50, this.textRenderer.width(label) + 8);
        int buttonHeight = 12;
        int x = 18 + this.textRenderer.width(getTitleString()) + 6;
        int y = 1;
        if (remoteButton != null) {
            x = remoteButton.getX() + remoteButton.getWidth() + 4;
        } else if (noticeButton != null) {
            x = noticeButton.getX() + noticeButton.getWidth() + 4;
        }
        int rightLimit = this.width - 18;
        if (enableAllButton != null) {
            rightLimit = Math.min(rightLimit, enableAllButton.getX() - 6);
        } else if (disableAllButton != null) {
            rightLimit = Math.min(rightLimit, disableAllButton.getX() - 6);
        }
        if (rightLimit - x < buttonWidth) {
            requiredButton.setEnabled(false);
            requiredButton.setX(-9999);
            requiredButton.setY(-9999);
            requiredButton.setWidth(0);
            requiredButton.setHeight(0);
            return;
        }
        requiredButton.setEnabled(true);
        requiredButton.setDisplayString(label);
        requiredButton.setX(x);
        requiredButton.setY(y);
        requiredButton.setWidth(buttonWidth);
        requiredButton.setHeight(buttonHeight);
    }

    private Component getHeaderConfirmMessage(boolean enable) {
        if (activeFilter == null) {
            String key = enable ? "dlc_manager.confirm.enable_all" : "dlc_manager.confirm.disable_all";
            return Component.translatable(key);
        }
        String key = enable ? "dlc_manager.confirm.enable_all_type" : "dlc_manager.confirm.disable_all_type";
        return Component.translatable(key, Component.translatable(activeFilter.getDisplayKey()));
    }

    private void handleBatchEnable(java.util.List<DlcItem> targets, Component scopeLabel) {
        DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(targets);
        if (!conflicts.getLines().isEmpty()) {
            showBatchConflictPrompt(conflicts.getLines());
            return;
        }
        DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(targets);
        if (!deps.getLines().isEmpty()) {
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel);
            return;
        }
        enableItems(targets, true);
        updateModeAvailability();
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

    private void showBatchConflictPrompt(java.util.List<DlcRuntimeState.ConflictLine> lines) {
        java.util.List<Component> components = new java.util.ArrayList<>();
        for (DlcRuntimeState.ConflictLine line : lines) {
            components.add(buildConflictLine(
                DlcDisplayNameUtil.resolveDisplayName(line.getLeftName()),
                DlcDisplayNameUtil.resolveDisplayName(line.getRightName())));
        }
        Component message = joinLines(components);
        Component title = Component.translatable("dlc_manager.batch.conflicts_title")
            .withStyle(net.minecraft.ChatFormatting.DARK_RED);
        rememberListScroll();
        Minecraft.getInstance().setScreen(new DlcSingleButtonScreen(this, title, message,
            Component.translatable("dlc_manager.confirm_no")));
    }

    private void showBatchDependencyPrompt(java.util.List<DlcRuntimeState.DependencyLine> lines,
        java.util.List<DlcItem> depsToEnable, java.util.List<DlcItem> targets, Component scopeLabel) {
        java.util.List<Component> components = new java.util.ArrayList<>();
        for (DlcRuntimeState.DependencyLine line : lines) {
            components.add(Component.translatable("dlc_manager.batch.dependencies_line",
                DlcDisplayNameUtil.resolveDisplayName(line.getItemName()),
                DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName())));
        }
        Component message = joinLines(components);
        Component title = Component.translatable("dlc_manager.batch.dependencies_title");
        Component enableLabel = Component.translatable("dlc_manager.batch.enable_deps", scopeLabel);
        rememberListScroll();
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (!accepted) {
                return;
            }
            enableItems(depsToEnable, true);
            enableItems(targets, true);
            refreshAfterStateChange();
        }, title, message, enableLabel, Component.translatable("dlc_manager.confirm_no")));
    }

    private Component buildConflictLine(String left, String right) {
        return Component.translatable("dlc_manager.batch.conflicts_line_prefix", left, right)
            .append(Component.translatable("dlc_manager.conflict.word").withStyle(net.minecraft.ChatFormatting.DARK_RED));
    }

    private Component joinLines(java.util.List<Component> lines) {
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.literal("\n"));
            }
            result = result.append(lines.get(i));
        }
        return result;
    }

    private HeaderLabels getHeaderLabels() {
        if (activeFilter == null) {
            return new HeaderLabels(
                StringUtils.translate("dlc_manager.enable_all"),
                StringUtils.translate("dlc_manager.disable_all"),
                StringUtils.translate("dlc_manager.all_enabled"),
                StringUtils.translate("dlc_manager.all_disabled"));
        }
        String typeName = StringUtils.translate(activeFilter.getDisplayKey());
        return new HeaderLabels(
            StringUtils.translate("dlc_manager.enable_all_type", typeName),
            StringUtils.translate("dlc_manager.disable_all_type", typeName),
            StringUtils.translate("dlc_manager.all_enabled_type", typeName),
            StringUtils.translate("dlc_manager.all_disabled_type", typeName));
    }

    @Override
    protected void closeGui(boolean closeInGame) {
        DlcRuntimeState.clearOverrides();
        super.closeGui(closeInGame);
    }

    void rememberListScroll() {
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

    private int maxTextWidth(String a, String b) {
        return Math.max(this.textRenderer.width(a), this.textRenderer.width(b));
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
}
