package com.cozooo.dlc_manager.client

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.notice.DlcNoticeManager
import com.cozooo.dlc_manager.client.pack.DlcPackRecovery
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen
import com.cozooo.dlc_manager.client.screen.DlcPackManagerScreen
import com.cozooo.dlc_manager.client.screen.DlcModpackUpdateScreen
import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen
import com.cozooo.dlc_manager.client.screen.DlcScreenCompat
import com.cozooo.dlc_manager.client.screen.DlcSelfUpdateScreen
import com.cozooo.dlc_manager.client.screen.DlcServerListScreen
import com.cozooo.dlc_manager.client.screen.DlcServerManagerScreen
import com.cozooo.dlc_manager.client.screen.DlcServerPasswordScreen
import com.cozooo.dlc_manager.client.screen.DlcUpdateChangelogScreen
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.server.DlcServerCache
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerList
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.cozooo.dlc_manager.server.DlcServerApi
import com.mojang.logging.LogUtils
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.util.FormattedCharSequence
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DlcClientRuntime {
    @Volatile private var startupCheckRunning = false
    @Volatile private var startupChecksCompleted = false
    @Volatile private var selfUpdatePrompted = false
    private var titleNoticeLang = ""
    private var titleNoticeTitle = ""
    private var titleNoticeSubhead = ""
    private var titleNoticeSubheadEnabled = false
    private var titleNoticeText = ""
    private var titleNoticeLoading = false
    private var titleNoticeScrollOffset = 0
    private var titleNoticeDraggingScrollbar = false
    private var titleNoticeDragStartY = 0
    private var titleNoticeDragStartScroll = 0
    private var titleNoticeFuture: CompletableFuture<String>? = null
    @Volatile private var firstRunManagerHandled = false

    private const val FIRST_RUN_MARKER_NAME = "ShownOnFirstRun"
    private val LOGGER: Logger = LogUtils.getLogger()

    @JvmStatic
    fun findButtonByKey(screen: Screen, key: String?): AbstractWidget? {
        for (child in screen.children()) {
            if (child is AbstractWidget) {
                val contents = child.message.contents
                if (contents is TranslatableContents && key == contents.key) {
                    return child
                }
            }
        }
        return null
    }

    @JvmStatic
    fun shouldShowMainMenuNoticeButton(): Boolean {
        return DlcConfig.isNoticeDisplayModeMainMenuButton() && DlcNoticeManager.isNoticeEnabled()
    }

    @JvmStatic
    fun shouldShowMainMenuNoticePanel(): Boolean {
        return DlcConfig.isNoticeDisplayModeMainMenuPanel() &&
            DlcNoticeManager.isNoticeEnabled() &&
            DlcConfig.getNoticePanelWidth() > 0 &&
            DlcConfig.getNoticePanelHeight() > 0
    }

    @JvmStatic
    fun openNoticeFromTitle(parent: Screen?) {
        DlcNoticeManager.forceShow(parent)
    }

    @JvmStatic
    fun openPackManagerFromTitle(parent: Screen?) {
        if (!DlcConfig.isPackFuncOn()) return
        Minecraft.getInstance().setScreen(DlcPackManagerScreen(parent))
    }

    @JvmStatic
    fun maybeOpenManagerOnFirstRun(parent: Screen?) {
        if (firstRunManagerHandled) return
        firstRunManagerHandled = true
        if (parent == null || !DlcConfig.isShowOnFirstRun()) return
        val dlcRoot = DlcConfig.getDlcRoot() ?: return
        val markerPath = dlcRoot.resolve(FIRST_RUN_MARKER_NAME)
        if (Files.exists(markerPath)) return
        if (DlcRequiredManager.tryShowFromTitle(parent)) return
        try {
            Files.createDirectories(dlcRoot)
            ensurePackArchiveRoot(dlcRoot)
            Files.createFile(markerPath)
        } catch (_: IOException) {
            return
        }
        val managerScreen: Screen = DlcMaLiLibScreen(parent)
        if (DlcRemoteManager.tryShowFromManager(managerScreen)) return
        Minecraft.getInstance().setScreen(managerScreen)
    }

    @JvmStatic
    fun tryContinueFirstRun(parent: Screen?): Boolean {
        if (parent == null || parent !is TitleScreen) return false
        if (!DlcConfig.isShowOnFirstRun()) return false
        val dlcRoot = DlcConfig.getDlcRoot() ?: return false
        val markerPath = dlcRoot.resolve(FIRST_RUN_MARKER_NAME)
        if (Files.exists(markerPath)) return false
        try {
            Files.createDirectories(dlcRoot)
            ensurePackArchiveRoot(dlcRoot)
            Files.createFile(markerPath)
        } catch (_: IOException) {
            return false
        }
        val managerScreen: Screen = DlcMaLiLibScreen(parent)
        if (DlcRemoteManager.tryShowFromManager(managerScreen)) return true
        Minecraft.getInstance().setScreen(managerScreen)
        return true
    }

    @JvmStatic
    fun renderTitleNoticePanel(screen: Screen?, guiGraphics: GuiGraphics?) {
        renderTitleNoticePanel(screen, guiGraphics, Int.MIN_VALUE, Int.MIN_VALUE)
    }

    @JvmStatic
    fun renderTitleNoticePanel(screen: Screen?, guiGraphics: GuiGraphics?, mouseX: Int, mouseY: Int) {
        if (!shouldShowMainMenuNoticePanel() || screen == null || guiGraphics == null) {
            titleNoticeDraggingScrollbar = false
            return
        }
        val config = DlcNoticeConfig.load()
        if (!config.isNoticeEnabled()) {
            titleNoticeDraggingScrollbar = false
            return
        }
        val layout = buildTitleNoticePanelLayout(screen, config)
        if (layout == null) {
            titleNoticeDraggingScrollbar = false
            return
        }
        clampTitleNoticeScroll(layout.maxScroll)
        if (titleNoticeDraggingScrollbar && mouseY != Int.MIN_VALUE && layout.maxScroll > 0) {
            val trackHeight = max(1, (layout.scrollbarBottom - layout.scrollbarTop) - layout.scrollbarHeight)
            val delta = mouseY - titleNoticeDragStartY
            val next = titleNoticeDragStartScroll + (delta * (layout.maxScroll / trackHeight.toDouble())).roundToInt()
            titleNoticeScrollOffset = max(0, min(layout.maxScroll, next))
        }

        guiGraphics.fill(layout.x, layout.y, layout.right, layout.bottom, 0xCC101215.toInt())
        guiGraphics.fill(layout.x, layout.y, layout.right, layout.y + 1, 0xFF2A2F36.toInt())
        guiGraphics.fill(layout.x, layout.bottom - 1, layout.right, layout.bottom, 0xFF2A2F36.toInt())
        guiGraphics.fill(layout.x, layout.y, layout.x + 1, layout.bottom, 0xFF2A2F36.toInt())
        guiGraphics.fill(layout.right - 1, layout.y, layout.right, layout.bottom, 0xFF2A2F36.toInt())

        val font = Minecraft.getInstance().font
        val titleY = layout.y + layout.padding
        guiGraphics.drawCenteredString(
            font,
            DlcTextFormatter.parse(layout.title),
            layout.x + (layout.right - layout.x) / 2,
            titleY,
            0xFFFFFF,
        )
        if (layout.subheadLines.isNotEmpty()) {
            var subheadY = titleY + font.lineHeight + 2
            for (subheadLine in layout.subheadLines) {
                val subheadWidth = font.width(subheadLine)
                var subheadX = layout.x + ((layout.right - layout.x) - subheadWidth) / 2
                if (subheadX < layout.textX) {
                    subheadX = layout.textX
                }
                guiGraphics.drawString(font, subheadLine, subheadX, subheadY, 0xBFC7D5, false)
                subheadY += layout.lineStep
            }
        }

        var drawY = layout.textTop - titleNoticeScrollOffset
        guiGraphics.enableScissor(layout.x + 1, layout.textTop, layout.right - 1, layout.textBottom)
        try {
            for (line in layout.bodyLines) {
                if (DlcMarkdownRenderer.isHrLine(line)) {
                    val hrY = drawY + layout.lineStep / 2
                    if (hrY >= layout.textTop && hrY <= layout.textBottom - 1) {
                        guiGraphics.fill(layout.textX, hrY, layout.textRight, hrY + 1, 0xFF5A5A5A.toInt())
                    }
                    drawY += layout.lineStep
                    continue
                }
                val scale = DlcMarkdownRenderer.getHeadingScale(font, line, layout.textWidth)
                val lineHeight = DlcMarkdownRenderer.getLineHeight(font, line, layout.lineStep, layout.textWidth)
                if (drawY > layout.textBottom - lineHeight) break
                if (drawY + lineHeight <= layout.textTop) {
                    drawY += lineHeight
                    continue
                }
                if (scale > 1.0f) {
                    guiGraphics.pose().pushPose()
                    guiGraphics.pose().scale(scale, scale, 1.0f)
                    guiGraphics.drawString(
                        font,
                        line,
                        (layout.textX / scale).toInt(),
                        (drawY / scale).toInt(),
                        0xFFFFFF,
                        false,
                    )
                    guiGraphics.pose().popPose()
                } else {
                    guiGraphics.drawString(font, line, layout.textX, drawY, 0xE6E6E6, false)
                }
                drawY += lineHeight
            }
        } finally {
            guiGraphics.disableScissor()
        }
        drawTitleNoticeScrollbar(guiGraphics, layout)
    }

    @JvmStatic
    fun handleTitleNoticePanelClick(screen: Screen?, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !shouldShowMainMenuNoticePanel() || screen == null) return false
        val config = DlcNoticeConfig.load()
        if (!config.isNoticeEnabled()) return false
        val layout = buildTitleNoticePanelLayout(screen, config) ?: return false
        clampTitleNoticeScroll(layout.maxScroll)
        if (!layout.contains(mouseX, mouseY)) return false
        if (layout.isOverScrollbar(mouseX, mouseY)) {
            updateTitleNoticeScrollFromTrack(layout, mouseY)
            titleNoticeDraggingScrollbar = true
            titleNoticeDragStartY = mouseY.toInt()
            titleNoticeDragStartScroll = titleNoticeScrollOffset
            return true
        }
        if (layout.isOverContent(mouseX, mouseY)) {
            var drawY = layout.textTop - titleNoticeScrollOffset
            val font = Minecraft.getInstance().font
            for (line in layout.bodyLines) {
                if (DlcMarkdownRenderer.isHrLine(line)) {
                    drawY += layout.lineStep
                    continue
                }
                val scale = DlcMarkdownRenderer.getHeadingScale(font, line, layout.textWidth)
                val lineHeight = DlcMarkdownRenderer.getLineHeight(font, line, layout.lineStep, layout.textWidth)
                if (drawY > layout.textBottom - lineHeight) break
                if (mouseY >= drawY && mouseY < drawY + lineHeight) {
                    val relX = mouseX.toInt() - layout.textX
                    val maxWidth = (font.width(line) * scale).roundToInt()
                    if (relX >= 0 && relX <= maxWidth) {
                        val styleX = if (scale > 1.0f) (relX / scale).toInt() else relX
                        val style = findStyleAt(line, styleX)
                        val click = style?.clickEvent
                        if (click != null &&
                            click.action == ClickEvent.Action.OPEN_URL &&
                            !click.value.isNullOrBlank()
                        ) {
                            Util.getPlatform().openUri(click.value)
                        }
                    }
                    return true
                }
                drawY += lineHeight
            }
        }
        return true
    }

    @JvmStatic
    fun handleTitleNoticePanelScroll(screen: Screen?, mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!shouldShowMainMenuNoticePanel() || screen == null) return false
        val config = DlcNoticeConfig.load()
        if (!config.isNoticeEnabled()) return false
        val layout = buildTitleNoticePanelLayout(screen, config)
        if (layout == null || !layout.contains(mouseX, mouseY)) return false
        clampTitleNoticeScroll(layout.maxScroll)
        if (layout.maxScroll > 0 && verticalAmount != 0.0) {
            titleNoticeScrollOffset -= (verticalAmount * layout.lineStep).roundToInt()
            clampTitleNoticeScroll(layout.maxScroll)
        }
        return true
    }

    @JvmStatic
    fun handleTitleNoticePanelDrag(screen: Screen?, mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button != 0 || !titleNoticeDraggingScrollbar || !shouldShowMainMenuNoticePanel() || screen == null) return false
        val config = DlcNoticeConfig.load()
        if (!config.isNoticeEnabled()) {
            titleNoticeDraggingScrollbar = false
            return false
        }
        val layout = buildTitleNoticePanelLayout(screen, config)
        if (layout == null || layout.maxScroll <= 0) {
            titleNoticeDraggingScrollbar = false
            return false
        }
        val trackHeight = max(1, (layout.scrollbarBottom - layout.scrollbarTop) - layout.scrollbarHeight)
        val delta = mouseY.toInt() - titleNoticeDragStartY
        val next = titleNoticeDragStartScroll + (delta * (layout.maxScroll / trackHeight.toDouble())).roundToInt()
        titleNoticeScrollOffset = max(0, min(layout.maxScroll, next))
        return true
    }

    @JvmStatic
    fun handleTitleNoticePanelRelease(screen: Screen?, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false
        if (titleNoticeDraggingScrollbar) {
            titleNoticeDraggingScrollbar = false
            return true
        }
        return false
    }

    private fun buildTitleNoticePanelLayout(screen: Screen, config: DlcNoticeConfig): TitleNoticePanelLayout? {
        val lang = Minecraft.getInstance().options.languageCode
        updateTitleNoticeText(config, lang)
        var title = titleNoticeTitle
        if (title.isBlank()) {
            title = Component.translatable("dlc_manager.notice_title").string
        }
        var body = titleNoticeText
        if (body.isBlank()) {
            body = if (titleNoticeLoading) {
                Component.translatable("dlc_manager.notice.loading").string
            } else {
                config.getContents().resolve(lang)
            }
        }
        val subhead = titleNoticeSubhead
        val subheadEnabled = titleNoticeSubheadEnabled

        var panelWidth = max(120, DlcConfig.getNoticePanelWidth())
        var panelHeight = max(60, DlcConfig.getNoticePanelHeight())
        panelWidth = min(panelWidth, max(120, screen.width - 8))
        panelHeight = min(panelHeight, max(60, screen.height - 8))
        val pos = DlcConfig.computeNoticePanelPosition(screen.width, screen.height, panelWidth, panelHeight, 0, 0)
        var x = pos[0]
        var y = pos[1]
        val maxX = max(0, screen.width - panelWidth)
        val maxY = max(0, screen.height - panelHeight)
        x = when {
            x < 0 -> 0
            x > maxX -> maxX
            else -> x
        }
        y = when {
            y < 0 -> 0
            y > maxY -> maxY
            else -> y
        }

        val right = x + panelWidth
        val bottom = y + panelHeight
        val padding = 6
        val font = Minecraft.getInstance().font
        val lineStep = font.lineHeight + 2
        val titleY = y + padding
        var contentTop = titleY + font.lineHeight + 4
        var subheadLines: List<FormattedCharSequence> = listOf()
        if (subheadEnabled && subhead.isNotBlank()) {
            subheadLines = wrapMultiline(subhead, max(40, panelWidth - padding * 2))
            if (subheadLines.isNotEmpty()) {
                contentTop += subheadLines.size * lineStep + 2
            }
        }
        val contentBottom = bottom - padding
        val visibleHeight = max(10, contentBottom - contentTop)
        val textX = x + padding
        val scrollbarGap = 4
        val scrollbarWidth = 4
        val textRightNoBar = right - padding
        val textWidthNoBar = max(20, textRightNoBar - textX)

        var bodyLines = DlcMarkdownRenderer.wrapMarkdown(font, body, textWidthNoBar)
        var totalContentHeight = computeTotalLineHeight(bodyLines, lineStep, textWidthNoBar)
        var maxScroll = max(0, totalContentHeight - visibleHeight)
        var textRight = textRightNoBar
        var textWidth = textWidthNoBar
        var scrollbarX = textRight - scrollbarWidth

        if (maxScroll > 0) {
            textRight = max(textX + 20, right - padding - scrollbarWidth - scrollbarGap)
            textWidth = max(20, textRight - textX)
            bodyLines = DlcMarkdownRenderer.wrapMarkdown(font, body, textWidth)
            totalContentHeight = computeTotalLineHeight(bodyLines, lineStep, textWidth)
            maxScroll = max(0, totalContentHeight - visibleHeight)
            scrollbarX = textRight + scrollbarGap
        }

        var scrollbarHeight = max(12, visibleHeight * visibleHeight / max(1, totalContentHeight))
        if (scrollbarHeight > visibleHeight) {
            scrollbarHeight = visibleHeight
        }

        return TitleNoticePanelLayout(
            x, y, right, bottom, padding,
            textX, textRight, contentTop, contentBottom, textWidth,
            lineStep, visibleHeight, totalContentHeight, maxScroll,
            scrollbarX, contentTop, contentBottom, scrollbarWidth, scrollbarHeight,
            title, subheadLines, bodyLines,
        )
    }

    private fun drawTitleNoticeScrollbar(guiGraphics: GuiGraphics, layout: TitleNoticePanelLayout) {
        if (layout.maxScroll <= 0 || layout.visibleHeight <= 0) return
        val barY = layout.scrollbarYForScroll(titleNoticeScrollOffset)
        guiGraphics.fill(layout.scrollbarX, layout.scrollbarTop, layout.scrollbarX + layout.scrollbarWidth, layout.scrollbarBottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(layout.scrollbarX, barY, layout.scrollbarX + layout.scrollbarWidth, barY + layout.scrollbarHeight, 0xFF6B6B6B.toInt())
    }

    private fun updateTitleNoticeScrollFromTrack(layout: TitleNoticePanelLayout, mouseY: Double) {
        if (layout.maxScroll <= 0 || layout.visibleHeight <= 0) {
            titleNoticeScrollOffset = 0
            return
        }
        val maxTrack = max(1, layout.visibleHeight - layout.scrollbarHeight)
        val barCenter = mouseY.toInt() - layout.scrollbarTop - (layout.scrollbarHeight / 2)
        val next = ((barCenter / maxTrack.toDouble()) * layout.maxScroll).roundToInt()
        titleNoticeScrollOffset = max(0, min(layout.maxScroll, next))
    }

    private fun clampTitleNoticeScroll(maxScroll: Int) {
        if (titleNoticeScrollOffset < 0) {
            titleNoticeScrollOffset = 0
        } else if (titleNoticeScrollOffset > maxScroll) {
            titleNoticeScrollOffset = maxScroll
        }
    }

    private fun computeTotalLineHeight(lines: List<FormattedCharSequence>, lineStep: Int, textWidth: Int): Int {
        var total = 0
        for (line in lines) {
            total += if (DlcMarkdownRenderer.isHrLine(line)) {
                lineStep
            } else {
                DlcMarkdownRenderer.getLineHeight(Minecraft.getInstance().font, line, lineStep, textWidth)
            }
        }
        return total
    }

    private fun wrapMultiline(text: String, maxWidth: Int): List<FormattedCharSequence> {
        return DlcTextFormatter.splitMultiline(Minecraft.getInstance().font, text, maxWidth)
    }

    private class TitleNoticePanelLayout(
        val x: Int,
        val y: Int,
        val right: Int,
        val bottom: Int,
        val padding: Int,
        val textX: Int,
        val textRight: Int,
        val textTop: Int,
        val textBottom: Int,
        val textWidth: Int,
        val lineStep: Int,
        val visibleHeight: Int,
        val totalContentHeight: Int,
        val maxScroll: Int,
        val scrollbarX: Int,
        val scrollbarTop: Int,
        val scrollbarBottom: Int,
        val scrollbarWidth: Int,
        val scrollbarHeight: Int,
        val title: String,
        val subheadLines: List<FormattedCharSequence>,
        val bodyLines: List<FormattedCharSequence>,
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= bottom
        }

        fun isOverContent(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= textX && mouseX <= textRight && mouseY >= textTop && mouseY <= textBottom
        }

        fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean {
            if (maxScroll <= 0) return false
            return mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + scrollbarWidth + 2 &&
                mouseY >= scrollbarTop && mouseY <= scrollbarBottom
        }

        fun scrollbarYForScroll(scrollOffset: Int): Int {
            if (maxScroll <= 0) return scrollbarTop
            val maxTrack = max(1, visibleHeight - scrollbarHeight)
            return scrollbarTop + ((scrollOffset / maxScroll.toFloat()) * maxTrack).toInt()
        }
    }

    private fun findStyleAt(sequence: FormattedCharSequence, x: Int): Style? {
        var width = 0
        var result: Style? = null
        sequence.accept { _, style, codePoint ->
            if (result != null) {
                return@accept false
            }
            val text = String(Character.toChars(codePoint))
            val charWidth = Minecraft.getInstance().font.width(text)
            if (width + charWidth > x) {
                result = style
                return@accept false
            }
            width += charWidth
            true
        }
        return result
    }

    @JvmStatic
    fun canOpenServerManager(): Boolean {
        if (!DlcConfig.isServerSupportOn()) return false
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null) return false
        if (minecraft.hasSingleplayerServer()) return false
        return minecraft.player!!.hasPermissions(2)
    }

    @JvmStatic
    fun openManagerFromCommand() {
        val minecraft = Minecraft.getInstance()
        val parent = minecraft.screen
        val managerScreen: Screen = DlcMaLiLibScreen(parent)
        if (DlcRemoteManager.tryShowFromManager(managerScreen)) return
        minecraft.setScreen(managerScreen)
    }

    @JvmStatic
    fun openSelfUpdateFromCommand() {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(DlcSelfUpdateScreen(minecraft.screen, null))
    }

    @JvmStatic
    fun openUpdateManagerFromCommand() {
        val minecraft = Minecraft.getInstance()
        DlcUpdateClient.openUpdateManager(minecraft.screen)
    }

    @JvmStatic
    fun openUpdatePromptFromCommand() {
        val minecraft = Minecraft.getInstance()
        val result = DlcUpdateService.checkForUpdates()
        if (!result.hasUpdates()) {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                minecraft.toasts,
                Component.translatable("dlc_manager.update.no_updates"),
                Component.empty(),
            )
            return
        }
        DlcUpdateClient.showPrompt(minecraft.screen, result)
    }

    @JvmStatic
    fun openServerManagerFromCommand() {
        if (!canOpenServerManager()) return
        openServerManagerFromIngame(null)
    }

    @JvmStatic
    fun openServerManagerFromIngame(parent: Screen?) {
        val minecraft = Minecraft.getInstance()
        val data = minecraft.currentServer
        if (data == null) {
            minecraft.setScreen(DlcServerListScreen(parent))
            return
        }
        var entry = createEntryFromServer(data)
        if (entry == null) {
            minecraft.setScreen(DlcServerListScreen(parent))
            return
        }
        DlcServerList.load()
        val existing = findEntryByKey(entry.getKey())
        if (existing != null) {
            entry = existing
        }
        if (!entry.password.isNullOrBlank()) {
            val finalEntry = entry
            CompletableFuture
                .supplyAsync { fetchServerInfo(finalEntry) }
                .whenComplete { info, _ ->
                    minecraft.execute {
                        if (info != null) {
                            saveEntryToList(finalEntry)
                            DlcServerCache.put(finalEntry.getKey(), info)
                            minecraft.setScreen(DlcServerManagerScreen(parent, finalEntry, info, DlcServerManagerScreen.Tab.SERVERS, true))
                            return@execute
                        }
                        minecraft.setScreen(DlcServerPasswordScreen(parent, finalEntry, false, true))
                    }
                }
            return
        }
        minecraft.setScreen(DlcServerPasswordScreen(parent, entry, false, true))
    }

    @JvmStatic
    fun runStartupChecksAsync(screen: Screen?) {
        if (startupCheckRunning || startupChecksCompleted) return
        DlcServerApi.start(null)
        DlcServerApi.setClientStateChangedListener { Minecraft.getInstance().execute(DlcMaLiLibScreen::refreshCurrentOpenScreen) }
        ensurePackArchiveRoot(DlcPaths.dlcRoot())
        DlcPackRecovery.recover(DlcPaths.gameDir(), DlcPaths.dlcRoot())
        uninstallIncompatiblePacksForCurrentVersion()
        DlcStartupStateSync.syncOnStartup()
        startupCheckRunning = true
        startupChecksCompleted = true
        Thread({
            LOGGER.info("[dlc-manager] startup check begin")
            val startAt = System.currentTimeMillis()
            var updateResult: UpdateCheckResult? = null
            var hasUpdate = false
            var packUpdateResult: DlcPackReleaseUpdateService.CheckResult? = null
            var hasPackUpdate = false
            var selfUpdateResult: DlcSelfUpdateService.SelfUpdateResult? = null
            var hasSelfUpdate = false
            var hasRequired = false
            var missing: List<DlcRequiredEntry> = listOf()
            val updateManagerOn = DlcConfig.isUpdateManagerOn()
            val selfUpdateAutoCheckOn = DlcConfig.isSelfUpdateAutoCheckOn()
            var changelogText = ""
            var changelogVersion: String? = null
            var currentBaseVersion = "1.0.0"
            var currentDlcVersion = "1.0.0"
            val dlcRoot = DlcPaths.dlcRoot()
            val version = DlcUpdateVersion.load(dlcRoot)
            if (!version.getBase().isNullOrBlank()) {
                currentBaseVersion = version.getBase()!!
            }
            if (!version.getDlc().isNullOrBlank()) {
                currentDlcVersion = version.getDlc()!!
            }
            val repoUrl = DlcConfig.getUpdateGitRepoUrl()
            if (!repoUrl.isNullOrBlank()) {
                val packAt = System.currentTimeMillis()
                packUpdateResult = DlcPackReleaseUpdateService.checkForUpdates(
                    DlcConfig.getModpackUrl(),
                    repoUrl,
                    currentBaseVersion,
                    currentDlcVersion,
                    DlcPackReleaseUpdateService.Side.CLIENT,
                )
                val promptSkipped = packUpdateResult.hasUpdate() &&
                    DlcPackReleaseUpdateService.isPromptSkippedForUpdate(packUpdateResult, DlcPackReleaseUpdateService.Side.CLIENT)
                val alreadyDownloaded = packUpdateResult.hasUpdate() &&
                    DlcPackReleaseUpdateService.hasDownloadedClientPackages(packUpdateResult)
                hasPackUpdate = packUpdateResult.hasUpdate() && !alreadyDownloaded && !promptSkipped
                LOGGER.info(
                    "[dlc-manager] pack update check done: hasUpdate={}, alreadyDownloaded={}, promptSkipped={}, took={}ms",
                    hasPackUpdate,
                    alreadyDownloaded,
                    promptSkipped,
                    System.currentTimeMillis() - packAt,
                )
            }
            if (updateManagerOn) {
                val currentVersion = DlcUpdateVersion.load(dlcRoot)
                val currentBase = currentVersion.getBase()
                val currentDlc = currentVersion.getDlc()
                val currentMarker = composeChangelogMarker(currentBase, currentDlc)
                var shown = currentVersion.getChangelogShown()
                if (!shown.isNullOrBlank() && !isPairMarker(shown)) {
                    val migratedShown = composeChangelogMarker(currentBase, shown)
                    currentVersion.setChangelogShown(migratedShown)
                    currentVersion.save(dlcRoot)
                    shown = migratedShown
                }
                if (!currentDlc.isNullOrBlank() &&
                    !shown.isNullOrBlank() &&
                    !isSameChangelogMarker(shown, currentMarker, currentDlc)
                ) {
                    val pending = collectPendingChangelogMeta(shown, currentMarker, currentBase, currentDlc)
                    if (pending.isNotEmpty()) {
                        changelogText = buildPendingChangelogText(pending, CrossPlatformRuntime.languageCode())
                        if (changelogText.isNotBlank()) {
                            changelogVersion = currentMarker
                        }
                    }
                }
            }
            if (updateManagerOn && !DlcUpdateClient.isPrompted()) {
                val updateAt = System.currentTimeMillis()
                updateResult = DlcUpdateService.checkForUpdates()
                val promptSkipped = updateResult.hasUpdates() &&
                    DlcUpdateService.isPromptSkippedForUpdate(updateResult)
                hasUpdate = updateResult.hasUpdates() && !promptSkipped
                val updateCount = updateResult.updates?.size ?: 0
                LOGGER.info(
                    "[dlc-manager] update check done: hasUpdate={}, count={}, promptSkipped={}, took={}ms",
                    hasUpdate,
                    updateCount,
                    promptSkipped,
                    System.currentTimeMillis() - updateAt,
                )
            }
            if (selfUpdateAutoCheckOn && !selfUpdatePrompted) {
                val selfAt = System.currentTimeMillis()
                selfUpdateResult = DlcSelfUpdateService.checkForUpdate()
                val promptSkipped = selfUpdateResult.hasUpdate &&
                    DlcSelfUpdateService.isPromptSkippedForUpdate(selfUpdateResult)
                hasSelfUpdate = selfUpdateResult.hasUpdate && !promptSkipped
                LOGGER.info(
                    "[dlc-manager] self update check done: hasUpdate={}, promptSkipped={}, took={}ms",
                    hasSelfUpdate,
                    promptSkipped,
                    System.currentTimeMillis() - selfAt,
                )
            }
            if (!hasUpdate && !DlcRequiredManager.hasSkipMarker()) {
                val requiredAt = System.currentTimeMillis()
                hasRequired = DlcRequiredManager.hasMissingDownloadsForPrompt()
                if (hasRequired) {
                    missing = DlcRequiredManager.collectMissingDownloadsForPrompt()
                }
                LOGGER.info(
                    "[dlc-manager] required check done: hasRequired={}, missingCount={}, took={}ms",
                    hasRequired,
                    missing.size,
                    System.currentTimeMillis() - requiredAt,
                )
            }

            val finalHasUpdate = hasUpdate
            val finalUpdateResult = updateResult
            val finalHasPackUpdate = hasPackUpdate
            val finalPackUpdateResult = packUpdateResult
            val finalHasSelfUpdate = hasSelfUpdate
            val finalSelfUpdateResult = selfUpdateResult
            val finalHasRequired = hasRequired
            val finalMissing = missing
            val finalChangelog = changelogText
            val finalChangelogVersion = changelogVersion
            Minecraft.getInstance().execute {
                startupCheckRunning = false
                LOGGER.info("[dlc-manager] startup check finished in {}ms", System.currentTimeMillis() - startAt)
                if (Minecraft.getInstance().screen !is TitleScreen) return@execute
                var flow = Runnable {
                    DlcNoticeManager.showFromTitle(screen)
                }
                if (finalHasRequired && finalMissing.isNotEmpty()) {
                    flow = wrapRequiredPrompt(screen, finalMissing, flow)
                }
                if (finalHasUpdate && finalUpdateResult != null) {
                    flow = wrapUpdatePrompt(screen, finalUpdateResult, flow)
                }
                if (finalChangelog.isNotBlank() && !finalChangelogVersion.isNullOrBlank()) {
                    flow = wrapChangelogPrompt(screen, finalChangelog, finalChangelogVersion, flow)
                }
                if (finalHasPackUpdate && finalPackUpdateResult != null) {
                    flow = wrapPackUpdatePrompt(screen, finalPackUpdateResult, flow)
                }
                if (finalHasSelfUpdate && finalSelfUpdateResult != null && !selfUpdatePrompted) {
                    flow = wrapSelfUpdatePrompt(screen, finalSelfUpdateResult, flow)
                }
                flow.run()
            }
        }, "dlc-startup-check").start()
    }

    private fun wrapSelfUpdatePrompt(fallback: Screen?, result: DlcSelfUpdateService.SelfUpdateResult?, next: Runnable): Runnable {
        return Runnable {
            if (result == null || !result.hasUpdate) {
                next.run()
                return@Runnable
            }
            selfUpdatePrompted = true
            val continuation = createContinuationScreen(fallback, next)
            Minecraft.getInstance().setScreen(DlcSelfUpdateScreen(continuation, result, true))
        }
    }

    private fun wrapPackUpdatePrompt(fallback: Screen?, result: DlcPackReleaseUpdateService.CheckResult?, next: Runnable): Runnable {
        return Runnable {
            if (result == null || !result.hasUpdate()) {
                next.run()
                return@Runnable
            }
            val continuation = createContinuationScreen(fallback, next)
            Minecraft.getInstance().setScreen(DlcModpackUpdateScreen(continuation, result, true))
        }
    }

    private fun wrapChangelogPrompt(fallback: Screen?, changelog: String?, changelogVersion: String?, next: Runnable): Runnable {
        return Runnable {
            if (changelog.isNullOrBlank() || changelogVersion.isNullOrBlank()) {
                next.run()
                return@Runnable
            }
            val continuation = createContinuationScreen(fallback, next)
            Minecraft.getInstance().setScreen(
                DlcUpdateChangelogScreen(continuation, changelog) {
                    val root = DlcPaths.dlcRoot()
                    val version = DlcUpdateVersion.load(root)
                    version.setChangelogShown(changelogVersion)
                    version.save(root)
                    Minecraft.getInstance().setScreen(continuation)
                },
            )
        }
    }

    private fun wrapUpdatePrompt(fallback: Screen?, result: UpdateCheckResult?, next: Runnable): Runnable {
        return Runnable {
            if (result == null || !result.hasUpdates()) {
                next.run()
                return@Runnable
            }
            DlcUpdateClient.markPrompted()
            val continuation = createContinuationScreen(fallback, next)
            Minecraft.getInstance().setScreen(DlcUpdatePromptScreen(continuation, result, true))
        }
    }

    private fun wrapRequiredPrompt(fallback: Screen?, missing: List<DlcRequiredEntry>?, next: Runnable): Runnable {
        return Runnable {
            if (missing.isNullOrEmpty()) {
                next.run()
                return@Runnable
            }
            DlcRequiredManager.markPrompted()
            val continuation = createContinuationScreen(fallback, next)
            Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(continuation, missing))
        }
    }

    private fun createContinuationScreen(fallback: Screen?, next: Runnable): Screen {
        return object : Screen(Component.empty()) {
            private var started = false

            override fun init() {
                if (started) return
                started = true
                val minecraft = Minecraft.getInstance()
                next.run()
                if (minecraft.screen === this) {
                    minecraft.setScreen(fallback)
                }
            }

            override fun shouldCloseOnEsc(): Boolean {
                return false
            }
        }
    }

    private fun collectPendingChangelogMeta(
        shownMarker: String?,
        currentMarker: String?,
        currentBaseVersion: String?,
        currentDlcVersion: String?,
    ): List<DlcUpdateMeta> {
        if (shownMarker.isNullOrBlank() ||
            currentMarker.isNullOrBlank() ||
            currentBaseVersion.isNullOrBlank() ||
            currentDlcVersion.isNullOrBlank()
        ) {
            return listOf()
        }
        val shown = shownMarker.trim()
        val currentBase = safeVersion(currentBaseVersion)
        val currentDlc = safeVersion(currentDlcVersion)
        val shownBase = extractBaseFromMarker(shown)
        val shownDlc = extractDlcFromMarker(shown)
        if (!isPairMarker(shown) && shownDlc == currentDlc) {
            return listOf()
        }
        if (isPairMarker(shown) && shownBase == currentBase && shownDlc == currentDlc) {
            val latest = DlcUpdateService.loadMetaForVersion(currentBase, currentDlc) ?: return listOf()
            val fromMarker = composeChangelogMarker(latest.getBaseOld(), latest.getDlcOld())
            val toMarker = composeChangelogMarker(resolveMetaTargetBase(latest), resolveMetaTargetDlc(latest))
            return if (fromMarker == shown && toMarker == currentMarker.trim()) {
                listOf(latest)
            } else {
                listOf()
            }
        }
        val reverse = ArrayList<DlcUpdateMeta>()
        val visited = HashSet<String>()
        var cursorBase = currentBase
        var cursorDlc = currentDlc
        val pairShown = isPairMarker(shown)
        while (cursorDlc.isNotBlank() &&
            !(if (pairShown) cursorBase == shownBase && cursorDlc == shownDlc else cursorDlc == shownDlc) &&
            visited.add(composeChangelogMarker(cursorBase, cursorDlc))
        ) {
            val meta = DlcUpdateService.loadMetaForVersion(cursorBase, cursorDlc) ?: break
            val oldBase = resolveMetaSourceBase(meta)
            val oldDlc = resolveMetaSourceDlc(meta)
            val newBase = resolveMetaTargetBase(meta)
            val newDlc = resolveMetaTargetDlc(meta)
            if (oldBase.isBlank() || oldDlc.isBlank() || newBase.isBlank() || newDlc.isBlank()) break
            if (cursorBase != newBase || cursorDlc != newDlc) break
            reverse.add(meta)
            cursorBase = oldBase
            cursorDlc = oldDlc
        }
        if (reverse.isEmpty()) return listOf()
        val reached = if (pairShown) shownBase == cursorBase && shownDlc == cursorDlc else shownDlc == cursorDlc
        if (!reached) return listOf()
        Collections.reverse(reverse)
        return reverse
    }

    private fun buildPendingChangelogText(metas: List<DlcUpdateMeta>?, lang: String?): String {
        if (metas.isNullOrEmpty()) return ""
        val builder = StringBuilder()
        for (meta in metas) {
            val log = meta.resolveChangelog(lang)
            if (log.isBlank()) continue
            if (builder.isNotEmpty()) {
                builder.append("\n\n")
            }
            val fromMarker = composeChangelogMarker(meta.getBaseOld(), meta.getDlcOld())
            val toMarker = composeChangelogMarker(resolveMetaTargetBase(meta), resolveMetaTargetDlc(meta))
            builder.append(fromMarker)
            if (fromMarker != toMarker) {
                builder.append(" -> ").append(toMarker)
            }
            builder.append("\n").append(log.trim())
        }
        return builder.toString()
    }

    private fun isSameChangelogMarker(shown: String?, currentMarker: String?, currentDlc: String?): Boolean {
        if (shown.isNullOrBlank()) return false
        val normalizedShown = shown.trim()
        if (isPairMarker(normalizedShown)) {
            return normalizedShown == (currentMarker ?: "").trim()
        }
        return safeVersion(normalizedShown) == safeVersion(currentDlc)
    }

    private fun isPairMarker(marker: String?): Boolean {
        return marker != null && marker.indexOf('+') > 0
    }

    private fun extractDlcFromMarker(marker: String?): String {
        if (marker.isNullOrBlank()) return safeVersion(null)
        val plus = marker.lastIndexOf('+')
        return if (plus >= 0 && plus + 1 < marker.length) {
            safeVersion(marker.substring(plus + 1))
        } else {
            safeVersion(marker)
        }
    }

    private fun extractBaseFromMarker(marker: String?): String {
        if (marker.isNullOrBlank()) return safeVersion(null)
        val plus = marker.indexOf('+')
        return if (plus > 0) {
            safeVersion(marker.substring(0, plus))
        } else {
            safeVersion(null)
        }
    }

    private fun composeChangelogMarker(base: String?, dlc: String?): String {
        return safeVersion(base) + "+" + safeVersion(dlc)
    }

    private fun resolveMetaSourceDlc(meta: DlcUpdateMeta?): String {
        return safeVersion(meta?.getDlcOld())
    }

    private fun resolveMetaTargetDlc(meta: DlcUpdateMeta?): String {
        if (meta == null) return safeVersion(null)
        val dlcNew = meta.getDlcNew()
        if (dlcNew.isNullOrBlank()) {
            return resolveMetaSourceDlc(meta)
        }
        return safeVersion(dlcNew)
    }

    private fun resolveMetaSourceBase(meta: DlcUpdateMeta?): String {
        return safeVersion(meta?.getBaseOld())
    }

    private fun resolveMetaTargetBase(meta: DlcUpdateMeta?): String {
        if (meta == null) return safeVersion(null)
        val baseNew = meta.getBaseNew()
        if (baseNew.isNullOrBlank()) {
            return resolveMetaSourceBase(meta)
        }
        return safeVersion(baseNew)
    }

    private fun safeVersion(value: String?): String {
        return if (value.isNullOrBlank()) "1.0.0" else value.trim()
    }

    @JvmStatic
    fun syncEnabledResourcePacks() {
        val minecraft = Minecraft.getInstance()
        val repo = minecraft.resourcePackRepository
        try {
            repo.reload()
            val selected = LinkedHashSet(repo.selectedIds)
            val originalSelected = LinkedHashSet(selected)
            var modified = false
            val grouped = DlcScanner().scan()
            for (item in (grouped.getOrDefault(DlcType.RESOURCE_PACK, listOf()) ?: listOf()).filterNotNull()) {
                if (!item.isEnabled()) continue
                val sourcePath = item.getSourcePath() ?: continue
                val fileName = sourcePath.fileName.toString()
                var packId = resolvePackId(repo, fileName)
                if (packId == null) {
                    val target = minecraft.resourcePackDirectory.resolve(fileName)
                    copyRecursive(sourcePath, target)
                    repo.reload()
                    packId = resolvePackId(repo, fileName)
                    modified = true
                }
                if (packId != null && !isPackSelected(repo, selected, fileName)) {
                    if (selected.add(packId)) {
                        modified = true
                    }
                }
            }
            if (modified || selected != originalSelected) {
                repo.setSelected(selected)
                minecraft.options.resourcePacks = ArrayList(selected)
                minecraft.options.save()
                minecraft.reloadResourcePacks()
            }
        } catch (_: Throwable) {
        }
    }

    private fun createEntryFromServer(data: ServerData): DlcServerEntry? {
        val entry = DlcServerEntry()
        entry.name = data.name
        val ip = data.ip ?: ""
        var host = ip
        val port = DlcConfig.getServerPort()
        if (ip.startsWith("[") && ip.contains("]")) {
            val end = ip.indexOf(']')
            if (end > 1) {
                host = ip.substring(1, end)
            }
        } else {
            val index = ip.lastIndexOf(':')
            if (index > 0 && index < ip.length - 1 && ip.indexOf(':') == index) {
                host = ip.substring(0, index)
            }
        }
        entry.host = host
        entry.port = port
        entry.useTcp = true
        return entry
    }

    private fun fetchServerInfo(entry: DlcServerEntry?): DlcServerInfo? {
        return try {
            DlcServerConnection.fetchServerInfo(entry, 5000, currentPlayerName(), currentPlayerUuid())
        } catch (_: Exception) {
            null
        }
    }

    private fun updateTitleNoticeText(config: DlcNoticeConfig, lang: String?) {
        val langKey = lang ?: ""
        if (langKey != titleNoticeLang) {
            titleNoticeLang = langKey
            titleNoticeTitle = config.getTitle().resolve(langKey)
            titleNoticeSubheadEnabled = config.getSubhead().enabled
            titleNoticeSubhead = config.getSubhead().text.resolve(langKey)
            titleNoticeText = ""
            titleNoticeLoading = false
            titleNoticeFuture = null
            titleNoticeScrollOffset = 0
            titleNoticeDraggingScrollbar = false
            val url = config.getContentUrl(langKey)
            if (url.isBlank()) {
                titleNoticeText = config.getContents().resolve(langKey)
            } else {
                titleNoticeLoading = true
                val fallback = config.getContents().resolve(langKey)
                titleNoticeFuture = CompletableFuture.supplyAsync {
                    val remote = config.resolveContents(langKey)
                    if (remote.isNullOrBlank()) fallback else remote
                }
            }
        }
        val future = titleNoticeFuture
        if (titleNoticeLoading && future != null && future.isDone) {
            val fallback = config.getContents().resolve(titleNoticeLang)
            titleNoticeText = try {
                val resolved = future.getNow(fallback)
                if (resolved.isNullOrBlank()) fallback else resolved
            } catch (_: Exception) {
                fallback
            }
            titleNoticeLoading = false
            titleNoticeFuture = null
        }
    }

    private fun currentPlayerName(): String? {
        val minecraft = Minecraft.getInstance()
        val profile = minecraft.player?.gameProfile
        val name = profile?.name
        if (!name.isNullOrBlank()) {
            return name
        }
        return CrossPlatformRuntime.playerName()
    }

    private fun currentPlayerUuid(): String? {
        val minecraft = Minecraft.getInstance()
        val id = minecraft.player?.gameProfile?.id
        if (id != null) {
            return id.toString()
        }
        return CrossPlatformRuntime.playerUuid()
    }

    private fun findEntryByKey(key: String?): DlcServerEntry? {
        if (key == null) return null
        for (entry in DlcServerList.getEntries()) {
            if (key == entry.getKey()) {
                return entry
            }
        }
        return null
    }

    private fun saveEntryToList(entry: DlcServerEntry) {
        DlcServerList.load()
        val list = ArrayList(DlcServerList.getEntries())
        var found = false
        for (i in list.indices) {
            val existing = list[i]
            if (entry.getKey() == existing.getKey()) {
                existing.password = entry.password
                if (!entry.name.isNullOrBlank()) {
                    existing.name = entry.name
                }
                found = true
                break
            }
        }
        if (!found) {
            list.add(entry)
        }
        DlcServerList.setEntries(list)
        DlcServerList.setLastSelectedKey(entry.getKey())
        DlcServerList.save()
    }

    private fun resolvePackId(repo: PackRepository, fileName: String): String? {
        val fileId = "file/$fileName"
        if (repo.isAvailable(fileId)) return fileId
        if (repo.isAvailable(fileName)) return fileName
        for (id in repo.availableIds) {
            if (id.endsWith("/$fileName") || id.endsWith(fileName)) {
                return id
            }
        }
        return null
    }

    private fun isPackSelected(repo: PackRepository, selected: Set<String>, fileName: String): Boolean {
        val fileId = "file/$fileName"
        for (id in selected) {
            if (!repo.isAvailable(id)) continue
            if (id == fileId || id == fileName || id.endsWith("/$fileName") || id.endsWith(fileName)) {
                return true
            }
        }
        return false
    }

    private fun ensurePackArchiveRoot(dlcRoot: Path?) {
        if (dlcRoot == null) {
            return
        }
        try {
            Files.createDirectories(dlcRoot.resolve("pack"))
        } catch (_: IOException) {
        }
    }

    @Throws(IOException::class)
    private fun copyRecursive(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(dir)
                        Files.createDirectories(target.resolve(relative))
                        return FileVisitResult.CONTINUE
                    }

                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(file)
                        Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } else {
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun uninstallIncompatiblePacksForCurrentVersion() {
        try {
            val reports = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstallIncompatibleForCurrentVersion()
            if (reports.isNotEmpty()) {
                LOGGER.info("[dlc-manager] auto uninstalled incompatible DLC packs: count={}", reports.size)
            }
        } catch (ex: Exception) {
            LOGGER.warn("[dlc-manager] failed to auto uninstall incompatible DLC packs", ex)
        }
    }
}
