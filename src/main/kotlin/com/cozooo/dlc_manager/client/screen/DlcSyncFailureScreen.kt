package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import net.minecraft.client.Minecraft

class DlcSyncFailureScreen(private val callback: BooleanConsumer, private val reason: String) :
    DlcPageScreen(DlcUiPage.CONFIRM, Minecraft.getInstance().screen, DlcTextCompat.translatable("dlc_manager.sync.failed")) {
    private var answered = false
    override fun showHeaderBackButton() = false
    override fun buildPage() {
        rich(node(content, "div", "page-dialog"), DlcTextCompat.translatable("dlc_manager.sync.failure_choice", reason))
        action(footer, tr("dlc_manager.sync.rollback")) { answer(true) }.getElement().setAttribute("class", "button danger")
        primary(tr("dlc_manager.sync.keep")) { answer(false) }
    }
    private fun answer(restore: Boolean) {
        if (answered) return
        answered = true
        callback.accept(restore)
    }
    override fun onClose() { answer(false) }
}
