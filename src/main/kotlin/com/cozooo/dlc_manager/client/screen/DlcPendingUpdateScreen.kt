package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.gui.screens.Screen

class DlcPendingUpdateScreen(parent: Screen?) : DlcPageScreen(DlcUiPage.UPDATE_RESULT, parent, DlcTextCompat.translatable("dlc_manager.update.title")) {
    private var status = DlcTextCompat.translatable("dlc_manager.update.pending_applying").string
    fun setStatus(status: String) { this.status = status }
    override fun buildPage() { live(content, { status }, "page-description") }
    override fun shouldCloseOnEsc(): Boolean = false
    override fun onClose() { }
}
