package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcUpdateChangelogScreen(parent: Screen?, changelog: String?, private val onDone: Runnable?) : DlcPageScreen(DlcUiPage.UPDATE_CHANGELOG, parent, DlcTextCompat.translatable("dlc_manager.update.changelog_title")) {
    private val changelog = changelog.orEmpty()
    override fun buildPage() {
        markdown(node(content, "div", "page-prose"), changelog)
        primary(tr("dlc_manager.update.changelog_button")) { onClose() }
    }
    override fun onClose() {
        onDone?.run()
        if (Minecraft.getInstance().screen === this) super.onClose()
    }
}
