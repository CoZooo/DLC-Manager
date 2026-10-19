package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import java.util.function.DoubleSupplier;

final class DlcUiProgress {
    private DlcUiProgress() {}
    static Runnable binding(Element track, Element fill, DoubleSupplier value) {
        int[] previous = {-1};
        return () -> {
            if ("display:none".equals(track.getAttribute("style"))) return;
            double fraction = value.getAsDouble();
            if (fraction < 0) {
                double phase = (System.nanoTime() % 1200000000L) / 1200000000.0;
                fill.setAttribute("style", "width:25%;transform:translateX(" + (track.getOffsetWidth() * 0.75 * phase) + "px)");
                if (track.hasAttribute("aria-valuenow")) track.removeAttribute("aria-valuenow");
                if (!track.hasAttribute("aria-busy")) track.setAttribute("aria-busy", "true"); previous[0] = -1; return;
            }
            if (track.hasAttribute("aria-busy")) track.removeAttribute("aria-busy");
            int percent = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 100);
            if (percent != previous[0]) { previous[0] = percent; fill.setAttribute("style", "width:" + percent + "%"); track.setAttribute("aria-valuenow", String.valueOf(percent)); }
        };
    }
}
