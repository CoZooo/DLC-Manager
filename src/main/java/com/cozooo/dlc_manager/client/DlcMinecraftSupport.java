package com.cozooo.dlc_manager.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

public final class DlcMinecraftSupport {
    private DlcMinecraftSupport() {
    }

    public static boolean isResourcePackSelected(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            PackRepository repo = minecraft.getResourcePackRepository();
            String packId = resolvePackId(repo, fileName);
            return packId != null && repo.getSelectedIds().contains(packId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String resolvePackId(PackRepository repo, String fileName) {
        if (repo == null) {
            return null;
        }
        String stripped = stripArchiveExtension(fileName);
        String[] candidates = new String[] {
            "file/" + fileName,
            "file/" + stripped,
            "vanilla/" + fileName,
            "vanilla/" + stripped
        };
        for (String candidate : candidates) {
            if (repo.getPack(candidate) != null) {
                return candidate;
            }
        }
        return repo.getAvailableIds().stream()
            .filter(id -> id.endsWith("/" + fileName) || id.endsWith("/" + stripped))
            .findFirst()
            .orElse(null);
    }

    private static String stripArchiveExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
