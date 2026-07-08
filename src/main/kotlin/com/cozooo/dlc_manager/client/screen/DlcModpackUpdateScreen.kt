package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CheckResult
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.DownloadResult
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.ReleaseStep
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.Side
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcModpackUpdateScreen @JvmOverloads constructor(
    private val parent: Screen?,
    checkResult: CheckResult?,
    private val serverEntry: DlcServerEntry? = null,
    private val allowSkipThisUpdate: Boolean = false,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.pack_update.title")) {
    private val checkResult: CheckResult = checkResult ?: CheckResult()
    private val side: Side = if (serverEntry == null) Side.CLIENT else Side.SERVER
    private var openButton: Button? = null
    private var downloadButton: Button? = null
    private var skipThisUpdateButton: Button? = null
    private var skipButton: Button? = null
    @Volatile
    private var downloading = false
    @Volatile
    private var progress = 0f
    @Volatile
    private var statusText = ""
    @Volatile
    private var errorText = ""
    private val cancelRequested = AtomicBoolean(false)
    private val changelogLines: MutableList<ChangelogLine> = ArrayList()
    private var changelogLeft = 0
    private var changelogRight = 0
    private var changelogTop = 0
    private var changelogBottom = 0
    private var changelogWidth = 0
    private var scrollOffset = 0
    private var maxScroll = 0
    private var visibleHeight = 0
    private var totalContentHeight = 0
    private var scrollbarX = 0
    private var scrollbarTop = 0
    private var scrollbarBottom = 0
    private var scrollbarHeight = 0
    private var draggingScrollbar = false
    private var dragStartY = 0
    private var dragStartScroll = 0
    private var changelogLang = ""

    constructor(parent: Screen?, checkResult: CheckResult?, allowSkipThisUpdate: Boolean) :
        this(parent, checkResult, null, allowSkipThisUpdate)

    override fun init() {
        relayoutChangelog(true)
        val showSkipThis = allowSkipThisUpdate && side == Side.CLIENT
        val buttonWidth = if (showSkipThis) 100 else 120
        val buttonHeight = 20
        val spacing = 6
        val buttonCount = if (showSkipThis) 4 else 3
        val totalWidth = buttonWidth * buttonCount + spacing * (buttonCount - 1)
        val startX = width / 2 - totalWidth / 2
        val y = height - 40
        val open = Button.builder(Component.translatable("dlc_manager.pack_update.open_modpack")) { openModpackUrl() }
            .bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)
            .build()
        val download = Button.builder(Component.translatable("dlc_manager.pack_update.download")) { onDownloadButton() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        val thirdX = startX + (buttonWidth + spacing) * 2
        if (showSkipThis) {
            val skipThis = Button.builder(Component.translatable("dlc_manager.self_update.skip_this_update")) { skipThisUpdate() }
                .bounds(thirdX, y, buttonWidth, buttonHeight)
                .build()
            val skip = Button.builder(Component.translatable("dlc_manager.pack_update.skip")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(startX + (buttonWidth + spacing) * 3, y, buttonWidth, buttonHeight)
                .build()
            skipThisUpdateButton = skipThis
            skipButton = skip
        } else {
            skipThisUpdateButton = null
            skipButton = Button.builder(Component.translatable("dlc_manager.pack_update.skip")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(thirdX, y, buttonWidth, buttonHeight)
                .build()
        }
        openButton = open
        downloadButton = download
        addRenderableWidget(open)
        addRenderableWidget(download)
        skipThisUpdateButton?.let { addRenderableWidget(it) }
        skipButton?.let { addRenderableWidget(it) }
        updateButtons()
    }

    private fun skipThisUpdate() {
        DlcPackReleaseUpdateService.skipPromptForUpdate(checkResult, side)
        Minecraft.getInstance().setScreen(parent)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        updateButtons()
        relayoutChangelog(false)
        val centerX = width / 2
        var y = height / 2 - 78 + CONTENT_Y_OFFSET
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 20

        y = drawCenteredVersionLine(
            guiGraphics,
            centerX,
            y,
            Component.translatable("dlc_manager.pack_update.base").string,
            checkResult.currentBase,
            checkResult.targetBase,
        )
        y += 14
        y = drawCenteredVersionLine(
            guiGraphics,
            centerX,
            y,
            Component.translatable("dlc_manager.pack_update.dlc").string,
            checkResult.currentDlc,
            checkResult.targetDlc,
        )
        y += 16
        y = renderChangelog(guiGraphics, y)

        if (downloading) {
            guiGraphics.drawCenteredString(font, statusText, centerX, y, 0xE6E6E6)
            y += 14
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, progress)
            val percent = (max(0f, min(1f, progress)) * 100f).roundToInt()
            guiGraphics.drawCenteredString(font, "$percent%", centerX, y + 12, 0xC8C8C8)
        } else if (errorText.isNotBlank()) {
            guiGraphics.drawCenteredString(font, errorText, centerX, y, 0xFF7777)
        } else if (checkResult.hasLatestGap()) {
            val warning = if (checkResult.canDownload()) {
                Component.translatable(
                    "dlc_manager.pack_update.latest_unreachable",
                    normalizeVersion(checkResult.downloadBase),
                    normalizeVersion(checkResult.downloadDlc),
                )
            } else {
                Component.translatable("dlc_manager.pack_update.latest_unreachable_no_download")
            }
            guiGraphics.drawCenteredString(font, warning, centerX, y, WARNING_COLOR)
            y += 12
            if (checkResult.canDownload()) {
                guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack_update.partial_download_hint"), centerX, y, WARNING_COLOR)
            } else {
                guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack_update.asset_missing"), centerX, y, WARNING_COLOR)
            }
        } else if (!checkResult.canDownload()) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack_update.asset_missing"), centerX, y, WARNING_COLOR)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderChangelog(guiGraphics: GuiGraphics, y: Int): Int {
        if (changelogLines.isEmpty()) {
            return y
        }
        guiGraphics.drawString(font, Component.translatable("dlc_manager.update.changelog_tooltip_title"), changelogLeft, y, 0xE6E6E6, false)
        drawChangelogPanel(guiGraphics)
        return changelogBottom + 8
    }

    private fun drawChangelogPanel(guiGraphics: GuiGraphics) {
        guiGraphics.fill(changelogLeft - 6, changelogTop - 6, changelogRight + 6, changelogBottom + 6, PANEL_BG)
        guiGraphics.fill(changelogLeft - 6, changelogTop - 6, changelogRight + 6, changelogTop - 5, PANEL_BORDER)
        guiGraphics.fill(changelogLeft - 6, changelogBottom + 5, changelogRight + 6, changelogBottom + 6, PANEL_BORDER)
        guiGraphics.fill(changelogLeft - 6, changelogTop - 6, changelogLeft - 5, changelogBottom + 6, PANEL_BORDER)
        guiGraphics.fill(changelogRight + 5, changelogTop - 6, changelogRight + 6, changelogBottom + 6, PANEL_BORDER)

        var textY = changelogTop - scrollOffset
        guiGraphics.enableScissor(changelogLeft, changelogTop, changelogRight, changelogBottom)
        try {
            for (line in changelogLines) {
                if (line.hr) {
                    val hrY = textY + line.height / 2
                    if (hrY >= changelogTop && hrY <= changelogBottom - 1) {
                        guiGraphics.fill(changelogLeft, hrY, changelogRight, hrY + 1, 0xFF5A5A5A.toInt())
                    }
                    textY += line.height
                    continue
                }
                if (line.empty) {
                    textY += line.height
                    continue
                }
                if (textY <= changelogBottom - line.height && textY + line.height >= changelogTop) {
                    if (line.scale > 1.0f) {
                        guiGraphics.pose().pushPose()
                        guiGraphics.pose().scale(line.scale, line.scale, 1.0f)
                        guiGraphics.drawString(font, line.sequence, (changelogLeft / line.scale).toInt(), (textY / line.scale).toInt(), line.color, false)
                        guiGraphics.pose().popPose()
                    } else {
                        guiGraphics.drawString(font, line.sequence, changelogLeft, textY, line.color, false)
                    }
                }
                textY += line.height
            }
        } finally {
            guiGraphics.disableScissor()
        }
        drawScrollBar(guiGraphics)
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !downloading
    }

    override fun onClose() {
        if (!downloading) {
            super.onClose()
        }
    }

    private fun openModpackUrl() {
        val url = checkResult.modpackUrl?.trim() ?: ""
        if (url.isBlank()) {
            return
        }
        Util.getPlatform().openUri(url)
    }

    private fun normalizeVersion(value: String?): String {
        if (value.isNullOrBlank()) {
            return "1.0.0"
        }
        return value
    }

    private fun drawCenteredVersionLine(guiGraphics: GuiGraphics, centerX: Int, y: Int, prefix: String, current: String?, target: String?): Int {
        val prefixText = "$prefix "
        val versionText = formatVersionLineValue(current, target)
        val totalWidth = font.width(prefixText) + font.width(versionText)
        val x = centerX - totalWidth / 2
        guiGraphics.drawString(font, prefixText, x, y, LABEL_COLOR, false)
        guiGraphics.drawString(font, versionText, x + font.width(prefixText), y, VERSION_COLOR, false)
        return y
    }

    private fun formatVersionLineValue(current: String?, target: String?): String {
        val from = normalizeVersion(current)
        val to = normalizeVersion(target)
        if (from != to) {
            return "$from -> $to"
        }
        return from
    }

    private fun relayoutChangelog(force: Boolean) {
        val language = DlcPlatformServices.languageCode()
        val nextLeft = 40
        val nextRight = max(nextLeft + 120, width - 40)
        val nextTop = height / 2 - 4 + CONTENT_Y_OFFSET
        val nextBottom = max(nextTop + 60, height - 60 + CONTENT_Y_OFFSET)
        val nextWidth = max(120, nextRight - nextLeft - 20)
        val changed = force ||
            language != changelogLang ||
            nextLeft != changelogLeft ||
            nextRight != changelogRight ||
            nextTop != changelogTop ||
            nextBottom != changelogBottom ||
            nextWidth != changelogWidth
        changelogLeft = nextLeft
        changelogRight = nextRight
        changelogTop = nextTop
        changelogBottom = nextBottom
        changelogWidth = nextWidth
        scrollbarX = changelogRight - 4
        scrollbarTop = changelogTop
        scrollbarBottom = changelogBottom
        visibleHeight = max(10, changelogBottom - changelogTop)
        if (changed) {
            changelogLang = language
            rebuildChangelogLines(language)
        }
    }

    private fun rebuildChangelogLines(language: String) {
        changelogLines.clear()
        val lineStep = font.lineHeight + 2
        val downloadableSteps = checkResult.steps ?: emptyList()
        appendChangelogSteps(downloadableSteps, language, lineStep)
        val unreachableSteps = resolveUnreachableSteps(downloadableSteps)
        if (unreachableSteps.isNotEmpty()) {
            if (changelogLines.isNotEmpty()) {
                changelogLines.add(ChangelogLine.empty(lineStep))
            }
            for (line in font.split(Component.translatable("dlc_manager.pack_update.unreachable_changelog"), changelogWidth)) {
                changelogLines.add(ChangelogLine.text(line, WARNING_COLOR, 1.0f, lineStep))
            }
            changelogLines.add(ChangelogLine.empty(lineStep))
            appendChangelogSteps(unreachableSteps, language, lineStep)
        }
        totalContentHeight = 0
        for (line in changelogLines) {
            totalContentHeight += line.height
        }
        maxScroll = max(0, totalContentHeight - visibleHeight)
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll
        }
        if (maxScroll <= 0) {
            scrollOffset = 0
        }
    }

    private fun appendChangelogSteps(steps: List<ReleaseStep?>?, language: String, lineStep: Int) {
        if (steps.isNullOrEmpty()) {
            return
        }
        for (stepIndex in steps.indices) {
            val step = steps[stepIndex] ?: continue
            val transition = formatTransition(step)
            for (line in font.split(Component.literal(transition), changelogWidth)) {
                changelogLines.add(ChangelogLine.text(line, SECTION_COLOR, 1.0f, lineStep))
            }
            val body = step.resolveChangelog(language)
            if (!body.isNullOrBlank()) {
                for (line in DlcMarkdownRenderer.wrapMarkdown(font, body.trim(), changelogWidth)) {
                    if (DlcMarkdownRenderer.isHrLine(line)) {
                        changelogLines.add(ChangelogLine.hr(lineStep))
                        continue
                    }
                    val scale = DlcMarkdownRenderer.getHeadingScale(font, line, changelogWidth)
                    val height = DlcMarkdownRenderer.getLineHeight(font, line, lineStep, changelogWidth)
                    changelogLines.add(ChangelogLine.text(line, TEXT_COLOR, scale, height))
                }
            }
            if (stepIndex < steps.size - 1) {
                changelogLines.add(ChangelogLine.empty(lineStep))
            }
        }
    }

    private fun resolveUnreachableSteps(downloadableSteps: List<ReleaseStep?>?): List<ReleaseStep> {
        val fullSteps = checkResult.fullSteps ?: emptyList()
        if (fullSteps.isEmpty() || !checkResult.hasLatestGap()) {
            return emptyList()
        }
        var index = 0
        while (index < fullSteps.size &&
            downloadableSteps != null &&
            index < downloadableSteps.size &&
            sameTransition(fullSteps[index], downloadableSteps[index])
        ) {
            index++
        }
        if (index >= fullSteps.size) {
            return emptyList()
        }
        return fullSteps.subList(index, fullSteps.size)
    }

    private fun sameTransition(left: ReleaseStep?, right: ReleaseStep?): Boolean {
        if (left == null || right == null) {
            return false
        }
        return normalizeVersion(left.oldBase) == normalizeVersion(right.oldBase) &&
            normalizeVersion(left.oldDlc) == normalizeVersion(right.oldDlc) &&
            normalizeVersion(left.newBase) == normalizeVersion(right.newBase) &&
            normalizeVersion(left.newDlc) == normalizeVersion(right.newDlc)
    }

    private fun formatTransition(step: ReleaseStep): String {
        return normalizeVersion(step.oldBase) + "+" + normalizeVersion(step.oldDlc) +
            " -> " +
            normalizeVersion(step.newBase) + "+" + normalizeVersion(step.newDlc)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(delta: Double): Boolean {
        if (maxScroll <= 0) {
            return false
        }
        val step = font.lineHeight + 2
        scrollOffset -= (delta * step).roundToInt()
        if (scrollOffset < 0) {
            scrollOffset = 0
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll
        }
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isOverChangelogContent(mouseX, mouseY)) {
            var textY = changelogTop - scrollOffset
            for (line in changelogLines) {
                if (line.hr || line.empty) {
                    textY += line.height
                    continue
                }
                if (mouseY >= textY && mouseY < textY + line.height) {
                    val relX = mouseX.toInt() - changelogLeft
                    val maxWidth = (font.width(line.sequence) * line.scale).roundToInt()
                    if (relX >= 0 && relX <= maxWidth) {
                        val styleX = if (line.scale > 1.0f) (relX / line.scale).toInt() else relX
                        val style = findStyleAt(line.sequence, styleX)
                        if (style != null && handleComponentClicked(style)) {
                            return true
                        }
                    }
                    break
                }
                textY += line.height
            }
        }
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            updateScrollFromDrag(mouseY)
            draggingScrollbar = true
            dragStartY = mouseY.toInt()
            dragStartScroll = scrollOffset
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            val trackHeight = max(1, scrollbarBottom - scrollbarTop - scrollbarHeight)
            val delta = mouseY.toInt() - dragStartY
            val next = dragStartScroll + (delta * (maxScroll / trackHeight.toDouble())).roundToInt()
            scrollOffset = max(0, min(maxScroll, next))
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun isOverChangelogContent(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= changelogLeft && mouseX <= changelogRight &&
            mouseY >= changelogTop && mouseY <= changelogBottom
    }

    private fun findStyleAt(sequence: FormattedCharSequence?, x: Int): Style? {
        if (sequence == null || x < 0) {
            return null
        }
        return font.splitter.componentStyleAtWidth(sequence, x)
    }

    private fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean {
        return maxScroll > 0 &&
            mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + 6 &&
            mouseY >= scrollbarTop && mouseY <= scrollbarBottom
    }

    private fun updateScrollFromDrag(mouseY: Double) {
        if (maxScroll <= 0) {
            scrollOffset = 0
            return
        }
        val trackHeight = max(1, scrollbarBottom - scrollbarTop - scrollbarHeight)
        val knobTop = mouseY.toInt() - scrollbarHeight / 2
        val rel = max(0, min(trackHeight, knobTop - scrollbarTop))
        scrollOffset = (rel * (maxScroll / trackHeight.toDouble())).roundToInt()
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics) {
        if (maxScroll <= 0) {
            return
        }
        val trackHeight = max(1, scrollbarBottom - scrollbarTop)
        scrollbarHeight = max(18, (trackHeight * (visibleHeight / max(visibleHeight, totalContentHeight).toDouble())).toInt())
        if (scrollbarHeight > trackHeight) {
            scrollbarHeight = trackHeight
        }
        val travel = max(1, trackHeight - scrollbarHeight)
        val knobTop = scrollbarTop + (scrollOffset / maxScroll.toDouble() * travel).roundToInt()
        guiGraphics.fill(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, 0x663A3F48)
        guiGraphics.fill(scrollbarX, knobTop, scrollbarX + 4, knobTop + scrollbarHeight, 0xFF8A95A8.toInt())
    }

    private fun onDownloadButton() {
        if (downloading) {
            cancelRequested.set(true)
            return
        }
        startDownload()
    }

    private fun startDownload() {
        if (!checkResult.canDownload()) {
            return
        }
        downloading = true
        progress = 0f
        errorText = ""
        cancelRequested.set(false)
        statusText = Component.translatable("dlc_manager.pack_update.downloading").string
        updateButtons()
        Thread({
            val result = if (side == Side.SERVER) {
                DlcPackReleaseUpdateService.downloadServerPackages(checkResult, serverEntry, 600000, this::onProgress, cancelRequested::get)
            } else {
                DlcPackReleaseUpdateService.downloadClientPackages(checkResult, this::onProgress, cancelRequested::get)
            }
            Minecraft.getInstance().execute { finishDownload(result) }
        }, "dlc-pack-release-download").start()
    }

    private fun onProgress(stepIndex: Int, stepCount: Int, fileName: String?, downloaded: Long, total: Long, overallProgress: Float) {
        progress = overallProgress
        val safeFile = if (fileName.isNullOrBlank()) "package.zip" else fileName
        statusText = Component.translatable("dlc_manager.pack_update.downloading_file", stepIndex + 1, stepCount, safeFile).string
    }

    private fun finishDownload(result: DownloadResult?) {
        downloading = false
        updateButtons()
        if (result == null) {
            errorText = DlcMessageAdapter.toDownloadFailureComponent("unknown").string
            return
        }
        if (result.canceled) {
            statusText = ""
            errorText = Component.translatable("dlc_manager.pack_update.canceled").string
            return
        }
        if (!result.success) {
            val reason = if (result.message.isNullOrBlank()) "failed" else result.message
            errorText = DlcMessageAdapter.toDownloadFailureComponent(reason).string
            return
        }
        if (side == Side.SERVER) {
            if (parent is DlcServerUpdateManagerScreen) {
                parent.refreshData()
            }
            Minecraft.getInstance().setScreen(parent)
            return
        }
        val check = DlcUpdateService.checkForUpdates(true)
        if (check.hasUpdates()) {
            DlcUpdateClient.showPrompt(parent, check)
            return
        }
        DlcScreenCompat.addOrUpdatePeriodicNotification(
            Minecraft.getInstance().toasts,
            Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.pack_update.download_success"),
        )
        Minecraft.getInstance().setScreen(parent)
    }

    private fun updateButtons() {
        openButton?.active = !checkResult.modpackUrl.isNullOrBlank() && !downloading
        downloadButton?.let {
            if (downloading) {
                it.active = true
                it.message = Component.translatable("dlc_manager.pack_update.cancel_download")
            } else {
                it.message = if (checkResult.hasLatestGap()) {
                    Component.translatable("dlc_manager.pack_update.download_reachable")
                } else {
                    Component.translatable("dlc_manager.pack_update.download")
                }
                it.active = checkResult.canDownload()
            }
        }
        skipButton?.active = !downloading
        skipThisUpdateButton?.active = !downloading
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, value: Float) {
        val clamped = max(0f, min(1f, value))
        val filled = max(0, min(width, (width * clamped).roundToInt()))
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B.toInt())
        if (filled > 0) {
            guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50.toInt())
        }
        guiGraphics.fill(x, y - 1, x + width, y, 0xFF4A4A4A.toInt())
        guiGraphics.fill(x, y + height, x + width, y + height + 1, 0xFF1A1A1A.toInt())
    }

    private class ChangelogLine private constructor(
        val sequence: FormattedCharSequence,
        val color: Int,
        val scale: Float,
        val height: Int,
        val hr: Boolean,
        val empty: Boolean,
    ) {
        companion object {
            fun text(sequence: FormattedCharSequence, color: Int, scale: Float, height: Int): ChangelogLine {
                return ChangelogLine(sequence, color, scale, height, false, false)
            }

            fun hr(height: Int): ChangelogLine {
                return ChangelogLine(FormattedCharSequence.EMPTY, TEXT_COLOR, 1.0f, height, true, false)
            }

            fun empty(height: Int): ChangelogLine {
                return ChangelogLine(FormattedCharSequence.EMPTY, TEXT_COLOR, 1.0f, height, false, true)
            }
        }
    }

    companion object {
        private const val TEXT_COLOR = 0xC8C8C8
        private const val LABEL_COLOR = 0xDCDCDC
        private const val VERSION_COLOR = 0x7FD0FF
        private const val SECTION_COLOR = 0x93D9FF
        private const val WARNING_COLOR = 0xD7B56D
        private const val CONTENT_Y_OFFSET = -30
        private const val PANEL_BG = 0xAA121519.toInt()
        private const val PANEL_BORDER = 0xFF2A2F36.toInt()
    }
}
