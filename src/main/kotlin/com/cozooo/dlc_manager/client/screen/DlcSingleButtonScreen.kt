package com.cozooo.dlc_manager.client.screen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcSingleButtonScreen(parent: Screen?, title: Component, private val message: Component, private val buttonLabel: Component) : DlcPageScreen(DlcUiPage.SINGLE_BUTTON, parent, title) {
    override fun buildPage() {
        rich(node(content, "div", "page-dialog"), message)
        val key = (buttonLabel.contents as? net.minecraft.network.chat.contents.TranslatableContents)?.key
        if (key !in setOf("gui.back", "dlc_manager.back", "dlc_manager.pack.back")) primary(buttonLabel.string) { onClose() }
    }
}
