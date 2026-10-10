package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcUpdateResultScreen(parent: Screen?, private val success: Boolean, private val message: Component) : DlcPageScreen(DlcUiPage.UPDATE_RESULT, parent, DlcTextCompat.translatable("dlc_manager.update.title")) {
    override fun buildPage() {
        val body = node(content, "div", "page-dialog")
        rich(node(body, "div", if (success) "page-message success" else "page-message error"), message)
        if (success) {
            text(body, tr("dlc_manager.update.restart"), "page-description")
            action(footer, tr("dlc_manager.restart_later")) { (pageParent as? DlcUpdateManagerScreen)?.refreshData(); onClose() }
            primary(tr("dlc_manager.restart_now")) { Minecraft.getInstance().stop() }
        }
    }
}
