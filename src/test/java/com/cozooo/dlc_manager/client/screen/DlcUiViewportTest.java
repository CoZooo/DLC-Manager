package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.viewport.ApricityViewport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DlcUiViewportTest {
    private static final String ROOT = "assets/apricityui/apricity/dlc_manager/ui/";
    private static final int[][] VIEWPORTS = {
        {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 360}, {854, 480}, {960, 540}
    };

    @Test
    void previousZoomValuesCannotMagnifyThePageOutsideTheGui() throws IOException {
        for (String template : new String[]{"manager", "page"}) {
            String path = "dlc_manager/ui/test_viewport_zoom_" + template + ".html";
            HTML.putTemple(path, resource(template + ".html"));
            try {
                ApricityViewport.State state = ApricityViewport.spec(path).createState();
                for (double previousZoom : new double[]{0.5, 1, 1.4, 2, 3}) {
                    state.setZoom(previousZoom);
                    for (int[] available : VIEWPORTS) {
                        ApricityViewport viewport = state.resolveHeadless(available[0], available[1]);
                        assertEquals(1, viewport.zoom());
                        assertEquals(available[0], viewport.layoutWidth() * viewport.renderScale(), 1);
                        assertEquals(available[1], viewport.layoutHeight() * viewport.renderScale(), 1);
                    }
                }
            } finally {
                HTML.putTemple(path, null);
            }
        }
    }

    @Test
    void managerKeepsItsActionsVisibleWithALongList() throws IOException {
        for (int[] viewport : VIEWPORTS) {
            withDocument("manager", viewport, document -> {
                addButton(document, "sidebar-toggle-slot", "«").setAttribute("class", "button sidebar-toggle");
                addButton(document, "batch-actions", "Enable All");
                addButton(document, "batch-actions", "Disable All");
                document.getElementById("manager-summary").setTextContent("60 items · 30 enabled");
                for (String category : new String[]{"All", "Mods", "Resource Packs", "Data Packs", "Other"}) {
                    addButton(document, "manager-filters", category).setAttribute("class", "button filter");
                }
                for (String tool : new String[]{"Presets", "Server Manager"}) {
                    addButton(document, "manager-tools", tool).setAttribute("class", "button nav-button");
                }
                Element list = document.getElementById("manager-list");
                for (int index = 0; index < 60; index++) {
                    Element row = list.appendChild(document.createElement("div"));
                    row.setAttribute("class", "dlc-row");
                    row.setTextContent("Example DLC " + index);
                }
                addButton(document, "manager-mode", "Apply Mode: Restart to Load");
                addButton(document, "manager-actions", "Apply changes");
                addButton(document, "manager-actions", "Done");
                assertFitsViewport(document, document.getElementById("manager"));
                assertFitsViewport(document, document.querySelector(".list-toolbar"));
                assertFitsViewport(document, document.querySelector(".workspace-footer"));
                assertFitsViewport(document, document.getElementById("manager-actions"));
                assertFitsViewport(document, document.getElementById("sidebar-content"));
                assertGuiCoordinates(document, document.getElementById("manager-actions"), viewport);
                assertTrue(list.getBoundingClientRect().height > 30, "The list needs a visible scroll area");
            });
        }
    }

    @Test
    void pageKeepsItsActionsVisibleWithLongContent() throws IOException {
        for (int[] viewport : VIEWPORTS) {
            withDocument("page", viewport, document -> {
                document.getElementById("page-title").setTextContent("DLC settings");
                Element content = document.getElementById("page-content");
                for (int index = 0; index < 60; index++) {
                    Element row = content.appendChild(document.createElement("div"));
                    row.setAttribute("class", "page-row");
                    row.setTextContent("Example setting " + index);
                }
                addButton(document, "page-actions", "Save");
                addButton(document, "page-actions", "Done");
                assertFitsViewport(document, document.querySelector(".page-shell"));
                assertFitsViewport(document, document.querySelector(".page-footer"));
                assertFitsViewport(document, document.getElementById("page-actions"));
                assertGuiCoordinates(document, document.getElementById("page-actions"), viewport);
                assertTrue(content.getBoundingClientRect().height > 30, "The page needs a visible scroll area");
            });
        }
    }

    private void withDocument(String template, int[] viewport, Consumer<Document> test) throws IOException {
        String path = "dlc_manager/ui/test_viewport_" + template + "_" + viewport[0] + "_" + viewport[1] + ".html";
        String markup = resource(template + ".html")
            .replace("<link rel=\"stylesheet\" href=\"manager.css\">", "")
            .replace("<link rel=\"stylesheet\" href=\"page.css\">", "");
        HTML.putTemple(path, markup);
        String oldWidth = System.getProperty("aui.test.viewport.width");
        String oldHeight = System.getProperty("aui.test.viewport.height");
        Document document = null;
        try {
            System.setProperty("aui.test.viewport.width", Integer.toString(viewport[0]));
            System.setProperty("aui.test.viewport.height", Integer.toString(viewport[1]));
            document = new Document(path, false);
            document.applyViewport(false);
            Document current = document;
            String stylesheet = resource("manager.css") + ("page".equals(template) ? resource("page.css") : "");
            Document.runWithContext(current, () -> {
                HTML.DocumentRoot root = HTML.create(current, path);
                assertNotNull(root);
                current.documentElement = root.documentElement();
                current.head = root.head();
                current.body = root.body();
                current.registerStylesheet(stylesheet, path, 0);
                test.accept(current);
            });
        } finally {
            if (document != null) document.remove();
            restoreProperty("aui.test.viewport.width", oldWidth);
            restoreProperty("aui.test.viewport.height", oldHeight);
            HTML.putTemple(path, null);
        }
    }

    private void assertFitsViewport(Document document, Element element) {
        assertNotNull(element);
        Element.DOMRect bounds = element.getBoundingClientRect();
        String context = document.getPath() + " " + element.getAttribute("class");
        assertTrue(bounds.width > 0 && bounds.height > 0, context + " has no visible size");
        assertTrue(bounds.x >= -1 && bounds.y >= -1, context + " starts outside the viewport");
        assertTrue(bounds.x + bounds.width <= document.getViewport().layoutWidth() + 1, context + " overflows horizontally");
        assertTrue(bounds.y + bounds.height <= document.getViewport().layoutHeight() + 1, context + " overflows vertically");
    }

    private Element addButton(Document document, String container, String label) {
        Element button = document.getElementById(container).appendChild(document.createElement("button"));
        button.setAttribute("class", "button");
        button.setTextContent(label);
        return button;
    }

    private void assertGuiCoordinates(Document document, Element element, int[] available) {
        Element.DOMRect bounds = element.getBoundingClientRect();
        Position point = new Position(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
        Position gui = document.documentToScreenPosition(point);
        assertTrue(gui.x >= 0 && gui.x <= available[0], "The button must render inside the GUI width");
        assertTrue(gui.y >= 0 && gui.y <= available[1], "The button must render inside the GUI height");
        Position restored = document.screenToDocumentPosition(gui);
        assertEquals(point.x, restored.x, 0.001);
        assertEquals(point.y, restored.y, 0.001);
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
