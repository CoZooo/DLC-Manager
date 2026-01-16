package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcTooltipLoader;
import com.cozooo.dlc_manager.core.DlcType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcRemoteManager {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean promptedThisSession = false;

    private DlcRemoteManager() {
    }

    public static boolean maybeShowFromManager(net.minecraft.client.gui.screens.Screen parent) {
        if (promptedThisSession) {
            return false;
        }
        List<DlcRemoteEntry> missing = collectMissing();
        if (missing.isEmpty()) {
            return false;
        }
        promptedThisSession = true;
        net.minecraft.client.Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcRemoteDownloadScreen(parent, missing));
        return true;
    }

    public static boolean hasMissing() {
        return !collectMissing().isEmpty();
    }

    public static List<DlcRemoteEntry> collectMissing() {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        Path dlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (dlcRoot == null) {
            return missing;
        }
        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(dlcRoot);
        for (DlcRemoteEntry entry : entries) {
            if (!entry.isInList() && !entry.isHidden()) {
                continue;
            }
            Path target = entry.getTargetPath();
            if (target == null) {
                missing.add(entry);
                continue;
            }
            if (!Files.exists(target)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    public static void writeInfoFile(Path dlcRoot, DlcRemoteEntry entry, String fileName) {
        if (entry == null || fileName == null || fileName.isBlank()) {
            return;
        }
        Path root = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (root == null) {
            return;
        }
        Path infoDir = root.resolve("info");
        try {
            Files.createDirectories(infoDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("dlc_name", fileName);
        String identifier = entry.getDlcIdentifier();
        if (identifier == null || identifier.isBlank()) {
            identifier = fileName;
        }
        obj.addProperty("dlc_identifier", identifier);
        JsonObject displayObj = new JsonObject();
        displayObj.addProperty("en_us", fileName);
        for (var e : entry.getDisplayNames().entrySet()) {
            displayObj.addProperty(e.getKey(), e.getValue());
        }
        obj.add("DisplayName", displayObj);
        DlcType type = entry.getType() == null ? DlcType.UNKNOWN : entry.getType();
        obj.addProperty("type", DlcTooltipLoader.toTypeString(type));
        obj.addProperty("isInList", entry.isInList());
        obj.addProperty("isHidden", entry.isHidden());
        obj.addProperty("AllowEnableByAll", entry.isAllowEnableByAll());
        obj.addProperty("dependencies", String.join(",", entry.getResolvedDependencyNames()));
        obj.addProperty("conflicts", String.join(",", entry.getConflictNames()));
        JsonObject tipObj = new JsonObject();
        for (var e : entry.getTips().entrySet()) {
            tipObj.addProperty(e.getKey(), e.getValue());
        }
        obj.add("tip", tipObj);
        Path out = infoDir.resolve(fileName + ".json");
        try (Writer writer = Files.newBufferedWriter(out)) {
            PRETTY_GSON.toJson(obj, writer);
        } catch (IOException ignored) {
            // Best-effort.
        }
    }
}
