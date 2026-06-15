package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.server.DlcServerCache
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerList
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DlcServerListScreen(private val parent: Screen?) :
    DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.list_title")) {
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

    override fun init() {
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
        val delete = Button.builder(Component.translatable("selectServer.delete")) { deleteSelected() }
            .bounds(row1X, row1Y, buttonWidth, buttonHeight)
            .build()
        val edit = Button.builder(Component.translatable("selectServer.edit")) { editSelected() }
            .bounds(row1X + buttonWidth + gap, row1Y, buttonWidth, buttonHeight)
            .build()
        val add = Button.builder(Component.translatable("selectServer.add")) { openAdd() }
            .bounds(row1X + (buttonWidth + gap) * 2, row1Y, buttonWidth, buttonHeight)
            .build()
        val connect = Button.builder(Component.translatable("dlc_manager.server.connect")) { connectSelected() }
            .bounds(row2X, row2Y, buttonWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) {
            if (parent != null) {
                Minecraft.getInstance().setScreen(parent)
                return@builder
            }
            Minecraft.getInstance().setScreen(DlcMaLiLibScreen(null))
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

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        guiGraphics.drawCenteredString(font, title, centerX, 20, 0xFFFFFF)
        val listTop = 50
        val listBottom = height - 70
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val totalLines = servers.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var rowY = listTop
        for (i in servers.indices) {
            val entry = servers[i]
            val visible = i >= scrollIndex && i < scrollIndex + maxVisible
            if (!visible) {
                continue
            }
            val selected = i == selectedIndex
            if (selected) {
                val left = 28
                val right = width - 28
                val top = rowY + 2
                val bottom = rowY + ROW_HEIGHT
                guiGraphics.fill(left, top, right, bottom, 0x33222222)
                guiGraphics.fill(left, top, right, top + 1, 0xFFFFFFFF.toInt())
                guiGraphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF.toInt())
                guiGraphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF.toInt())
                guiGraphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF.toInt())
            }
            val label = entry.getDisplayName()
            val host = entry.getKey()
            guiGraphics.drawString(font, DlcTextFormatter.parse(label), 36, rowY + 4, 0xE6E6E6, false)
            guiGraphics.drawString(font, host, 36, rowY + 13, 0x9A9A9A, false)
            rowY += ROW_HEIGHT
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
        if (connecting) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("dlc_manager.server.connecting"),
                centerX,
                listBottom + 4,
                0xC8C8C8,
            )
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val listTop = 50
        val listBottom = height - 70
        if (mouseY >= listTop && mouseY <= listBottom) {
            val index = ((mouseY - listTop) / ROW_HEIGHT).toInt() + scrollIndex
            if (index >= 0 && index < servers.size) {
                selectedIndex = index
                val key = servers[index].getKey()
                DlcServerList.setLastSelectedKey(key)
                DlcServerList.save()
                updateButtonStates()
                if (button == 0) {
                    val now = System.currentTimeMillis()
                    if (lastClickIndex == index && now - lastClickTime < 250) {
                        connectSelected()
                        lastClickIndex = -1
                        lastClickTime = 0L
                        return true
                    }
                    lastClickIndex = index
                    lastClickTime = now
                }
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun handleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        val listTop = 50
        val listBottom = height - 70
        if (mouseY < listTop || mouseY > listBottom) {
            return false
        }
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val maxScroll = max(0, servers.size - maxVisible)
        if (maxScroll > 0) {
            val step = if (deltaY > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        clearWidgets()
        init()
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
        val title = Component.translatable("selectServer.deleteQuestion")
        val message = Component.translatable("selectServer.deleteWarning", entry.getDisplayName())
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ result ->
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
                Minecraft.getInstance().toasts,
                Component.translatable("dlc_manager.server.select_required"),
                Component.empty(),
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
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.connect_failed"),
                            Component.empty(),
                        )
                        return@execute
                    }
                    DlcServerCache.put(entry.getKey(), info)
                    Minecraft.getInstance().setScreen(DlcServerMaLiLibScreen(this, entry, info))
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
        deleteButton?.active = hasSelection
        editButton?.active = hasSelection
        connectButton?.active = hasSelection && !connecting
    }

    private val selectedEntry: DlcServerEntry?
        get() = if (selectedIndex >= 0 && selectedIndex < servers.size) servers[selectedIndex] else null

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (totalLines <= maxVisible) {
            return
        }
        val barX = width - 20
        val barWidth = 6
        val barHeight = max(20, (bottom - top) * maxVisible / max(1, totalLines))
        val trackHeight = bottom - top
        val barY = top + (trackHeight - barHeight) * scrollIndex / max(1, maxScroll)
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    companion object {
        private const val ROW_HEIGHT = 22
    }
}
