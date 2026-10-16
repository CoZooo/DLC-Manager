package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcUiButtonTest {
    @Test
    void leftPressActivatesImmediatelyAndReleaseDoesNotRepeat() {
        withDocument(document -> {
            var actions = new AtomicInteger();
            var button = new DlcUiButton(document, document.body, "Resource Pack", actions::incrementAndGet);

            for (int count = 1; count <= 3; count++) {
                mouse(document, button.getElement(), "mousedown", 0);
                assertEquals(count, actions.get());
                button.setDisplayString("Selected " + count);
                mouse(document, button.getElement(), "mouseup", 0);
                assertEquals(count, actions.get());
            }
        });
    }

    @Test
    void releasingOutsideDoesNotUndoActivationOrSuppressProgrammaticClick() {
        withDocument(document -> {
            var actions = new AtomicInteger();
            var button = new DlcUiButton(document, document.body, "Resource Pack", actions::incrementAndGet);

            mouse(document, button.getElement(), "mousedown", 0);
            assertEquals(1, actions.get());
            mouse(document, document.body, "mouseup", 0);
            assertEquals(1, actions.get());
            button.getElement().click();
            assertEquals(2, actions.get());
        });
    }

    @Test
    void programmaticActivationStillWorksAfterMouseClick() {
        withDocument(document -> {
            var actions = new AtomicInteger();
            var button = new DlcUiButton(document, document.body, "Resource Pack", actions::incrementAndGet);

            button.getElement().click();
            assertEquals(1, actions.get());
            mouse(document, button.getElement(), "mousedown", 0);
            assertEquals(2, actions.get());
            mouse(document, button.getElement(), "mouseup", 0);
            button.getElement().click();
            assertEquals(3, actions.get());
        });
    }

    @Test
    void nonPrimaryMouseButtonsDoNotActivate() {
        withDocument(document -> {
            var actions = new AtomicInteger();
            var button = new DlcUiButton(document, document.body, "Resource Pack", actions::incrementAndGet);

            for (int mouseButton : new int[] {1, 2}) {
                mouse(document, button.getElement(), "mousedown", mouseButton);
                mouse(document, button.getElement(), "mouseup", mouseButton);
            }
            assertEquals(0, actions.get());
        });
    }

    @Test
    void disabledButtonsIgnoreMouseAndProgrammaticActivation() {
        withDocument(document -> {
            var actions = new AtomicInteger();
            var button = new DlcUiButton(document, document.body, "Resource Pack", actions::incrementAndGet);

            button.setEnabled(false);
            mouse(document, button.getElement(), "mousedown", 0);
            mouse(document, button.getElement(), "mouseup", 0);
            button.getElement().click();
            assertEquals(0, actions.get());

            button.setEnabled(true);
            button.getElement().setAttribute("disabled", "");
            mouse(document, button.getElement(), "mousedown", 0);
            mouse(document, button.getElement(), "mouseup", 0);
            button.getElement().click();
            assertEquals(0, actions.get());

            button.getElement().removeAttribute("disabled");
            mouse(document, button.getElement(), "mousedown", 0);
            assertEquals(1, actions.get());
            mouse(document, button.getElement(), "mouseup", 0);
            assertEquals(1, actions.get());
        });
    }

    @Test
    void enablingDuringPressDoesNotActivateOnRelease() {
        withDocument(document -> {
            var actions = new AtomicInteger();
            var button = new DlcUiButton(document, document.body, "Resource Pack", actions::incrementAndGet);

            button.setEnabled(false);
            mouse(document, button.getElement(), "mousedown", 0);
            button.setEnabled(true);
            mouse(document, button.getElement(), "mouseup", 0);
            assertEquals(0, actions.get());
            button.getElement().click();
            assertEquals(1, actions.get());
        });
    }

    private void mouse(Document document, Element target, String type, int button) {
        MouseEvent.dispatchToTarget(new MouseEvent(type, Position.ZERO, button, false), document, target);
    }

    private void withDocument(Consumer<Document> test) {
        var document = new Document("dlc_manager/ui/page.html", false);
        Document.runWithContext(document, () -> {
            document.documentElement = new Html(document);
            document.body = new Body(document);
            document.documentElement.appendChild(document.body);
            test.accept(document);
        });
    }
}
