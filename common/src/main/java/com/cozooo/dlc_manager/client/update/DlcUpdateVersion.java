package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
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
        version.dlc = DEFAULT_VERSION;
        if (dlcRoot == null) {
            return version;
        }
        Path path = resolveVersionPath(dlcRoot);
        if (!Files.exists(path)) {
            version.save(dlcRoot);
            return version;
        }
        try {
            JsonObject obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC);
            if (obj == null) {
                return version;
            }
            version.base = DcConfigIO.getStringIgnoreCase(obj, "base");
            version.dlc = DcConfigIO.getStringIgnoreCase(obj, "dlc");
            String rollback = DcConfigIO.getStringIgnoreCase(obj, "rollback_from");
            if (rollback == null || rollback.isBlank()) {
                rollback = DcConfigIO.getStringIgnoreCase(obj, "rollbackFrom");
            }
            version.rollbackFrom = rollback;
            String shown = DcConfigIO.getStringIgnoreCase(obj, "changelog_shown");
            if (shown == null || shown.isBlank()) {
                shown = DcConfigIO.getStringIgnoreCase(obj, "changelogShown");
            }
            version.changelogShown = shown;
        } catch (Exception ignored) {
        }
        if (version.base == null || version.base.isBlank()) {
            version.base = DEFAULT_VERSION;
        }
        if (version.dlc == null || version.dlc.isBlank()) {
            version.dlc = DEFAULT_VERSION;
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
            obj.addProperty("base", base == null || base.isBlank() ? DEFAULT_VERSION : base);
            obj.addProperty("dlc", dlc == null || dlc.isBlank() ? DEFAULT_VERSION : dlc);
            if (rollbackFrom != null && !rollbackFrom.isBlank()) {
                obj.addProperty("rollback_from", rollbackFrom);
            }
            if (changelogShown != null && !changelogShown.isBlank()) {
                obj.addProperty("changelog_shown", changelogShown);
            }
            DcConfigIO.write(path, obj, DcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
        }
    }

    private static Path resolveVersionPath(Path dlcRoot) {
        Path infoDir = dlcRoot.resolve("info");
        return DcFormatUtil.resolveConfigPath(infoDir, "version");
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
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
