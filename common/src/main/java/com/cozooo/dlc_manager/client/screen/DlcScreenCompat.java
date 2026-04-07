package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DlcScreenCompat {
    private static final Method MODERN_RENDER_BACKGROUND = findMethod(
        Screen.class, "renderBackground", GuiGraphics.class, int.class, int.class, float.class);
    private static final Method LEGACY_RENDER_BACKGROUND = findMethod(
        Screen.class, "renderBackground", GuiGraphics.class);
    private static final Method ADD_OR_UPDATE_PERIODIC_NOTIFICATION = findAddOrUpdateMethod();
    private static final Object PERIODIC_NOTIFICATION_ID = findPeriodicNotificationId();

    private DlcScreenCompat() {
    }

    public static void renderBackground(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        try {
            if (MODERN_RENDER_BACKGROUND != null) {
                MODERN_RENDER_BACKGROUND.invoke(screen, guiGraphics, mouseX, mouseY, partialTick);
                return;
            }
            if (LEGACY_RENDER_BACKGROUND != null) {
                LEGACY_RENDER_BACKGROUND.invoke(screen, guiGraphics);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to render compatible background", ex);
        }
    }

    public static void addOrUpdatePeriodicNotification(Object toasts, Component title, Component description) {
        if (ADD_OR_UPDATE_PERIODIC_NOTIFICATION == null || PERIODIC_NOTIFICATION_ID == null) {
            throw new IllegalStateException("Failed to resolve SystemToast periodic notification compatibility");
        }
        try {
            ADD_OR_UPDATE_PERIODIC_NOTIFICATION.invoke(null, toasts, PERIODIC_NOTIFICATION_ID, title, description);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to show compatible periodic notification", ex);
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findAddOrUpdateMethod() {
        for (String nestedType : new String[] {
            "net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId",
            "net.minecraft.client.gui.components.toasts.SystemToast$SystemToastIds"
        }) {
            try {
                Class<?> toastIdClass = Class.forName(nestedType);
                return SystemToast.class.getMethod("addOrUpdate", Object.class, toastIdClass, Component.class, Component.class);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (Method method : SystemToast.class.getMethods()) {
            if (!"addOrUpdate".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 4 && Component.class.equals(parameterTypes[2]) && Component.class.equals(parameterTypes[3])) {
                return method;
            }
        }
        return null;
    }

    private static Object findPeriodicNotificationId() {
        for (String nestedType : new String[] {
            "net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId",
            "net.minecraft.client.gui.components.toasts.SystemToast$SystemToastIds"
        }) {
            try {
                Class<?> toastIdClass = Class.forName(nestedType);
                Field field = toastIdClass.getField("PERIODIC_NOTIFICATION");
                return field.get(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
