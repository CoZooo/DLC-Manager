package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DlcUiTemplate {
    private final Document document;
    private final Map<Node, Node> parents = new IdentityHashMap<>();
    private final Map<Element, Set<String>> classes = new IdentityHashMap<>();
    private final Map<String, Element> slots = new LinkedHashMap<>();
    private final Set<String> regionIds = new LinkedHashSet<>();
    private final Set<Element> regions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Element> nonSlotContainers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Element> usedSlots = Collections.newSetFromMap(new IdentityHashMap<>());

    public DlcUiTemplate(Document document) {
        this.document = Objects.requireNonNull(document);
        if (document.getDocumentElement() != null) capture(document.getDocumentElement());
        for (Node node : new ArrayList<>(document.getNodes())) capture(node);
        if (document.getDocumentElement() != null) nonSlotContainers.add(document.getDocumentElement());
        if (document.body != null) nonSlotContainers.add(document.body);
        for (Element region : regions) {
            for (Node parent = parents.get(region); parent != null; parent = parents.get(parent)) {
                if (parent instanceof Element element) nonSlotContainers.add(element);
            }
        }
    }

    private void capture(Node node) {
        if (parents.containsKey(node)) return;
        parents.put(node, node.getParentNode());
        if (node instanceof Element element) {
            classes.put(element, tokens(element.getAttribute("class")));
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                regionIds.add(id);
                regions.add(element);
            }
            String name = element.getAttribute("data-dlc-slot");
            if (name != null && !name.isBlank()) slots.putIfAbsent(name, element);
        }
        for (Node child : new ArrayList<>(node.getChildNodes())) capture(child);
    }

    public void clear(Element parent) {
        clearChildren(parent);
    }

    private void clearChildren(Node parent) {
        for (Node child : new ArrayList<>(parent.getChildNodes())) {
            if (child instanceof Element element && regionIds.contains(element.getAttribute("id"))) continue;
            if (!parents.containsKey(child) || parents.get(child) != parent) {
                parent.removeChild(child);
            } else {
                clearChildren(child);
            }
        }
    }

    public Element slot(String name, Element fallback) {
        Element element = slots.get(name);
        if (element != null && !nonSlotContainers.contains(element)
            && element.document == document && element.isConnected()
            && name.equals(element.getAttribute("data-dlc-slot"))) {
            usedSlots.add(element);
            return element;
        }
        return fallback;
    }

    public void clearSlots() {
        for (Element element : new ArrayList<>(usedSlots)) clear(element);
        usedSlots.clear();
    }

    public void restoreAttributes(Element element) {
        Set<String> original = classes.get(element);
        if (original == null || original.isEmpty()) return;
        Set<String> merged = tokens(element.getAttribute("class"));
        if (merged.addAll(original)) element.setAttribute("class", String.join(" ", merged));
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) result.addAll(List.of(value.trim().split("\\s+")));
        return result;
    }
}
