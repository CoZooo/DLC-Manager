package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiProgressTest {
    @Test void hiddenProgressDoesNotInvalidateTheScrollingListOnEveryTick() {
        var invalidations = new AtomicInteger();
        var document = new Document("dlc_manager/ui/page.html", false) {
            @Override public void markDirty(Element element, int flags) { invalidations.incrementAndGet(); }
        };
        Document.runWithContext(document, () -> {
            document.documentElement = new com.sighs.apricityui.element.Html(document);
            document.body = new com.sighs.apricityui.element.Body(document);
            document.documentElement.appendChild(document.body);
            var results = document.body.appendChild(document.createElement("div"));
            var track = results.appendChild(document.createElement("div"));
            var fill = track.appendChild(document.createElement("div"));
            var binding = DlcUiProgress.binding(track, fill, () -> -1);
            binding.run();
            track.setAttribute("style", "display:none");
            invalidations.set(0);
            for (int tick = 0; tick < 100; tick++) binding.run();
            assertEquals(0, invalidations.get(), "Hidden progress must not continually invalidate a long scrolling list");
            track.setAttribute("style", ""); invalidations.set(0); binding.run();
            assertTrue(invalidations.get() > 0, "Visible indeterminate progress must resume");
        });
    }
}
