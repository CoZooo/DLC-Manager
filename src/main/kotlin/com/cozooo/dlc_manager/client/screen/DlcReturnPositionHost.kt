package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.screens.Screen

interface DlcReturnPositionHost {
    fun rememberReturnPosition()

    companion object {
        @JvmStatic
        fun remember(screen: Screen?) {
            if (screen is DlcReturnPositionHost) {
                screen.rememberReturnPosition()
            }
        }
    }
}
