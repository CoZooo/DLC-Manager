package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DlcUpdateVersion {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_VERSION = "1.0.0";

    private String base;
    private String dlc;
    private String rollbackFrom;
    private String changelogShown;

    public static DlcUpdateVersion load(Path dlcRoot) {
        DlcUpdateVersion version = new DlcUpdateVersion();
        version.base = DEFAULT_VERSION;
        version.dlc = null;
        if (dlcRoot == null) {
            return version;
        }
        Path path = resolveVersionPath(dlcRoot);
        if (!Files.exists(path)) {
            version.save(dlcRoot);
            return version;
        }
        try {
            JsonObject obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
            if (obj == null) {
                return version;
            }
            String canonical = DlcDcConfigIO.getStringIgnoreCase(obj, "version");
            String legacy = DlcDcConfigIO.getStringIgnoreCase(obj, "base");
            boolean migrateLegacy = canonical == null || canonical.isBlank();
            version.base = migrateLegacy ? legacy : canonical;
            version.dlc = DlcDcConfigIO.getStringIgnoreCase(obj, "dlc");
            String rollback = DlcDcConfigIO.getStringIgnoreCase(obj, "rollback_from");
            if (rollback == null || rollback.isBlank()) {
                rollback = DlcDcConfigIO.getStringIgnoreCase(obj, "rollbackFrom");
            }
            version.rollbackFrom = rollback;
            String shown = DlcDcConfigIO.getStringIgnoreCase(obj, "changelog_shown");
            if (shown == null || shown.isBlank()) {
                shown = DlcDcConfigIO.getStringIgnoreCase(obj, "changelogShown");
            }
            version.changelogShown = shown;
            if (migrateLegacy && version.base != null && !version.base.isBlank()) {
                DlcDcConfigIO.removeIgnoreCase(obj, "base");
                obj.addProperty("version", version.base);
                DlcDcConfigIO.write(path, obj, DlcDcConfigIO.Layout.GENERIC);
            }
        } catch (Exception ignored) {
        }
        if (version.base == null || version.base.isBlank()) {
            version.base = DEFAULT_VERSION;
        }
        if (version.dlc != null && version.dlc.isBlank()) {
            version.dlc = null;
        }
        return version;
    }

    public void save(Path dlcRoot) {
        if (dlcRoot == null) {
            return;
        }
        Path path = resolveVersionPath(dlcRoot);
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("version", base == null || base.isBlank() ? DEFAULT_VERSION : base);
            if (dlc != null && !dlc.isBlank()) {
                obj.addProperty("dlc", dlc);
            }
            if (rollbackFrom != null && !rollbackFrom.isBlank()) {
                obj.addProperty("rollback_from", rollbackFrom);
            }
            if (changelogShown != null && !changelogShown.isBlank()) {
                obj.addProperty("changelog_shown", changelogShown);
            }
            DlcDcConfigIO.write(path, obj, DlcDcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
        }
    }

    private static Path resolveVersionPath(Path dlcRoot) {
        return DlcPaths.resolveVersionConfigPath(dlcRoot);
    }

    public String getBase() {
        return base;
    }

    /** Canonical public name; getBase remains for source compatibility. */
    public String getVersion() {
        return base;
    }

    public String getModpack() {
        return DlcConfig.getModpack();
    }

    public void setModpack(String modpack) {
        DlcConfig.setModpack(modpack);
    }

    public void setBase(String base) {
        this.base = base;
    }

    /** Canonical public name; setBase remains for source compatibility. */
    public void setVersion(String version) {
        this.base = version;
    }

    public String getDlc() {
        return dlc;
    }

    public void setDlc(String dlc) {
        this.dlc = dlc;
    }

    public String getRollbackFrom() {
        return rollbackFrom;
    }

    public void setRollbackFrom(String rollbackFrom) {
        this.rollbackFrom = rollbackFrom;
    }

    public String getChangelogShown() {
        return changelogShown;
    }

    public void setChangelogShown(String changelogShown) {
        this.changelogShown = changelogShown;
    }
}
