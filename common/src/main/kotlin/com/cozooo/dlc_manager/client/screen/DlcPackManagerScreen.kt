package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.pack.DlcPackArchive
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller
import com.cozooo.dlc_manager.client.pack.DlcPackMeta
import com.cozooo.dlc_manager.client.pack.DlcPackPackage
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.core.DlcFilePicker
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

class DlcPackManagerScreen(
    private val parent: Screen?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.pack.manager_title")) {
    private var backButton: Button? = null
    private var importButton: Button? = null
    private var uninstallButton: Button? = null
    private var installedPacks: List<DlcPackMeta> = emptyList()
    private var selectedIndex = 0
    private var scrollIndex = 0
    private var status: Component? = null
    @Volatile private var importingPack = false
    @Volatile private var cancelImportRequested = false

    override fun init() {
        refreshInstalled()
        clearWidgets()
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 28
        val gap = 10
        val total = buttonWidth * 3 + gap * 2
        val startX = width / 2 - total / 2
        val importLabel = if (importingPack) {
            Component.translatable("dlc_manager.pack.cancel")
        } else {
            Component.translatable("dlc_manager.pack.import")
        }
        importButton = Button.builder(importLabel) {
            if (importingPack) {
                requestCancelImport()
            } else {
                importPack()
            }
        }.bounds(startX, y, buttonWidth, buttonHeight).build()
        importButton?.active = DlcPathConfig.isPackFuncOn() && (!importingPack || !cancelImportRequested)
        uninstallButton = Button.builder(Component.translatable("dlc_manager.pack.uninstall")) {
            uninstallSelected()
        }.bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight).build()
        uninstallButton?.active = !importingPack && installedPacks.isNotEmpty()
        backButton = Button.builder(Component.translatable("gui.back")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight).build()
        backButton?.active = !importingPack
        addRenderableWidget(importButton)
        addRenderableWidget(uninstallButton)
        addRenderableWidget(backButton)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        if (!DlcPathConfig.isPackFuncOn()) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.pack.disabled"), width / 2, height / 2 - 8, 0xFFCC66)
        } else {
            renderSummary(guiGraphics)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (importingPack || installedPacks.isEmpty()) {
            return false
        }
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) {
            return false
        }
        val step = if (deltaY > 0) -1 else 1
        scrollIndex = max(0, min(maxScrollValue, scrollIndex + step))
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!importingPack && button == 0 && isInsidePackList(mouseX, mouseY)) {
            val row = ((mouseY - packListTop) / LIST_ROW_HEIGHT).toInt()
            val index = scrollIndex + row
            if (index >= 0 && index < installedPacks.size && row < visiblePackCount) {
                selectedIndex = index
                ensureSelectionVisible()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun renderSummary(guiGraphics: GuiGraphics) {
        val packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack")
        val count = try {
            if (Files.isDirectory(packRoot)) {
                Files.list(packRoot).use { stream -> stream.filter { Files.isDirectory(it) }.count().toInt() }
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
        val x = packListX
        var y = 56
        guiGraphics.drawString(font, Component.translatable("dlc_manager.pack.installed_count", count), x, y, 0xE6E6E6, false)
        y += 18
        guiGraphics.drawString(font, DlcTextFormatter.parse(Component.translatable("dlc_manager.pack.first_stage").string), x, y, 0xC8C8C8, false)
        y += 22
        val currentStatus = status
        if (currentStatus != null) {
            guiGraphics.drawString(font, currentStatus, x, y, 0xFFCC66, false)
            y += 18
        }
        if (installedPacks.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("dlc_manager.pack.none_installed"), x, y, 0x9A9A9A, false)
            return
        }
        clampListState()
        val visibleCount = min(visiblePackCount, installedPacks.size - scrollIndex)
        for (row in 0 until visibleCount) {
            val index = scrollIndex + row
            val pack = installedPacks[index]
            val color = if (index == selectedIndex) 0x66CCFF else 0xD8D8D8
            guiGraphics.drawString(
                font,
                Component.literal((if (index == selectedIndex) "> " else "  ") + pack.identifier + "  " + pack.version),
                x,
                y + row * LIST_ROW_HEIGHT,
                color,
                false
            )
        }
        drawPackScrollBar(guiGraphics)
    }

    private fun importPack() {
        if (!DlcPathConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled")
            return
        }
        val zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").string) ?: return
        startImportPrecheck(zip)
    }

    private fun startImportPrecheck(zip: Path) {
        setImportingPack(true, Component.translatable("dlc_manager.pack.importing"))
        Thread({
            var staging: Path? = null
            try {
                val extracted = DlcPackArchive.extractToStaging(zip, DlcPaths.dlcRoot())
                staging = extracted.stagingDir
                checkImportCanceled()
                val result = DlcPackPackage.inspect(staging)
                val pack = result.pack
                if (!result.valid || pack == null) {
                    DlcPackArchive.deleteRecursive(staging)
                    finishImport(Component.translatable("dlc_manager.pack.import_invalid", result.error ?: "unknown"))
                    return@Thread
                }
                val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                val precheck = installer.precheck(pack)
                if (precheck.hasBlockingIssues()) {
                    DlcPackArchive.deleteRecursive(staging)
                    finishImport(Component.translatable("dlc_manager.pack.import_failed", DlcConfirmUtil.describePackBlockingPrecheck(precheck)))
                    return@Thread
                }
                checkImportCanceled()
                val preparedStaging = staging
                staging = null
                Minecraft.getInstance().execute {
                    handlePreparedPack(installer, pack, precheck, preparedStaging)
                }
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                DlcPackArchive.deleteRecursive(staging)
                finishImport(Component.translatable("dlc_manager.pack.import_cancelled"))
            } catch (ex: Exception) {
                DlcPackArchive.deleteRecursive(staging)
                finishImport(Component.translatable("dlc_manager.pack.import_failed", ex.message ?: ex.javaClass.simpleName))
            }
        }, "dlc-pack-precheck").start()
    }

    private fun handlePreparedPack(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        precheck: DlcPackInstaller.PrecheckResult,
        staging: Path?,
    ) {
        if (cancelImportRequested) {
            DlcPackArchive.deleteRecursive(staging)
            finishImport(Component.translatable("dlc_manager.pack.import_cancelled"))
            return
        }
        importingPack = false
        cancelImportRequested = false
        val installed = findInstalledPack(pack.metadata.identifier)
        if (installed != null) {
            confirmReplacePack(installer, pack, precheck, installed, staging)
            return
        }
        continueImportPack(installer, pack, precheck, false, staging)
    }

    private fun requestCancelImport() {
        cancelImportRequested = true
        status = Component.translatable("dlc_manager.pack.canceling")
        rebuildPackWidgets()
    }

    private fun checkImportCanceled() {
        if (cancelImportRequested) {
            throw DlcDownloadService.DownloadCanceledException("canceled")
        }
    }

    private fun setImportingPack(importing: Boolean, message: Component?) {
        importingPack = importing
        cancelImportRequested = false
        status = message
        rebuildPackWidgets()
    }

    private fun finishImport(message: Component) {
        Minecraft.getInstance().execute {
            importingPack = false
            cancelImportRequested = false
            status = message
            refreshInstalled()
            rebuildPackWidgets()
        }
    }

    private fun withCancelChecker(options: DlcPackInstaller.InstallOptions?): DlcPackInstaller.InstallOptions {
        val effectiveOptions = options ?: DlcPackInstaller.InstallOptions.strict()
        return effectiveOptions.withCancelChecker { cancelImportRequested }
    }

    private fun startInstallPreparedPack(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        options: DlcPackInstaller.InstallOptions,
        replaceExisting: Boolean,
        staging: Path?,
    ) {
        setImportingPack(true, Component.translatable("dlc_manager.pack.importing"))
        Thread({
            try {
                val report = installer.install(pack, replaceExisting, withCancelChecker(options))
                finishImport(Component.translatable("dlc_manager.pack.import_success", report.identifier, report.filesWritten))
            } catch (_: DlcDownloadService.DownloadCanceledException) {
                finishImport(Component.translatable("dlc_manager.pack.import_cancelled"))
            } catch (ex: Exception) {
                finishImport(Component.translatable("dlc_manager.pack.import_failed", ex.message ?: ex.javaClass.simpleName))
            } finally {
                DlcPackArchive.deleteRecursive(staging)
            }
        }, "dlc-pack-install").start()
    }

    private fun continueImportPack(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        precheck: DlcPackInstaller.PrecheckResult,
        replaceExisting: Boolean,
        staging: Path?,
    ) {
        if (precheck.requiresRuntimeConfirmation()) {
            confirmRuntimeChanges(installer, pack, precheck, replaceExisting, staging)
            return
        }
        startInstallPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.strict(), replaceExisting, staging)
    }

    private fun confirmReplacePack(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        precheck: DlcPackInstaller.PrecheckResult,
        installed: DlcPackMeta,
        staging: Path?,
    ) {
        val message = DlcConfirmUtil.buildPackReplaceConfirmMessage(pack, installed)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (!accepted) {
                        status = Component.translatable("dlc_manager.pack.import_cancelled")
                        DlcPackArchive.deleteRecursive(staging)
                        rebuildPackWidgets()
                        return@ConfirmScreen
                    }
                    continueImportPack(installer, pack, precheck, true, staging)
                },
                title,
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun confirmRuntimeChanges(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        precheck: DlcPackInstaller.PrecheckResult,
        replaceExisting: Boolean,
        staging: Path?,
    ) {
        val message = DlcConfirmUtil.buildPackRuntimeConfirmMessage(pack, precheck)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (accepted) {
                        startInstallPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges(), replaceExisting, staging)
                    } else {
                        status = Component.translatable("dlc_manager.pack.import_cancelled")
                        DlcPackArchive.deleteRecursive(staging)
                        rebuildPackWidgets()
                    }
                },
                title,
                message,
                DlcConfirmUtil.confirmYes(),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun uninstallSelected() {
        val selected = installedPacks.getOrNull(selectedIndex) ?: return
        try {
            val report = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstall(selected.identifier)
            status = Component.translatable("dlc_manager.pack.uninstall_success", report.identifier, report.filesRestored)
            refreshInstalled()
            if (selectedIndex >= installedPacks.size) {
                selectedIndex = maxOf(0, installedPacks.size - 1)
            }
            clampListState()
            rebuildPackWidgets()
        } catch (ex: Exception) {
            status = Component.translatable("dlc_manager.pack.uninstall_failed", ex.message ?: ex.javaClass.simpleName)
        }
    }

    private fun refreshInstalled() {
        installedPacks = loadInstalledPacks()
        if (selectedIndex >= installedPacks.size) {
            selectedIndex = maxOf(0, installedPacks.size - 1)
        }
        clampListState()
    }

    private fun loadInstalledPacks(): List<DlcPackMeta> {
        val packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack")
        if (!Files.isDirectory(packRoot)) {
            return emptyList()
        }
        return try {
            Files.list(packRoot).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .map { DlcPackMeta.load(it.resolve("meta.dc")) }
                    .filter { it != null }
                    .map { it!! }
                    .sorted { a, b -> a.identifier.compareTo(b.identifier, ignoreCase = true) }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun rebuildPackWidgets() {
        init(minecraft, width, height)
    }

    private fun findInstalledPack(identifier: String?): DlcPackMeta? {
        if (identifier == null) {
            return null
        }
        return installedPacks.firstOrNull { it.identifier == identifier }
    }

    private val packListX: Int
        get() = max(20, width / 2 - 150)

    private val packListTop: Int
        get() {
            var y = 56 + 18 + 22
            if (status != null) {
                y += 18
            }
            return y
        }

    private val packListBottom: Int
        get() = max(packListTop + LIST_ROW_HEIGHT, height - 42)

    private val visiblePackCount: Int
        get() = max(1, (packListBottom - packListTop) / LIST_ROW_HEIGHT)

    private val maxScroll: Int
        get() = max(0, installedPacks.size - visiblePackCount)

    private fun isInsidePackList(mouseX: Double, mouseY: Double): Boolean {
        val x = packListX
        val top = packListTop
        val bottom = min(packListBottom, top + visiblePackCount * LIST_ROW_HEIGHT)
        return mouseX >= x && mouseX <= x + LIST_WIDTH && mouseY >= top && mouseY < bottom
    }

    private fun clampListState() {
        if (installedPacks.isEmpty()) {
            selectedIndex = 0
            scrollIndex = 0
            return
        }
        selectedIndex = max(0, min(installedPacks.size - 1, selectedIndex))
        scrollIndex = max(0, min(maxScroll, scrollIndex))
        ensureSelectionVisible()
    }

    private fun ensureSelectionVisible() {
        val visible = visiblePackCount
        if (selectedIndex < scrollIndex) {
            scrollIndex = selectedIndex
        } else if (selectedIndex >= scrollIndex + visible) {
            scrollIndex = max(0, selectedIndex - visible + 1)
        }
        scrollIndex = max(0, min(maxScroll, scrollIndex))
    }

    private fun drawPackScrollBar(guiGraphics: GuiGraphics) {
        val maxScrollValue = maxScroll
        if (maxScrollValue <= 0) {
            return
        }
        val top = packListTop
        val bottom = packListBottom
        val trackHeight = max(1, bottom - top)
        val barHeight = max(12, trackHeight * visiblePackCount / max(1, installedPacks.size))
        val barY = top + (trackHeight - barHeight) * scrollIndex / max(1, maxScrollValue)
        val x = packListX + LIST_WIDTH - 6
        guiGraphics.fill(x, top, x + 4, bottom, 0x66333333)
        guiGraphics.fill(x, barY, x + 4, barY + barHeight, 0xFF66CCFF.toInt())
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !importingPack
    }

    override fun onClose() {
        if (!importingPack) {
            super.onClose()
        }
    }

    companion object {
        private const val LIST_ROW_HEIGHT = 14
        private const val LIST_WIDTH = 320
    }
}
