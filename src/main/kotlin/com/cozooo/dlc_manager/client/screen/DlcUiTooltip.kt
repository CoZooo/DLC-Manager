package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.event.MouseEvent
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.layout.Box
import java.util.function.Supplier

internal class DlcUiTooltip(private val document: Document) {
    data class Detail(val label: String, val text: String, val warning: Boolean)
    data class Content(val title: String, val description: String, val details: List<Detail>, val hint: String)

    private val generation = document.refreshGeneration
    private val panel = document.body.appendChild(document.createElement("div"))
    private var owner: Element? = null
    private var pending: Supplier<Content>? = null
    private var pointerX = 0.0
    private var pointerY = 0.0
    private var placedWidth = -1.0
    private var placedHeight = -1.0
    private var viewportWidth = -1.0
    private var viewportHeight = -1.0
    private var placedPointerX = Double.NaN
    private var placedPointerY = Double.NaN
    private var visible = false

    init {
        panel.setAttribute("id", "dlc-tooltip")
        panel.setAttribute("class", "dlc-tooltip")
        panel.setAttribute("role", "tooltip")
        panel.setAttribute("aria-hidden", "true")
        panel.setAttribute("style", "display:none")
        panel.setTopLayer(true)
        document.addEventListener("wheel") { event ->
            val target = event.target as? Element
            if (visible && event is MouseEvent && target != null && owner?.contains(target) == true) {
                panel.commitScrollMetricsFromLayout()
                val limit = (panel.scrollHeight - Box.of(panel).innerSize().height()).coerceAtLeast(0.0)
                if (limit > 0) {
                    event.preventDefault()
                    panel.scrollTo(0.0, (panel.targetScrollTop + event.scrollDelta).coerceIn(0.0, limit))
                    return@addEventListener
                }
            }
            hide()
        }
    }

    fun bind(target: Element, content: Supplier<Content>) {
        target.setAttribute("aria-describedby", "dlc-tooltip")
        target.addEventListener("mouseenter") { event ->
            if (event is MouseEvent && document.isCurrentGeneration(generation)) {
                hide()
                owner = target
                pending = content
                pointerX = event.clientX
                pointerY = event.clientY
            }
        }
        target.addEventListener("mousemove") { event ->
            if (owner === target && event is MouseEvent) {
                pointerX = event.clientX
                pointerY = event.clientY
            }
        }
        target.addEventListener("mouseleave") { if (owner === target) hide() }
    }

    fun hide() {
        pending = null
        owner = null
        if (!visible) return
        visible = false
        panel.setAttribute("style", "display:none")
        panel.setAttribute("aria-hidden", "true")
    }

    fun update() {
        if (!document.isCurrentGeneration(generation)) return
        val vw = document.viewport.layoutWidth().toDouble()
        val vh = document.viewport.layoutHeight().toDouble()
        val limits = "max-width:${(vw - 24).coerceAtLeast(1.0)}px;max-height:${(vh - 24).coerceAtLeast(1.0)}px"
        if (pending != null && !visible) {
            val content = pending!!.get()
            pending = null
            panel.setInnerHTML("")
            append(panel, content.title, "tooltip-title")
            append(panel, content.description, "tooltip-description")
            for (detail in content.details) {
                if (detail.text.isBlank()) continue
                val section = append(panel, "", "tooltip-detail" + if (detail.warning) " warning" else "")
                append(section, detail.label, "tooltip-label")
                append(section, detail.text, "tooltip-value")
            }
            append(panel, content.hint, "tooltip-hint")
            visible = true
            placedWidth = -1.0
            placedHeight = -1.0
            viewportWidth = -1.0
            viewportHeight = -1.0
            panel.setAttribute("style", "display:block;visibility:hidden;$limits")
            panel.setScrollTop(0.0)
        }
        if (!visible) return
        val width = panel.offsetWidth
        val height = panel.offsetHeight
        if (width <= 0 || height <= 0) return
        if (width == placedWidth && height == placedHeight && vw == viewportWidth && vh == viewportHeight
            && pointerX == placedPointerX && pointerY == placedPointerY) return
        var left = pointerX + 16
        var top = pointerY + 20
        if (left + width > vw - 12) left = pointerX - width - 16
        if (top + height > vh - 12) top = pointerY - height - 12
        left = left.coerceIn(12.0, (vw - width - 12).coerceAtLeast(12.0))
        top = top.coerceIn(12.0, (vh - height - 12).coerceAtLeast(12.0))
        panel.setAttribute("style", "display:block;visibility:visible;left:${left}px;top:${top}px;$limits")
        panel.setAttribute("aria-hidden", "false")
        placedWidth = width
        placedHeight = height
        viewportWidth = vw
        viewportHeight = vh
        placedPointerX = pointerX
        placedPointerY = pointerY
    }

    private fun append(parent: Element, value: String?, className: String): Element {
        val element = document.createElement("div")
        element.setAttribute("class", className)
        if (!value.isNullOrBlank()) DlcUiText.set(element, value)
        else if (!className.startsWith("tooltip-detail")) return element
        return parent.appendChild(element)
    }
}
