package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
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
    private static final Field RENDERABLES_FIELD = findField(Screen.class, "renderables");

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

    public static void renderModalBackground(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(screen, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, screen.width, screen.height, 0xC0101010);
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

    public static Checkbox createCheckbox(Font font, int x, int y, int width, int height, Component label, boolean selected) {
        try {
            Method builderMethod = findMethod(Checkbox.class, "builder", Component.class, Font.class);
            if (builderMethod != null) {
                Object builder = builderMethod.invoke(null, label, font);
                builder = invokeBuilder(builder, "pos", int.class, int.class, x, y);
                builder = invokeBuilder(builder, "selected", boolean.class, selected);
                Method buildMethod = builder.getClass().getMethod("build");
                Object checkbox = buildMethod.invoke(builder);
                if (checkbox instanceof Checkbox result) {
                    return result;
                }
            }
            return Checkbox.class
                .getConstructor(int.class, int.class, int.class, int.class, Component.class, boolean.class)
                .newInstance(x, y, width, height, label, selected);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create compatible checkbox", ex);
        }
    }

    public static void renderWidgets(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean renderedAny = false;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
                renderedAny = true;
            }
        }
        if (renderedAny) {
            return;
        }
        if (RENDERABLES_FIELD == null) {
            return;
        }
        try {
            Object value = RENDERABLES_FIELD.get(screen);
            if (!(value instanceof Iterable<?> iterable)) {
                return;
            }
            for (Object candidate : iterable) {
                if (candidate instanceof Renderable renderable) {
                    renderable.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to render compatible widgets", ex);
        }
    }

    public static boolean invokeCompatibleMouseScroll(Object target, String methodName, int mouseX, int mouseY, double deltaX, double deltaY) {
        Method modern = findDeclaredMethod(target.getClass(), methodName, int.class, int.class, double.class, double.class);
        if (modern != null) {
            try {
                Object value = modern.invoke(target, mouseX, mouseY, deltaX, deltaY);
                return value instanceof Boolean result && result;
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed to invoke compatible mouse scroll handler", ex);
            }
        }
        Method legacy = findDeclaredMethod(target.getClass(), methodName, int.class, int.class, double.class);
        if (legacy != null) {
            try {
                Object value = legacy.invoke(target, mouseX, mouseY, deltaY);
                return value instanceof Boolean result && result;
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed to invoke compatible mouse scroll handler", ex);
            }
        }
        throw new IllegalStateException("Failed to resolve compatible mouse scroll handler: " + methodName);
    }

    private static Object invokeBuilder(Object builder, String methodName, Class<?> parameterType, Object argument) throws ReflectiveOperationException {
        Method method = builder.getClass().getMethod(methodName, parameterType);
        return method.invoke(builder, argument);
    }

    private static Object invokeBuilder(Object builder, String methodName, Class<?> firstType, Class<?> secondType, Object firstArgument, Object secondArgument)
        throws ReflectiveOperationException {
        Method method = builder.getClass().getMethod(methodName, firstType, secondType);
        return method.invoke(builder, firstArgument, secondArgument);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findDeclaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        Class<?> current = owner;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
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
