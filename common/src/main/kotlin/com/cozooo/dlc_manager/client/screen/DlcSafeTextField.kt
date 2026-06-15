package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.GuiTextFieldGeneric
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.util.Mth
import java.lang.reflect.Field

class DlcSafeTextField(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    textRenderer: Font,
) : GuiTextFieldGeneric(x, y, width, height, textRenderer) {
    override fun setCursorPosition(position: Int) {
        val field = CURSOR_POS_FIELD
        if (field == null) {
            super.setCursorPosition(position)
            return
        }
        try {
            field.setInt(this, Mth.clamp(position, 0, value.length))
        } catch (_: IllegalAccessException) {
            super.setCursorPosition(position)
        }
    }

    companion object {
        private val CURSOR_POS_FIELD: Field? = findIntField("cursorPos", "field_2108", "f_94093_", "A")

        private fun findIntField(vararg names: String): Field? {
            for (name in names) {
                try {
                    val field = EditBox::class.java.getDeclaredField(name)
                    if (field.type == Int::class.javaPrimitiveType) {
                        field.isAccessible = true
                        return field
                    }
                } catch (_: NoSuchFieldException) {
                }
            }
            return null
        }
    }
}
