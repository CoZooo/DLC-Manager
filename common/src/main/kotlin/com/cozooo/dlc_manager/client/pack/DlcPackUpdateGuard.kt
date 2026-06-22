package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object DlcPackUpdateGuard {
    @JvmStatic
    fun check(dlcRoot: Path?, targetBase: String?, targetDlc: String?): CheckResult {
        val statuses = ArrayList<PackStatus>()
        val packRoot = packRoot(dlcRoot)
        if (packRoot == null || !Files.isDirectory(packRoot)) {
            return CheckResult(statuses)
        }
        try {
            Files.list(packRoot).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .sorted { a, b -> a.fileName.toString().lowercase(Locale.ROOT).compareTo(b.fileName.toString().lowercase(Locale.ROOT)) }
                    .forEach { statuses.add(checkPack(dlcRoot, it, targetBase, targetDlc)) }
            }
        } catch (_: Exception) {
        }
        return CheckResult(statuses)
    }

    private fun packRoot(dlcRoot: Path?): Path? {
        return dlcRoot?.resolve("tmp")?.resolve("pack")
    }

    private fun checkPack(dlcRoot: Path?, packDir: Path, targetBase: String?, targetDlc: String?): PackStatus {
        val meta = DlcPackMeta.load(packDir.resolve("meta.dc"))
            ?: return PackStatus(packDir, null, null, false, listOf("missing meta.dc"))
        val metadataPath = packDir.resolve("metadata").resolve("pack.dc")
        if (!Files.isRegularFile(metadataPath)) {
            return PackStatus(packDir, meta, null, false, listOf("missing metadata/pack.dc"))
        }
        val parsed = DlcPackMetadata.read(metadataPath)
        if (!parsed.valid || parsed.metadata == null) {
            return PackStatus(packDir, meta, null, false, listOf(parsed.error ?: "invalid metadata/pack.dc"))
        }
        val metadata = parsed.metadata
        val reasons = ArrayList<String>()
        val targetModpack = DlcUpdateVersion.load(dlcRoot).getModpack()
        val currentPlatform = DlcPackMetadata.currentPlatform()
        val currentGameVersion = CrossPlatformRuntime.mcVersion()
        val currentModLoader = CrossPlatformRuntime.loaderName()
        if (!DlcPackMetadata.anyStringMatches(metadata.modpacks, targetModpack, metadata.modpackAnyWildcard)) {
            reasons.add("modpack ${safeText(targetModpack)} not in ${metadata.modpacks.joinToString(", ")}")
        }
        if (!DlcPackMetadata.platformMatches(metadata.platform, currentPlatform)) {
            reasons.add("platform ${safeText(currentPlatform)} not in ${metadata.platform}")
        }
        if (!DlcPackMetadata.stringRuleMatches(metadata.gameVersion, currentGameVersion)) {
            reasons.add("gameVersion ${safeText(currentGameVersion)} not in ${metadata.gameVersion}")
        }
        if (!DlcPackMetadata.stringRuleMatches(metadata.modLoader, currentModLoader)) {
            reasons.add("modLoader ${safeText(currentModLoader)} not in ${metadata.modLoader}")
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.baseVersions, targetBase)) {
            reasons.add("base ${safe(targetBase)} not in ${metadata.baseVersions.joinToString(", ")}")
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.dlcVersions, targetDlc)) {
            reasons.add("dlc ${safe(targetDlc)} not in ${metadata.dlcVersions.joinToString(", ")}")
        }
        return PackStatus(packDir, meta, metadata, reasons.isEmpty(), reasons)
    }

    private fun safe(value: String?): String {
        return if (value.isNullOrBlank()) "1.0.0" else value.trim()
    }

    private fun safeText(value: String?): String {
        return if (value.isNullOrBlank()) "<empty>" else value.trim()
    }

    class CheckResult(
        packs: List<PackStatus>?,
    ) {
        @JvmField val packs: List<PackStatus> = packs?.toList() ?: emptyList()
        @JvmField val compatiblePacks: List<PackStatus> = this.packs.filter { it.compatible }
        @JvmField val incompatiblePacks: List<PackStatus> = this.packs.filter { !it.compatible }

        fun hasInstalledPacks(): Boolean {
            return packs.isNotEmpty()
        }

        fun hasIncompatiblePacks(): Boolean {
            return incompatiblePacks.isNotEmpty()
        }
    }

    class PackStatus(
        @JvmField val packDir: Path?,
        @JvmField val meta: DlcPackMeta?,
        @JvmField val metadata: DlcPackMetadata?,
        @JvmField val compatible: Boolean,
        reasons: List<String>?,
    ) {
        @JvmField val reasons: List<String> = reasons?.toList() ?: emptyList()

        fun identifier(): String {
            return meta?.identifier ?: packDir?.fileName?.toString().orEmpty()
        }
    }
}
