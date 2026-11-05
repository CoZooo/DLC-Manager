package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.element.Input
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.style.Text
import com.sighs.apricityui.render.Drawer

open class DlcUiTextInput(document: Document?) : Input(document) {
    override fun deleteBackward(): Boolean = canEditText() && super.deleteBackward()

    override fun deleteForward(): Boolean = canEditText() && super.deleteForward()

    override fun dispatchInputEvent(inputType: String?, data: String?) {
        refreshValue()
        super.dispatchInputEvent(inputType, data)
    }

    override fun setValue(value: String?) {
        super.setValue(value)
        refreshValue()
    }

    override fun tick() {
        refreshValue()
        super.tick()
    }

    private fun refreshValue() {
        if (value != null && cachedValue != value) {
            cachedValue = value
            renderer.text.clear()
            renderer.wrappedText.clear()
            document?.markDirty(this, Drawer.REPAINT)
        }
    }

    override fun getRenderText(): String {
        val value = super.getRenderText()
        Text.of(this).also {
            it.lineHeight = it.renderedFontSize()
            it.content = value
        }
        return value
    }

    companion object {
        @JvmStatic fun adapt(element: Element): DlcUiTextInput {
            if (element is DlcUiTextInput) return element
            val value = element.getValue()
            val input = DlcUiTextInput(element.document)
            element.attributes.forEach { (key, value) -> input.setAttribute(key, value) }
            element.parentElement.replaceChild(input, element)
            input.setValue(value)
            return input
        }
    }
}
