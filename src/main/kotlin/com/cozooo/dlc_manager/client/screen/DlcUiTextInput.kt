package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.element.Input
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.style.Text
import com.sighs.apricityui.render.Drawer

open class DlcUiTextInput(document: Document?) : Input(document) {
    override fun deleteBackward(): Boolean {
        if (!canEditText() || !super.deleteBackward()) return false
        dispatchInputEvent("deleteContentBackward", null)
        return true
    }

    override fun deleteForward(): Boolean {
        if (!canEditText() || !super.deleteForward()) return false
        dispatchInputEvent("deleteContentForward", null)
        return true
    }

    override fun replaceSelection(text: String?) {
        val previous = getValue()
        super.replaceSelection(text)
        if (text.orEmpty().replace("\r", "").replace("\n", "").isEmpty() && previous != getValue())
            dispatchInputEvent("deleteContentBackward", null)
    }

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
            it.fontFamily = "unset"
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
