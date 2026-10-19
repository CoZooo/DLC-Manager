package com.cozooo.dlc_manager.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiTextInputTest {
    private AuiClientService previous;
    private final List<String> drawn = new ArrayList<>();

    @BeforeEach void captureImmediateFontDrawing() {
        previous = AuiServices.client();
        AuiServices.setClient((AuiClientService) Proxy.newProxyInstance(
            AuiClientService.class.getClassLoader(), new Class<?>[]{AuiClientService.class}, (proxy, method, args) -> {
                if (method.getName().equals("drawDefaultFont")) {
                    drawn.add((String) args[2]);
                    return null;
                }
                if (method.getName().equals("getDefaultFontWidth")) return ((String) args[0]).codePointCount(0, ((String) args[0]).length()) * 8;
                return method.invoke(previous, args);
            }));
    }

    @AfterEach void restoreClientService() {
        AuiServices.setClient(previous);
    }

    private DlcUiTextInput input(String value) {
        var input = new DlcUiTextInput(null);
        prepare(input, value);
        return input;
    }

    private void prepare(DlcUiTextInput input, String value) {
        input.value = value;
        Text text = new Text();
        text.fontFamily = "DlcInter";
        text.fontSize = 13;
        text.lineHeight = 18;
        input.getRenderer().text.set(text);
    }

    private void draw(DlcUiTextInput input) {
        assertEquals(input.getValue(), input.getRenderText());
        FontDrawer.drawFont(new PoseStack(), Text.of(input), new Position(0, 0));
    }

    private DlcUiTextInput editable(String value, int cursor) {
        var input = new DlcUiTextInput(null) {
            @Override protected void clampScroll() {}
        };
        input.value = value;
        input.setSelectionRange(cursor, cursor);
        return input;
    }

    @Test void deletionPublishesTheValueImmediatelyAndPreservesUndo() {
        var input = editable("search", 6);
        var changes = new ArrayList<String>();
        var binding = new DlcUiInputBinding(input::getValue, () -> true, changes::add);
        input.addEventListener("input", event -> binding.run());
        assertTrue(input.deleteBackward());
        assertEquals(List.of("searc"), changes);
        assertTrue(input.undo());
        assertEquals("search", input.getValue());
        assertEquals(List.of("searc", "search"), changes);
    }

    @Test void forwardDeletionAndCutPublishOneEventPerEdit() {
        var input = editable("search", 2);
        var changes = new ArrayList<String>();
        var types = new ArrayList<String>();
        input.addEventListener("input", event -> {
            changes.add(input.getValue());
            types.add(((com.sighs.apricityui.event.Event.InputEvent) event).inputType);
        });
        assertTrue(input.deleteForward());
        input.setSelectionRange(0, input.getValue().length());
        input.replaceSelection("");
        assertEquals(List.of("serch", ""), changes);
        assertEquals(List.of("deleteContentForward", "deleteContentBackward"), types);
    }

    @Test void selectionReplacementAndTypingKeepNativeInputEvents() {
        var input = editable("search", 6);
        var changes = new ArrayList<String>();
        input.addEventListener("input", event -> changes.add(input.getValue()));
        input.setSelectionRange(0, input.getValue().length());
        input.replaceSelection("new");
        input.insertText(" text");
        input.setSelectionRange(0, input.getValue().length());
        input.replaceSelection("\r\n");
        assertEquals(List.of("new", "new text", ""), changes);
    }

    @Test void readonlyAndCancelledDeletionDoNotPublishChanges() {
        var input = editable("search", 6);
        input.addEventListener("input", event -> fail("Unexpected input event"));
        input.setAttribute("readonly", "");
        assertFalse(input.deleteBackward());
        assertFalse(input.deleteForward());
        input.setSelectionRange(0, input.getValue().length());
        input.replaceSelection("");
        assertEquals("search", input.getValue());
        input.removeAttribute("readonly");
        input.addEventListener("beforeinput", event -> event.preventDefault());
        assertFalse(input.deleteBackward());
        assertFalse(input.deleteForward());
        input.replaceSelection("");
        assertEquals("search", input.getValue());
    }

    @Test void deletingAtTheBoundaryDoesNotPublishAnInputEvent() {
        var input = editable("a", 0);
        input.addEventListener("input", event -> fail("Unexpected input event"));
        assertFalse(input.deleteBackward());
        input.setSelectionRange(1, 1);
        assertFalse(input.deleteForward());
    }

    @Test void typingAndDeletingDrawCurrentTextWithoutWaitingForRasterization() {
        var input = input("a");
        draw(input);
        prepare(input, "ab");
        draw(input);
        prepare(input, "a");
        draw(input);
        assertEquals(List.of("a", "ab", "a"), drawn);
    }

    @Test void previousPlaceholderCannotReplaceTheFirstTypedCharacter() {
        var input = input("的");
        draw(input);
        assertEquals(List.of("的"), drawn);
    }

    @Test void clearingDrawsPlaceholderThroughTheSameImmediateFontPath() {
        var input = input("search");
        prepare(input, "");
        input.getRenderText();
        Text.of(input).content = "搜索DLC...";
        FontDrawer.drawFont(new PoseStack(), Text.of(input), new Position(0, 0));
        assertEquals(List.of("搜索DLC..."), drawn);
    }

    @Test void caretAndSelectionMeasureWithTheFontUsedForDrawing() {
        var input = input("的de");
        draw(input);
        Text text = Text.of(input);
        assertEquals("unset", text.fontFamily);
        assertEquals(24 * text.defaultFontScale(), Size.measureText(input, "的de"), 0.001);
        assertEquals(8 * text.defaultFontScale(), Size.measureText(input, "的"), 0.001);
    }

    @Test void adaptingHtmlInputPreservesItsAttributesValueAndDocumentIdentity() {
        var document = new Document("dlc_manager/ui/manager.html", false);
        Document.runWithContext(document, () -> {
            var parent = document.createElement("div");
            var original = parent.appendChild(new Input(document));
            original.setAttribute("id", "manager-search");
            original.setAttribute("type", "search");
            original.setAttribute("placeholder", "搜索DLC...");
            original.setAttribute("class", "page-input");
            original.setValue("的de");
            var replacement = DlcUiTextInput.adapt(original);
            assertSame(replacement, document.getElementById("manager-search"));
            assertSame(replacement, parent.getFirstElementChild());
            assertSame(replacement, DlcUiTextInput.adapt(replacement));
            assertEquals("的de", replacement.getValue());
            assertEquals("search", replacement.getAttribute("type"));
            assertEquals("搜索DLC...", replacement.getAttribute("placeholder"));
            assertEquals("page-input", replacement.getAttribute("class"));
            assertNull(original.parentElement);
        });
    }

    @Test void rapidEditsRepaintBeforePublishingWithoutWaitingForATick() {
        Map<Element, Integer> invalidated = new HashMap<>();
        var document = new Document("dlc_manager/ui/manager.html", false) {
            @Override public void markDirty(Element element, int flags) {
                invalidated.merge(element, flags, (previous, next) -> previous | next);
            }
        };
        Document.runWithContext(document, () -> {
            var input = new DlcUiTextInput(document) {
                @Override protected void clampScroll() {}
            };
            input.value = "";
            input.setSelectionRange(0, 0);
            var changes = new ArrayList<String>();
            input.addEventListener("input", event -> {
                assertNotEquals(0, invalidated.getOrDefault(input, 0) & Drawer.REPAINT);
                assertEquals(0, invalidated.getOrDefault(input, 0) & Drawer.RELAYOUT);
                changes.add(input.getValue());
                invalidated.clear();
            });
            input.insertText("a");
            input.insertText("b");
            assertTrue(input.deleteBackward());
            assertTrue(input.deleteBackward());
            assertTrue(input.undo());
            assertEquals(List.of("a", "ab", "a", "", "a"), changes);
            input.setValue("");
            assertNotEquals(0, invalidated.getOrDefault(input, 0) & Drawer.REPAINT);
            assertEquals(0, invalidated.getOrDefault(input, 0) & Drawer.RELAYOUT);
            assertEquals(List.of("a", "ab", "a", "", "a"), changes, "Programmatic clearing must not synthesize an input event");
        });
    }

    @Test void deletingTextRepaintsWithoutRelayingOutTheInputOrItsParent() {
        Map<Element, Integer> invalidated = new HashMap<>();
        var document = new Document("dlc_manager/ui/manager.html", false) {
            @Override public void markDirty(Element element, int flags) {
                invalidated.merge(element, flags, (previous, next) -> previous | next);
            }
        };
        Document.runWithContext(document, () -> {
            var parent = document.createElement("div");
            var input = new DlcUiTextInput(document) {
                @Override protected void clampScroll() {}
            };
            parent.appendChild(input);
            input.value = "search";
            input.setSelectionRange(6, 6);
            input.tick();
            invalidated.clear();
            parent.clearDirtyFlags();
            input.clearDirtyFlags();
            assertTrue(input.deleteBackward());
            input.tick();
            assertEquals("searc", input.getValue());
            assertNotEquals(0, invalidated.getOrDefault(input, 0) & Drawer.REPAINT);
            assertEquals(0, invalidated.getOrDefault(input, 0) & Drawer.RELAYOUT, "Editing a fixed-size input must not invalidate its layout");
            assertEquals(0, invalidated.getOrDefault(parent, 0) & Drawer.RELAYOUT, "Deleting text must not invalidate the toolbar/page layout");
        });
    }
}
