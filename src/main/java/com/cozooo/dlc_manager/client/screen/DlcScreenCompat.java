package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

public final class DlcScreenCompat {
    private DlcScreenCompat() {
    }

    public static void addOrUpdatePeriodicNotification(Object toasts, Component title, Component description) {
        if (!(toasts instanceof ToastComponent toastComponent)) {
            throw new IllegalStateException("Failed to resolve ToastComponent for periodic notification");
        }
        //? if >=1.21.1 {
        /*SystemToast.addOrUpdate(toastComponent, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
        *///?} else {
        SystemToast.addOrUpdate(toastComponent, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, description);
        //?}
    }

}
