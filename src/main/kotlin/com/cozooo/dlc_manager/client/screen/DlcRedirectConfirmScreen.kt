package com.cozooo.dlc_manager.client.screen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.Util
class DlcRedirectConfirmScreen(parent: Screen?, url: String?, notice: String?) : DlcPageScreen(parent, Component.translatable("dlc_manager.redirect.title")) {
    private val url = url.orEmpty()
    private val notice = notice.orEmpty()
    override fun buildPage() {
        val body = node(content, "div", "page-dialog")
        text(body, tr("dlc_manager.redirect.header"), "section-title")
        text(body, tr("dlc_manager.redirect.prompt"), "page-description")
        text(body, url, "page-message success")
        if (notice.isNotBlank()) text(body, notice, "page-message")
        action(footer, DlcConfirmUtil.confirmNo().string) { onClose() }
        action(footer, tr("dlc_manager.redirect.copy")) { Minecraft.getInstance().keyboardHandler.setClipboard(url); onClose() }
        primary(tr("dlc_manager.redirect.open")) { Util.getPlatform().openUri(url); onClose() }
    }
}
