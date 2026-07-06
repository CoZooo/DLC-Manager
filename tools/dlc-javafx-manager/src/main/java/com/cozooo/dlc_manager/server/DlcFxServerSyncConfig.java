package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class DlcFxServerSyncConfig {
    private static final String CONFIG_DIR = "dlc_manager";
    private static final String CONFIG_NAME = "external_sync.dc";

    private TransportMode transportMode = TransportMode.DLC_TCP;
    private String externalAddress = "";
    private boolean excludeDlcManagedMods = true;
    private String syncStrategy = "upload_changed";

    public enum TransportMode {
        DLC_TCP,
        WEBRTC_WSS;

        static TransportMode fromString(String value) {
            if (value == null || value.isBlank()) {
                return DLC_TCP;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (TransportMode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            return DLC_TCP;
        }
    }

    public static DlcFxServerSyncConfig load() {
        DlcFxServerSyncConfig config = new DlcFxServerSyncConfig();
        JsonObject root = DlcDcConfigIO.read(configPath(), DlcDcConfigIO.Layout.GENERIC);
        if (root == null) {
            return config;
        }
        config.transportMode = TransportMode.fromString(DlcDcConfigIO.getStringIgnoreCase(root, "transportMode"));
        config.externalAddress = safe(DlcDcConfigIO.getStringIgnoreCase(root, "externalAddress"));
        config.excludeDlcManagedMods = DlcDcConfigIO.getBooleanIgnoreCase(root, "excludeDlcManagedMods", true);
        String strategy = DlcDcConfigIO.getStringIgnoreCase(root, "syncStrategy");
        config.syncStrategy = strategy == null || strategy.isBlank() ? "upload_changed" : strategy.trim();
        return config;
    }

    public boolean save() {
        JsonObject root = new JsonObject();
        root.addProperty("transportMode", transportMode.name().toLowerCase(Locale.ROOT));
        root.addProperty("externalAddress", safe(externalAddress));
        root.addProperty("excludeDlcManagedMods", excludeDlcManagedMods);
        root.addProperty("syncStrategy", safe(syncStrategy).isBlank() ? "upload_changed" : syncStrategy.trim());
        Path path = configPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException ignored) {
            return false;
        }
        return DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC);
    }

    public TransportMode transportMode() {
        return transportMode;
    }

    public void setTransportMode(TransportMode transportMode) {
        this.transportMode = transportMode == null ? TransportMode.DLC_TCP : transportMode;
    }

    public String externalAddress() {
        return externalAddress;
    }

    public void setExternalAddress(String externalAddress) {
        this.externalAddress = safe(externalAddress);
    }

    public boolean excludeDlcManagedMods() {
        return excludeDlcManagedMods;
    }

    public void setExcludeDlcManagedMods(boolean excludeDlcManagedMods) {
        this.excludeDlcManagedMods = excludeDlcManagedMods;
    }

    public String syncStrategy() {
        return syncStrategy;
    }

    public void setSyncStrategy(String syncStrategy) {
        this.syncStrategy = safe(syncStrategy).isBlank() ? "upload_changed" : syncStrategy.trim();
    }

    static Path configPath() {
        return CrossPlatformRuntime.configDir().resolve(CONFIG_DIR).resolve(CONFIG_NAME);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
