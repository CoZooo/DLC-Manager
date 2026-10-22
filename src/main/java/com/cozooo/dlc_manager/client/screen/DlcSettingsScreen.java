package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import com.cozooo.dlc_manager.client.config.DlcConfigValue;
import com.cozooo.dlc_manager.core.DlcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.sighs.apricityui.init.Element;
import java.util.*;
public abstract class DlcSettingsScreen extends DlcPageScreen {
    protected final Map<String, List<DlcConfigValue>> categories;
    private final Map<String, String> original = new LinkedHashMap<>();
    private final Map<String, String> drafts = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private String category;
    private String query = "";
    private long categoryTransitionRevision;
    private Element list;
    private Element noResults;
    private boolean emptyResults;
    private final Map<String, OptionRow> optionRows = new LinkedHashMap<>();
    private final Map<String, DlcUiButton> categoryButtons = new LinkedHashMap<>();
    private static final class OptionRow {
        final DlcConfigValue value;
        final Element row, control;
        final String label, comment;
        boolean visible = true;
        OptionRow(DlcConfigValue value, Element row, Element control, String label, String comment) {
            this.value = value; this.row = row; this.control = control; this.label = label; this.comment = comment;
        }
    }
    protected boolean busy;
    private DlcUiButton saveButton;
    private Boolean locked;
    protected DlcSettingsScreen(Screen parent, Component title, Map<String, List<DlcConfigValue>> categories, String category) {
        this(DlcUiPage.PAGE, parent, title, categories, category);
    }
    protected DlcSettingsScreen(DlcUiPage page, Screen parent, Component title, Map<String, List<DlcConfigValue>> categories, String category) {
        super(page, parent, title); this.categories = categories; this.category = category;
        captureValues();
    }
    protected final void captureValues() {
        original.clear(); drafts.clear(); errors.clear();
        for (var configs : categories.values()) for (var base : configs) original.put(base.getName(), base.getStringValue());
        drafts.putAll(original);
    }
    @Override protected void buildPage() {
        locked = null;
        content.setAttribute("class", "settings-content");
        var nav = node(content, "nav", "settings-nav");
        categoryButtons.clear();
        for (String key : categories.keySet()) {
            var button = action(nav, tr(key), () -> selectCategory(key));
            button.getElement().setAttribute("class", "button filter" + (key.equals(category) ? " active" : ""));
            categoryButtons.put(key, button);
        }
        var workspace = node(content, "div", "settings-workspace");
        input(workspace, tr("dlc_manager.ui.search_settings"), query, "search", value -> { query = value; renderOptions(); });
        list = node(workspace, "div", "settings-list");
        optionRows.clear();
        for (var configs : categories.values()) for (var value : configs) createOption(value);
        noResults = text(list, tr("dlc_manager.ui.no_results"), "empty-state");
        emptyResults = true;
        renderOptions();
        action(toolbar, tr("dlc_manager.config.check_update"), () -> Minecraft.getInstance().setScreen(new DlcSelfUpdateScreen(this, null)));
        action(footer, tr("dlc_manager.config.cancel"), this::onClose);
        saveButton = primary(tr("dlc_manager.config.save"), this::save);
        updatePage();
    }
    private void selectCategory(String key) {
        if (key.equals(category)) return;
        var previous = categoryButtons.get(category);
        if (previous != null) previous.getElement().setAttribute("class", "button filter");
        category = key;
        categoryButtons.get(key).getElement().setAttribute("class", "button filter active");
        renderOptions(++categoryTransitionRevision);
    }
    private void renderOptions() {
        renderOptions(-1);
    }
    private void renderOptions(long transitionRevision) {
        var entries = optionRows.values().stream().map(option -> new DlcUiAsyncSearch.Entry<>(option.value.getName(), option.label, option.value.getName(), option.comment)).toList();
        list.setAttribute("aria-busy", "true");
        list.getStyle().setProperty("pointer-events", "none");
        filterAsync(query, entries, matches -> {
            if (transitionRevision == categoryTransitionRevision && query.isBlank()) {
                captureUiChange(list);
                list.setScrollTop(0);
            }
            Set<String> categoryNames = new HashSet<>();
            if (query.isBlank()) for (var value : categories.getOrDefault(category, List.of())) categoryNames.add(value.getName());
            int count = 0;
            for (var option : optionRows.values()) {
                boolean shown = query.isBlank() ? categoryNames.contains(option.value.getName()) : matches.contains(option.value.getName());
                if (option.visible != shown) {
                    option.visible = shown;
                    option.row.setAttribute("style", shown ? "" : "display:none");
                }
                if (shown) count++;
            }
            boolean empty = count == 0;
            if (empty != emptyResults) {
                emptyResults = empty;
                noResults.setAttribute("style", empty ? "" : "display:none");
            }
            list.removeAttribute("aria-busy");
            list.getStyle().removeProperty("pointer-events");
        });
    }
    private void createOption(DlcConfigValue value) {
        String label = value.getConfigGuiDisplayName();
        var line = row(list, label, value.getComment());
        var control = node(line, "div", "settings-control");
        var option = new OptionRow(value, line, control, label, value.getComment());
        optionRows.put(value.getName(), option);
        DlcUiSearch.warmup(label, value.getName(), value.getComment());
        updateOption(option);
        var reset = action(line, tr("dlc_manager.config.reset"), () -> {
            drafts.put(value.getName(), value.getDefaultStringValue());
            errors.remove(value.getName()); updateOption(option); updatePage();
        });
        reset.getElement().setAttribute("class", "button quiet");
    }
    private void updateOption(OptionRow option) {
        var value = option.value;
        var control = option.control;
        control.setInnerHTML("");
        String draft = drafts.get(value.getName());
        if (value instanceof DlcConfigValue.BooleanValue) {
            boolean enabled = Boolean.parseBoolean(draft);
            toggle(control, tr(enabled ? "dlc_manager.toggle.on" : "dlc_manager.toggle.off"), enabled, next -> {
                drafts.put(value.getName(), next.toString()); updateOption(option); updatePage();
            });
        } else {
            String[] choices = options(value.getName());
            if (choices.length > 0) {
                Element select = node(control, "select", "page-input");
                select.setAttribute("aria-label", option.label);
                for (String choice : choices) {
                    Element item = node(select, "option", ""); item.setAttribute("value", choice);
                    String key = "dlc_manager.config." + (value.getName().startsWith("download") ? "download." : "ui.") + value.getName() + "." + choice;
                    String translated = tr(key); item.setTextContent(translated.equals(key) ? choice : translated);
                }
                select.setValue(draft);
                select.addEventListener("change", event -> { drafts.put(value.getName(), select.getValue()); updatePage(); });
            } else {
                Element field = input(control, "", draft, value.getName().toLowerCase(Locale.ROOT).contains("password") || value.getName().toLowerCase(Locale.ROOT).contains("token") ? "password" : "text", next -> {
                    drafts.put(value.getName(), next); validate(value, next); updatePage();
                });
                field.setAttribute("aria-label", option.label);
            }
        }
    }
    private void validate(DlcConfigValue value, String input) {
        errors.remove(value.getName());
        if (value instanceof DlcConfigValue.IntegerValue number) {
            try {
                int parsed = Integer.parseInt(input.trim());
                if (parsed < number.getMinIntegerValue() || parsed > number.getMaxIntegerValue()) throw new NumberFormatException();
            } catch (NumberFormatException ignored) { errors.put(value.getName(), tr("dlc_manager.ui.number_range", value.getConfigGuiDisplayName(), number.getMinIntegerValue(), number.getMaxIntegerValue())); }
        }
    }
    protected abstract void persist();
    private void save() {
        if (busy || !errors.isEmpty()) return;
        for (var configs : categories.values()) for (var base : configs) base.setValueFromString(drafts.get(base.getName()));
        persist();
    }
    @Override protected void updatePage() {
        if (locked == null || locked != busy) {
            locked = busy;
            for (var element : pageDocument.querySelectorAll("input, select, button")) {
                if (saveButton != null && element == saveButton.getElement()) continue;
                if (busy) element.setAttribute("disabled", ""); else element.removeAttribute("disabled");
            }
        }
        if (saveButton != null) saveButton.setEnabled(!busy && errors.isEmpty() && !drafts.equals(original));
        if (!errors.isEmpty()) status(errors.values().iterator().next());
        else if (!busy) status(drafts.equals(original) ? "" : tr("dlc_manager.ui.unsaved"));
    }
    @Override public void onClose() {
        if (busy) return;
        if (drafts.equals(original)) { super.onClose(); return; }
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                for (var configs : categories.values()) for (var base : configs) base.setValueFromString(original.get(base.getName()));
                Minecraft.getInstance().setScreen(pageParent);
            }
        }, DlcTextCompat.translatable("dlc_manager.config.cancel_title"), DlcTextCompat.translatable("dlc_manager.config.cancel_prompt"), DlcTextCompat.translatable("dlc_manager.config.cancel_yes"), DlcTextCompat.translatable("dlc_manager.config.cancel_no")));
    }
    private static String[] options(String name) {
        if (name.endsWith("Anchor")) return name.equals("titleAnchor") ? new String[]{"auto", DlcConfig.TITLE_ANCHOR_HIDDEN, "top_left","top","top_right","left","center","right","bottom_left","bottom","bottom_right"} : name.equals("titleUpdateAnchor") ? new String[]{"auto","top_left","top","top_right","left","center","right","bottom_left","bottom","bottom_right"} : new String[]{"top_left","top","top_right","left","center","right","bottom_left","bottom","bottom_right"};
        if (name.equals("noticeDisplayMode")) return new String[]{"manager_button","main_menu_button","main_menu_panel"};
        if (name.equals("downloadMirrorMode")) return new String[]{"official_only","mirror_only","official_first","mirror_first"};
        return new String[0];
    }
    @Override public boolean shouldCloseOnEsc() { return !busy; }
}
