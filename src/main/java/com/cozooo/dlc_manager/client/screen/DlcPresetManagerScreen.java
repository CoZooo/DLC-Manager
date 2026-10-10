package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import java.util.List;
public class DlcPresetManagerScreen extends DlcPageScreen {
    private String query = "";
    private java.util.Set<String> matchedPresets = java.util.Set.of();
    private final List<DlcPresetEntry> presets;
    public DlcPresetManagerScreen(Screen parent) {
        this(DlcUiPage.LOCAL_PRESETS, parent);
    }
    public DlcPresetManagerScreen(DlcUiPage page, Screen parent) {
        super(page, parent, DlcTextCompat.translatable("dlc_manager.preset.title"));
        DlcPresetRuntimeState.reload(); presets = DlcPresetRuntimeState.getPresets();
    }
    @Override protected void buildPage() {
        input(toolbar, tr("dlc_manager.ui.search_presets"), query, "search", value -> { query = value; searchPresets(); });
        searchPresets();

    }
    private void searchPresets() {
        String lang = Minecraft.getInstance().options.languageCode;
        var entries = presets.stream().map(preset -> new DlcUiAsyncSearch.Entry<>(preset.getId(), preset.resolveDisplayName(lang), preset.getId())).toList();
        content.setAttribute("style", "pointer-events:none");
        content.setAttribute("aria-busy", "true");
        filterAsync(query, entries, matches -> {
            matchedPresets = matches; renderPresets(); content.removeAttribute("style"); content.removeAttribute("aria-busy");
        });
    }
    private void renderPresets() {
        content.setInnerHTML("");
        String lang = Minecraft.getInstance().options.languageCode;
        var list = section("", tr("dlc_manager.preset.desc"));
        int shown = 0;
        for (DlcPresetEntry preset : presets) {
            String name = preset.resolveDisplayName(lang);
            if (!matchedPresets.contains(preset.getId())) continue;
            shown++;
            boolean blocked = DlcPresetRuntimeState.isPresetBlocked(preset.getId());
            DlcPresetStatus state = pageParent instanceof DlcServerScreen server ? server.getPresetStatus(preset.getId()) : DlcPresetRuntimeState.getPresetStatus(preset.getId());
            String conflicts = String.join("\n", DlcPresetRuntimeState.getPresetBlockerNames(preset.getId(), lang).stream().map(other -> tr("dlc_manager.preset.conflict", other)).toList());
            var row = row(list, name, conflicts);
            tip(row, () -> new DlcUiTooltip.Content(name, preset.resolveTip(lang), List.of(), conflicts));
            var toggle = toggle(row, tr(state == DlcPresetStatus.PART ? "dlc_manager.ui.part" : state == DlcPresetStatus.ON ? "dlc_manager.toggle.on" : "dlc_manager.toggle.off"), state.isEnabled(), ignored -> togglePreset(preset));
            toggle.setEnabled(!blocked);
        }
        if (shown == 0) text(list, tr(query.isBlank() ? "dlc_manager.ui.empty_presets" : "dlc_manager.ui.no_results"), "empty-state");
    }
    private void togglePreset(DlcPresetEntry preset) {
        if (DlcPresetRuntimeState.isPresetBlocked(preset.getId())) return;
        boolean enable = !DlcPresetRuntimeState.isPresetEnabled(preset.getId());
        if (enable && preset.isAlarm()) Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(this, preset.resolveTip(Minecraft.getInstance().options.languageCode), () -> apply(preset, true)));
        else apply(preset, enable);
    }
    private void apply(DlcPresetEntry preset, boolean enabled) {
        DlcPresetRuntimeState.setPresetEnabled(preset.getId(), enabled);
        if (pageParent instanceof DlcManagerScreen manager) manager.refreshAfterStateChange();
        else if (pageParent instanceof DlcServerScreen server) server.refreshAfterPresetChange();
        renderPresets();
    }
}
