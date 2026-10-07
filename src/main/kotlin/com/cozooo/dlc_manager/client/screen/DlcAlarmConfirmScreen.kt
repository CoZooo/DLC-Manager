package com.cozooo.dlc_manager.client.screen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.cozooo.dlc_manager.core.DlcConfig
class DlcAlarmConfirmScreen(parent: Screen?, tip: String?, private val onConfirm: Runnable?) : DlcPageScreen(parent, Component.translatable("dlc_manager.alarm.title")) {
    private val tip = tip.orEmpty()
    private var acknowledged = false
    private val waitEndMillis = System.currentTimeMillis() + DlcConfig.getAlarmWaitingTime().coerceIn(0, 10) * 1000L
    private var confirmButton: DlcUiButton? = null
    private var acknowledgeButton: DlcUiButton? = null
    override fun buildPage() {
        val body = node(content, "div", "page-dialog")
        text(body, tip, "page-message warning")
        acknowledgeButton = toggle(body, tr("dlc_manager.alarm.read_ack"), acknowledged) { acknowledged = it; refreshPage() }
        action(footer, tr("dlc_manager.alarm.cancel")) { onClose() }
        confirmButton = primary(tr("dlc_manager.alarm.confirm")) {
            if (acknowledged && remaining() == 0) {
                onClose()
                onConfirm?.let { Minecraft.getInstance().execute(it) }
            }
        }
        updatePage()
    }
    private fun remaining(): Int = kotlin.math.ceil((waitEndMillis - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)
    override fun updatePage() {
        confirmButton?.setEnabled(acknowledged && remaining() == 0)
        acknowledgeButton?.setEnabled(remaining() == 0)
        status(if (remaining() > 0) tr("dlc_manager.alarm.remaining", remaining()) else "")
    }
}
