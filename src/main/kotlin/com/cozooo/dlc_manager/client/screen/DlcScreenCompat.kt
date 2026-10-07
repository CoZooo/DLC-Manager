package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.network.chat.Component

object DlcScreenCompat {

    @JvmStatic
    fun addOrUpdatePeriodicNotification(toasts: Any, title: Component, description: Component) {
        if (toasts !is ToastComponent) {
            throw IllegalStateException("Failed to resolve ToastComponent for periodic notification")
        }
        //? if >=1.21.1 {
        /*SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description)
        *///?} else {
        SystemToast.addOrUpdate(toasts, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, description)
        //?}
    }

}
