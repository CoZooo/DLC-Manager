package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.client.screen.DlcRemoteDownloadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Path;
import java.util.List;

public final class DlcRemoteManager {
    private DlcRemoteManager() {
    }

    public static boolean tryShowFromManager(Screen parent) {
        List<DlcRemoteEntry> missing = DlcRemoteRuntime.pollMissingForManagerPrompt();
        if (missing.isEmpty()) {
            return false;
        }
        Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(parent, missing));
        return true;
    }

    public static boolean hasMissing() {
        return DlcRemoteRuntime.hasMissing();
    }

    public static List<DlcRemoteEntry> collectMissing() {
        return DlcRemoteRuntime.collectMissing();
    }

    public static void writeInfoFile(Path dlcRoot, DlcRemoteEntry entry, String fileName) {
        DlcRemoteRuntime.writeInfoFile(dlcRoot, entry, fileName);
    }

    public static void updateFileNameKey(DlcRemoteEntry entry, String fileName) {
        DlcRemoteRuntime.updateFileNameKey(entry, fileName);
    }
}
