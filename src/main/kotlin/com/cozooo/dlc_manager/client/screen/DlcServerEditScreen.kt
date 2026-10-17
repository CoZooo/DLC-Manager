package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import java.util.function.Consumer
class DlcServerEditScreen(parent: Screen?, private val editing: DlcServerEntry?, private val onSave: Consumer<DlcServerEntry>?) : DlcPageScreen(DlcUiPage.SERVER_EDIT, parent, DlcTextCompat.translatable("dlc_manager.server.edit_title")) {
    private var name = editing?.name.orEmpty()
    private var host = editing?.host.orEmpty()
    private var port = (editing?.port?.takeIf { it > 0 } ?: 25565).toString()
    private var password = editing?.password.orEmpty()
    private var saveButton: DlcUiButton? = null
    override fun buildPage() {
        val form = node(content, "div", "page-dialog")
        input(form, tr("dlc_manager.server.name"), name, "text") { name = it }
        input(form, tr("dlc_manager.server.host"), host, "text") { host = it; updatePage() }
        input(form, tr("dlc_manager.server.port"), port, "text") { port = it; updatePage() }
        input(form, tr("dlc_manager.server.password"), password, "password") { password = it }
        action(footer, tr("gui.cancel")) { onClose() }
        saveButton = primary(tr("dlc_manager.server.save")) { save() }
        updatePage()
    }
    private fun portNumber(): Int = port.trim().toIntOrNull() ?: -1
    override fun updatePage() {
        val valid = portNumber() in 1..65535
        saveButton?.setEnabled(host.isNotBlank() && valid)
        status(if (valid) "" else tr("dlc_manager.ui.invalid_port"))
    }
    private fun save() {
        if (host.isBlank() || portNumber() !in 1..65535) return
        val entry = editing ?: DlcServerEntry()
        entry.name = name.trim(); entry.host = host.trim(); entry.port = portNumber(); entry.password = password.trim()
        onSave?.accept(entry)
        onClose()
    }
}
