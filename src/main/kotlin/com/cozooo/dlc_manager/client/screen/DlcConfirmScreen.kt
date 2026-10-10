package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import it.unimi.dsi.fastutil.booleans.BooleanConsumer
class DlcConfirmScreen @JvmOverloads constructor(private val callback: BooleanConsumer, title: Component, private val message: Component, private val yes: Component = DlcTextCompat.translatable("gui.yes"), private val no: Component = DlcTextCompat.translatable("gui.no")) : DlcPageScreen(DlcUiPage.CONFIRM, Minecraft.getInstance().screen, title) {
    private var answered = false
    override fun buildPage() {
        rich(node(content, "div", "page-dialog"), message)
        action(footer, no.string) { onClose() }
        primary(yes.string) { answer(true) }
    }
    private fun answer(value: Boolean) {
        if (answered) return
        answered = true
        callback.accept(value)
    }
    override fun onClose() { answer(false) }
}
