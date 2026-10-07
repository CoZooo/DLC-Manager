package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.parser.Selector
import com.sighs.apricityui.screen.ApricityScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

abstract class DlcApricityScreen(path: String) : ApricityScreen(path) {
    private var boundDocument: Document? = null
    private var boundGeneration = -1L
    private var boundStyleIndex: Selector.Index? = null

    init { setPauseGame(true) }

    override fun init() {
        super.init()
        bindCurrentDocument()
    }

    override fun tick() {
        super.tick()
        bindCurrentDocument()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val document = linkedDocument
        if (document != null && document.isActive && document.documentElement != null) {
            val index = document.selectorIndex
            if (index !== boundStyleIndex) {
                boundStyleIndex = index
                Document.runWithContext(document) { document.documentElement.invalidateStyle() }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun bindCurrentDocument() {
        val document = linkedDocument ?: return
        if (!document.isActive || (document === boundDocument && document.refreshGeneration == boundGeneration)) return
        if (document !== boundDocument) {
            document.addEventListener("DOMContentLoaded") {
                Minecraft.getInstance().execute {
                    if (Minecraft.getInstance().screen === this && document === linkedDocument) {
                        boundGeneration = -1L
                    }
                }
            }
        }
        document.setManuallyRendered(false)
        boundDocument = document
        boundGeneration = document.refreshGeneration
        Document.runWithContext(document) { bindDocument(document) }
    }

    protected abstract fun bindDocument(document: Document)

    protected fun button(document: Document, container: Element, label: String, action: Runnable): DlcUiButton {
        val generation = document.refreshGeneration
        return DlcUiButton(document, container, label) {
            if (Minecraft.getInstance().screen === this && document === linkedDocument && document.isCurrentGeneration(generation)) {
                Document.runWithContext(document, action)
            }
        }
    }
}
