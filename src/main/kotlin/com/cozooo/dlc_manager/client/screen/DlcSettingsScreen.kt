package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat
import com.cozooo.dlc_manager.client.config.DlcConfigValue
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.sighs.apricityui.init.Element
abstract class DlcSettingsScreen(page: DlcUiPage, parent: Screen?, title: Component, protected val categories: Map<String, List<DlcConfigValue>>, private var category: String) : DlcPageScreen(page, parent, title) {
    protected constructor(parent: Screen?, title: Component, categories: Map<String, List<DlcConfigValue>>, category: String) : this(DlcUiPage.PAGE, parent, title, categories, category)
    private val original = linkedMapOf<String, String>()
    private val drafts = linkedMapOf<String, String>()
    private val errors = linkedMapOf<String, String>()
    private var query = ""
    private var categoryTransitionRevision = 0L
    private lateinit var list: Element
    private lateinit var noResults: Element
    private var emptyResults = false
    private val optionRows = linkedMapOf<String, OptionRow>()
    private val categoryButtons = linkedMapOf<String, DlcUiButton>()
    private class OptionRow(val value: DlcConfigValue, val row: Element, val control: Element, val label: String, val comment: String?) {
        var visible = true
    }
    protected var busy = false
    private var saveButton: DlcUiButton? = null
    private var locked: Boolean? = null
    init { captureValues() }
    protected fun captureValues() {
        original.clear(); drafts.clear(); errors.clear()
        categories.values.flatten().forEach { original[it.name] = it.stringValue }
        drafts.putAll(original)
    }
    override fun buildPage() {
        locked = null
        content.setAttribute("class", "settings-content")
        val nav = node(content, "nav", "settings-nav")
        categoryButtons.clear()
        for (key in categories.keys) {
            val button = action(nav, tr(key)) { selectCategory(key) }
            button.getElement().setAttribute("class", "button filter" + if (key == category) " active" else "")
            categoryButtons[key] = button
        }
        val workspace = node(content, "div", "settings-workspace")
        input(workspace, tr("dlc_manager.ui.search_settings"), query, "search") { query = it; renderOptions() }
        list = node(workspace, "div", "settings-list")
        optionRows.clear()
        categories.values.flatten().forEach { createOption(it) }
        noResults = text(list, tr("dlc_manager.ui.no_results"), "empty-state")
        emptyResults = true
        renderOptions()
        action(toolbar, tr("dlc_manager.config.check_update")) { Minecraft.getInstance().setScreen(DlcSelfUpdateScreen(this, null)) }
        action(footer, tr("dlc_manager.config.cancel")) { onClose() }
        saveButton = primary(tr("dlc_manager.config.save")) { save() }
        updatePage()
    }
    private fun selectCategory(key: String) {
        if (key == category) return
        categoryButtons[category]?.getElement()?.setAttribute("class", "button filter")
        category = key
        categoryButtons[key]?.getElement()?.setAttribute("class", "button filter active")
        renderOptions(++categoryTransitionRevision)
    }
    private fun renderOptions(transitionRevision: Long = -1L) {
        val entries = optionRows.values.map { DlcUiAsyncSearch.Entry(it.value.name, it.label, it.value.name, it.comment) }
        list.setAttribute("aria-busy", "true")
        list.getStyle().setProperty("pointer-events", "none")
        filterAsync(query, entries) { matches ->
            if (transitionRevision == categoryTransitionRevision && query.isBlank()) {
                captureUiChange(list)
                list.scrollTop = 0.0
            }
            val categoryNames = if (query.isBlank()) categories[category].orEmpty().map { it.name }.toSet() else emptySet()
            var count = 0
            for (option in optionRows.values) {
                val shown = if (query.isBlank()) option.value.name in categoryNames else option.value.name in matches
                if (option.visible != shown) {
                    option.visible = shown
                    option.row.setAttribute("style", if (shown) "" else "display:none")
                }
                if (shown) count++
            }
            val empty = count == 0
            if (empty != emptyResults) {
                emptyResults = empty
                noResults.setAttribute("style", if (empty) "" else "display:none")
            }
            list.removeAttribute("aria-busy")
            list.getStyle().removeProperty("pointer-events")
        }
    }
    private fun createOption(value: DlcConfigValue) {
        val label = value.configGuiDisplayName
        val line = row(list, label, value.comment)
        val control = node(line, "div", "settings-control")
        val option = OptionRow(value, line, control, label, value.comment)
        optionRows[value.name] = option
        DlcUiSearch.warmup(label, value.name, value.comment)
        updateOption(option)
        action(line, tr("dlc_manager.config.reset")) {
            drafts[value.name] = value.defaultStringValue
            errors.remove(value.name); updateOption(option); updatePage()
        }.getElement().setAttribute("class", "button quiet")
    }
    private fun updateOption(option: OptionRow) {
        val value = option.value
        val control = option.control
        control.setInnerHTML("")
        val draft = drafts[value.name].orEmpty()
        if (value is DlcConfigValue.BooleanValue) {
            val enabled = draft.toBoolean()
            toggle(control, tr(if (enabled) "dlc_manager.toggle.on" else "dlc_manager.toggle.off"), enabled) {
                drafts[value.name] = it.toString(); updateOption(option); updatePage()
            }
        } else {
            val choices = options(value.name)
            if (choices.isNotEmpty()) {
                val select = node(control, "select", "page-input")
                select.setAttribute("aria-label", option.label)
                for (choice in choices) {
                    val item = node(select, "option", "")
                    item.setAttribute("value", choice)
                    val key = "dlc_manager.config." + (if (value.name.startsWith("download")) "download." else "ui.") + value.name + "." + choice
                    val translated = tr(key)
                    item.setTextContent(if (translated == key) choice else translated)
                }
                select.setValue(draft)
                select.addEventListener("change") { drafts[value.name] = select.getValue(); updatePage() }
            } else {
                val field = input(control, "", draft, if (value.name.lowercase().contains("password") || value.name.lowercase().contains("token")) "password" else "text") {
                    drafts[value.name] = it; validate(value, it); updatePage()
                }
                field.setAttribute("aria-label", option.label)
            }
        }
    }
    private fun validate(value: DlcConfigValue, input: String) {
        errors.remove(value.name)
        if (value is DlcConfigValue.IntegerValue) {
            val parsed = input.trim().toIntOrNull()
            if (parsed == null || parsed !in value.minIntegerValue..value.maxIntegerValue) errors[value.name] = tr("dlc_manager.ui.number_range", value.configGuiDisplayName, value.minIntegerValue, value.maxIntegerValue)
        }
    }
    protected abstract fun persist()
    private fun save() {
        if (busy || errors.isNotEmpty()) return
        categories.values.flatten().forEach { it.setValueFromString(drafts[it.name].orEmpty()) }
        persist()
    }
    override fun updatePage() {
        if (locked != busy) {
            locked = busy
            for (element in pageDocument.querySelectorAll("input, select, button")) {
                if (element === saveButton?.getElement()) continue
                if (busy) element.setAttribute("disabled", "") else element.removeAttribute("disabled")
            }
        }
        saveButton?.setEnabled(!busy && errors.isEmpty() && drafts != original)
        if (errors.isNotEmpty()) status(errors.values.first()) else if (!busy) status(if (drafts == original) "" else tr("dlc_manager.ui.unsaved"))
    }
    override fun onClose() {
        if (busy) return
        if (drafts == original) { super.onClose(); return }
        Minecraft.getInstance().setScreen(DlcConfirmScreen({ accepted ->
            Minecraft.getInstance().setScreen(this)
            if (accepted) {
                categories.values.flatten().forEach { it.setValueFromString(original[it.name].orEmpty()) }
                Minecraft.getInstance().setScreen(pageParent)
            }
        }, DlcTextCompat.translatable("dlc_manager.config.cancel_title"), DlcTextCompat.translatable("dlc_manager.config.cancel_prompt"), DlcTextCompat.translatable("dlc_manager.config.cancel_yes"), DlcTextCompat.translatable("dlc_manager.config.cancel_no")))
    }
    private fun options(name: String): List<String> = when {
        name.endsWith("Anchor") -> (if (name == "titleAnchor") listOf("auto", DlcConfig.TITLE_ANCHOR_HIDDEN) else if (name == "titleUpdateAnchor") listOf("auto") else emptyList()) + listOf("top_left","top","top_right","left","center","right","bottom_left","bottom","bottom_right")
        name == "noticeDisplayMode" -> listOf("manager_button","main_menu_button","main_menu_panel")
        name == "downloadMirrorMode" -> listOf("official_only","mirror_only","official_first","mirror_first")
        else -> emptyList()
    }
    override fun shouldCloseOnEsc() = !busy
}
