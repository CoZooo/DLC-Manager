package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.sighs.apricityui.init.Element
import java.util.Locale

internal object DlcUiText {
    data class Run(val text: String, val css: String)

    @JvmStatic
    fun runs(value: String?): List<Run> = DlcTextFormatter.parse(value).siblings.map { part ->
        val style = part.style
        val css = buildString {
            style.color?.let { append("color:").append(String.format(Locale.ROOT, "#%06x", it.value)).append(';') }
            if (style.isBold) append("font-weight:700;")
            if (style.isItalic) append("font-style:italic;")
            if (style.isUnderlined || style.isStrikethrough) {
                append("text-decoration:")
                if (style.isUnderlined) append("underline ")
                if (style.isStrikethrough) append("line-through")
                append(';')
            }
        }
        Run(part.string, css)
    }

    @JvmStatic
    fun set(element: Element, value: String?) {
        val runs = runs(value)
        if (runs.size == 1 && runs[0].css.isEmpty()) {
            element.setTextContent(runs[0].text)
            return
        }
        element.setTextContent("")
        for (run in runs) {
            val span = element.document.createElement("span")
            span.setAttribute("style", "display:inline;pointer-events:none;${run.css}")
            span.setTextContent(run.text)
            element.appendChild(span)
        }
    }
}
