package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerListScreen extends DlcPageScreen {


    private final Screen parent;
    private List<DlcServerEntry> servers = List.of();
    private int scrollIndex;
    private int selectedIndex = -1;
    private int lastClickIndex = -1;
    private long lastClickTime;
    private Button addButton;
    private Button connectButton;
    private Button deleteButton;
    private Button editButton;
    private Button backButton;
    private volatile boolean connecting;

    public DlcServerListScreen(Screen parent) {
        super(DlcUiPage.SERVER_LIST, parent, Component.translatable("dlc_manager.server.list_title"));
        this.parent = parent;
    }

    private void initControls() {
        DlcServerList.load();
        servers = new ArrayList<>(DlcServerList.getEntries());
        String lastKey = DlcServerList.getLastSelectedKey();
        if (lastKey != null) {
            for (int i = 0; i < servers.size(); i++) {
                DlcServerEntry entry = servers.get(i);
                if (entry != null && lastKey.equals(entry.getKey())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        int buttonWidth = 110;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int row1Y = this.height - 52;
        int row2Y = this.height - 28;
        int gap = 6;
        int row1Total = buttonWidth * 3 + gap * 2;
        int row2Total = buttonWidth * 2 + gap;
        int row1X = centerX - row1Total / 2;
        int row2X = centerX - row2Total / 2;
        deleteButton = Button.builder(Component.translatable("selectServer.delete"), btn -> deleteSelected())
            .bounds(row1X, row1Y, buttonWidth, buttonHeight)
            .build();
        editButton = Button.builder(Component.translatable("selectServer.edit"), btn -> editSelected())
            .bounds(row1X + buttonWidth + gap, row1Y, buttonWidth, buttonHeight)
            .build();
        addButton = Button.builder(Component.translatable("selectServer.add"), btn -> openAdd())
            .bounds(row1X + (buttonWidth + gap) * 2, row1Y, buttonWidth, buttonHeight)
            .build();
        connectButton = Button.builder(Component.translatable("dlc_manager.server.connect"), btn -> connectSelected())
            .bounds(row2X, row2Y, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> {
            if (parent != null) {
                Minecraft.getInstance().setScreen(parent);
                return;
            }
            Minecraft.getInstance().setScreen(new DlcManagerScreen(null));
        })
            .bounds(row2X + buttonWidth + gap, row2Y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(deleteButton);
        addRenderableWidget(editButton);
        addRenderableWidget(addButton);
        addRenderableWidget(connectButton);
        addRenderableWidget(backButton);
        updateButtonStates();
    }

    private void openAdd() {
        Minecraft.getInstance().setScreen(new DlcServerEditScreen(this, null, entry -> {
            servers.add(entry);
            DlcServerList.setEntries(servers);
            DlcServerList.save();
            selectedIndex = servers.size() - 1;
            updateButtonStates();
        }));
    }

    private void editSelected() {
        DlcServerEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new DlcServerEditScreen(this, entry, updated -> {
            if (selectedIndex >= 0 && selectedIndex < servers.size()) {
                servers.set(selectedIndex, updated);
                DlcServerList.setEntries(servers);
                DlcServerList.save();
            }
            updateButtonStates();
        }));
    }

    private void deleteSelected() {
        DlcServerEntry entry = getSelectedEntry();
        if (entry == null) {
            return;
        }
        Component title = Component.translatable("selectServer.deleteQuestion");
        Component message = Component.translatable("selectServer.deleteWarning", entry.getDisplayName());
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(result -> {
            if (result && selectedIndex >= 0 && selectedIndex < servers.size()) {
                servers.remove(selectedIndex);
                if (selectedIndex >= servers.size()) {
                    selectedIndex = servers.size() - 1;
                }
                DlcServerList.setEntries(servers);
                DlcServerList.save();
            }
            updateButtonStates();
            Minecraft.getInstance().setScreen(this);
        }, title, message));
    }

    private void connectSelected() {
        if (connecting) {
            return;
        }
        DlcServerEntry entry = getSelectedEntry();
        if (entry == null) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                Component.translatable("dlc_manager.server.select_required"), Component.empty());
            return;
        }
        connecting = true;
        updateButtonStates();
        CompletableFuture
            .supplyAsync(() -> fetchInfo(entry))
            .whenComplete((info, throwable) -> Minecraft.getInstance().execute(() -> {
                connecting = false;
                updateButtonStates();
                if (info == null) {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
                        Component.translatable("dlc_manager.server.connect_failed"), Component.empty());
                    return;
                }
                DlcServerCache.put(entry.getKey(), info);
                Minecraft.getInstance().setScreen(new DlcServerScreen(this, entry, info));
            }));
    }

    private DlcServerInfo fetchInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void updateButtonStates() {
        boolean hasSelection = getSelectedEntry() != null;
        if (addButton != null) addButton.active = !connecting;
        if (backButton != null) backButton.active = !connecting;
        if (deleteButton != null) {
            deleteButton.active = hasSelection && !connecting;
        }
        if (editButton != null) {
            editButton.active = hasSelection && !connecting;
        }
        if (connectButton != null) {
            connectButton.active = hasSelection && !connecting;
        }
    }
    private DlcServerEntry getSelectedEntry() {
        if (selectedIndex >= 0 && selectedIndex < servers.size()) {
            return servers.get(selectedIndex);
        }
        return null;
    }

    private String query = "";
    private java.util.Set<String> matchedServers = java.util.Set.of();
    private final java.util.Map<Integer, com.sighs.apricityui.init.Element> serverRows = new java.util.LinkedHashMap<>();
    @Override protected void buildPage() {
        initControls();
        input(toolbar, tr("dlc_manager.ui.search_servers"), query, "search", value -> { query = value; searchServers(); });
        mount(toolbar, addButton);
        searchServers();
        mount(footer, deleteButton).getElement().setAttribute("class", "button danger");
        mount(footer, editButton);

        mount(footer, connectButton).getElement().setAttribute("class", "button primary");
    }
    private void searchServers() {
        var entries = new java.util.ArrayList<DlcUiAsyncSearch.Entry<String>>();
        for (int i = 0; i < servers.size(); i++) {
            var entry = servers.get(i);
            if (entry != null) entries.add(new DlcUiAsyncSearch.Entry<>(String.valueOf(i), entry.getDisplayName(), entry.getKey()));
        }
        content.setAttribute("style", "pointer-events:none"); content.setAttribute("aria-busy", "true");
        filterAsync(query, entries, matches -> {
            matchedServers = matches; renderServers(); content.removeAttribute("style"); content.removeAttribute("aria-busy");
        });
    }
    private void renderServers() {
        content.setInnerHTML(""); serverRows.clear();
        for (int i = 0; i < servers.size(); i++) {
            DlcServerEntry entry = servers.get(i);
            if (entry == null || !matchedServers.contains(String.valueOf(i))) continue;
            int index = i;
            var row = row(content, entry.getDisplayName(), entry.getKey());
            row.setAttribute("class", "page-row server-row" + (index == selectedIndex ? " selected" : ""));
            row.setAttribute("tabindex", "0");
            row.setAttribute("role", "option");
            row.setAttribute("aria-selected", String.valueOf(index == selectedIndex));
            row.addEventListener("click", event -> {
                if (connecting) return;
                selectedIndex = index; DlcServerList.setLastSelectedKey(entry.getKey()); DlcServerList.save();
                updateButtonStates();
                serverRows.forEach((key, item) -> { item.setAttribute("class", "page-row server-row" + (key == selectedIndex ? " selected" : "")); item.setAttribute("aria-selected", String.valueOf(key == selectedIndex)); });
            });
            row.addEventListener("dblclick", event -> connectSelected());
            serverRows.put(index, row);
        }
        if (serverRows.isEmpty()) text(content, tr(query.isBlank() ? "dlc_manager.ui.empty_servers" : "dlc_manager.ui.no_results"), "empty-state");
    }
    @Override protected void updatePage() { updateButtonStates(); status(connecting ? tr("dlc_manager.server.connecting") : ""); }
    @Override public boolean shouldCloseOnEsc() { return !connecting; }
    @Override public void onClose() { if (connecting) return; if (pageParent == null) Minecraft.getInstance().setScreen(new DlcManagerScreen(null)); else super.onClose(); }

}
