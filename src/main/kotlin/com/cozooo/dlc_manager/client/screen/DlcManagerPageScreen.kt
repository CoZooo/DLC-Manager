package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.sighs.apricityui.event.MouseEvent
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.layout.Box
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
import net.minecraft.network.chat.Component

abstract class DlcManagerPageScreen(page: DlcUiPage = DlcUiPage.MANAGER) : DlcApricityScreen(page) {
    protected var searchClearButton: DlcUiButton? = null
    protected var searchQuery = ""
    internal var tooltip: DlcUiTooltip? = null
    private var layoutDocument: Document? = null
    private var layoutGeneration = -1L
    private var sidebarCollapsed = false
    private var sidebarWidth = 240.0
    private var rowHeight = 40.0
    private val sidebarMotion = DlcUiMotion(1.0)
    private var lastSidebarProgress = Double.NaN
    private val groupMotions = HashMap<String, DlcUiMotion>()
    private val groupViews = ArrayList<GroupView>()

    private class GroupView(
        val motion: DlcUiMotion, val body: Element, val button: Element, val caret: Element, val itemCount: Int
    ) {
        var lastProgress = Double.NaN
    }

    protected var headerBackButton: DlcUiButton? = null

    protected fun bindManagerLayout(document: Document, title: String) {
        val manager = document.getElementById("manager")
        sidebarWidth = layoutMetric(manager, "data-sidebar-width", 240.0)
        rowHeight = layoutMetric(manager, "data-row-height", 40.0)
        document.getElementById("manager-header").setAttribute("class", "workspace-header")
        restoreUiClasses(document.getElementById("manager-header"))
        DlcUiText.set(document.getElementById("manager-title"), title)
        val back = document.getElementById("manager-back")
        headerBackButton = null
        if (back != null) {
            clearUiContent(back)
            headerBackButton = button(document, back, "‹", ::onClose).also {
                it.getElement().setAttribute("class", "button manager-back")
                it.getElement().setAttribute("aria-label", layoutTranslate("gui.back"))
            }
        }
        document.getElementById("category-label").setTextContent(layoutTranslate("dlc_manager.ui.categories"))
        document.getElementById("tools-label").setTextContent(layoutTranslate("dlc_manager.ui.tools"))
        if (layoutDocument !== document || layoutGeneration != document.refreshGeneration) {
            layoutDocument = document
            layoutGeneration = document.refreshGeneration
            tooltip = DlcUiTooltip(document)
            bindSidebar(document)
            bindSearch(document)
        }
    }

    protected abstract fun onManagerSearch(document: Document, query: String?)

    protected fun clearManagerGroups() { groupViews.clear() }

    protected fun addManagerGroup(document: Document, list: Element, heading: Element, key: String, itemCount: Int): Element {
        val motion = groupMotions.getOrPut(key) { DlcUiMotion(1.0) }
        val body = list.appendChild(document.createElement("div"))
        body.setAttribute("class", "group-items")
        body.setAttribute("id", "dlc-group-" + groupViews.size)
        val collapse = button(document, heading, "") {
            tooltip?.hide()
            motion.animateTo(if (motion.getTarget() == 0.0) 1.0 else 0.0, System.nanoTime())
            updateAnimations(document, System.nanoTime())
        }
        collapse.getElement().setAttribute("class", "button group-collapse")
        collapse.getElement().setAttribute("aria-controls", body.getAttribute("id"))
        val caret = managerText(document, collapse.getElement(), "span", "", "group-caret")
        caret.setAttribute("aria-hidden", "true")
        caret.setInnerHTML("<svg viewBox=\"0 0 16 16\"><path d=\"M3 10 L8 5 L13 10\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\"></path></svg>")
        groupViews.add(GroupView(motion, body, collapse.getElement(), caret, itemCount))
        return body
    }

    protected fun styleManagerRow(document: Document, entry: Element, name: String, enabled: Boolean, pending: Boolean): Element {
        return DlcUiRows.managerRow(document, entry, name, enabled, pending)
    }

    protected fun managerToggle(document: Document, entry: Element, enabled: Boolean, action: Runnable): DlcUiButton {
        val toggle = button(document, entry, layoutTranslate(if (enabled) "dlc_manager.toggle.on" else "dlc_manager.toggle.off"), action)
        toggle.getElement().setAttribute("class", "button toggle" + if (enabled) " on" else "")
        toggle.getElement().setAttribute("aria-pressed", enabled.toString())
        return toggle
    }

