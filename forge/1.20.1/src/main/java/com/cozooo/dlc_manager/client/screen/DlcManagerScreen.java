package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.ApplyMode;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DlcManagerScreen extends Screen {
    private final Screen parent;
    private DlcEntryList entryList;
    private CycleButton<ApplyMode> modeButton;

    public DlcManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entryList = new DlcEntryList(minecraft, width, height, 44, height - 72, 26);
        addRenderableWidget(entryList);
        entryList.setEntries(loadEntries());

        CycleButton.Builder<ApplyMode> builder = CycleButton.builder(mode -> Component.translatable(mode.getLabelKey()));
        modeButton = builder
            .withValues(ApplyMode.RESTART_ONLY, ApplyMode.TRY_HOT_RELOAD)
            .withInitialValue(ApplyMode.RESTART_ONLY)
            .create(width / 2 - 110, height - 56, 220, 20, Component.translatable("dlc_manager.apply_mode"));
        addRenderableWidget(modeButton);

        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.apply"), btn -> applyChanges())
            .bounds(width / 2 - 110, height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.back"), btn -> onClose())
            .bounds(width / 2 + 10, height - 32, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        int panelLeft = 24;
        int panelRight = width - 24;
        int panelTop = 16;
        int panelBottom = height - 20;
        int panelColor = 0xCC0F1114;
        int panelBorder = 0xFF2A2F36;
        int accent = 0xFF4C84FF;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, panelColor);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, panelBorder);
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, panelBorder);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, panelBorder);
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, panelBorder);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 120, panelTop + 2, accent);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFF);
    }

    private void applyChanges() {
        List<DlcItem> items = entryList.getItems();
        DlcApplier.ApplyResult result = DlcApplier.apply(items, modeButton.getValue());

        String message;
        if (result.requiresRestart()) {
            message = "dlc_manager.message.restart";
        } else if (modeButton.getValue() == ApplyMode.TRY_HOT_RELOAD && result.hotReloaded()) {
            message = "dlc_manager.message.reloaded";
        } else {
            message = "dlc_manager.message.reopen_world";
        }

        if (modeButton.getValue() == ApplyMode.RESTART_ONLY) {
            minecraft.setScreen(new ConfirmScreen(accepted -> {
                if (accepted) {
                    minecraft.stop();
                } else {
                    minecraft.setScreen(parent);
                }
            }, Component.translatable("dlc_manager.title"), Component.translatable(message),
                Component.translatable("dlc_manager.restart_now"),
                Component.translatable("dlc_manager.restart_later")));
        } else {
            minecraft.setScreen(new ConfirmScreen(accepted -> minecraft.setScreen(parent),
                Component.translatable("dlc_manager.title"), Component.translatable(message)));
        }
    }

    private List<DlcEntryList.Entry> loadEntries() {
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
        List<DlcEntryList.Entry> entries = new ArrayList<>();
        for (DlcType type : DlcType.values()) {
            List<DlcItem> items = grouped.get(type);
            if (items == null || items.isEmpty()) {
                continue;
            }
            entries.add(new DlcEntryList.GroupHeaderEntry(Component.translatable(type.getDisplayKey())));
            Map<String, List<DlcItem>> byPath = groupByRelativeFolder(items);
            for (Map.Entry<String, List<DlcItem>> group : byPath.entrySet()) {
                String key = group.getKey();
                Component label = "__root__".equals(key)
                    ? Component.translatable("dlc_manager.group.root")
                    : Component.literal(key);
                entries.add(new DlcEntryList.PathHeaderEntry(label));
                for (DlcItem item : group.getValue()) {
                    entries.add(new DlcEntryList.DlcItemEntry(item));
                }
            }
        }
        return entries;
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
            String relative = DlcPaths.gameDir().relativize(item.getSourcePath()).toString().replace('\\', '/');
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash <= 0) {
                return "__root__";
            }
            return relative.substring(0, lastSlash);
        } catch (Exception ignored) {
            return "__root__";
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
