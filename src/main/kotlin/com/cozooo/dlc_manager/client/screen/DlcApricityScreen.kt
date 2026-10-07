package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.element.AbstractText
import com.sighs.apricityui.event.KeyEvent
import com.sighs.apricityui.parser.Selector
import com.sighs.apricityui.screen.ApricityScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW

abstract class DlcApricityScreen(path: String) : ApricityScreen(path) {
    private var boundDocument: Document? = null
    private var boundGeneration = -1L
    private var boundStyleIndex: Selector.Index? = null
    private val inputBindings = linkedMapOf<Element, DlcUiInputBinding>()
    private var syncingInputs = false
    private val pendingInputs = linkedSetOf<Element>()
    private val searchApplications = DlcUiFrameTasks()
    private val asyncSearch = DlcUiAsyncSearch<String>(searchWorker, searchApplications)

    init { setPauseGame(true) }

    override fun init() {
        super.init()
        updateViewport()
        bindCurrentDocument()
    }

    override fun tick() {
        super.tick()
        updateViewport()
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
        syncInputs(document)
        searchApplications.runPending()
    }

    private fun updateViewport() {
        val document = linkedDocument ?: return
        if (document.isActive) document.setViewportZoom(1120.0 / Minecraft.getInstance().window.screenWidth.coerceAtLeast(1))
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        updateViewport()
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
        inputBindings.clear()
        asyncSearch.cancel()
        searchApplications.clear()
        pendingInputs.clear()
        Document.runWithContext(document) { bindDocument(document) }
    }

    protected abstract fun bindDocument(document: Document)

    protected fun filterAsync(query: String?, entries: List<DlcUiAsyncSearch.Entry<String>>, apply: java.util.function.Consumer<Set<String>>) {
        val document = linkedDocument ?: return
        val generation = document.refreshGeneration
        asyncSearch.request(query, entries) { matches ->
            if (Minecraft.getInstance().screen === this && linkedDocument === document && document.isCurrentGeneration(generation))
                Document.runWithContext(document) { apply.accept(matches) }
        }
    }

    override fun removed() { asyncSearch.cancel(); searchApplications.clear(); super.removed() }

    companion object {
        private val searchWorker = java.util.concurrent.Executors.newFixedThreadPool(2) { task ->
            Thread(task, "dlc-ui-search").apply { isDaemon = true }
        }
    }

    protected fun bindInput(document: Document, input: Element, change: java.util.function.Consumer<String>): DlcUiInputBinding = bindInput(document, input, change, false)

    protected fun bindInput(document: Document, input: Element, change: java.util.function.Consumer<String>, deferred: Boolean): DlcUiInputBinding {
        val generation = document.refreshGeneration
        val current = java.util.function.BooleanSupplier {
            Minecraft.getInstance().screen === this && document === linkedDocument
                && document.isCurrentGeneration(generation) && input.isConnected
        }
        val binding = DlcUiInputBinding(input::getValue, current, change)
        val synchronize = Runnable {
            if (current.asBoolean) Document.runWithContext(document) {
                if (deferred) pendingInputs.add(input)
                else binding.run()
            }
        }
        inputBindings.keys.removeIf { !it.isConnected }
        inputBindings[input] = binding
        input.addEventListener("input") { synchronize.run() }
        input.addEventListener("change") { Document.runWithContext(document, binding) }
        if (input is AbstractText) input.addEventListener("keydown") { event ->
            if (event is KeyEvent && !event.defaultPrevented && Minecraft.getInstance().screen === this
                && document === linkedDocument && document.isCurrentGeneration(generation)
                && input.isConnected && document.focusedElement === input) {
                Document.runWithContext(document) {
                    if (handleInputShortcut(input, event)) {
                        event.preventDefault()
                    }
                }
            }
        }
        return binding
    }

    private fun handleInputShortcut(input: AbstractText, key: KeyEvent): Boolean {
        if ((!key.controlKey && !key.metaKey) || key.altKey || input.isDisabled) return false
        val keyboard = Minecraft.getInstance().keyboardHandler
        when (key.keyCode) {
            GLFW.GLFW_KEY_A -> if (input.canSelectText()) input.selectAll()
            GLFW.GLFW_KEY_C -> if (input.canSelectText() && input.hasSelection()) keyboard.setClipboard(input.selectedText)
            GLFW.GLFW_KEY_X -> if (input.canEditText() && input.hasSelection()) {
                keyboard.setClipboard(input.selectedText)
                input.replaceSelection("")
            }
            GLFW.GLFW_KEY_V -> if (input.canEditText()) input.insertText(keyboard.clipboard)
            GLFW.GLFW_KEY_Z -> {
                if (key.shiftKey) return false
                if (input.canEditText()) input.undo()
            }
            else -> return false
        }
        return true
    }

    private fun syncInputs(document: Document?, all: Boolean = false) {
        if (syncingInputs || document == null || !document.isActive || Minecraft.getInstance().screen !== this) return
        if (!all && pendingInputs.isEmpty()) return
        syncingInputs = true
        try {
            Document.runWithContext(document) {
                if (all) {
                    pendingInputs.clear()
                    inputBindings.keys.removeIf { !it.isConnected }
                    inputBindings.values.toList().forEach { it.run() }
                } else {
                    if (pendingInputs.isNotEmpty()) {
                        val pending = pendingInputs.toList()
                        pendingInputs.clear()
                        pending.forEach { inputBindings[it]?.run() }
                    }
                }
            }
        } finally {
            syncingInputs = false
        }
    }

    protected fun button(document: Document, container: Element, label: String, action: Runnable): DlcUiButton {
        val generation = document.refreshGeneration
        return DlcUiButton(document, container, label) {
            if (Minecraft.getInstance().screen === this && document === linkedDocument && document.isCurrentGeneration(generation)) {
                Document.runWithContext(document) { syncInputs(document, true); action.run() }
            }
        }
    }
}
