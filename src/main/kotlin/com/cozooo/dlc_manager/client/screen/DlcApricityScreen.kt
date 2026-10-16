package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.element.AbstractText
import com.sighs.apricityui.event.KeyEvent
import com.sighs.apricityui.parser.Selector
import com.sighs.apricityui.parser.HTML
import com.sighs.apricityui.screen.ApricityScreen
import net.minecraft.client.Minecraft
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20.1 {
//import com.mojang.blaze3d.vertex.PoseStack
//?}
//? if >=26.1 {
//import net.minecraft.client.gui.GuiGraphicsExtractor
//?}
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

abstract class DlcApricityScreen private constructor(
    private val uiPage: DlcUiPage,
    private val uiResources: DlcUiResources
) : ApricityScreen(uiResources.prepare()) {
    protected constructor(page: DlcUiPage) : this(page, DlcUiResources(page))

    private val transition = DlcUiTransition()
    private var navigationPending = false
    private var hasPresentedFrame = false
    private var frameTransitionsEnabled = false
    private var renderedCurrentFrame = false
    private var transitionReadyFrames = 0
    private var transitionDocument: Document? = null
    private var transitionGeneration = -1L
    private var uiTemplate: DlcUiTemplate? = null
    private var templateDocument: Document? = null
    private var templateGeneration = -1L
    private var resourceGeneration = -1L
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
        if (preparedScreen === this) preparedScreen = null
        if (!navigationPending) transition.cancel()
        hasPresentedFrame = false
        renderedCurrentFrame = false
        transitionReadyFrames = 0
        uiResources.prepare()
        super.init()
        if (!uiResources.isValid(linkedDocument)) {
            uiResources.useDefault()
            super.init()
        }
        updateViewport()
        resourceGeneration = linkedDocument?.refreshGeneration ?: -1L
        bindCurrentDocument()
        val document = linkedDocument
        if (navigationPending) {
            transitionDocument = document
            transitionGeneration = document?.refreshGeneration ?: -1L
            navigationPending = false
            if (document == null || transitionDisabled(document.body)) transition.cancel()
        }
    }

    override fun tick() {
        super.tick()
        refreshResources()
        updateViewport()
        bindCurrentDocument()
    }

    private fun refreshResources() {
        val document = linkedDocument ?: return
        val fullReload = document.refreshGeneration != resourceGeneration
        val changed = if (fullReload) {
            val previousHtml = HTML.getTemple(document.getPath())
            uiResources.prepare()
            previousHtml != HTML.getTemple(document.getPath())
        } else uiResources.refresh()
        if (changed) {
            document.refresh()
            if (!uiResources.isValid(document)) {
                uiResources.useDefault()
                document.refresh()
            }
        }
        resourceGeneration = document.refreshGeneration
    }

    //? if >=1.20.1 <26.1 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val document = prepareRender()
        super.render(graphics, mouseX, mouseY, partialTick)
        completeRender(document)
    }
    //?}

    //? if <1.20.1 {
    /*override fun render(graphics: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val document = prepareRender()
        super.render(graphics, mouseX, mouseY, partialTick)
        completeRender(document)
    }
    *///?}
    //? if >=26.1 {
    /*override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val document = prepareRender()
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        completeRender(document)
    }
    *///?}

    private fun prepareRender(): Document? {
        renderedCurrentFrame = false
        val document = linkedDocument
        if (document != null && document.isActive && document.documentElement != null) {
            val index = document.selectorIndex
            if (index !== boundStyleIndex) {
                boundStyleIndex = index
                Document.runWithContext(document) { document.documentElement.invalidateStyle() }
            }
        }
        return document
    }

    private fun completeRender(document: Document?) {
        renderedCurrentFrame = document != null && document.isActive
            && !document.needsInitialFullCommit() && !document.isInitialCommitSlicing
            && document.paintList.isNotEmpty()
        syncInputs(document)
    }

    fun finishUiFrame(graphics: Any) {
        if (Minecraft.getInstance().screen !== this) return
        val document = linkedDocument
        if (transition.isRunning()) {
            if (document == null || document !== transitionDocument
                || !document.isCurrentGeneration(transitionGeneration)
                || transitionDisabled(document.body)) {
                transition.cancel()
            } else {
                val ready = renderedCurrentFrame && isUiContentReady()
                transitionReadyFrames = if (ready) transitionReadyFrames + 1 else 0
                transition.render(graphics, transitionReadyFrames >= 2, System.nanoTime())
            }
        }
        hasPresentedFrame = renderedCurrentFrame || transition.isRunning()
        frameTransitionsEnabled = document != null && !transitionDisabled(document.body)
        searchApplications.runPending()
    }

    protected open fun isUiContentReady(): Boolean = true

    private fun updateViewport() {
        val document = linkedDocument ?: return
        if (document.isActive) document.setViewportZoom(1120.0 / Minecraft.getInstance().window.screenWidth.coerceAtLeast(1))
    }

    //? if <26.1 {
    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        navigationPending = false
        transition.cancel()
        super.resize(minecraft, width, height)
        updateViewport()
    }
    //?}

    //? if >=26.1 {
    /*override fun resize(width: Int, height: Int) {
        navigationPending = false
        transition.cancel()
        super.resize(width, height)
        updateViewport()
    }
    *///?}

    private fun bindCurrentDocument() {
        val document = linkedDocument ?: return
        if (!document.isActive || (document === boundDocument && document.refreshGeneration == boundGeneration)
            || !uiResources.isValid(document)) return
        document.setManuallyRendered(false)
        boundDocument = document
        boundGeneration = document.refreshGeneration
        inputBindings.clear()
        asyncSearch.cancel()
        searchApplications.clear()
        pendingInputs.clear()
        if (templateDocument !== document || templateGeneration != document.refreshGeneration) {
            if (!navigationPending) transition.cancel()
            uiTemplate = DlcUiTemplate(document)
            templateDocument = document
            templateGeneration = document.refreshGeneration
        }
        Document.runWithContext(document) {
            document.body.setAttribute("data-dlc-page", uiPage.id())
            bindDocument(document)
        }
    }

    protected fun clearUiContent(element: Element) {
        val template = uiTemplate
        if (template != null) template.clear(element) else element.setInnerHTML("")
    }

    protected fun clearUiSlots() { uiTemplate?.clearSlots() }
    protected fun uiSlot(name: String, fallback: Element): Element = uiTemplate?.slot(name, fallback) ?: fallback
    protected fun restoreUiClasses(element: Element?) { if (element != null) uiTemplate?.restoreAttributes(element) }

    protected fun captureUiChange(element: Element?) {
        val document = linkedDocument ?: return
        if (!hasPresentedFrame || !document.isActive || element == null
            || element.document !== document || !element.isConnected) return
        Document.runWithContext(document) { transition.capture(element) }
        transitionDocument = document
        transitionGeneration = document.refreshGeneration
        transitionReadyFrames = 0
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

    override fun removed() {
        transition.cancel()
        navigationPending = false
        renderedCurrentFrame = false
        asyncSearch.cancel()
        searchApplications.clear()
        super.removed()
    }

    companion object {
        private var preparedScreen: DlcApricityScreen? = null

        @JvmStatic
        fun beforeScreenChange(next: Screen?) {
            cancelAbandonedNavigation(null)
            val current = Minecraft.getInstance().screen
            if (current === next || current !is DlcApricityScreen
                || next !is DlcApricityScreen
                || !current.hasPresentedFrame || !current.frameTransitionsEnabled) return
            next.transition.capture(null)
            next.navigationPending = next.transition.isRunning()
            if (next.navigationPending) preparedScreen = next
        }

        @JvmStatic
        fun cancelAbandonedNavigation(current: Screen?) {
            val prepared = preparedScreen ?: return
            if (prepared !== current) {
                prepared.transition.cancel()
                prepared.navigationPending = false
                preparedScreen = null
            }
        }

        private fun transitionDisabled(element: Element?): Boolean =
            element?.getAttribute("data-dlc-transition")?.trim().equals("none", ignoreCase = true)

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
