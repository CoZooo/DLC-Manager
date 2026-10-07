package com.cozooo.dlc_manager.client.screen
import com.cozooo.dlc_manager.client.config.DlcConfigValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.cozooo.dlc_manager.client.config.DlcServerConfigClient
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import java.util.concurrent.CompletableFuture
class DlcServerConfigScreen @JvmOverloads constructor(parent: Screen?, private val entry: DlcServerEntry?, tab: Tab? = Tab.SERVER) : DlcConfigScreen(parent, Component.translatable("dlc_manager.server.config_title"), categories(), key(tab)) {
    enum class Tab { SERVER, PATHS, UI, MODPACK, DOWNLOADS }
    private var loaded = false
    private var error: String? = null
    init { load() }
    private fun load() {
        busy = true; error = null
        CompletableFuture.supplyAsync { runCatching { DlcServerConnection.fetchServerConfig(entry, 5000) }.getOrNull() }.whenComplete { json, _ ->
            Minecraft.getInstance().execute {
                busy = false
                if (json == null) error = tr("dlc_manager.server.connect_failed")
                else { DlcServerConfigClient.loadFromJson(json); captureValues(); loaded = true }
                refreshPage()
            }
        }
    }
    override fun buildPage() {
        if (loaded) { super.buildPage(); return }
        text(content, error ?: tr("dlc_manager.server.connecting"), "empty-state")
        if (!busy) primary(tr("dlc_manager.server.rcon_refresh")) { load(); refreshPage() }

    }
    override fun updatePage() {
        if (loaded) super.updatePage()
        error?.let { status(it) }
    }
    override fun persist() {
        busy = true; error = null
        status(tr("dlc_manager.ui.saving"))
        val json = DlcServerConfigClient.toJson()
        CompletableFuture.supplyAsync { runCatching { DlcServerConnection.saveServerConfig(entry, 5000, json) }.getOrDefault(false) }.whenComplete { success, _ ->
            Minecraft.getInstance().execute {
                busy = false
                if (success == true) Minecraft.getInstance().setScreen(pageParent)
                else { error = tr("dlc_manager.server.config_save_failed"); status(error) }
            }
        }
    }
    companion object {
        private fun key(tab: Tab?): String = "dlc_manager.config.tab." + when (tab) { Tab.DOWNLOADS -> "download"; null -> "server"; else -> tab.name.lowercase() }
        private fun categories(): Map<String, List<DlcConfigValue>> = linkedMapOf(key(Tab.SERVER) to DlcServerConfigClient.getServerConfigs(), key(Tab.PATHS) to DlcServerConfigClient.getPathConfigs(), key(Tab.UI) to DlcServerConfigClient.getUiConfigs(), key(Tab.MODPACK) to DlcServerConfigClient.getModpackConfigs(), key(Tab.DOWNLOADS) to DlcServerConfigClient.getDownloadConfigs())
    }
}
