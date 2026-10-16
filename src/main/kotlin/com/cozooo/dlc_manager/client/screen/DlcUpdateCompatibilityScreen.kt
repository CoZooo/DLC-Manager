package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.platform.DlcTextCompat
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

class DlcUpdateCompatibilityScreen(
    private val parent: Screen,
    parameters: List<String>,
    private val proceed: Consumer<DlcUpdatePackage.CompatibilityAction>,
) : DlcPageScreen(DlcUiPage.CONFIRM, parent, DlcTextCompat.translatable("dlc_manager.update.unknown_title")) {
    private val parameters = parameters.toList()
    private var answered = false

    override fun buildPage() {
        text(content, tr("dlc_manager.update.unknown_description"), "page-warning")
        for (parameter in parameters) text(content, parameter, "page-description")
        action(footer, tr("dlc_manager.update.cancel"), ::onClose)
        action(footer, tr("dlc_manager.update.ignore_unknown")) { answer(DlcUpdatePackage.CompatibilityAction.IGNORE) }
        primary(tr("dlc_manager.update.update_manager_retry")) { answer(DlcUpdatePackage.CompatibilityAction.UPDATE_MANAGER) }
    }

    private fun answer(action: DlcUpdatePackage.CompatibilityAction) {
        if (answered) return
        answered = true
        Minecraft.getInstance().setScreen(parent)
        proceed.accept(action)
    }

    override fun onClose() {
        if (answered) return
        answered = true
        Minecraft.getInstance().setScreen(parent)
    }
}
