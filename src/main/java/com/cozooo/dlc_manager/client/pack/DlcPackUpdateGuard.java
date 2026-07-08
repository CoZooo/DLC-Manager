package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DlcPackUpdateGuard {
    private DlcPackUpdateGuard() {
    }

    public static CheckResult check(Path dlcRoot, String targetBase, String targetDlc) {
        List<PackStatus> statuses = new ArrayList<>();
        Path packRoot = packRoot(dlcRoot);
        if (packRoot == null || !Files.isDirectory(packRoot)) {
            return new CheckResult(statuses);
        }
        try (var stream = Files.list(packRoot)) {
            stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                .map(packDir -> checkPack(dlcRoot, packDir, targetBase, targetDlc))
                .filter(status -> status != null)
                .forEach(statuses::add);
        } catch (Exception ignored) {
        }
        return new CheckResult(statuses);
    }

    private static Path packRoot(Path dlcRoot) {
        return dlcRoot == null ? null : dlcRoot.resolve("tmp").resolve("pack");
    }

    private static PackStatus checkPack(Path dlcRoot, Path packDir, String targetBase, String targetDlc) {
        DlcPackMeta meta = DlcPackMeta.load(packDir.resolve("meta.dc"));
        if (meta == null) {
            return null;
        }
        Path metadataPath = packDir.resolve("metadata").resolve("pack.dc");
        if (!Files.isRegularFile(metadataPath)) {
            return new PackStatus(packDir, meta, null, false, List.of("missing metadata/pack.dc"));
        }
        DlcPackMetadata.Result parsed = DlcPackMetadata.read(metadataPath);
        if (!parsed.valid || parsed.metadata == null) {
            return new PackStatus(packDir, meta, null, false, List.of(parsed.error == null ? "invalid metadata/pack.dc" : parsed.error));
        }
        DlcPackMetadata metadata = parsed.metadata;
        List<String> reasons = new ArrayList<>();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        String targetModpack = version == null ? null : version.getModpack();
        String currentPlatform = DlcPackMetadata.currentPlatform();
        String currentGameVersion = DlcPlatformServices.mcVersion();
        String currentModLoader = DlcPlatformServices.loaderName();
        if (!DlcPackMetadata.anyStringMatches(metadata.modpacks, targetModpack, metadata.modpackAnyWildcard)) {
            reasons.add("modpack " + safeText(targetModpack) + " not in " + String.join(", ", metadata.modpacks));
        }
        if (!DlcPackMetadata.platformMatches(metadata.platform, currentPlatform)) {
            reasons.add("platform " + safeText(currentPlatform) + " not in " + metadata.platform);
        }
        if (!DlcPackMetadata.stringRuleMatches(metadata.gameVersion, currentGameVersion)) {
            reasons.add("gameVersion " + safeText(currentGameVersion) + " not in " + metadata.gameVersion);
        }
        if (!DlcPackMetadata.stringRuleMatches(metadata.modLoader, currentModLoader)) {
            reasons.add("modLoader " + safeText(currentModLoader) + " not in " + metadata.modLoader);
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.baseVersions, targetBase)) {
            reasons.add("base " + safe(targetBase) + " not in " + String.join(", ", metadata.baseVersions));
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.dlcVersions, targetDlc)) {
            reasons.add("dlc " + safe(targetDlc) + " not in " + String.join(", ", metadata.dlcVersions));
        }
        return new PackStatus(packDir, meta, metadata, reasons.isEmpty(), reasons);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "<empty>" : value.trim();
    }

    public static final class CheckResult {
        public final List<PackStatus> packs;
        public final List<PackStatus> compatiblePacks;
        public final List<PackStatus> incompatiblePacks;

        public CheckResult(List<PackStatus> packs) {
            this.packs = List.copyOf(packs == null ? List.of() : packs);
            this.compatiblePacks = this.packs.stream().filter(status -> status.compatible).toList();
            this.incompatiblePacks = this.packs.stream().filter(status -> !status.compatible).toList();
        }

        public boolean hasInstalledPacks() {
            return !packs.isEmpty();
        }

        public boolean hasIncompatiblePacks() {
            return !incompatiblePacks.isEmpty();
        }
    }

    public static final class PackStatus {
        public final Path packDir;
        public final DlcPackMeta meta;
        public final DlcPackMetadata metadata;
        public final boolean compatible;
        public final List<String> reasons;

        public PackStatus(Path packDir, DlcPackMeta meta, DlcPackMetadata metadata, boolean compatible, List<String> reasons) {
            this.packDir = packDir;
            this.meta = meta;
            this.metadata = metadata;
            this.compatible = compatible;
            this.reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }

        public String identifier() {
            if (meta != null && meta.identifier != null) {
                return meta.identifier;
            }
            return packDir == null ? "" : packDir.getFileName().toString();
        }
    }
}
