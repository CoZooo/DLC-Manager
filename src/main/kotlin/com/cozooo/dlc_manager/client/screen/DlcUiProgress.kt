package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import java.util.function.DoubleSupplier

internal object DlcUiProgress {
    @JvmStatic fun binding(track: Element, fill: Element, value: DoubleSupplier): Runnable {
        var previous = -1
        return Runnable {
            if (track.getAttribute("style") == "display:none") return@Runnable
            val fraction = value.asDouble
            if (fraction < 0) {
                val phase = (System.nanoTime() % 1200000000L) / 1200000000.0
                fill.setAttribute("style", "width:25%;transform:translateX(${track.offsetWidth * 0.75 * phase}px)")
                if (track.hasAttribute("aria-valuenow")) track.removeAttribute("aria-valuenow")
                if (!track.hasAttribute("aria-busy")) track.setAttribute("aria-busy", "true"); previous = -1
            } else {
                if (track.hasAttribute("aria-busy")) track.removeAttribute("aria-busy")
                val percent = Math.round(fraction.coerceIn(0.0, 1.0) * 100).toInt()
                if (percent != previous) { previous = percent; fill.setAttribute("style", "width:$percent%"); track.setAttribute("aria-valuenow", percent.toString()) }
            }
        }
    }
}
