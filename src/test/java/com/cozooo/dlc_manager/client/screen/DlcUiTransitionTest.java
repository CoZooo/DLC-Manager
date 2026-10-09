package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiTransitionTest {
    @Test void transitionLeavesAuthoredStylesUntouched() {
        withDocument(target -> {
            target.setAttribute("style", "opacity: 0.7; color: #123456");
            String authored = target.getAttribute("style");
            var backend = new FakeBackend();
            var motion = new DlcUiTransition(backend);
            assertTrue(motion.capture(target));
            motion.render(null, true, 0);
            motion.render(null, true, 100_000_000L);
            assertEquals(authored, target.getAttribute("style"));
            motion.cancel();
            assertEquals(authored, target.getAttribute("style"));
            assertEquals(List.of(1.0f, 0.5f), backend.latest().alphas);
        });
    }

    @Test void loadingTimeDoesNotConsumeTheVisibleCrossfade() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        assertTrue(motion.capture(null));
        assertTrue(motion.isRunning());
        assertTrue(motion.isWaiting());
        motion.render(null, false, 10_000_000_000L);
        motion.render(null, false, 15_000_000_000L);
        assertTrue(motion.isWaiting());
        motion.render(null, true, 20_000_000_000L);
        assertFalse(motion.isWaiting());
        motion.render(null, true, 20_100_000_000L);
        assertEquals(List.of(1.0f, 1.0f, 1.0f, 0.5f), backend.latest().alphas);
        assertTrue(motion.isRunning());
        motion.render(null, true, 20_200_000_000L);
        assertFalse(motion.isRunning());
        assertFalse(motion.isWaiting());
        assertEquals(1, backend.latest().closed);
    }

    @Test void rapidSwitchCapturesTheVisibleCompositeBeforeReleasingItsPreviousImage() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        motion.capture(null);
        var first = backend.latest();
        motion.render(null, true, 0);
        motion.render(null, true, 100_000_000L);
        assertTrue(motion.capture(null));
        var second = backend.latest();
        assertEquals(List.of("capture:1", "capture:2", "close:1"), backend.events);
        assertEquals(1, first.closed);
        assertTrue(motion.isWaiting());
        motion.render(null, true, 100_000_000L);
        motion.render(null, true, 200_000_000L);
        assertEquals(List.of(1.0f, 0.5f), second.alphas);
        motion.render(null, true, 300_000_000L);
        assertEquals(1, second.closed);
    }

    @Test void cancellationReleasesWaitingAndRunningImagesExactlyOnce() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        motion.capture(null);
        var first = backend.latest();
        motion.cancel();
        motion.cancel();
        motion.render(null, true, 0);
        assertEquals(1, first.closed);
        assertTrue(first.alphas.isEmpty());
        motion.capture(null);
        var second = backend.latest();
        motion.render(null, true, 0);
        motion.cancel();
        motion.render(null, true, 100_000_000L);
        assertEquals(1, second.closed);
        assertEquals(List.of(1.0f), second.alphas);
        assertFalse(motion.isRunning());
        assertFalse(motion.isWaiting());
    }

    @Test void resizingInvalidatesTheImageBeforeItCanStretchOntoTheNewViewport() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        motion.capture(null);
        var captured = backend.latest();
        motion.render(null, true, 0);
        captured.valid = false;
        motion.render(null, true, 50_000_000L);
        assertEquals(List.of(1.0f), captured.alphas);
        assertEquals(1, captured.closed);
        assertFalse(motion.isRunning());
    }

    @Test void captureFailureReleasesTheOldImageAndReturnsToNormalRendering() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        motion.capture(null);
        var previous = backend.latest();
        backend.captureFailure = true;
        assertFalse(motion.capture(null));
        assertEquals(1, previous.closed);
        assertFalse(motion.isRunning());
        assertFalse(motion.isWaiting());
        assertDoesNotThrow(() -> motion.render(null, true, 0));
    }

    @Test void invalidNewImageAndPreviousImageAreBothReleased() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        motion.capture(null);
        var previous = backend.latest();
        backend.nextValid = false;
        assertFalse(motion.capture(null));
        assertEquals(1, previous.closed);
        assertEquals(1, backend.latest().closed);
        assertEquals(List.of("capture:1", "capture:2", "close:2", "close:1"), backend.events);
        assertFalse(motion.isRunning());
    }

    @Test void drawingFailureAndReleaseFailureCannotKeepABrokenOverlayActive() {
        var backend = new FakeBackend();
        var motion = new DlcUiTransition(backend);
        motion.capture(null);
        var captured = backend.latest();
        captured.drawFailure = true;
        captured.closeFailure = true;
        assertDoesNotThrow(() -> motion.render(null, true, 0));
        assertFalse(motion.isRunning());
        assertFalse(motion.isWaiting());
        motion.cancel();
        assertEquals(1, captured.closed);
    }

    @Test void targetAndBodyOptOutCancelWithoutCapturingAnotherImage() {
        withDocument(target -> {
            var backend = new FakeBackend();
            var motion = new DlcUiTransition(backend);
            motion.capture(target);
            target.setAttribute("data-dlc-transition", " none ");
            assertFalse(motion.capture(target));
            assertEquals(1, backend.images.size());
            assertEquals(1, backend.latest().closed);
            target.removeAttribute("data-dlc-transition");
            target.document.body.setAttribute("data-dlc-transition", "NONE");
            assertFalse(motion.capture(target));
            assertEquals(1, backend.images.size());
            assertFalse(motion.isRunning());
        });
    }

    @Test void detachedContentCannotBeCapturedButAnExistingImageSurvivesItsReplacement() {
        withDocument(target -> {
            var backend = new FakeBackend();
            var motion = new DlcUiTransition(backend);
            assertTrue(motion.capture(target));
            target.remove();
            motion.render(null, true, 0);
            assertEquals(List.of(1.0f), backend.latest().alphas);
            assertTrue(motion.isRunning());
            assertFalse(motion.capture(target));
            assertEquals(1, backend.images.size());
            assertEquals(1, backend.latest().closed);
            assertFalse(motion.isRunning());
        });
    }

    @Test void cropMapsGuiCoordinatesToFramebufferPixelsAndBackWithoutMovingTheContent() {
        var bounds = DlcUiTransition.Bounds.crop(1920, 1080, 960, 540, 100, 60, 400, 200);
        assertNotNull(bounds);
        assertEquals(200, bounds.left);
        assertEquals(1000, bounds.right);
        assertEquals(560, bounds.bottom);
        assertEquals(960, bounds.top);
        assertEquals(100, bounds.guiLeft(), 0.00001);
        assertEquals(500, bounds.guiRight(), 0.00001);
        assertEquals(60, bounds.guiTop(), 0.00001);
        assertEquals(260, bounds.guiBottom(), 0.00001);
    }

    @Test void cropRoundsOutwardAndClipsToTheActualFramebuffer() {
        var bounds = DlcUiTransition.Bounds.crop(1500, 900, 600, 360, -1.2, 10.1, 201.3, 400);
        assertNotNull(bounds);
        assertEquals(0, bounds.left);
        assertEquals(501, bounds.right);
        assertEquals(0, bounds.bottom);
        assertEquals(875, bounds.top);
        assertEquals(0, bounds.guiLeft(), 0.00001);
        assertEquals(200.4, bounds.guiRight(), 0.00001);
        assertEquals(10, bounds.guiTop(), 0.00001);
        assertEquals(360, bounds.guiBottom(), 0.00001);
    }

    @Test void emptyOffscreenAndNonFiniteRegionsDoNotAllocateAnImage() {
        assertNull(DlcUiTransition.Bounds.crop(1920, 1080, 960, 540, 0, 0, 0, 100));
        assertNull(DlcUiTransition.Bounds.crop(1920, 1080, 960, 540, 961, 0, 100, 100));
        assertNull(DlcUiTransition.Bounds.crop(1920, 1080, 960, 540, Double.NaN, 0, 100, 100));
        assertNull(DlcUiTransition.Bounds.crop(1920, 1080, 960, 540, 0, 0, Double.POSITIVE_INFINITY, 100));
        assertNull(DlcUiTransition.Bounds.crop(0, 1080, 960, 540, 0, 0, 100, 100));
        assertNull(DlcUiTransition.Bounds.crop(1920, 1080, 0, 540, 0, 0, 100, 100));
    }

    private void withDocument(Consumer<Element> test) {
        var document = new Document("dlc_manager/ui/page.html", false);
        Document.runWithContext(document, () -> {
            document.documentElement = new Html(document);
            document.body = new Body(document);
            document.documentElement.appendChild(document.body);
            test.accept(document.body.appendChild(document.createElement("section")));
        });
    }

    private static final class FakeBackend implements DlcUiTransition.Backend {
        private final List<String> events = new ArrayList<>();
        private final List<FakeSnapshot> images = new ArrayList<>();
        private boolean captureFailure;
        private boolean nextValid = true;

        @Override
        public DlcUiTransition.Snapshot capture(Element region) {
            if (captureFailure) throw new IllegalStateException("framebuffer unavailable");
            int id = images.size() + 1;
            events.add("capture:" + id);
            var result = new FakeSnapshot(id, events);
            result.valid = nextValid;
            images.add(result);
            return result;
        }

        private FakeSnapshot latest() { return images.get(images.size() - 1); }
    }

    private static final class FakeSnapshot implements DlcUiTransition.Snapshot {
        private final int id;
        private final List<String> events;
        private final List<Float> alphas = new ArrayList<>();
        private boolean valid = true;
        private boolean drawFailure;
        private boolean closeFailure;
        private int closed;

        private FakeSnapshot(int id, List<String> events) {
            this.id = id;
            this.events = events;
        }

        @Override public boolean isValid() { return valid; }

        @Override public void draw(GuiGraphics graphics, float alpha) {
            if (drawFailure) throw new IllegalStateException("composite unavailable");
            alphas.add(alpha);
        }

        @Override public void close() {
            closed++;
            events.add("close:" + id);
            if (closeFailure) throw new IllegalStateException("framebuffer context lost");
        }
    }
}
