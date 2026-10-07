package com.cozooo.dlc_manager.client.screen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus
open class DlcPresetManagerScreen(parent: Screen?) : DlcPageScreen(parent, Component.translatable("dlc_manager.preset.title")) {
    private var query = ""
    private var matchedPresets = emptySet<String>()
    private val presets: List<DlcPresetEntry>
    init { DlcPresetRuntimeState.reload(); presets = DlcPresetRuntimeState.getPresets() }
    override fun buildPage() {
        input(toolbar, tr("dlc_manager.ui.search_presets"), query, "search") { query = it; searchPresets() }
        searchPresets()

    }
    private fun searchPresets() {
        val lang = Minecraft.getInstance().options.languageCode
        val entries = presets.map { DlcUiAsyncSearch.Entry(it.getId().orEmpty(), it.resolveDisplayName(lang), it.getId()) }
        content.setAttribute("style", "pointer-events:none")
        content.setAttribute("aria-busy", "true")
        filterAsync(query, entries) { matches ->
            matchedPresets = matches; renderPresets(); content.removeAttribute("style"); content.removeAttribute("aria-busy")
        }
    }
    private fun renderPresets() {
        content.setInnerHTML("")
        val lang = Minecraft.getInstance().options.languageCode
        val list = section("", tr("dlc_manager.preset.desc"))
        var shown = 0
        for (preset in presets) {
            val name = preset.resolveDisplayName(lang).orEmpty()
            if (preset.getId().orEmpty() !in matchedPresets) continue
            shown++
            val blocked = DlcPresetRuntimeState.isPresetBlocked(preset.getId())
            val state = (pageParent as? DlcServerScreen)?.getPresetStatus(preset.getId()) ?: DlcPresetRuntimeState.getPresetStatus(preset.getId())
            val conflicts = DlcPresetRuntimeState.getPresetBlockerNames(preset.getId(), lang).joinToString("\n") { tr("dlc_manager.preset.conflict", it) }
            val row = row(list, name, conflicts)
            tip(row) { DlcUiTooltip.Content(name, preset.resolveTip(lang).orEmpty(), emptyList(), conflicts) }
            toggle(row, tr(when (state) { DlcPresetStatus.PART -> "dlc_manager.ui.part"; DlcPresetStatus.ON -> "dlc_manager.toggle.on"; else -> "dlc_manager.toggle.off" }), state.isEnabled()) { togglePreset(preset) }.setEnabled(!blocked)
        }
        if (shown == 0) text(list, tr(if (query.isBlank()) "dlc_manager.ui.empty_presets" else "dlc_manager.ui.no_results"), "empty-state")
    }
    private fun togglePreset(preset: DlcPresetEntry) {
        if (DlcPresetRuntimeState.isPresetBlocked(preset.getId())) return
        val enable = !DlcPresetRuntimeState.isPresetEnabled(preset.getId())
        if (enable && preset.isAlarm()) Minecraft.getInstance().setScreen(DlcAlarmConfirmScreen(this, preset.resolveTip(Minecraft.getInstance().options.languageCode)) { apply(preset, true) })
        else apply(preset, enable)
    }
    private fun apply(preset: DlcPresetEntry, enabled: Boolean) {
        DlcPresetRuntimeState.setPresetEnabled(preset.getId(), enabled)
        when (val parent = pageParent) { is DlcManagerScreen -> parent.refreshAfterStateChange(); is DlcServerScreen -> parent.refreshAfterPresetChange() }
        renderPresets()
    }
}
