//? if fabric {
package com.cozooo.dlc_manager.client

import com.cozooo.dlc_manager.client.screen.DlcConfigScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class ModMenuCompat : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory(::DlcConfigScreen)
    }
}
//?}
