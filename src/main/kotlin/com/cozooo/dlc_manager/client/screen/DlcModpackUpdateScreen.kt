package com.cozooo.dlc_manager.client.screen

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
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import java.util.concurrent.atomic.AtomicBoolean

class DlcModpackUpdateScreen @JvmOverloads constructor(
    private val parent: Screen?,
    checkResult: CheckResult?,
    private val serverEntry: DlcServerEntry? = null,
    private val allowSkipThisUpdate: Boolean = false,
) : DlcPageScreen(parent, Component.translatable("dlc_manager.pack_update.title")) {
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

    private fun initControls() {

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

    private fun formatVersionLineValue(current: String?, target: String?): String {
        val from = normalizeVersion(current)
        val to = normalizeVersion(target)
        if (from != to) {
            return "$from -> $to"
        }
        return from
    }

    private fun formatTransition(step: ReleaseStep): String {
        return normalizeVersion(step.oldBase) + "+" + normalizeVersion(step.oldDlc) +
            " -> " +
            normalizeVersion(step.newBase) + "+" + normalizeVersion(step.newDlc)
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







    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 0

        val list = section(null, null); row(list, tr("dlc_manager.pack_update.base"), formatVersionLineValue(checkResult.currentBase, checkResult.targetBase)); row(list, tr("dlc_manager.pack_update.dlc"), formatVersionLineValue(checkResult.currentDlc, checkResult.targetDlc))
        if (checkResult.hasLatestGap()) text(list, tr("dlc_manager.pack_update.latest_unreachable_no_download"), "page-warning")
        val steps = checkResult.fullSteps.takeIf { it.isNotEmpty() } ?: checkResult.steps
        for (step in steps) {
            val part = section(formatTransition(step), null)
            markdown(part, step.resolveChangelog(DlcPlatformServices.languageCode()))
        }
        live(content, { statusText }, "page-description"); live(content, { errorText }, "page-error"); progress(content) { progress.toDouble() }
        skipButton?.let { mount(footer, it) }; skipThisUpdateButton?.let { mount(footer, it) }; openButton?.let { mount(footer, it) }; downloadButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }

    }
    override fun updatePage() {
        updateButtons()
        if (pageRevision != 0) refreshPage()
    }

}
