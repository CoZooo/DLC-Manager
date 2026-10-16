package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.server.DlcServerCache
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerList
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerListScreen(private val parent: Screen?) : DlcPageScreen(DlcUiPage.SERVER_LIST, parent, DlcTextCompat.translatable("dlc_manager.server.list_title")) {
    private var servers: MutableList<DlcServerEntry> = ArrayList()
    private var scrollIndex = 0
    private var selectedIndex = -1
    private var lastClickIndex = -1
    private var lastClickTime = 0L
    private var addButton: Button? = null
    private var connectButton: Button? = null
    private var deleteButton: Button? = null
    private var editButton: Button? = null
    private var backButton: Button? = null
    @Volatile
    private var connecting = false

    private fun initControls() {
        DlcServerList.load()
        servers = ArrayList(DlcServerList.getEntries())
        val lastKey = DlcServerList.getLastSelectedKey()
        if (lastKey != null) {
            for (i in servers.indices) {
                val entry = servers[i]
                if (lastKey == entry.getKey()) {
                    selectedIndex = i
                    break
                }
            }
        }
        val buttonWidth = 110
        val buttonHeight = 20
        val centerX = width / 2
        val row1Y = height - 52
        val row2Y = height - 28
        val gap = 6
        val row1Total = buttonWidth * 3 + gap * 2
        val row2Total = buttonWidth * 2 + gap
        val row1X = centerX - row1Total / 2
        val row2X = centerX - row2Total / 2
        val delete = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("selectServer.delete")) { deleteSelected() }
            .bounds(row1X, row1Y, buttonWidth, buttonHeight)
            .build()
        val edit = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("selectServer.edit")) { editSelected() }
            .bounds(row1X + buttonWidth + gap, row1Y, buttonWidth, buttonHeight)
            .build()
        val add = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("selectServer.add")) { openAdd() }
            .bounds(row1X + (buttonWidth + gap) * 2, row1Y, buttonWidth, buttonHeight)
            .build()
        val connect = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.connect")) { connectSelected() }
            .bounds(row2X, row2Y, buttonWidth, buttonHeight)
            .build()
        val back = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back")) {
            if (parent != null) {
                Minecraft.getInstance().setScreen(parent)
                return@buttonBuilder
            }
            Minecraft.getInstance().setScreen(DlcManagerScreen(null))
        }
            .bounds(row2X + buttonWidth + gap, row2Y, buttonWidth, buttonHeight)
            .build()
        deleteButton = delete
        editButton = edit
        addButton = add
        connectButton = connect
        backButton = back
        addRenderableWidget(delete)
        addRenderableWidget(edit)
        addRenderableWidget(add)
        addRenderableWidget(connect)
        addRenderableWidget(back)
        updateButtonStates()
    }

    private fun openAdd() {
        Minecraft.getInstance().setScreen(
            DlcServerEditScreen(this, null) { entry ->
                servers.add(entry)
                DlcServerList.setEntries(servers)
                DlcServerList.save()
                selectedIndex = servers.size - 1
                updateButtonStates()
            },
        )
    }

    private fun editSelected() {
        val entry = selectedEntry ?: return
        Minecraft.getInstance().setScreen(
            DlcServerEditScreen(this, entry) { updated ->
                if (selectedIndex >= 0 && selectedIndex < servers.size) {
                    servers[selectedIndex] = updated
                    DlcServerList.setEntries(servers)
                    DlcServerList.save()
                }
                updateButtonStates()
            },
        )
    }

    private fun deleteSelected() {
        val entry = selectedEntry ?: return
        val title = DlcTextCompat.translatable("selectServer.deleteQuestion")
        val message = DlcTextCompat.translatable("selectServer.deleteWarning", entry.getDisplayName())
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen({ result ->
                if (result && selectedIndex >= 0 && selectedIndex < servers.size) {
                    servers.removeAt(selectedIndex)
                    if (selectedIndex >= servers.size) {
                        selectedIndex = servers.size - 1
                    }
                    DlcServerList.setEntries(servers)
                    DlcServerList.save()
                }
                updateButtonStates()
                Minecraft.getInstance().setScreen(this)
            }, title, message),
        )
    }

    private fun connectSelected() {
        if (connecting) {
            return
        }
        val entry = selectedEntry
        if (entry == null) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                DlcScreenCompat.toastManager(Minecraft.getInstance()),
                DlcTextCompat.translatable("dlc_manager.server.select_required"),
                DlcTextCompat.empty(),
            )
            return
        }
        connecting = true
        updateButtonStates()
        CompletableFuture
            .supplyAsync { fetchInfo(entry) }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    connecting = false
                    updateButtonStates()
                    if (info == null) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            DlcScreenCompat.toastManager(Minecraft.getInstance()),
                            DlcTextCompat.translatable("dlc_manager.server.connect_failed"),
                            DlcTextCompat.empty(),
                        )
                        return@execute
                    }
                    DlcServerCache.put(entry.getKey(), info)
                    Minecraft.getInstance().setScreen(DlcServerScreen(this, entry, info))
                }
            }
    }

    private fun fetchInfo(entry: DlcServerEntry): DlcServerInfo? {
        return try {
            DlcServerConnection.fetchServerInfo(entry, 5000)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateButtonStates() {
        val hasSelection = selectedEntry != null
        deleteButton?.active = hasSelection && !connecting
        editButton?.active = hasSelection && !connecting
        connectButton?.active = hasSelection && !connecting
        addButton?.active = !connecting
        backButton?.active = !connecting
    }

    private val selectedEntry: DlcServerEntry?
        get() = if (selectedIndex >= 0 && selectedIndex < servers.size) servers[selectedIndex] else null


    private var query = ""
    private var matchedServers = emptySet<String>()
    private val serverRows = linkedMapOf<Int, com.sighs.apricityui.init.Element>()
    override fun buildPage() {
        initControls()
        input(toolbar, tr("dlc_manager.ui.search_servers"), query, "search") { query = it; searchServers() }
        addButton?.let { mount(toolbar, it) }
        searchServers()
        deleteButton?.let { mount(footer, it).getElement().setAttribute("class", "button danger") }
        editButton?.let { mount(footer, it) }
        connectButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
    private fun searchServers() {
        val entries = servers.mapIndexed { index, entry -> DlcUiAsyncSearch.Entry(index.toString(), entry.getDisplayName(), entry.getKey()) }
        content.setAttribute("style", "pointer-events:none"); content.setAttribute("aria-busy", "true")
        filterAsync(query, entries) { matches ->
            matchedServers = matches; renderServers(); content.removeAttribute("style"); content.removeAttribute("aria-busy")
        }
    }
    private fun renderServers() {
        content.setInnerHTML(""); serverRows.clear()
        for ((index, entry) in servers.withIndex()) {
            if (index.toString() !in matchedServers) continue
            val row = row(content, entry.getDisplayName(), entry.getKey())
            row.setAttribute("class", "page-row server-row" + if (index == selectedIndex) " selected" else "")
            row.setAttribute("tabindex", "0"); row.setAttribute("role", "option"); row.setAttribute("aria-selected", (index == selectedIndex).toString())
            row.addEventListener("click") {
                if (connecting) return@addEventListener
                selectedIndex = index; DlcServerList.setLastSelectedKey(entry.getKey()); DlcServerList.save(); updateButtonStates()
                serverRows.forEach { (key, item) -> item.setAttribute("class", "page-row server-row" + if (key == selectedIndex) " selected" else ""); item.setAttribute("aria-selected", (key == selectedIndex).toString()) }
            }
            row.addEventListener("dblclick") { connectSelected() }
            serverRows[index] = row
        }
        if (serverRows.isEmpty()) text(content, tr(if (query.isBlank()) "dlc_manager.ui.empty_servers" else "dlc_manager.ui.no_results"), "empty-state")
    }
    override fun updatePage() { updateButtonStates(); status(if (connecting) tr("dlc_manager.server.connecting") else "") }
    override fun shouldCloseOnEsc() = !connecting
    override fun onClose() { if (connecting) return; if (pageParent == null) Minecraft.getInstance().setScreen(DlcManagerScreen(null)) else super.onClose() }

}
