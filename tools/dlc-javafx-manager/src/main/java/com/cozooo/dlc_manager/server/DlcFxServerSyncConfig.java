package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncPlanner.Direction;
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
    private Direction syncDirection = Direction.PUSH;
    private boolean deepVerify;

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
        config.syncDirection = parseDirection(DlcDcConfigIO.getStringIgnoreCase(root, "syncDirection"));
        config.deepVerify = DlcDcConfigIO.getBooleanIgnoreCase(root, "deepVerify", false);
        return config;
    }

    public boolean save() {
        JsonObject root = new JsonObject();
        root.addProperty("transportMode", transportMode.name().toLowerCase(Locale.ROOT));
        root.addProperty("externalAddress", safe(externalAddress));
        root.addProperty("syncDirection", syncDirection.name().toLowerCase(Locale.ROOT));
        root.addProperty("deepVerify", deepVerify);
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

    public Direction syncDirection() {
        return syncDirection;
    }

    public void setSyncDirection(Direction syncDirection) {
        this.syncDirection = syncDirection == null ? Direction.PUSH : syncDirection;
    }

    public boolean deepVerify() {
        return deepVerify;
    }

    public void setDeepVerify(boolean deepVerify) {
        this.deepVerify = deepVerify;
    }

    static Path configPath() {
        return DlcPlatformServices.configDir().resolve(CONFIG_DIR).resolve(CONFIG_NAME);
    }

    private static Direction parseDirection(String value) {
        return value != null && "pull".equalsIgnoreCase(value.trim()) ? Direction.PULL : Direction.PUSH;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
