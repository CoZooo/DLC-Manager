package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.Consumer
import java.util.function.Supplier

abstract class DlcPageScreen(
    page: DlcUiPage,
    protected val pageParent: Screen?,
    private val pageTitle: Component
) : DlcApricityScreen(page) {
    protected constructor(parent: Screen?, title: Component) : this(DlcUiPage.PAGE, parent, title)
    protected lateinit var pageDocument: Document
    protected lateinit var content: Element
    protected lateinit var toolbar: Element
    protected lateinit var footer: Element
    protected lateinit var footerLeading: Element
    private lateinit var tooltip: DlcUiTooltip
    private var lastStatus: String? = null
    private var backAction: DlcUiButton? = null
    private val bindings = mutableListOf<Runnable>()

    override fun getTitle(): Component = pageTitle

    final override fun bindDocument(document: Document) {
        pageDocument = document
        tooltip = DlcUiTooltip(document)
        lastStatus = null
        content = document.getElementById("page-content")
        toolbar = document.getElementById("page-toolbar")
        footer = document.getElementById("page-actions")
        footerLeading = document.getElementById("page-leading-actions")
        DlcUiText.set(document.getElementById("page-title"), pageTitle.string)
        val back = document.getElementById("page-back")
        clearUiContent(back)
        backAction = null
        back.setAttribute("style", if (showHeaderBackButton()) "" else "display:none")
        if (showHeaderBackButton()) {
            backAction = action(back, "‹") { onClose() }
            backAction!!.getElement().setAttribute("aria-label", tr("gui.back"))
        }
        refreshPage()
    }

    protected open fun showHeaderBackButton(): Boolean = true

    protected abstract fun buildPage()
    protected open fun updatePage() {}

    protected fun refreshPage() {
        if (!::pageDocument.isInitialized || !pageDocument.isActive) return
        Document.runWithContext(pageDocument) {
            val scroll = content.scrollTop
            tooltip.hide()
            bindings.clear()
            clearUiSlots()
            clearUiContent(content)
            clearUiContent(toolbar)
            clearUiContent(footer)
            clearUiContent(footerLeading)
            footerLeading.setAttribute("style", "display:none")
            buildPage()
            restoreUiClasses(content)
            restoreUiClasses(content.parentElement)
            restoreUiClasses(toolbar)
            restoreUiClasses(footer)
            restoreUiClasses(footerLeading)
            content.setScrollTop(scroll)
        }
    }

    override fun tick() {
        super.tick()
        if (::pageDocument.isInitialized && pageDocument.isActive) Document.runWithContext(pageDocument) { backAction?.setEnabled(shouldCloseOnEsc()); updatePage(); bindings.toList().forEach { it.run() } }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (::tooltip.isInitialized) Document.runWithContext(pageDocument) { tooltip.update() }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    protected fun node(parent: Element, tag: String, classes: String): Element {
        val slot = classes.trim().split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
        val target = if (slot.isEmpty()) parent else uiSlot(slot, parent)
        return target.appendChild(pageDocument.createElement(tag)).also { it.setAttribute("class", classes) }
    }
    protected fun text(parent: Element, value: String?, classes: String): Element = node(parent, "div", classes).also { DlcUiText.set(it, value.orEmpty()) }
    protected fun section(title: String?, description: String?): Element = node(content, "section", "page-section").also {
        if (!title.isNullOrBlank()) text(it, title, "section-title")
        if (!description.isNullOrBlank()) text(it, description, "page-description")
    }
    protected fun row(parent: Element, title: String?, description: String?): Element = node(parent, "div", "page-row").also {
        val label = node(it, "div", "row-copy")
        text(label, title, "row-title")
        if (!description.isNullOrBlank()) text(label, description, "row-description")
    }
    protected fun action(parent: Element, label: String, run: Runnable): DlcUiButton = button(pageDocument, parent, label, run)
    protected fun primary(label: String, run: Runnable): DlcUiButton = action(uiSlot("primary-action", footer), label, run).also { it.getElement().setAttribute("class", "button primary") }
    protected fun input(parent: Element, label: String, value: String?, type: String, change: Consumer<String>): Element {
        val field = node(parent, "label", "form-field")
        if (label.isNotBlank() && type != "search") text(field, label, "field-label")
        val input = field.appendChild(DlcUiTextInput(pageDocument))
        input.setAttribute("class", "page-input")
        input.setAttribute("type", type)
        input.setAttribute("aria-label", label)
        if (type == "search") input.setAttribute("placeholder", label)
        input.setValue(value.orEmpty())
        bindInput(pageDocument, input, change, type == "search")
        return input
    }
    protected fun toggle(parent: Element, label: String, selected: Boolean, change: Consumer<Boolean>): DlcUiButton = action(parent, label) { change.accept(!selected) }.also {
        it.getElement().setAttribute("class", "button choice" + if (selected) " selected" else "")
        it.getElement().setAttribute("aria-pressed", selected.toString())
    }
    override fun <T> addRenderableWidget(widget: T): T where T : net.minecraft.client.gui.components.events.GuiEventListener, T : net.minecraft.client.gui.components.Renderable, T : net.minecraft.client.gui.narration.NarratableEntry = widget

    protected fun mount(parent: Element, model: net.minecraft.client.gui.components.Button): DlcUiButton {
        val button = action(parent, model.message.string) { if (model.active && model.visible) model.onPress() }
        var shown = true
        val binding = Runnable {
            button.setDisplayString(model.message.string)
            button.setEnabled(model.active)
            if (shown != model.visible) { shown = model.visible; button.getElement().setAttribute("style", if (shown) "" else "display:none") }
        }
        bindings.add(binding); binding.run()
        return button
    }
    protected fun mount(parent: Element, model: net.minecraft.client.gui.components.EditBox, label: String, type: String): Element {
        val field = input(parent, label, model.value, type) { model.value = it }
        val binding = Runnable {
            if (field.getValue() != model.value) field.setValue(model.value)
            if (model.active) field.removeAttribute("disabled") else if (!field.hasAttribute("disabled")) field.setAttribute("disabled", "")
        }
        bindings.add(binding); binding.run()
        return field
    }
    protected fun live(parent: Element, value: Supplier<String?>, classes: String): Element {
        val element = text(parent, "", classes)
        var previous: String? = null
        val binding = Runnable {
            val next = value.get()
            if (next != previous) { previous = next; DlcUiText.set(element, next.orEmpty()) }
        }
        bindings.add(binding); binding.run()
        return element
    }

    protected fun showWhen(element: Element, visible: java.util.function.BooleanSupplier) {
        var previous: Boolean? = null
        val binding = Runnable {
            val next = visible.asBoolean
            if (previous != next) {
                previous = next
                element.setAttribute("style", if (next) "" else "display:none")
            }
        }
        bindings.add(binding)
        binding.run()
    }

    protected fun progress(parent: Element, value: java.util.function.DoubleSupplier): Element {
        val track = node(parent, "div", "progress-track")
        track.setAttribute("role", "progressbar"); track.setAttribute("aria-valuemin", "0"); track.setAttribute("aria-valuemax", "100")
        val fill = node(track, "div", "progress-fill")
        var previous = -1
        val binding = Runnable {
            val fraction = value.asDouble
            if (fraction < 0) {
                val phase = (System.nanoTime() % 1200000000L) / 1200000000.0
                fill.setAttribute("style", "width:25%;transform:translateX(${track.offsetWidth * 0.75 * phase}px)")
                track.removeAttribute("aria-valuenow"); track.setAttribute("aria-busy", "true"); previous = -1
            } else {
                track.removeAttribute("aria-busy")
                val percent = (fraction.coerceIn(0.0, 1.0) * 100).toInt()
            if (percent != previous) { previous = percent; fill.setAttribute("style", "width:$percent%"); track.setAttribute("aria-valuenow", percent.toString()) }
            }
        }
        bindings.add(binding); binding.run(); return track
    }

    protected fun markdown(parent: Element, value: String) {
        rich(parent, com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer.renderMarkdownComponent(value))
    }
    protected fun rich(parent: Element, value: Component) {
        value.visit({ style, chunk ->
            val marker = style.insertion
            if (marker == "__dlc_hr__") {
                node(parent, "div", "prose-rule")
            } else {
                val span = node(parent, "span", "rich-text")
                val css = buildString {
                    append("display:inline;white-space:pre-wrap;")
                    style.color?.let { append("color:").append(String.format(java.util.Locale.ROOT, "#%06x", it.value)).append(';') }
                    if (style.isBold) append("font-weight:700;")
                    if (style.isItalic) append("font-style:italic;")
                    if (style.isUnderlined) append("text-decoration:underline;")
                    if (style.isStrikethrough) append("text-decoration:line-through;")
                    if (marker?.startsWith("__dlc_heading__:") == true) append("font-size:").append((26 - marker.substringAfterLast(':').toInt() * 2).coerceAtLeast(16)).append("px;")
                    if (style.clickEvent != null) append("cursor:pointer;color:var(--accent);") else append("pointer-events:none;")
                }
                if (style.clickEvent != null) span.addEventListener("click") { handleComponentClicked(style) }
                span.setAttribute("style", css)
                span.setTextContent(chunk)
            }
            java.util.Optional.empty<Unit>()
        }, net.minecraft.network.chat.Style.EMPTY)
    }

    internal fun tip(owner: Element, content: Supplier<DlcUiTooltip.Content>) { tooltip.bind(owner, content) }
    protected fun status(text: String?) {
        val next = text.orEmpty()
        if (next != lastStatus) { lastStatus = next; DlcUiText.set(pageDocument.getElementById("page-status"), next) }
    }
    protected fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string
    override fun onClose() { if (shouldCloseOnEsc()) Minecraft.getInstance().setScreen(pageParent) }

}
