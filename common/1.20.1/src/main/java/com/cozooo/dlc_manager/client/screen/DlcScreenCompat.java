package com.cozooo.dlc_manager.client.screen;

import fi.dy.masa.malilib.gui.GuiListBase;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;

public final class DlcScreenCompat {
    private static final Field RENDERABLES_FIELD = findField(Screen.class, "renderables");

    private DlcScreenCompat() {
    }

    public abstract static class MouseScroll extends Screen {
        protected MouseScroll(Component title) {
            super(title);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return handleCompatibleMouseScrolled(mouseX, mouseY, delta);
        }

        protected abstract boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY);
    }

    public static void renderBackground(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(guiGraphics);
    }

    public static void renderModalBackground(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(screen, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, screen.width, screen.height, 0xC0101010);
    }

    public static void addOrUpdatePeriodicNotification(Object toasts, Component title, Component description) {
        if (!(toasts instanceof ToastComponent toastComponent)) {
            throw new IllegalStateException("Failed to resolve ToastComponent for periodic notification");
        }
        SystemToast.addOrUpdate(toastComponent, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, description);
    }

    public static Checkbox createCheckbox(Font font, int x, int y, int width, int height, Component label, boolean selected) {
        return new Checkbox(x, y, width, height, label, selected);
    }

    public static void renderWidgets(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean renderedAny = false;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
                renderedAny = true;
            }
        }
        if (renderedAny || RENDERABLES_FIELD == null) {
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

    public static boolean invokeCompatibleMouseScroll(GuiListBase<?, ?, ?> target, int mouseX, int mouseY, double deltaX, double deltaY) {
        return target.onMouseScrolled(mouseX, mouseY, deltaY);
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
}
