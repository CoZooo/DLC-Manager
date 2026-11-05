package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.LayoutCommit;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.render.RectFrameCache;
import com.sighs.apricityui.render.TransformFrameCache;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.viewport.ApricityViewport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiScrollTest {
    private Element output(double height) {
        Element element = new Element(null, "div");
        Box box = new Box() {
            @Override public Size innerSize() { return new Size(200, 100); }
        };
        box.applyPaddingAll("16px");
        element.getLayout().box.set(box);
        element.scrollHeight = height;
        return element;
    }

    @Test void consoleScrollUsesNativeLimitWithPaddingWithoutOverscroll() {
        Element element = output(500);
        DlcUiScroll.scrollToEnd(element);
        assertEquals(400, element.getTargetScrollTop());
        assertTrue(Double.isFinite(element.getTargetScrollTop()));
        assertTrue(DlcUiScroll.isAtEnd(element, 24));
        element.scrollHeight = 540;
        assertFalse(DlcUiScroll.isAtEnd(element, 24));
        DlcUiScroll.scrollToEnd(element);
        assertEquals(440, element.getTargetScrollTop());
    }

    @Test void rebuildingAnInnerListPreservesItsCurrentAndPendingScrollBeforeLayout() {
        Element previous = output(900);
        previous.scrollTop = 200;
        previous.setScrollTop(350);
        var state = DlcUiScroll.capture(previous);
        Element rebuilt = output(0);
        DlcUiScroll.restore(rebuilt, state);
        assertEquals(200, rebuilt.getScrollTop());
        assertEquals(350, rebuilt.getTargetScrollTop());
        assertEquals(900, rebuilt.scrollHeight);
    }

    @Test void shortOutputRemainsVisibleAndUserScrollingDisablesFollowing() {
        Element element = output(40);
        DlcUiScroll.scrollToEnd(element);
        assertEquals(0, element.getTargetScrollTop());
        element.scrollHeight = 500;
        element.setScrollTop(100);
        assertFalse(DlcUiScroll.isAtEnd(element, 24));
    }

    @Test void loadingTooltipContentDoesNotPullTheSyncListBackToTheTop() throws Exception {
        var document = new Document("dlc_manager/ui/page.html", false) {
            @Override public ApricityViewport getViewport() { return new ApricityViewport(1120, 640, 1, 1); }
            @Override public Size getViewportSize() { return new Size(1120, 640); }
        };
        String cssRoot = "/assets/apricityui/apricity/dlc_manager/ui/";
        try (var manager = getClass().getResourceAsStream(cssRoot + "manager.css");
             var page = getClass().getResourceAsStream(cssRoot + "page.css")) {
            document.registerStylesheet(new String(manager.readAllBytes(), StandardCharsets.UTF_8), "dlc_manager/ui/manager.css", 0);
            document.registerStylesheet(new String(page.readAllBytes(), StandardCharsets.UTF_8), "dlc_manager/ui/page.css", 1);
        }
        Document.runWithContext(document, () -> {
            document.documentElement = new Html(document);
            document.body = new Body(document);
            document.documentElement.appendChild(document.body);
            document.body.setAttribute("class", "dlc-manager-ui");
            var shell = node(document.body, "page-shell sync-page");
            node(shell, "page-header");
            var content = node(shell, "");
            content.setAttribute("id", "page-content");
            var layout = node(content, "sync-layout");
            node(layout, "sync-types");
            var results = node(layout, "sync-results");
            var list = node(results, "page-section file-list");
            Element[] rows = new Element[80];
            var tooltip = new DlcUiTooltip(document);
            for (int i = 0; i < rows.length; i++) {
                rows[i] = node(list, "dlc-row sync-row");
                DlcUiRows.managerRow(document, rows[i], "DLC " + i, false, false);
                rows[i].setAttribute("class", "dlc-row sync-row");
                String name = "DLC " + i;
                tooltip.bind(rows[i], () -> new DlcUiTooltip.Content(name, "Description", List.of(), "Server missing"));
            }
            node(shell, "page-footer");
            document.commitPendingStyleRecalcForRender();
            document.commitRenderState();
            assertTrue(results.scrollHeight > results.getOffsetHeight() + 500,
                "Scroll area=" + results.scrollHeight + ", viewport=" + results.getOffsetHeight());
            results.scrollTop = 200;
            results.setScrollTop(500);
            double scrollHeight = results.scrollHeight;
            double viewportHeight = Box.of(results).innerSize().height();
            for (int i = 5; i < 25; i++) {
                var event = new MouseEvent("mouseenter", new Position(400, 300), 0);
                event.target = rows[i];
                rows[i].dispatchEvent(event);
                tooltip.update();
                RectFrameCache.begin();
                TransformFrameCache.begin();
                LayoutMeasureCache.begin();
                StyleFrameCache.begin();
                try {
                    if (document.commitPendingStyleRecalcForRender()) document.commitRenderStateForMotion();
                    else if (document.hasPendingRenderState()) document.commitRenderState();
                    assertEquals(scrollHeight, results.scrollHeight, 0.001, "The list's scroll extent must remain stable");
                    assertEquals(viewportHeight, Box.of(results).innerSize().height(), 0.001, "The list's viewport must remain stable");
                    document.stepScrollRender();
                    assertEquals(500, results.getTargetScrollTop(), 0.001,
                        "Tooltip layout must not erase the pending downward scroll");
                    LayoutCommit.commit(document);
                } finally {
                    StyleFrameCache.end();
                    LayoutMeasureCache.end();
                    TransformFrameCache.end();
                    RectFrameCache.end();
                }
            }
        });
    }

    private Element node(Element parent, String classes) {
        Element element = parent.appendChild(parent.document.createElement("div"));
        element.setAttribute("class", classes);
        return element;
    }
}