    private fun bindSidebar(document: Document) {
        val sidebar = document.getElementById("sidebar-content")
        val generation = document.refreshGeneration
        sidebar.addEventListener("wheel") { event ->
            if (event is MouseEvent && Minecraft.getInstance().screen === this
                && linkedDocument === document && document.isCurrentGeneration(generation)) {
                event.preventDefault()
                Document.runWithContext(document) {
                    sidebar.commitScrollMetricsFromLayout()
                    val limit = (sidebar.scrollHeight - Box.of(sidebar).innerSize().height()).coerceAtLeast(0.0)
                    sidebar.scrollTo(0.0, (sidebar.targetScrollTop + event.scrollDelta).coerceIn(0.0, limit))
                }
            }
        }
        val slot = document.getElementById("sidebar-toggle-slot")
        clearUiContent(slot)
        val toggle = button(document, slot, "") {
            val scroll = document.getElementById("manager-list").targetScrollTop
            sidebarCollapsed = !sidebarCollapsed
            sidebarMotion.animateTo(if (sidebarCollapsed) 0.0 else 1.0, System.nanoTime())
            renderSidebarState(document)
            document.getElementById("manager-list").setScrollTop(scroll)
        }
        toggle.getElement().setAttribute("id", "sidebar-toggle")
        toggle.getElement().setAttribute("class", "button sidebar-toggle")
        toggle.getElement().setAttribute("aria-controls", "manager-sidebar")
        renderSidebarState(document)
        lastSidebarProgress = Double.NaN
        updateAnimations(document, System.nanoTime())
    }

    private fun renderSidebarState(document: Document) {
        document.getElementById("manager").setAttribute("class", if (sidebarCollapsed) "manager sidebar-collapsed" else "manager")
        restoreUiClasses(document.getElementById("manager"))
        val toggle = document.getElementById("sidebar-toggle")
        toggle.setTextContent(if (sidebarCollapsed) "»" else "«")
        toggle.setAttribute("aria-expanded", (!sidebarCollapsed).toString())
        toggle.setAttribute("aria-label", layoutTranslate(if (sidebarCollapsed) "dlc_manager.ui.expand_sidebar" else "dlc_manager.ui.collapse_sidebar"))
        document.getElementById("manager-sidebar").setAttribute("aria-hidden", sidebarCollapsed.toString())
    }

    private fun bindSearch(document: Document) {
        val input = DlcUiTextInput.adapt(document.getElementById("manager-search"))
        input.setValue(searchQuery)
        input.setAttribute("placeholder", layoutTranslate("dlc_manager.ui.search"))
        input.setAttribute("aria-label", layoutTranslate("dlc_manager.ui.search"))
        val searchBinding = bindInput(document, input, { onManagerSearch(document, it) }, true)
        val actions = document.getElementById("search-actions")
        clearUiContent(actions)
        searchClearButton = button(document, actions, "×") {
            input.setValue("")
            searchBinding.run()
        }
        searchClearButton!!.getElement().setAttribute("class", "button clear-search")
        searchClearButton!!.getElement().setAttribute("aria-label", layoutTranslate("dlc_manager.ui.clear_search"))
        searchClearButton!!.setEnabled(searchQuery.isNotBlank())
    }

    protected fun updateAnimations(document: Document, now: Long) {
        Document.runWithContext(document) {
            val progress = sidebarMotion.valueAt(now)
            if (progress != lastSidebarProgress) {
                document.getElementById("manager-sidebar").setAttribute("style",
                    "width:${sidebarWidth * progress}px;border-right-width:${progress}px;opacity:$progress")
                lastSidebarProgress = progress
            }
            for (group in groupViews) {
                val value = group.motion.valueAt(now)
                if (value == group.lastProgress) continue
                val expanded = group.motion.getTarget() == 1.0
                val height = if (value == 1.0) "auto" else "${group.itemCount * rowHeight * value}px"
                group.body.setAttribute("style", "height:$height;opacity:$value;pointer-events:" + if (expanded) "auto" else "none")
                group.body.setAttribute("aria-hidden", (!expanded).toString())
                group.button.setAttribute("aria-expanded", expanded.toString())
                group.button.setAttribute("aria-label", layoutTranslate(if (expanded) "dlc_manager.ui.collapse_group" else "dlc_manager.ui.expand_group"))
                group.caret.setAttribute("style", "transform:rotate(${(1 - value) * 180}deg)")
                group.lastProgress = value
            }
        }
    }

    protected fun managerText(document: Document, parent: Element, tag: String, value: String?, className: String): Element {
        return DlcUiRows.text(document, parent, tag, value, className)
    }


    //? if >=1.20.1 <26.1 {
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        linkedDocument?.let {
            updateAnimations(it, System.nanoTime())
            Document.runWithContext(it) { tooltip?.update() }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }
    //?}
    //? if <1.20.1 {
    /*override fun render(graphics: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        linkedDocument?.let {
            updateAnimations(it, System.nanoTime())
            Document.runWithContext(it) { tooltip?.update() }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }
    *///?}
    //? if >=26.1 {
    /*override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        linkedDocument?.let {
            updateAnimations(it, System.nanoTime())
            Document.runWithContext(it) { tooltip?.update() }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
    *///?}

    private fun layoutTranslate(key: String): String = DlcTextCompat.translatable(key).string

    private fun layoutMetric(element: Element, name: String, fallback: Double): Double {
        val value = element.getAttribute(name)?.toDoubleOrNull() ?: return fallback
        return if (value.isFinite() && value > 0 && value <= 10000) value else fallback
    }
}
