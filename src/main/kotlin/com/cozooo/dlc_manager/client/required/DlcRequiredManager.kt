package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object DlcRequiredManager {
    @JvmStatic
    fun tryShowFromTitle(parent: Screen?): Boolean {
        val missing = DlcRequiredRuntime.pollMissingDownloadsForTitlePrompt()
        if (missing.isEmpty()) {
            return false
        }
        Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(parent, missing))
        return true
    }

    @JvmStatic
    fun markPrompted() {
        DlcRequiredRuntime.markPrompted()
    }

    @JvmStatic
    fun loadEntries(): List<DlcRequiredEntry> {
        return DlcRequiredRuntime.loadEntries()
    }

    @JvmStatic
    fun loadAllEntries(): List<DlcRequiredEntry> {
        return DlcRequiredRuntime.loadAllEntries()
    }

    @JvmStatic
    fun buildIndex(entries: List<DlcRequiredEntry>?): Map<String, DlcRequiredEntry> {
        return DlcRequiredRuntime.buildIndex(entries)
    }

    @JvmStatic
    fun buildDependents(entries: List<DlcRequiredEntry>?): Map<String, Set<String>> {
        return DlcRequiredRuntime.buildDependents(entries)
    }

    @JvmStatic
    fun collectDependencies(roots: List<DlcRequiredEntry>?): List<DlcRequiredEntry> {
        return DlcRequiredRuntime.collectDependencies(roots)
    }

    @JvmStatic
    fun hasMissingDownloads(): Boolean {
        return DlcRequiredRuntime.hasMissingDownloads()
    }

    @JvmStatic
    fun hasMissingDownloadsForPrompt(): Boolean {
        return DlcRequiredRuntime.hasMissingDownloadsForPrompt()
    }

    @JvmStatic
    fun hasMissingOrApply(): Boolean {
        return DlcRequiredRuntime.hasMissingOrApply()
    }

    @JvmStatic
    fun collectMissingDownloads(): List<DlcRequiredEntry> {
        return DlcRequiredRuntime.collectMissingDownloads()
    }

    @JvmStatic
    fun collectMissingDownloadsForPrompt(): List<DlcRequiredEntry> {
        return DlcRequiredRuntime.collectMissingDownloadsForPrompt()
    }

    @JvmStatic
    fun collectMissingOrApply(): List<DlcRequiredEntry> {
        return DlcRequiredRuntime.collectMissingOrApply()
    }

    @JvmStatic
    fun hasMissingAppliedTargets(): Boolean {
        return DlcRequiredRuntime.hasMissingAppliedTargets()
    }

    @JvmStatic
    fun entryNeedsApply(entry: DlcRequiredEntry?): Boolean {
        return DlcRequiredRuntime.entryNeedsApply(entry)
    }

    @JvmStatic
    fun guessFileName(entry: DlcRequiredEntry?): String? {
        return DlcRequiredRuntime.guessFileName(entry)
    }

    @JvmStatic
    fun ensureFileNameKey(entry: DlcRequiredEntry?, fileName: String?) {
        DlcRequiredRuntime.ensureFileNameKey(entry, fileName)
    }

    @JvmStatic
    fun updateFileNameKey(entry: DlcRequiredEntry?, fileName: String?) {
        DlcRequiredRuntime.updateFileNameKey(entry, fileName)
    }

}
