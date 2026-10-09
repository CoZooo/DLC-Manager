package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.init.Node
import java.util.Collections
import java.util.IdentityHashMap

class DlcUiTemplate(private val document: Document) {
    private val parents = IdentityHashMap<Node, Node?>()
    private val classes = IdentityHashMap<Element, Set<String>>()
    private val slots = linkedMapOf<String, Element>()
    private val regionIds = linkedSetOf<String>()
    private val regions: MutableSet<Element> = Collections.newSetFromMap(IdentityHashMap())
    private val nonSlotContainers: MutableSet<Element> = Collections.newSetFromMap(IdentityHashMap())
    private val usedSlots: MutableSet<Element> = Collections.newSetFromMap(IdentityHashMap())

    init {
        document.getDocumentElement()?.let(::capture)
        document.getNodes().toList().forEach(::capture)
        document.getDocumentElement()?.let(nonSlotContainers::add)
        document.body?.let(nonSlotContainers::add)
        for (region in regions) {
            var parent = parents[region]
            while (parent != null) {
                if (parent is Element) nonSlotContainers.add(parent)
                parent = parents[parent]
            }
        }
    }

    private fun capture(node: Node) {
        if (parents.containsKey(node)) return
        parents[node] = node.getParentNode()
        if (node is Element) {
            classes[node] = tokens(node.getAttribute("class"))
            val id = node.getAttribute("id")
            if (!id.isNullOrBlank()) {
                regionIds.add(id)
                regions.add(node)
            }
            val name = node.getAttribute("data-dlc-slot")
            if (!name.isNullOrBlank()) slots.putIfAbsent(name, node)
        }
        node.getChildNodes().toList().forEach(::capture)
    }

    fun clear(parent: Element) {
        clearChildren(parent)
    }

    private fun clearChildren(parent: Node) {
        for (child in parent.getChildNodes().toList()) {
            if (child is Element && regionIds.contains(child.getAttribute("id"))) continue
            if (!parents.containsKey(child) || parents[child] !== parent) {
                parent.removeChild(child)
            } else {
                clearChildren(child)
            }
        }
    }

    fun slot(name: String, fallback: Element): Element {
        val element = slots[name]
        if (element != null && !nonSlotContainers.contains(element)
            && element.document === document && element.isConnected
            && name == element.getAttribute("data-dlc-slot")) {
            usedSlots.add(element)
            return element
        }
        return fallback
    }

    fun clearSlots() {
        usedSlots.toList().forEach(::clear)
        usedSlots.clear()
    }

    fun restoreAttributes(element: Element) {
        val original = classes[element] ?: return
        if (original.isEmpty()) return
        val merged = tokens(element.getAttribute("class"))
        if (merged.addAll(original)) element.setAttribute("class", merged.joinToString(" "))
    }

    private fun tokens(value: String?): LinkedHashSet<String> = linkedSetOf<String>().also { result ->
        if (!value.isNullOrBlank()) result.addAll(value.trim().split(Regex("\\s+")))
    }
}
