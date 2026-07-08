package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

class DlcPackVersionSelectScreen(
    private val parent: DlcPackManagerScreen,
    private val identifier: String,
    private val displayName: Component,
    private val versions: List<String>,
    private val selectedKey: String,
    private val installedVersion: String?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.pack.version_select_title")) {
    private var scrollOffset = 0
    private var draggingScrollbar = false
    private var scrollbarDragOffset = 0.0

    override fun init() {
        clearWidgets()
        val buttonWidth = 180
        val buttonHeight = 20
        clampScroll()
        addRenderableWidget(
            Button.builder(Component.translatable("dlc_manager.pack.back")) {
                Minecraft.getInstance().setScreen(parent)
            }.bounds(width / 2 - buttonWidth / 2, height - 28, buttonWidth, buttonHeight).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        guiGraphics.drawCenteredString(font, displayName, width / 2, 32, 0xC8C8C8)
        renderVersionList(guiGraphics, mouseX, mouseY)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (mouseY < listTop || mouseY > listBottom) return false
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return false
        scrollOffset = max(0, min(maxScrollValue, scrollOffset + if (deltaY > 0) -SCROLL_STEP else SCROLL_STEP))
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            if (isInsideScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true
                scrollbarDragOffset = scrollbarDragOffset(mouseY)
                updateScrollFromMouse(mouseY)
                return true
            }
            if (mouseX >= listLeft && mouseX < listRight - SCROLLBAR_WIDTH - 4 && mouseY >= listTop && mouseY < listBottom) {
                val index = ((mouseY - listTop + scrollOffset) / ROW_HEIGHT).toInt()
                if (index in versions.indices) {
                    val versionKey = versions[index]
                    if (versionKey != selectedKey) {
                        parent.selectPackVersion(identifier, versionKey)
                    }
                    Minecraft.getInstance().setScreen(parent)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        scrollbarDragOffset = 0.0
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    private fun renderVersionList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        clampScroll()
        val contentRight = listRight - SCROLLBAR_WIDTH - 4
        guiGraphics.enableScissor(listLeft, listTop, contentRight, listBottom)
        var y = listTop - scrollOffset
        for ((index, versionKey) in versions.withIndex()) {
            if (y + BUTTON_HEIGHT > listTop && y < listBottom) {
                val hover = mouseX >= listLeft && mouseX < contentRight && mouseY >= y && mouseY < y + BUTTON_HEIGHT
                val selected = versionKey == selectedKey
                guiGraphics.fill(listLeft, y, contentRight, y + BUTTON_HEIGHT, when {
                    hover -> 0xFF5A5A5A.toInt()
                    selected -> 0xFF454545.toInt()
                    else -> 0xFF303030.toInt()
                })
                guiGraphics.drawCenteredString(font, versionLabel(versionKey), (listLeft + contentRight) / 2, y + 6, if (selected) 0xAFC8FF else 0xFFFFFF)
            }
            y += ROW_HEIGHT
            if (y > listBottom) break
            if (index == versions.lastIndex) break
        }
        guiGraphics.disableScissor()
        drawScrollBar(guiGraphics)
    }

    private fun versionLabel(versionKey: String): Component {
        val version = versionKey.substringAfterLast('@', versionKey)
        return if (version == installedVersion) {
            Component.translatable("dlc_manager.pack.version_installed", version)
        } else {
            Component.literal(version)
        }
    }

    private fun clampScroll() {
        scrollOffset = max(0, min(maxScroll, scrollOffset))
    }

    private val maxScroll: Int
        get() = max(0, contentHeight - (listBottom - listTop))

    private val contentHeight: Int
        get() = versions.size * ROW_HEIGHT

    private fun isInsideScrollbar(mouseX: Double, mouseY: Double): Boolean {
        if (maxScroll <= 0) return false
        val x = listRight - SCROLLBAR_WIDTH
        return mouseX >= x && mouseX <= x + SCROLLBAR_WIDTH && mouseY >= listTop && mouseY <= listBottom
    }

    private fun updateScrollFromMouse(mouseY: Double) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) {
            scrollOffset = 0
            return
        }
        val travel = listBottom - listTop - scrollbarHeight()
        val ratio = max(0.0, min(1.0, (mouseY - listTop - scrollbarDragOffset) / travel))
        scrollOffset = max(0, min(maxScrollValue, (ratio * maxScrollValue).toInt()))
    }

    private fun scrollbarDragOffset(mouseY: Double): Double {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return 0.0
        val travel = listBottom - listTop - scrollbarHeight()
        val barY = listTop + (travel * (scrollOffset / maxScrollValue.toDouble())).toInt()
        return mouseY - barY
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) return
        val barHeight = scrollbarHeight()
        val travel = listBottom - listTop - barHeight
        val barY = listTop + (travel * (scrollOffset / maxScrollValue.toDouble())).toInt()
        val x = listRight - SCROLLBAR_WIDTH
        guiGraphics.fill(x, listTop, x + SCROLLBAR_WIDTH, listBottom, 0x663A3F48)
        guiGraphics.fill(x, barY, x + SCROLLBAR_WIDTH, barY + barHeight, 0xFF8A95A8.toInt())
    }

    private fun scrollbarHeight(): Int {
        val viewport = listBottom - listTop
        return max(20, (viewport * (viewport / contentHeight.toDouble())).toInt())
    }

    private val listLeft: Int get() = width / 2 - 110
    private val listRight: Int get() = width / 2 + 110
    private val listTop: Int get() = 48
    private val listBottom: Int get() = max(listTop + BUTTON_HEIGHT, height - 40)

    companion object {
        private const val BUTTON_HEIGHT = 20
        private const val ROW_HEIGHT = 26
        private const val SCROLLBAR_WIDTH = 6
        private const val SCROLL_STEP = 24
    }
}
