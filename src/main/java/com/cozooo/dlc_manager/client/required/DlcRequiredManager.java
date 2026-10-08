package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcRequiredManager {
    private DlcRequiredManager() {
    }

    public static boolean tryShowFromTitle(Screen parent) {
        List<DlcRequiredEntry> missing = DlcRequiredRuntime.pollMissingDownloadsForTitlePrompt();
        if (missing.isEmpty()) {
            return false;
        }
        Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, missing));
        return true;
    }

    public static void markPrompted() {
        DlcRequiredRuntime.markPrompted();
    }

    public static List<DlcRequiredEntry> loadEntries() {
        return DlcRequiredRuntime.loadEntries();
    }

    public static List<DlcRequiredEntry> loadAllEntries() {
        return DlcRequiredRuntime.loadAllEntries();
    }

    public static Map<String, DlcRequiredEntry> buildIndex(List<DlcRequiredEntry> entries) {
        return DlcRequiredRuntime.buildIndex(entries);
    }

    public static Map<String, Set<String>> buildDependents(List<DlcRequiredEntry> entries) {
        return DlcRequiredRuntime.buildDependents(entries);
    }

    public static List<DlcRequiredEntry> collectDependencies(List<DlcRequiredEntry> roots) {
        return DlcRequiredRuntime.collectDependencies(roots);
    }

    public static boolean hasMissingDownloads() {
        return DlcRequiredRuntime.hasMissingDownloads();
    }

    public static boolean hasMissingDownloadsForPrompt() {
        return DlcRequiredRuntime.hasMissingDownloadsForPrompt();
    }

    public static boolean hasMissingOrApply() {
        return DlcRequiredRuntime.hasMissingOrApply();
    }

    public static List<DlcRequiredEntry> collectMissingDownloads() {
        return DlcRequiredRuntime.collectMissingDownloads();
    }

    public static List<DlcRequiredEntry> collectMissingDownloadsForPrompt() {
        return DlcRequiredRuntime.collectMissingDownloadsForPrompt();
    }

    public static List<DlcRequiredEntry> collectMissingOrApply() {
        return DlcRequiredRuntime.collectMissingOrApply();
    }

    public static boolean hasMissingAppliedTargets() {
        return DlcRequiredRuntime.hasMissingAppliedTargets();
    }

    public static boolean entryNeedsApply(DlcRequiredEntry entry) {
        return DlcRequiredRuntime.entryNeedsApply(entry);
    }

    public static String guessFileName(DlcRequiredEntry entry) {
        return DlcRequiredRuntime.guessFileName(entry);
    }

    public static void ensureFileNameKey(DlcRequiredEntry entry, String fileName) {
        DlcRequiredRuntime.ensureFileNameKey(entry, fileName);
    }

    public static void updateFileNameKey(DlcRequiredEntry entry, String fileName) {
        DlcRequiredRuntime.updateFileNameKey(entry, fileName);
    }

}
