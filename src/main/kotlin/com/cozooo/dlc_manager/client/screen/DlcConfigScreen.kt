package com.cozooo.dlc_manager.client.screen
import com.cozooo.dlc_manager.client.config.DlcConfigValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.cozooo.dlc_manager.client.config.DlcConfigClient
import com.cozooo.dlc_manager.core.DlcConfig
open class DlcConfigScreen protected constructor(page: DlcUiPage, parent: Screen?, title: Component, values: Map<String, List<DlcConfigValue>>, category: String) : DlcSettingsScreen(page, parent, title, values, category) {
    protected constructor(parent: Screen?, title: Component, values: Map<String, List<DlcConfigValue>>, category: String) : this(DlcUiPage.LOCAL_CONFIG, parent, title, values, category)
    @JvmOverloads constructor(parent: Screen?, tab: Tab? = Tab.PATHS) : this(DlcUiPage.LOCAL_CONFIG, parent, Component.translatable("dlc_manager.config.menu.title"), categories(), key(tab))
    enum class Tab { SERVER, PATHS, UI, MODPACK, DOWNLOADS }
    override fun persist() { DlcConfigClient.applyToConfig(); DlcConfig.save(); Minecraft.getInstance().setScreen(pageParent) }
    companion object {
        private fun key(tab: Tab?): String = "dlc_manager.config.tab." + when (tab) { Tab.DOWNLOADS -> "download"; null -> "paths"; else -> tab.name.lowercase() }
        private fun categories(): Map<String, List<DlcConfigValue>> {
            DlcConfigClient.syncFromConfig()
            return linkedMapOf(key(Tab.PATHS) to DlcConfigClient.getPathConfigs(), key(Tab.SERVER) to DlcConfigClient.getServerConfigs(), key(Tab.UI) to DlcConfigClient.getUiConfigs(), key(Tab.MODPACK) to DlcConfigClient.getModpackConfigs(), key(Tab.DOWNLOADS) to DlcConfigClient.getDownloadConfigs())
        }
    }
}
