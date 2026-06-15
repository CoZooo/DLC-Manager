package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.pack.DlcPackArchive
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller
import com.cozooo.dlc_manager.client.pack.DlcPackMeta
import com.cozooo.dlc_manager.client.pack.DlcPackPackage
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.core.DlcFilePicker
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

class DlcPackManagerScreen(
    private val parent: Screen?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.pack.manager_title")) {
    private var backButton: Button? = null
    private var importButton: Button? = null
    private var uninstallButton: Button? = null
    private var installedPacks: List<DlcPackMeta> = emptyList()
    private var selectedIndex = 0
    private var status: Component? = null

    override fun init() {
        refreshInstalled()
        clearWidgets()
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 28
        val gap = 10
        val total = buttonWidth * 3 + gap * 2
        val startX = width / 2 - total / 2
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import")) {
            importPack()
        }.bounds(startX, y, buttonWidth, buttonHeight).build()
        uninstallButton = Button.builder(Component.translatable("dlc_manager.pack.uninstall")) {
            uninstallSelected()
        }.bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight).build()
        uninstallButton?.active = installedPacks.isNotEmpty()
        backButton = Button.builder(Component.translatable("gui.back")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight).build()
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
        return false
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
        val x = max(20, width / 2 - 150)
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
        for ((index, pack) in installedPacks.take(8).withIndex()) {
            val color = if (index == selectedIndex) 0x66CCFF else 0xD8D8D8
            guiGraphics.drawString(
                font,
                Component.literal((if (index == selectedIndex) "> " else "  ") + pack.identifier + "  " + pack.version),
                x,
                y,
                color,
                false
            )
            y += 14
        }
    }

    private fun importPack() {
        if (!DlcPathConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled")
            return
        }
        val zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").string) ?: return
        var staging: Path? = null
        try {
            val extracted = DlcPackArchive.extractToStaging(zip, DlcPaths.dlcRoot())
            staging = extracted.stagingDir
            val result = DlcPackPackage.inspect(staging)
            val pack = result.pack
            if (!result.valid || pack == null) {
                status = Component.translatable("dlc_manager.pack.import_invalid", result.error ?: "unknown")
                return
            }
            val installer = DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot())
            val precheck = installer.precheck(pack)
            if (precheck.hasBlockingIssues()) {
                status = Component.translatable("dlc_manager.pack.import_failed", describeBlockingPrecheck(precheck))
                return
            }
            if (precheck.requiresRuntimeConfirmation()) {
                val confirmStaging = staging
                staging = null
                confirmRuntimeChanges(installer, pack, precheck, confirmStaging)
                return
            }
            installPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.strict())
        } catch (ex: Exception) {
            status = Component.translatable("dlc_manager.pack.import_failed", ex.message ?: ex.javaClass.simpleName)
        } finally {
            DlcPackArchive.deleteRecursive(staging)
        }
    }

    private fun confirmRuntimeChanges(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        precheck: DlcPackInstaller.PrecheckResult,
        staging: Path?,
    ) {
        val message = Component.translatable("dlc_manager.pack.runtime_confirm", DlcConfirmUtil.buildPackRuntimeChangeMessage(pack, precheck))
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    try {
                        if (accepted) {
                            installPreparedPack(installer, pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges())
                        } else {
                            status = Component.translatable("dlc_manager.pack.import_cancelled")
                            rebuildPackWidgets()
                        }
                    } finally {
                        DlcPackArchive.deleteRecursive(staging)
                    }
                },
                title,
                message,
                Component.translatable("dlc_manager.confirm_yes"),
                Component.translatable("dlc_manager.confirm_no"),
            )
        )
    }

    private fun installPreparedPack(
        installer: DlcPackInstaller,
        pack: DlcPackPackage,
        options: DlcPackInstaller.InstallOptions,
    ) {
        val report = installer.install(pack, options)
        status = Component.translatable("dlc_manager.pack.import_success", report.identifier, report.filesWritten)
        refreshInstalled()
        rebuildPackWidgets()
    }

    private fun describeBlockingPrecheck(precheck: DlcPackInstaller.PrecheckResult): String {
        if (precheck.forbiddenPaths.isNotEmpty()) {
            return Component.translatable("dlc_manager.pack.forbidden_paths", joinLimited(precheck.forbiddenPaths)).string
        }
        if (precheck.missingDependencies.isNotEmpty()) {
            return Component.translatable("dlc_manager.pack.missing_dependencies", joinLimited(precheck.missingDependencies)).string
        }
        return "unknown"
    }

    private fun joinLimited(values: List<String>): String {
        val limit = minOf(values.size, 6)
        var joined = values.subList(0, limit).joinToString(", ")
        if (values.size > limit) {
            joined += ", ..."
        }
        return joined
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

    private fun showMessage(message: Component) {
        Minecraft.getInstance().setScreen(
            DlcSingleButtonScreen(
                this,
                Component.translatable("dlc_manager.pack.manager_title"),
                message,
                Component.translatable("gui.back"),
            )
        )
    }
}
