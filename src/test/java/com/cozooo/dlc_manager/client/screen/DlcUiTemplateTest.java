package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DlcUiTemplateTest {
    private void withDocument(Consumer<Document> test) {
        var document = new Document("dlc_manager/ui/page.html", false);
        Document.runWithContext(document, () -> {
            document.documentElement = new Html(document);
            document.body = new Body(document);
            document.documentElement.appendChild(document.body);
            test.accept(document);
        });
    }

    @Test
    void emptyDefaultRegionNeedsNoWrapperAndRemovesEachRuntimeRender() {
        withDocument(document -> {
            var region = document.body.appendChild(document.createElement("div"));
            var template = new DlcUiTemplate(document);
            assertTrue(region.getChildNodes().isEmpty());

            for (int revision = 0; revision < 3; revision++) {
                var runtime = region.appendChild(document.createElement("section"));
                runtime.setAttribute("id", "runtime-row-" + revision);
                runtime.appendChild(document.createTextNode("Generated content"));
                template.clear(region);
                assertTrue(region.getChildNodes().isEmpty());
                assertNull(runtime.getParentNode());
                assertSame(region, document.body.getFirstChild());
            }
        });
    }

    @Test
    void repeatedRefreshPreservesStaticNodesAndCleansRuntimeContentInsideThem() {
        withDocument(document -> {
            var region = document.body.appendChild(document.createElement("div"));
            var introduction = region.appendChild(document.createTextNode("Static introduction"));
            var banner = region.appendChild(document.createElement("aside"));
            var message = banner.appendChild(document.createTextNode("Static banner"));
            var comment = banner.appendChild(document.createComment("Template note"));
            var clicks = new AtomicInteger();
            banner.addEventListener("click", event -> clicks.incrementAndGet());
            var template = new DlcUiTemplate(document);

            for (int revision = 0; revision < 3; revision++) {
                var generated = region.appendChild(document.createElement("section"));
                generated.appendChild(document.createElement("button"));
                region.appendChild(document.createTextNode("Runtime status"));
                var nested = banner.appendChild(document.createElement("div"));
                nested.appendChild(document.createTextNode("Nested runtime content"));
                banner.appendChild(document.createTextNode("Runtime banner status"));

                template.clear(region);

                assertEquals(List.of(introduction, banner), region.getChildNodes());
                assertEquals(List.of(message, comment), banner.getChildNodes());
                assertSame(introduction, region.getFirstChild());
                assertSame(message, banner.getFirstChild());
                assertEquals("Static introduction", introduction.getTextContent());
                assertEquals("Static banner", message.getTextContent());
                assertNull(generated.getParentNode());
                assertNull(nested.getParentNode());
                banner.dispatchEvent(new Event(banner, "click"));
                assertEquals(revision + 1, clicks.get());
            }
        });
    }

    @Test
    void identicalRuntimeMarkupDoesNotBecomePartOfTheTemplate() {
        withDocument(document -> {
            var region = document.body.appendChild(document.createElement("div"));
            var original = region.appendChild(document.createElement("span"));
            original.setTextContent("Same markup");
            var template = new DlcUiTemplate(document);
            var generated = region.appendChild(document.createElement("span"));
            generated.setTextContent("Same markup");
            assertEquals(original.getOuterHTML(), generated.getOuterHTML());

            template.clear(region);

            assertEquals(1, region.getChildNodes().size());
            assertSame(original, region.getFirstChild());
            assertNull(generated.getParentNode());
        });
    }

    @Test
    void slotsUseLiteralTemplateNamesAndFallBackWhenMissingOrDetached() {
        withDocument(document -> {
            var fallback = document.body.appendChild(document.createElement("div"));
            var slot = document.body.appendChild(document.createElement("section"));
            slot.setAttribute("data-dlc-slot", "server.save\"]");
            var template = new DlcUiTemplate(document);
            var generated = document.body.appendChild(document.createElement("div"));
            generated.setAttribute("data-dlc-slot", "runtime-only");

            assertSame(slot, template.slot("server.save\"]", fallback));
            assertSame(fallback, template.slot("missing", fallback));
            assertSame(fallback, template.slot("runtime-only", fallback));
            template.clear(slot);
            assertSame(slot, template.slot("server.save\"]", fallback));
            slot.remove();
            assertSame(fallback, template.slot("server.save\"]", fallback));
        });
    }

    @Test
    void duplicateSlotsUseTemplateDomOrderAndIgnoreRenamedCandidates() {
        withDocument(document -> {
            var fallback = document.body.appendChild(document.createElement("div"));
            var later = document.body.appendChild(document.createElement("section"));
            later.setAttribute("data-dlc-slot", "page-prose");
            var first = document.createElement("section");
            first.setAttribute("data-dlc-slot", "page-prose");
            document.body.insertBefore(first, later);
            var template = new DlcUiTemplate(document);

            assertSame(first, template.slot("page-prose", fallback));
            first.setAttribute("data-dlc-slot", "renamed");
            assertSame(fallback, template.slot("page-prose", fallback));
            assertSame(fallback, template.slot("renamed", fallback));
        });
    }

    @Test
    void clearingDoesNotReinsertAnInputReplacedByTheHost() {
        withDocument(document -> {
            var region = document.body.appendChild(document.createElement("div"));
            var original = region.appendChild(document.createElement("input"));
            var template = new DlcUiTemplate(document);
            var replacement = document.createElement("input");
            region.replaceChild(replacement, original);

            template.clear(region);

            assertTrue(region.getChildNodes().isEmpty());
            assertNull(original.getParentNode());
            assertNull(replacement.getParentNode());
        });
    }

    @Test
    void parentRefreshPreservesNestedBindingRegionsAndTheirRuntimeState() {
        withDocument(document -> {
            var content = document.body.appendChild(document.createElement("div"));
            content.setAttribute("id", "page-content");
            var wrapper = content.appendChild(document.createElement("section"));
            var caption = wrapper.appendChild(document.createTextNode("Static layout"));
            var back = wrapper.appendChild(document.createElement("div"));
            back.setAttribute("id", "page-back");
            var title = wrapper.appendChild(document.createElement("h1"));
            title.setAttribute("id", "page-title");
            title.setTextContent("Template title");
            var template = new DlcUiTemplate(document);
            var clicks = new AtomicInteger();
            var button = new DlcUiButton(document, back, "Back", clicks::incrementAndGet);
            title.setTextContent("Bound title");
            var boundText = title.getFirstChild();

            for (int revision = 0; revision < 3; revision++) {
                var runtime = wrapper.appendChild(document.createElement("section"));
                runtime.setAttribute("id", "runtime-group-" + revision);
                wrapper.appendChild(document.createTextNode("Runtime description"));

                template.clear(content);

                assertEquals(List.of(caption, back, title), wrapper.getChildNodes());
                assertSame(button.getElement(), back.getFirstChild());
                assertSame(boundText, title.getFirstChild());
                assertEquals("Bound title", title.getTextContent());
                assertNull(runtime.getParentNode());
                button.getElement().dispatchEvent(new Event(button.getElement(), "click"));
                assertEquals(revision + 1, clicks.get());
            }

            template.clear(back);
            assertTrue(back.getChildNodes().isEmpty(), "A region's owner can still clear its runtime children");
        });
    }

    @Test
    void adaptedInputWithATemplateIdSurvivesParentRefreshWithoutRevivingTheOldNode() {
        withDocument(document -> {
            var content = document.body.appendChild(document.createElement("div"));
            content.setAttribute("id", "manager-list");
            var original = content.appendChild(document.createElement("input"));
            original.setAttribute("id", "manager-search");
            original.setValue("Initial query");
            var template = new DlcUiTemplate(document);
            var adapted = DlcUiTextInput.adapt(original);
            adapted.setValue("Current query");

            for (int revision = 0; revision < 3; revision++) {
                var row = content.appendChild(document.createElement("div"));
                row.setAttribute("id", "runtime-row-" + revision);
                template.clear(content);
                assertEquals(List.of(adapted), content.getChildNodes());
                assertSame(adapted, document.getElementById("manager-search"));
                assertEquals("Current query", adapted.getValue());
                assertNull(original.getParentNode());
                assertNull(row.getParentNode());
            }

            adapted.remove();
            template.clear(content);
            assertTrue(content.getChildNodes().isEmpty());
            assertNull(original.getParentNode());
        });
    }

    @Test
    void slotsRejectDocumentRootsAndAncestorsOfOtherBindingRegions() {
        withDocument(document -> {
            document.documentElement.setAttribute("data-dlc-slot", "html-slot");
            document.body.setAttribute("data-dlc-slot", "body-slot");
            var main = document.body.appendChild(document.createElement("main"));
            main.setAttribute("data-dlc-slot", "page-prose");
            var content = main.appendChild(document.createElement("div"));
            content.setAttribute("id", "page-content");
            var back = main.appendChild(document.createElement("div"));
            back.setAttribute("id", "page-back");
            var later = document.body.appendChild(document.createElement("aside"));
            later.setAttribute("data-dlc-slot", "page-prose");
            var standalone = document.body.appendChild(document.createElement("aside"));
            standalone.setAttribute("id", "custom-actions");
            standalone.setAttribute("data-dlc-slot", "primary-action");
            var label = standalone.appendChild(document.createTextNode("Custom actions"));
            var template = new DlcUiTemplate(document);
            var tooltip = document.body.appendChild(document.createElement("div"));
            var backButton = back.appendChild(document.createElement("button"));

            assertSame(content, template.slot("html-slot", content));
            assertSame(content, template.slot("body-slot", content));
            assertSame(content, template.slot("page-prose", content),
                "An invalid first slot must not redirect to another duplicate");
            assertSame(standalone, template.slot("primary-action", content));
            var generated = standalone.appendChild(document.createElement("button"));

            template.clearSlots();

            assertEquals(List.of(label), standalone.getChildNodes());
            assertNull(generated.getParentNode());
            assertSame(document.body, tooltip.getParentNode());
            assertSame(back, backButton.getParentNode());
            assertTrue(later.getChildNodes().isEmpty());
        });
    }

    @Test
    void refreshClearsUsedExternalSlotsWithoutClearingUnusedCustomRegions() {
        withDocument(document -> {
            var content = document.body.appendChild(document.createElement("div"));
            var external = document.body.appendChild(document.createElement("aside"));
            external.setAttribute("data-dlc-slot", "primary-action");
            var label = external.appendChild(document.createTextNode("Static actions label"));
            var unused = document.body.appendChild(document.createElement("aside"));
            unused.setAttribute("data-dlc-slot", "unused");
            var template = new DlcUiTemplate(document);
            var customContent = unused.appendChild(document.createTextNode("Unmanaged content"));

            for (int revision = 0; revision < 3; revision++) {
                var target = template.slot("primary-action", content);
                var button = target.appendChild(document.createElement("button"));
                template.clear(content);
                assertSame(external, button.getParentNode());

                template.clearSlots();

                assertEquals(List.of(label), external.getChildNodes());
                assertSame(label, external.getFirstChild());
                assertNull(button.getParentNode());
                assertEquals(List.of(customContent), unused.getChildNodes());
            }
            external.appendChild(document.createTextNode("Not mounted through a slot"));
            template.clearSlots();
            assertEquals(2, external.getChildNodes().size());
        });
    }

    @Test
    void restoringClassesKeepsCurrentStateWithoutRestoringStylesOrDisabled() {
        withDocument(document -> {
            var region = document.body.appendChild(document.createElement("div"));
            region.setAttribute("class", "custom-panel\t shared custom-panel");
            region.setAttribute("style", "width:400px");
            var template = new DlcUiTemplate(document);
            region.setAttribute("class", "shared active");
            region.setAttribute("style", "width:172px;display:none");
            region.setAttribute("disabled", "");

            template.restoreAttributes(region);
            template.restoreAttributes(region);

            assertEquals("shared active custom-panel", region.getAttribute("class"));
            assertEquals(Set.of("shared", "active", "custom-panel"), region.getClassNames());
            assertEquals("width:172px;display:none", region.getAttribute("style"));
            assertTrue(region.hasAttribute("disabled"));

            region.setAttribute("class", "shared");
            region.removeAttribute("disabled");
            template.restoreAttributes(region);
            assertEquals("shared custom-panel", region.getAttribute("class"));
            assertFalse(region.hasAttribute("disabled"));
        });
    }
}
