package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.BooleanSupplier
import java.util.function.DoubleSupplier

abstract class DlcRemotePageScreen(page: DlcUiPage, parent: Screen?, title: Component) : DlcPageScreen(page, parent, title) {
    protected constructor(parent: Screen?, title: Component) : this(DlcUiPage.PAGE, parent, title)
    private val selections = mutableListOf<Runnable>()
    private var downloadList: Element? = null
    private var scrollTop = 0.0

    protected fun remoteList(selected: BooleanSupplier, enabled: BooleanSupplier, selectAll: Runnable): Element {
        content.parentElement.setAttribute("class", "page-shell remote-page")
        content.setAttribute("class", "remote-content")
        selections.clear()
        text(content, tr("dlc_manager.remote.desc"), "remote-description")
        selection(node(content, "div", "remote-list-heading"), tr("dlc_manager.remote.select_all"), selected, enabled, selectAll)
        scrollTop = downloadList?.targetScrollTop ?: 0.0
        return node(content, "div", "remote-list").also { downloadList = it }
    }

    protected fun remoteRow(list: Element, name: String, selected: BooleanSupplier, enabled: BooleanSupplier, change: Runnable): Element {
        val row = node(list, "div", "remote-row")
        selection(row, name, selected, enabled, change)
        return row
    }

    private fun selection(parent: Element, name: String, selected: BooleanSupplier, enabled: BooleanSupplier, change: Runnable) {
        val button = action(parent, "") {
            if (enabled.asBoolean) { change.run(); updateRemoteSelections() }
        }
        val element = button.getElement()
        element.setAttribute("class", "button remote-selection")
        element.setAttribute("role", "checkbox")
        element.setAttribute("aria-label", name)
        val box = node(element, "span", "remote-checkbox")
        box.setAttribute("aria-hidden", "true")
        box.setInnerHTML("""<svg class="remote-checkmark" viewBox="0 0 16 16"><path d="M3 8 L6.5 11.5 L13 4.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></path></svg>""")
        rich(node(element, "span", "remote-name"), com.cozooo.dlc_manager.client.core.DlcTextFormatter.parse(name))
        var previous: Boolean? = null
        val sync = Runnable {
            val checked = selected.asBoolean
            if (previous != checked) {
                previous = checked
                element.setAttribute("class", "button remote-selection" + if (checked) " checked" else "")
                element.setAttribute("aria-checked", checked.toString())
            }
            button.setEnabled(enabled.asBoolean)
        }
        selections.add(sync)
        sync.run()
    }

    protected fun remoteTip(owner: Element, name: String, tips: Map<String, String>?) {
        val language = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode())
        val description = tips?.get(language) ?: tips?.get("en_us") ?: tips?.values?.firstOrNull().orEmpty()
        tip(owner) { DlcUiTooltip.Content(name, description, emptyList(), "") }
    }

    protected fun updateRemoteSelections() { selections.forEach { it.run() } }

    protected fun remoteStatus(): Element {
        downloadList?.setScrollTop(scrollTop)
        return node(content, "div", "remote-status")
    }

    protected fun remoteProgress(parent: Element, value: DoubleSupplier) {
        val meter = node(parent, "div", "remote-progress")
        progress(meter, value)
        live(meter, { "${Math.round(value.asDouble.coerceIn(0.0, 1.0) * 100)}%" }, "remote-percent")
    }
}
