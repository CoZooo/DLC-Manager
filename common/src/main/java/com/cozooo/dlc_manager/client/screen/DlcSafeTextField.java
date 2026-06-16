package com.cozooo.dlc_manager.client.screen;

import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.Mth;
import java.lang.reflect.Field;

public class DlcSafeTextField extends GuiTextFieldGeneric {
    private static final Field CURSOR_POS_FIELD = findIntField("cursorPos", "field_2108", "f_94093_", "A");

    public DlcSafeTextField(int x, int y, int width, int height, Font textRenderer) {
        super(x, y, width, height, textRenderer);
    }

    @Override
    public void setCursorPosition(int position) {
        Field field = CURSOR_POS_FIELD;
        if (field == null) {
            super.setCursorPosition(position);
            return;
        }
        try {
            field.setInt(this, Mth.clamp(position, 0, getValue().length()));
        } catch (IllegalAccessException e) {
            super.setCursorPosition(position);
        }
    }

    private static Field findIntField(String... names) {
        for (String name : names) {
            try {
                Field field = EditBox.class.getDeclaredField(name);
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
