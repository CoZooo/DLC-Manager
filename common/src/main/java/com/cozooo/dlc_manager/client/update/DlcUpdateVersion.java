package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.Json5Util;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
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
            migrateLegacyVersionFile(dlcRoot, path);
        }
        if (!Files.exists(path)) {
            version.save(dlcRoot);
            return version;
        }
        try {
            JsonObject obj = Json5Util.readObject(path);
            if (obj == null) {
                return version;
            }
            version.base = JsonCaseUtil.getStringIgnoreCase(obj, "base");
            version.dlc = JsonCaseUtil.getStringIgnoreCase(obj, "dlc");
            String rollback = JsonCaseUtil.getStringIgnoreCase(obj, "rollback_from");
            if (rollback == null || rollback.isBlank()) {
                rollback = JsonCaseUtil.getStringIgnoreCase(obj, "rollbackFrom");
            }
            version.rollbackFrom = rollback;
            String shown = JsonCaseUtil.getStringIgnoreCase(obj, "changelog_shown");
            if (shown == null || shown.isBlank()) {
                shown = JsonCaseUtil.getStringIgnoreCase(obj, "changelogShown");
            }
            version.changelogShown = shown;
        } catch (Exception ignored) {
            // Best-effort load.
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
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException ignored) {
            // Best-effort save.
        }
    }

    private static Path resolveVersionPath(Path dlcRoot) {
        Path infoDir = dlcRoot.resolve("info");
        Json5Util.migrateLegacy(infoDir.resolve("version.json"), GSON);
        return Json5Util.resolveConfigPath(infoDir, "version");
    }

    private static void migrateLegacyVersionFile(Path dlcRoot, Path targetPath) {
        if (dlcRoot == null || targetPath == null) {
            return;
        }
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException ignored) {
            return;
        }
        Path legacyJson5 = dlcRoot.resolve("version.json5");
        if (Files.exists(legacyJson5)) {
            try {
                Files.move(legacyJson5, targetPath);
            } catch (IOException ignored) {
                // Best-effort migration.
            }
            return;
        }
        Path legacy = dlcRoot.resolve("version.json");
        if (!Files.exists(legacy)) {
            return;
        }
        try {
            Files.move(legacy, targetPath);
            Json5Util.migrateLegacy(targetPath, GSON);
        } catch (IOException ignored) {
            // Best-effort migration.
        }
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
