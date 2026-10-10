package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.ClickEvent;
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if <26.1 {
import net.minecraft.client.gui.components.toasts.ToastComponent;
//?}
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.resources.Identifier;
*///?}
//? if <1.20.1 {
/*import net.minecraft.client.gui.GuiComponent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
*///?}
//? if <26.1 {
import net.minecraft.resources.ResourceLocation;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class DlcScreenCompat {
    private DlcScreenCompat() {}

    public static Object toastManager(Minecraft minecraft) {
        //? if >=26.1 {
        //return minecraft.getToastManager();
        //?}
        //? if <26.1 {
        return minecraft.getToasts();
        //?}
    }

    public static void openUri(String uri) {
        //? if >=26.1 {
        //net.minecraft.util.Util.getPlatform().openUri(uri);
        //?}
        //? if <26.1 {
        net.minecraft.Util.getPlatform().openUri(uri);
        //?}
    }

    public static void openClickEvent(ClickEvent click) {
        if (click == null) return;
        //? if >=26.1 {
        /*if (click instanceof ClickEvent.OpenUrl openUrl) {
            net.minecraft.util.Util.getPlatform().openUri(openUrl.uri());
        }
        *///?}
        //? if <26.1 {
        if (click.getAction() == ClickEvent.Action.OPEN_URL && click.getValue() != null && !click.getValue().isBlank()) {
            net.minecraft.Util.getPlatform().openUri(click.getValue());
        }
        //?}
    }

    public static void handleClick(Screen screen, net.minecraft.network.chat.Style style) {
        var click = style.getClickEvent();
        if (click == null) return;
        //? if >=26.1 {
        /*try {
            var modern = Screen.class.getDeclaredMethod("defaultHandleClickEvent", ClickEvent.class, Minecraft.class, Screen.class);
            modern.setAccessible(true);
            modern.invoke(null, click, Minecraft.getInstance(), screen);
        } catch (ReflectiveOperationException ignored) {
        }
        *///?}
        //? if <26.1 {
        screen.handleComponentClicked(style);
        //?}
    }

    public static boolean hasCommandPermission(Object player, int level) {
        if (player == null) return false;
        //? if >=26.1 {
        /*if (!(player instanceof net.minecraft.client.player.LocalPlayer local)) return false;
        return local.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        *///?}
        //? if <26.1 {
        if (!(player instanceof net.minecraft.client.player.LocalPlayer local)) return false;
        return local.hasPermissions(level);
        //?}
    }

    public static DynamicTexture createDynamicTexture(String name, NativeImage image) {
        //? if >=26.1 {
        //return new DynamicTexture(() -> name, image);
        //?}
        //? if <26.1 {
        return new DynamicTexture(image);
        //?}
    }

    public static Object registerTexture(TextureManager manager, String name, DynamicTexture texture) {
        //? if >=26.1 {
        /*var identifier = Identifier.parse(name);
        manager.register(identifier, texture);
        return identifier;
        *///?}
        //? if <26.1 {
        return manager.register(name, texture);
        //?}
    }

    public static void releaseTexture(TextureManager manager, Object location) {
        //? if >=26.1 {
        //manager.release((Identifier) location);
        //?}
        //? if <26.1 {
        manager.release((ResourceLocation) location);
        //?}
    }

    public static void addOrUpdatePeriodicNotification(Object toasts, Component title, Component description) {
        //? if >=26.1 {
        /*if (!(toasts instanceof ToastManager toastManager)) {
            throw new IllegalStateException("Failed to resolve ToastManager for periodic notification");
        }
        SystemToast.addOrUpdate(toastManager, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
        *///?}
        //? if <1.21.1 {
        if (!(toasts instanceof ToastComponent toastComponent)) {
            throw new IllegalStateException("Failed to resolve ToastComponent for periodic notification");
        }
        SystemToast.addOrUpdate(toastComponent, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, description);
        //?}
        //? if >=1.21.1 <26.1 {
        /*if (!(toasts instanceof ToastComponent toastComponent)) {
            throw new IllegalStateException("Failed to resolve ToastComponent for periodic notification");
        }
        SystemToast.addOrUpdate(toastComponent, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
        *///?}
    }

    @FunctionalInterface
    public interface DlcButtonPress { void press(Button button); }

    public static ButtonBuilder buttonBuilder(Component message, DlcButtonPress onPress) {
        return new ButtonBuilder(message, onPress);
    }

    public static void press(Button button) {
        //? if >=26.1 {
        /*button.onPress(new InputWithModifiers() {
            @Override public int input() { return 257; }
            @Override public int modifiers() { return 0; }
        });
        *///?}
        //? if <26.1 {
        button.onPress();
        //?}
    }

    public static final class ButtonBuilder {
        private final Component message;
        private final DlcButtonPress onPress;
        private int x;
        private int y;
        private int width;
        private int height;

        private ButtonBuilder(Component message, DlcButtonPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public ButtonBuilder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Button build() {
            //? if >=1.19.3 {
            return Button.builder(message, onPress::press).bounds(x, y, width, height).build();
            //?}
            //? if <1.19.3 {
            //return new Button(x, y, width, height, message, onPress::press);
            //?}
        }
    }

    public static String translationKey(Component component) {
        if (component == null) return null;
        //? if >=1.19.2 {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
            return contents.getKey();
        }
        //?}
        //? if <1.19.2 {
        /*if (component instanceof net.minecraft.network.chat.TranslatableComponent translatable) {
            return translatable.getKey();
        }
        *///?}
        return null;
    }

    public static boolean isFocused(AbstractWidget widget) {
        return widget.isFocused();
    }

    public static int getX(AbstractWidget widget) {
        //? if >=1.19.3 {
        return widget.getX();
        //?} else {
        //return widget.x;
        //?}
    }

    public static int getY(AbstractWidget widget) {
        //? if >=1.19.3 {
        return widget.getY();
        //?} else {
        //return widget.y;
        //?}
    }

    public static void setX(AbstractWidget widget, int x) {
        //? if >=1.19.3 {
        widget.setX(x);
        //?} else {
        //widget.x = x;
        //?}
    }

    public static void setY(AbstractWidget widget, int y) {
        //? if >=1.19.3 {
        widget.setY(y);
        //?} else {
        //widget.y = y;
        //?}
    }

    public static void setNumericFilter(net.minecraft.client.gui.components.EditBox field) {
        //? if >=26.1 {
        /*field.setResponder(value -> {
            String filtered = value.codePoints()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
            if (!filtered.equals(value)) field.setValue(filtered);
        });
        *///?}
        //? if <26.1 {
        field.setFilter(value -> value.chars().allMatch(Character::isDigit));
        //?}
    }

    public static void setFocused(AbstractWidget widget, boolean focused) {
        //? if >=1.19.3 {
        widget.setFocused(focused);
        //?} else {
        /*try {
            var method = AbstractWidget.class.getDeclaredMethod("setFocused", boolean.class);
            method.setAccessible(true);
            method.invoke(widget, focused);
        } catch (ReflectiveOperationException ignored) {
        }
        *///?}
    }

    public static void fill(Object graphics, int left, int top, int right, int bottom, int color) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).fill(left, top, right, bottom, color);
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).fill(left, top, right, bottom, color);
        //?}
        //? if <1.20.1 {
        //GuiComponent.fill((PoseStack) graphics, left, top, right, bottom, color);
        //?}
    }

    public static void drawCenteredString(Object graphics, Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).centeredText(font, text, x, y, color);
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).drawCenteredString(font, text, x, y, color);
        //?}
        //? if <1.20.1 {
        //GuiComponent.drawCenteredString((PoseStack) graphics, font, text, x, y, color);
        //?}
    }

    public static void drawString(Object graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).text(font, text, x, y, color, shadow);
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).drawString(font, text, x, y, color, shadow);
        //?}
        //? if <1.20.1 {
        //GuiComponent.drawString((PoseStack) graphics, font, text, x, y, color);
        //?}
    }

    public static void drawString(Object graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).text(font, text, x, y, color, shadow);
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).drawString(font, text, x, y, color, shadow);
        //?}
        //? if <1.20.1 {
        //GuiComponent.drawString((PoseStack) graphics, font, text, x, y, color);
        //?}
    }

    public static void enableScissor(Object graphics, int left, int top, int right, int bottom) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).enableScissor(left, top, right, bottom);
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).enableScissor(left, top, right, bottom);
        //?}
        //? if >=1.19.2 <1.20.1 {
        //GuiComponent.enableScissor(left, top, right, bottom);
        //?}
        //? if <1.19.2 {
        //RenderSystem.enableScissor(left, top, right, bottom);
        //?}
    }

    public static void disableScissor(Object graphics) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).disableScissor();
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).disableScissor();
        //?}
        //? if >=1.19.2 <1.20.1 {
        //GuiComponent.disableScissor();
        //?}
        //? if <1.19.2 {
        //RenderSystem.disableScissor();
        //?}
    }

    public static void pushPose(Object graphics) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).pose().pushMatrix();
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).pose().pushPose();
        //?}
        //? if <1.20.1 {
        //((PoseStack) graphics).pushPose();
        //?}
    }

    public static void scale(Object graphics, float x, float y, float z) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).pose().scale(x, y);
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).pose().scale(x, y, z);
        //?}
        //? if <1.20.1 {
        //((PoseStack) graphics).scale(x, y, z);
        //?}
    }

    public static void popPose(Object graphics) {
        //? if >=26.1 {
        //((GuiGraphicsExtractor) graphics).pose().popMatrix();
        //?}
        //? if >=1.20.1 <26.1 {
        ((GuiGraphics) graphics).pose().popPose();
        //?}
        //? if <1.20.1 {
        //((PoseStack) graphics).popPose();
        //?}
    }
}
