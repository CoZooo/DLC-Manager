package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object DlcUpdatePackGuard {
    fun confirmBeforeUpdate(
        returnScreen: Screen,
        packages: List<DlcUpdatePackage?>?,
        handler: DecisionHandler,
    ) {
        if (packages.isNullOrEmpty()) { handler.proceed(null, false); return }
        val unknown = DlcUpdateService.unknownParameters(packages)
        if (unknown.isNotEmpty()) {
            Minecraft.getInstance().setScreen(DlcUpdateCompatibilityScreen(returnScreen, unknown) { action ->
                packages.orEmpty().filterNotNull().forEach { it.setCompatibilityAction(action) }
                confirmBeforeUpdate(returnScreen, packages, handler)
            })
            return
        }
        val target = resolveTargetVersion(packages)
        val check = DlcPackUpdateGuard.check(DlcPaths.dlcRoot(), target.base, target.dlc)
        if (!check.hasInstalledPacks() || !check.hasIncompatiblePacks()) {
            proceed(returnScreen, packages, handler, check, false)
            return
        }
        val title = DlcTextCompat.translatable("dlc_manager.pack.manager_title")
        val message = DlcConfirmUtil.buildPackUpdateIncompatibleConfirmMessage(check)
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen({ accepted ->
                if (accepted) {
                    Minecraft.getInstance().setScreen(returnScreen)
                    proceed(returnScreen, packages, handler, check, true)
                } else {
                    Minecraft.getInstance().setScreen(returnScreen)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    private fun proceed(returnScreen: Screen, packages: List<DlcUpdatePackage?>?, handler: DecisionHandler, check: DlcPackUpdateGuard.CheckResult?, uninstall: Boolean) {
        packages.orEmpty().filterNotNull().forEach { it.setAllowUninstallPacks(uninstall) }
        Minecraft.getInstance().setScreen(DlcWorldBackupScreen(returnScreen, packages.orEmpty().filterNotNull()) { handler.proceed(check, uninstall) })
    }

    fun resolveTargetVersion(packages: List<DlcUpdatePackage?>?): TargetVersion {
        var targetBase: String? = null
        var targetDlc: String? = null
        if (packages != null) {
            for (pkg in packages) {
                val version = pkg?.getManifest()?.getVersion() ?: continue
                val base = normalize(version.getBase())
                val dlcOld = normalize(version.getDlcOld())
                val baseNew = normalize(version.getBaseNew())
                val dlcNew = normalize(version.getDlcNew())
                targetBase = baseNew ?: base
                targetDlc = dlcNew ?: dlcOld
            }
        }
        return TargetVersion(safeVersion(targetBase), safeVersion(targetDlc))
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.takeUnless { it.isBlank() }
    }

    private fun safeVersion(value: String?): String {
        return value?.trim()?.takeUnless { it.isBlank() } ?: "1.0.0"
    }

    fun interface DecisionHandler {
        fun proceed(check: DlcPackUpdateGuard.CheckResult?, uninstallIncompatible: Boolean)
    }

    data class TargetVersion(
        val base: String,
        val dlc: String,
    )
}
