package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.style.Transform;
import com.sighs.apricityui.viewport.ApricityViewport;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiTooltipTest {
    @Test void hoveringShowsContentOnTheNextFrameWithoutWaiting() {
        var document = new TooltipDocument();
        Document.runWithContext(document, () -> {
            initialize(document);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            tooltip.bind(row, () -> content("DLC"));
            mouse(row, "mouseenter", 100, 100);
            tooltip.update();
            var panel = document.getElementById("dlc-tooltip");
            assertEquals("DLC", panel.children.get(0).getTextContent());
            assertEquals("false", panel.getAttribute("aria-hidden"));
            assertEquals("visible", panel.getRawComputedStyle().visibility);
            assertPosition(panel, 116, 120);
        });
    }

    @Test void movingWithinOneRowFollowsThePointerWithoutRelayingOutTheList() {
        var document = new TooltipDocument();
        var builds = new AtomicInteger();
        Document.runWithContext(document, () -> {
            initialize(document);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            tooltip.bind(row, () -> { builds.incrementAndGet(); return content("DLC"); });
            mouse(row, "mouseenter", 100, 100);
            tooltip.update();
            var panel = document.getElementById("dlc-tooltip");
            panel.recomputeStyleSelf();
            int geometryChanges = 0;
            for (int frame = 1; frame <= 100; frame++) {
                mouse(row, "mousemove", 100 + frame, 100 + frame);
                document.dirtyMask.set(0);
                tooltip.update();
                panel.recomputeStyleSelf();
                geometryChanges |= document.dirtyMask.get();
                assertPosition(panel, 116 + frame, 120 + frame);
                assertEquals("false", panel.getAttribute("aria-hidden"));
            }
            assertEquals(0, geometryChanges & (Drawer.RELAYOUT | Drawer.REORDER),
                "Following the pointer must not invalidate layout or rebuild the paint list");
            assertNotEquals(0, geometryChanges & Drawer.COMMIT_LAYOUT,
                "The renderer must receive the new visual position");
            assertEquals(1, builds.get());
        });
    }

    @Test void crossingRowsImmediatelyShowsTheCurrentContentWithoutSettling() {
        var document = new TooltipDocument();
        Document.runWithContext(document, () -> {
            initialize(document);
            var first = document.body.appendChild(document.createElement("div"));
            var second = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            var label = new AtomicReference<>("First");
            tooltip.bind(first, () -> content(label.get()));
            tooltip.bind(second, () -> content("Second"));
            Element previous = null;
            for (int frame = 0; frame < 100; frame++) {
                if (previous != null) mouse(previous, "mouseleave", 100, 100);
                Element row = frame % 2 == 0 ? first : second;
                mouse(row, "mouseenter", 100, 100);
                tooltip.update();
                var panel = document.getElementById("dlc-tooltip");
                assertEquals(row == first ? label.get() : "Second", panel.children.get(0).getTextContent());
                assertEquals("false", panel.getAttribute("aria-hidden"));
                previous = row;
                label.set("Updated");
            }
        });
    }

    @Test void leavingOrScrollingBeforeRenderingCancelsPendingContent() {
        var document = new TooltipDocument();
        Document.runWithContext(document, () -> {
            initialize(document);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            tooltip.bind(row, () -> { fail("Canceled tooltip content must never be built"); return null; });
            mouse(row, "mouseenter", 100, 100);
            mouse(row, "mouseleave", 100, 100);
            tooltip.update();
            mouse(row, "mouseenter", 100, 100);
            var wheel = new MouseEvent("wheel", new Position(100, 100), 0);
            wheel.target = row;
            document.dispatchEvent(wheel);
            tooltip.update();
            assertEquals("true", document.getElementById("dlc-tooltip").getAttribute("aria-hidden"));
        });
    }

    @Test void anIdleTooltipDoesNotInvalidateLayoutAndAResizeStillKeepsItOnScreen() {
        var document = new TooltipDocument();
        Document.runWithContext(document, () -> {
            initialize(document);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            tooltip.bind(row, () -> content("DLC"));
            document.dirtyMask.set(0);
            for (int frame = 0; frame < 100; frame++) tooltip.update();
            assertEquals(0, document.dirtyMask.get());
            mouse(row, "mouseenter", 1000, 600);
            tooltip.update();
            document.dirtyMask.set(0);
            for (int frame = 0; frame < 100; frame++) tooltip.update();
            assertEquals(0, document.dirtyMask.get());
            document.viewport.set(new ApricityViewport(800, 480, 1, 1));
            tooltip.update();
            assertPosition(document.getElementById("dlc-tooltip"), 548, 348);
            tooltip.hide();
            document.dirtyMask.set(0);
            for (int frame = 0; frame < 100; frame++) tooltip.update();
            assertEquals(0, document.dirtyMask.get());
        });
    }

    @Test void hidingAndShowingTheSameContentDoesNotInvalidateListLayout() {
        var document = new TooltipDocument();
        Document.runWithContext(document, () -> {
            initialize(document);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            tooltip.bind(row, () -> content("DLC"));
            mouse(row, "mouseenter", 100, 100);
            tooltip.update();
            var panel = document.getElementById("dlc-tooltip");
            var title = panel.children.get(0);
            panel.recomputeStyleSelf();
            document.dirtyMask.set(0);
            tooltip.hide();
            panel.recomputeStyleSelf();
            assertEquals("hidden", panel.getRawComputedStyle().visibility);
            assertEquals("true", panel.getAttribute("aria-hidden"));
            assertEquals(0, document.dirtyMask.get() & (Drawer.RELAYOUT | Drawer.REORDER));
            for (int frame = 0; frame < 20; frame++) {
                mouse(row, "mouseenter", 100 + frame, 100);
                tooltip.update();
                panel.recomputeStyleSelf();
                assertSame(title, panel.children.get(0), "Unchanged content should reuse its existing nodes");
                assertEquals("visible", panel.getRawComputedStyle().visibility);
                assertPosition(panel, 116 + frame, 120);
                mouse(row, "mouseleave", 100 + frame, 100);
                panel.recomputeStyleSelf();
            }
            assertEquals(0, document.dirtyMask.get() & (Drawer.RELAYOUT | Drawer.REORDER));
        });
    }

    @Test void reusingTheSuppliersContentObjectStillUpdatesChangedDetails() {
        var document = new TooltipDocument();
        Document.runWithContext(document, () -> {
            initialize(document);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            var details = new java.util.ArrayList<DlcUiTooltip.Detail>();
            details.add(new DlcUiTooltip.Detail("State", "Before", false));
            var content = new DlcUiTooltip.Content("DLC", "Details", details, "");
            tooltip.bind(row, () -> content);
            mouse(row, "mouseenter", 100, 100);
            tooltip.update();
            var panel = document.getElementById("dlc-tooltip");
            assertTrue(panel.getTextContent().contains("Before"));
            tooltip.hide();
            details.set(0, new DlcUiTooltip.Detail("State", "After", true));
            mouse(row, "mouseenter", 100, 100);
            tooltip.update();
            assertTrue(panel.getTextContent().contains("After"));
            assertFalse(panel.getTextContent().contains("Before"));
        });
    }

    private void assertDescendantsPainted(Element element) {
        for (Element child : element.children) {
            assertFalse(com.sighs.apricityui.render.RenderNode.shouldSkip(child),
                "Shown tooltip content must be painted: " + child.getAttribute("class") + " visibility=" + child.getComputedStyle().visibility);
            assertDescendantsPainted(child);
        }
    }

    @Test void realLayoutKeepsTooltipTextAndItsPaintNodesAfterShowing() {
        var document = new Document("dlc_manager/ui/page.html", false) {
            @Override public ApricityViewport getViewport() { return new ApricityViewport(1120, 640, 1, 1); }
            @Override public com.sighs.apricityui.layout.Size getViewportSize() { return new com.sighs.apricityui.layout.Size(1120, 640); }
        };
        Document.runWithContext(document, () -> {
            initialize(document);
            document.registerStylesheet("html,body{display:block;width:1120px;height:640px;overflow:hidden} .dlc-tooltip{position:fixed;width:240px;padding:16px;overflow-y:auto} .tooltip-title,.tooltip-description,.tooltip-detail,.tooltip-hint{display:block}", "dlc_manager/ui/page.css", 0);
            var row = document.body.appendChild(document.createElement("div"));
            var tooltip = new DlcUiTooltip(document);
            var label = new AtomicReference<>("DLC");
            tooltip.bind(row, () -> content(label.get()));
            document.commitPendingStyleRecalcForRender();
            document.commitRenderState();
            for (int cycle = 0; cycle < 3; cycle++) {
                label.set("DLC " + cycle);
                mouse(row, "mouseenter", 100, 100);
                tooltip.update();
                document.commitPendingStyleRecalcForRender();
                document.commitRenderState();
                tooltip.update();
                var panel = document.getElementById("dlc-tooltip");
                assertEquals("false", panel.getAttribute("aria-hidden"));
                assertDescendantsPainted(panel);
                assertEquals(1, document.getPaintList().stream().filter(node -> node instanceof com.sighs.apricityui.render.RenderNode.ElementPhaseNode phase
                    && phase.target() == panel && phase.phase() == com.sighs.apricityui.render.Base.RenderPhase.BODY).count(),
                    "A repeated tooltip background must not paint over its text");
                for (Element child : panel.children) {
                    assertFalse(com.sighs.apricityui.style.Text.of(child).content.isEmpty(), "Text cache must contain drawable content: " + child.getTextContent());
                    assertTrue(document.getPaintList().stream().anyMatch(node -> com.sighs.apricityui.render.RenderNode.getRenderNodeTarget(node) == child), "Text must be in the paint list");
                }
                mouse(row, "mouseleave", 100, 100);
                document.commitPendingStyleRecalcForRender();
                document.commitRenderState();
            }
        });
    }

    private static class TooltipDocument extends Document {
        final AtomicInteger dirtyMask = new AtomicInteger();
        final AtomicReference<ApricityViewport> viewport = new AtomicReference<>(new ApricityViewport(1120, 640, 1, 1));

        TooltipDocument() { super("dlc_manager/ui/page.html", false); }
        @Override public ApricityViewport getViewport() { return viewport.get(); }
        @Override public void markDirty(Element element, int flags) { dirtyMask.getAndUpdate(mask -> mask | flags); }
        @Override public Element createElement(String tag) {
            return new Element(this, tag) {
                @Override public double getOffsetWidth() { return 240; }
                @Override public double getOffsetHeight() { return 120; }
            };
        }
    }

    private void initialize(Document document) {
        document.documentElement = new Html(document);
        document.body = new Body(document);
        document.documentElement.appendChild(document.body);
    }

    private DlcUiTooltip.Content content(String title) {
        return new DlcUiTooltip.Content(title, "Details", List.of(), "");
    }

    private void assertPosition(Element panel, double x, double y) {
        var transform = Transform.parse(panel.getRawComputedStyle().transform, 1120, 640);
        assertEquals(List.of(new Transform.Translate(x, y, 0)), transform);
    }

    private void mouse(Element target, String type, double x, double y) {
        var event = new MouseEvent(type, new Position(x, y), 0);
        event.target = target;
        event.currentTarget = target;
        target.dispatchEvent(event);
    }
}
