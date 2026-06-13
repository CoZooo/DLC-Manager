@file:Suppress("SENSELESS_COMPARISON")

package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadHeaderUtil
import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient
import com.cozooo.dlc_manager.client.preset.DlcPresetLoader
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.preset.DlcPresetStateStore
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.core.*
import com.cozooo.dlc_manager.core.DlcApplier.ApplyResult
import com.cozooo.dlc_manager.core.DlcConfig.DlcState
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.*
import net.minecraft.network.chat.Component
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.*
import java.util.List
import java.util.function.Function
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Comparator
import kotlin.Exception
import kotlin.Float
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.IntArray
import kotlin.Long
import kotlin.NumberFormatException
import kotlin.Throwable
import kotlin.Throws
import kotlin.also
import kotlin.arrayOf
import kotlin.check
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.Iterable
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.dropLastWhile
import kotlin.collections.indices
import kotlin.collections.isEmpty
import kotlin.collections.iterator
import kotlin.collections.mutableListOf
import kotlin.collections.mutableSetOf
import kotlin.collections.sort
import kotlin.collections.toTypedArray
import kotlin.io.iterator
import kotlin.io.normalize
import kotlin.io.resolve
import kotlin.io.use
import kotlin.math.max
import kotlin.math.min
import kotlin.plus
import kotlin.text.StringBuilder
import kotlin.text.compareTo
import kotlin.text.contains
import kotlin.text.endsWith
import kotlin.text.equals
import kotlin.text.get
import kotlin.text.indexOf
import kotlin.text.isBlank
import kotlin.text.isEmpty
import kotlin.text.iterator
import kotlin.text.lowercase
import kotlin.text.replace
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toInt
import kotlin.text.toLong
import kotlin.text.toRegex
import kotlin.text.trim
import kotlin.use

object DlcUpdateService {
    private val UPDATE_GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private const val PLATFORM_CLIENT = "client"
    private const val PLATFORM_SERVER = "server"
    private const val PLATFORM_UNIVERSAL = "universal"
    private val CURRENT_UPDATE_CONTEXT = ThreadLocal<UpdateContext?>()
    private val CURRENT_PROGRESS_LISTENER = ThreadLocal<ApplyProgressListener?>()

    @JvmStatic
    fun checkForUpdates(): UpdateCheckResult {
        if (!DlcPathConfig.isUpdateManagerOn()) {
            val dlcRoot: Path? = dlcRoot
            return UpdateCheckResult(DlcUpdateVersion.load(dlcRoot), mutableListOf<DlcUpdatePackage?>())
        }
        val dlcRoot: Path? = dlcRoot
        val version = DlcUpdateVersion.load(dlcRoot)
        val packages: MutableList<DlcUpdatePackage?> = scanUpdatePackages(dlcRoot)
        val applicable: MutableList<DlcUpdatePackage?> = resolveUpdateChain(version, packages, false)
        return UpdateCheckResult(version, applicable)
    }

    @JvmStatic
    fun checkForUpdates(ignoreRollback: Boolean): UpdateCheckResult {
        if (!DlcPathConfig.isUpdateManagerOn()) {
            val dlcRoot: Path? = dlcRoot
            return UpdateCheckResult(DlcUpdateVersion.load(dlcRoot), mutableListOf<DlcUpdatePackage?>())
        }
        val dlcRoot: Path? = dlcRoot
        val version = DlcUpdateVersion.load(dlcRoot)
        val packages: MutableList<DlcUpdatePackage?> = scanUpdatePackages(dlcRoot)
        val applicable: MutableList<DlcUpdatePackage?> = resolveUpdateChain(version, packages, ignoreRollback)
        return UpdateCheckResult(version, applicable)
    }

    @JvmStatic
    fun findUpdatePackage(dlcOld: String?, dlcNew: String?): DlcUpdatePackage? {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return null
        }
        val current = DlcUpdateVersion.load(dlcRoot)
        val packages: MutableList<DlcUpdatePackage?> = scanUpdatePackages(dlcRoot)
        if (packages.isEmpty()) {
            return null
        }
        val base: String = DlcUpdateService.safeVersion(current.getBase())
        val targetOld: String = safeVersion(dlcOld)
        var targetNew: String? = optionalVersion(dlcNew)
        if (targetNew == null) {
            targetNew = targetOld
        }
        for (pkg in packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue
            }
            val info = pkg.getManifest().getVersion()
            if (!hasVersionTransition(info)) {
                continue
            }
            if (base != sourceBaseVersion(info)) {
                continue
            }
            if (targetOld == sourceDlcVersion(info) && targetNew == targetDlcVersion(info)) {
                return pkg
            }
        }
        return null
    }

    @JvmStatic
    fun resolveUpdateChainTo(targetDlcNew: String?, ignoreRollback: Boolean): MutableList<DlcUpdatePackage?> {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val version = DlcUpdateVersion.load(dlcRoot)
        val packages: MutableList<DlcUpdatePackage?> = scanUpdatePackages(dlcRoot)
        val currentBase: String? = if (version == null) null else version.getBase()
        return resolveUpdateChainTo(version, packages, currentBase, targetDlcNew, ignoreRollback)
    }

    @JvmStatic
    fun resolveUpdateChainTo(
        targetDlcOld: String?,
        targetDlcNew: String?,
        ignoreRollback: Boolean
    ): MutableList<DlcUpdatePackage?> {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val version = DlcUpdateVersion.load(dlcRoot)
        val packages: MutableList<DlcUpdatePackage?> = scanUpdatePackages(dlcRoot)
        val currentBase: String? = if (version == null) null else version.getBase()
        return resolveUpdateChainTo(
            version,
            packages,
            currentBase,
            targetDlcOld,
            currentBase,
            targetDlcNew,
            ignoreRollback
        )
    }

    @JvmStatic
    fun resolveUpdateChainTo(
        targetBaseOld: String?,
        targetDlcOld: String?,
        targetBaseNew: String?,
        targetDlcNew: String?,
        ignoreRollback: Boolean
    ): MutableList<DlcUpdatePackage?> {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val version = DlcUpdateVersion.load(dlcRoot)
        val packages: MutableList<DlcUpdatePackage?> = scanUpdatePackages(dlcRoot)
        return resolveUpdateChainTo(
            version,
            packages,
            targetBaseOld,
            targetDlcOld,
            targetBaseNew,
            targetDlcNew,
            ignoreRollback
        )
    }

    @JvmStatic
    @JvmOverloads
    fun applyUpdates(
        packages: MutableList<DlcUpdatePackage?>?,
        forceMissing: Boolean,
        progressListener: ApplyProgressListener? = null
    ): UpdateApplyResult {
        CURRENT_PROGRESS_LISTENER.set(progressListener)
        try {
            return applyUpdatesInternal(packages, forceMissing)
        } finally {
            CURRENT_PROGRESS_LISTENER.remove()
        }
    }

    @JvmStatic
    fun toFailureComponent(result: UpdateApplyResult?): Component {
        val message: String? = if (result == null) "" else result.message
        if (message == null || message.isBlank()) {
            return Component.translatable("dlc_manager.update.failed")
        }
        if ("Version mismatch".equals(message.trim { it <= ' ' }, ignoreCase = true)) {
            return Component.translatable("dlc_manager.update.failed_version_mismatch")
        }
        return Component.literal(message)
    }

    @JvmStatic
    fun toImportValidationComponent(result: ImportValidationResult?): Component {
        if (result == null) {
            return Component.translatable("dlc_manager.update.import_invalid")
        }
        return when (result) {
            ImportValidationResult.VALID -> Component.empty()
            ImportValidationResult.DLC_ROOT_MISSING -> Component.translatable("dlc_manager.update.dlc_root_missing")
            ImportValidationResult.PLATFORM_MISMATCH -> Component.translatable("dlc_manager.update.import_platform_mismatch")
            ImportValidationResult.VERSION_MISMATCH -> Component.translatable("dlc_manager.update.import_version_mismatch")
            ImportValidationResult.INVALID_PACKAGE -> Component.translatable("dlc_manager.update.import_invalid")
        }
    }

    private fun applyUpdatesInternal(
        packages: MutableList<DlcUpdatePackage?>?,
        forceMissing: Boolean
    ): UpdateApplyResult {
        if (packages == null || packages.isEmpty()) {
            return UpdateApplyResult.Companion.empty()
        }
        notifyApplyProgress("Applying update...", -1f)
        val dlcRoot: Path? = dlcRoot
        var current = DlcUpdateVersion.load(dlcRoot)
        var addedRequired = false
        var addedRemote = false
        var helperScheduled = false
        var dlcManagerUpdated = false
        val requiresDlcManagerUpdate = packages.stream().anyMatch { pkg: DlcUpdatePackage? ->
            pkg != null && pkg.getManifest() != null && pkg.getManifest().isUpdateDLCM()
        }
        val requestedDlcManagerVersions: LinkedHashSet<String?> = collectRequestedDlcManagerVersions(packages)
        if (requestedDlcManagerVersions.size > 1) {
            return UpdateApplyResult.Companion.failed("Conflicting UpdateDLCM versions in update chain")
        }
        val requiredDlcManagerVersion = requestedDlcManagerVersions.stream().findFirst().orElse(null)
        if (requiresDlcManagerUpdate) {
            val progressMessage = if (requiredDlcManagerVersion == null)
                "Downloading DLC Manager update..."
            else
                "Downloading DLC Manager update " + requiredDlcManagerVersion + "..."
            notifyApplyProgress(progressMessage, -1f)
            val managerResult = applyRequiredDlcManagerUpdate(requiredDlcManagerVersion)
            if (!managerResult.isSuccess) {
                return managerResult
            }
            helperScheduled = helperScheduled || managerResult.isHelperScheduled
            dlcManagerUpdated = managerResult.isDlcManagerUpdated
        }
        for (pkg in packages) {
            val result = applyUpdate(pkg, current, forceMissing)
            if (!result.isSuccess) {
                return result
            }
            addedRequired = addedRequired || result.addedRequired
            addedRemote = addedRemote || result.addedRemote
            helperScheduled = helperScheduled || result.isHelperScheduled
            current = DlcUpdateVersion.load(dlcRoot)
        }
        clearUpdatePromptSkipMarker()
        return UpdateApplyResult.Companion.success("OK", addedRequired, addedRemote, helperScheduled, dlcManagerUpdated)
    }

    private fun applyRequiredDlcManagerUpdate(targetVersion: String?): UpdateApplyResult {
        val check = DlcSelfUpdateService.checkForUpdate(targetVersion)
        if (check == null) {
            return UpdateApplyResult.Companion.failed("Failed to check DLC Manager update")
        }
        if (check.missingRequestedVersion) {
            return UpdateApplyResult.Companion.failed("Failed to find DLC Manager version: " + targetVersion)
        }
        if (!check.hasUpdate) {
            return UpdateApplyResult.Companion.success("OK", false, false, false, false)
        }
        val downloadResult = DlcSelfUpdateService.downloadUpdate(check)
        if (downloadResult == null || !downloadResult.success) {
            var message = if (downloadResult == null) "" else downloadResult.reason
            if (message == null || message.isBlank()) {
                message = "Failed to update DLC Manager"
            } else {
                message = "Failed to update DLC Manager: " + message
            }
            return UpdateApplyResult.Companion.failed(message)
        }
        return UpdateApplyResult.Companion.success("OK", false, false, false, true)
    }

    private fun collectRequestedDlcManagerVersions(packages: MutableList<DlcUpdatePackage?>?): LinkedHashSet<String?> {
        val versions = LinkedHashSet<String?>()
        if (packages == null) {
            return versions
        }
        for (pkg in packages) {
            if (pkg == null || pkg.getManifest() == null || !pkg.getManifest().isUpdateDLCM()) {
                continue
            }
            val version = pkg.getManifest().getUpdateDLCMVersion()
            if (version != null && !version.isBlank()) {
                versions.add(version.trim { it <= ' ' })
            }
        }
        return versions
    }

    @JvmStatic
    fun applyUpdate(pkg: DlcUpdatePackage?, current: DlcUpdateVersion?, forceMissing: Boolean): UpdateApplyResult {
        if (pkg == null || pkg.getManifest() == null) {
            return UpdateApplyResult.Companion.failed("Missing update manifest")
        }
        val manifest = pkg.getManifest()
        val info = manifest.getVersion()
        if (info == null) {
            return UpdateApplyResult.Companion.failed("Missing version info")
        }
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return UpdateApplyResult.Companion.failed("Missing DLC root")
        }
        if (!matchesVersion(current, info)) {
            return UpdateApplyResult.Companion.failed("Version mismatch")
        }

        val missingModify: MutableList<String?> =
            DlcUpdateService.findMissingModifyTargets(manifest, DlcPaths.gameDir())
        if (!missingModify.isEmpty() && !forceMissing) {
            return UpdateApplyResult.Companion.missing(missingModify)
        }

        val staging = pkg.extractTo(dlcRoot)
        if (staging == null) {
            return UpdateApplyResult.Companion.failed("Failed to extract update package")
        }

        val meta = DlcUpdateMeta()
        meta.setDlcOld(sourceDlcVersion(info))
        meta.setDlcNew(targetDlcVersion(info))
        meta.setBaseOld(DlcUpdateService.safeVersion(current!!.getBase()))
        val baseNew: String? = optionalVersion(info.getBaseNew())
        if (baseNew != null) {
            meta.setBaseNew(baseNew)
        }
        meta.setTimestamp(System.currentTimeMillis().toString())
        meta.setHidden(manifest.isHidden())
        meta.setChangelog(manifest.getChangelog())
        val nextBase: String =
            if (baseNew != null) safeVersion(baseNew) else DlcUpdateService.safeVersion(current.getBase())
        val nextDlc: String = targetDlcVersion(info)
        val backupRoot: Path? = resolveMetaRootByPair(dlcRoot, nextBase, nextDlc)
        val metaPath: Path? = resolveMetaPath(backupRoot)

        val context = UpdateContext(dlcRoot, staging, backupRoot, meta)
        val presetSession = preparePresetUpdateSession(manifest.getUpdateOps(), dlcRoot)

        CURRENT_UPDATE_CONTEXT.set(context)
        try {
            applyDeleteOps(manifest.getFileOps(), context)
            applyMoveOps(manifest.getFileOps(), context)
            applyAddOps(manifest.getFileOps(), context)
            applyModifyOps(manifest.getFileOps(), context, forceMissing)
            disablePresetUpdatesForApply(presetSession)
            applyUpdateOps(manifest.getUpdateOps(), context)
            check(context.failureMessages.isEmpty()) { context.failureMessages.joinToString("\n") }
            meta.save(metaPath)

            val updated = DlcUpdateVersion()
            updated.setBase(nextBase)
            updated.setDlc(nextDlc)
            updated.setRollbackFrom(null)
            updated.setChangelogShown(sourceBaseVersion(info) + "+" + sourceDlcVersion(info))
            updated.save(dlcRoot)

            if (context.addedRemote) {
                DlcRemoteMarker.clearPartMarker(dlcRoot)
            }
            finalizePresetUpdateSession(presetSession)

            pkg.cleanupStaging()
            return UpdateApplyResult.Companion.success(
                "OK",
                context.addedRequired,
                context.addedRemote,
                context.helperScheduled,
                false
            )
        } catch (ex: Exception) {
            rollbackUpdate(context)
            restorePresetUpdatesAfterRollback(presetSession)
            pkg.cleanupStaging()
            var message = if (ex == null || ex.message == null) "" else ex.message!!.trim { it <= ' ' }
            if (message.isBlank() && !context.failureMessages.isEmpty()) {
                message = context.failureMessages.joinToString("\n")
            }
            if (message.isBlank()) {
                message = "Update failed"
            }
            return UpdateApplyResult.Companion.failed(message)
        } finally {
            CURRENT_UPDATE_CONTEXT.remove()
        }
    }

    @JvmStatic
    fun rollbackTo(targetVersion: kotlin.String?): UpdateRollbackResult {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return UpdateRollbackResult.Companion.failed("Missing DLC root")
        }
        val current = DlcUpdateVersion.load(dlcRoot)
        if (targetVersion == null || targetVersion.isBlank()) {
            return UpdateRollbackResult.Companion.failed("Missing target version")
        }
        val currentBase: kotlin.String = DlcUpdateService.safeVersion(current.getBase())
        val currentDlc: kotlin.String = DlcUpdateService.safeVersion(current.getDlc())
        val targetState = parseVersionState(targetVersion, currentBase)
        if (targetState == null) {
            return UpdateRollbackResult.Companion.failed("Missing target version")
        }
        if (stateMatchesTarget(currentBase, currentDlc, targetState)) {
            return UpdateRollbackResult.Companion.success(current.getDlc())
        }

        val chain: MutableList<DlcUpdateMeta?> = loadRollbackChain(dlcRoot, currentBase, currentDlc)
        if (chain.isEmpty()) {
            return UpdateRollbackResult.Companion.failed("No rollback data")
        }
        var targetFound = false
        for (meta in chain) {
            if (meta != null && stateMatchesTarget(sourceBaseVersion(meta), sourceDlcVersion(meta), targetState)) {
                targetFound = true
                break
            }
        }
        if (!targetFound) {
            return UpdateRollbackResult.Companion.failed("Target version not found")
        }
        val beforeRollback = currentBase + "+" + currentDlc
        var working = currentDlc
        var workingBase = currentBase
        for (meta in chain) {
            if (meta == null) {
                break
            }
            val metaTargetDlc: kotlin.String = targetDlcVersion(meta)
            val metaTargetBase: kotlin.String = targetBaseVersion(meta)
            if (working != metaTargetDlc || safeVersion(workingBase) != metaTargetBase) {
                continue
            }
            val backupRoot: Path? = resolveMetaRootByPair(dlcRoot, metaTargetBase, metaTargetDlc)
            val context = UpdateContext(dlcRoot, null, backupRoot, meta)
            rollbackUpdate(context)
            working = sourceDlcVersion(meta)
            workingBase = sourceBaseVersion(meta)
            if (stateMatchesTarget(workingBase, working, targetState)) {
                break
            }
        }
        val updated = DlcUpdateVersion()
        updated.setBase(workingBase)
        updated.setDlc(working)
        updated.setRollbackFrom(beforeRollback)
        updated.save(dlcRoot)
        if (!stateMatchesTarget(workingBase, working, targetState)) {
            return UpdateRollbackResult.Companion.failed("Rollback incomplete")
        }
        return UpdateRollbackResult.Companion.success(working)
    }

    @JvmStatic
    fun listUpdateHistory(): MutableList<DlcUpdateMeta?> {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return mutableListOf<DlcUpdateMeta?>()
        }
        val version = DlcUpdateVersion.load(dlcRoot)
        return DlcUpdateService.loadRollbackChain(dlcRoot, version.getBase(), version.getDlc())
    }

    @JvmStatic
    fun listUpdateHistoryFrom(startVersion: kotlin.String?): MutableList<DlcUpdateMeta?> {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null || startVersion == null || startVersion.isBlank()) {
            return mutableListOf<DlcUpdateMeta?>()
        }
        val version = DlcUpdateVersion.load(dlcRoot)
        val start = DlcUpdateService.parseVersionState(startVersion, version.getBase())
        if (start == null) {
            return mutableListOf<DlcUpdateMeta?>()
        }
        val chain: MutableList<DlcUpdateMeta?> = loadRollbackChain(dlcRoot, start.base, start.dlc)
        if (!chain.isEmpty()) {
            return chain
        }
        return loadRollbackChain(dlcRoot, null, start.dlc)
    }

    @JvmStatic
    fun loadMetaForVersion(dlcVersion: kotlin.String?): DlcUpdateMeta? {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null || dlcVersion == null || dlcVersion.isBlank()) {
            return null
        }
        val version = DlcUpdateVersion.load(dlcRoot)
        val base: kotlin.String? = if (version == null) null else version.getBase()
        val meta = loadMetaForVersion(dlcRoot, base, dlcVersion)
        if (meta != null) {
            return meta
        }
        return loadMetaForVersion(dlcRoot, null, dlcVersion)
    }

    @JvmStatic
    fun loadMetaForVersion(baseVersion: kotlin.String?, dlcVersion: kotlin.String?): DlcUpdateMeta? {
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return null
        }
        return loadMetaForVersion(dlcRoot, baseVersion, dlcVersion)
    }

    @JvmStatic
    fun buildChangelogText(packages: MutableList<DlcUpdatePackage?>?, lang: kotlin.String?): kotlin.String {
        if (packages == null || packages.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        for (pkg in packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue
            }
            val text = pkg.getManifest().resolveChangelog(lang)
            if (text == null || text.isBlank()) {
                continue
            }
            val info = pkg.getManifest().getVersion()
            if (builder.length > 0) {
                builder.append("\n\n")
            }
            if (info != null) {
                builder.append(
                    formatCompactTransition(
                        sourceBaseVersion(info),
                        sourceDlcVersion(info),
                        targetBaseVersion(info),
                        targetDlcVersion(info)
                    )
                ).append("\n")
            }
            builder.append(text.trim { it <= ' ' })
        }
        return builder.toString()
    }

    @get:JvmStatic
    val updateDir: Path?
        get() {
            val dlcRoot: Path? = dlcRoot
            return if (dlcRoot == null) null else dlcRoot.resolve("update")
        }

    @JvmStatic
    fun ensureUpdateDir() {
        if (!DlcPathConfig.isUpdateManagerOn()) {
            return
        }
        val updateDir: Path? = updateDir
        if (updateDir == null) {
            return
        }
        try {
            Files.createDirectories(updateDir)
        } catch (ignored: IOException) {
        }
    }

    @JvmStatic
    fun isImportPackageValid(zipPath: Path?): Boolean {
        return validateImportPackage(zipPath).isValid
    }

    @JvmStatic
    fun validateImportPackage(zipPath: Path?): ImportValidationResult {
        if (zipPath == null) {
            return ImportValidationResult.INVALID_PACKAGE
        }
        val pkg = DlcUpdatePackage.fromZip(zipPath)
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return ImportValidationResult.INVALID_PACKAGE
        }
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return ImportValidationResult.DLC_ROOT_MISSING
        }
        val current = DlcUpdateVersion.load(dlcRoot)
        val info = pkg.getManifest().getVersion()
        if (!hasVersionTransition(info)) {
            return ImportValidationResult.INVALID_PACKAGE
        }
        if (!isPlatformCompatible(info)) {
            return ImportValidationResult.PLATFORM_MISMATCH
        }
        val currentBase: kotlin.String = DlcUpdateService.safeVersion(if (current == null) null else current.getBase())
        val currentDlc: kotlin.String = DlcUpdateService.safeVersion(if (current == null) null else current.getDlc())
        if (currentBase != sourceBaseVersion(info) || currentDlc != sourceDlcVersion(info)) {
            return ImportValidationResult.VERSION_MISMATCH
        }
        return ImportValidationResult.VALID
    }

    @JvmStatic
    fun importUpdatePackage(zipPath: Path?): Boolean {
        if (zipPath == null || !Files.exists(zipPath)) {
            return false
        }
        val pkg = DlcUpdatePackage.fromZip(zipPath)
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return false
        }
        val updateDir: Path? = updateDir
        if (updateDir == null) {
            return false
        }
        try {
            Files.createDirectories(updateDir)
            val target = updateDir.resolve(zipPath.getFileName().toString())
            if (target.normalize() != zipPath.normalize()) {
                if (!copyWithHelperFallback(zipPath, target, true)) {
                    return false
                }
            }
            removeDuplicatePackages(updateDir, target, pkg.getManifest().getVersion())
            return true
        } catch (ignored: IOException) {
            return false
        }
    }

    @JvmStatic
    fun removeSkipMarker(addedRequiredFiles: MutableList<Path?>?) {
        if (addedRequiredFiles == null || addedRequiredFiles.isEmpty()) {
            return
        }
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot == null) {
            return
        }
        val skip = dlcRoot.resolve("required").resolve("SKIP")
        deleteWithHelperFallback(skip)
    }

    @JvmStatic
    fun skipPromptForUpdate(result: UpdateCheckResult?) {
        val span = resolveUpdatePromptSpan(result)
        if (span == null) {
            return
        }
        try {
            val obj = JsonObject()
            obj.addProperty("fromBase", span.fromBase)
            obj.addProperty("fromDlc", span.fromDlc)
            obj.addProperty("toBase", span.toBase)
            obj.addProperty("toDlc", span.toDlc)
            obj.addProperty("skippedAt", Instant.now().toString())
            DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_HOT_UPDATE, obj)
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun isPromptSkippedForUpdate(result: UpdateCheckResult?): Boolean {
        val span = resolveUpdatePromptSpan(result)
        if (span == null) {
            return false
        }
        val obj = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_HOT_UPDATE)
        if (obj == null) {
            return false
        }
        val fromBase: kotlin.String = safeVersion(readJsonString(obj, "fromBase"))
        val fromDlc: kotlin.String = safeVersion(readJsonString(obj, "fromDlc"))
        val toBase: kotlin.String = safeVersion(readJsonString(obj, "toBase"))
        val toDlc: kotlin.String = safeVersion(readJsonString(obj, "toDlc"))
        return span.fromBase == fromBase
                && span.fromDlc == fromDlc
                && span.toBase == toBase
                && span.toDlc == toDlc
    }

    private fun resolveUpdatePromptSpan(result: UpdateCheckResult?): UpdatePromptSpan? {
        if (result == null || !result.hasUpdates() || result.current == null) {
            return null
        }
        val updates: MutableList<DlcUpdatePackage?>? =
            result.updates
        if (updates == null || updates.isEmpty()) {
            return null
        }
        var tail: DlcUpdateManifest.VersionInfo? = null
        for (i in updates.indices.reversed()) {
            val pkg = updates.get(i)
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue
            }
            tail = pkg.getManifest().getVersion()
            break
        }
        if (tail == null) {
            return null
        }
        val span = UpdatePromptSpan()
        span.fromBase = DlcUpdateService.safeVersion(result.current.getBase())
        span.fromDlc = DlcUpdateService.safeVersion(result.current.getDlc())
        span.toBase = targetBaseVersion(tail)
        span.toDlc = targetDlcVersion(tail)
        return span
    }

    private fun clearUpdatePromptSkipMarker() {
        DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_HOT_UPDATE)
    }

    private fun scanUpdatePackages(dlcRoot: Path?): MutableList<DlcUpdatePackage?> {
        if (dlcRoot == null) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val updateDir = dlcRoot.resolve("update")
        if (!Files.isDirectory(updateDir)) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val packages: MutableList<DlcUpdatePackage?> = ArrayList<DlcUpdatePackage?>()
        try {
            Files.list(updateDir).use { stream ->
                stream.filter { path: Path? -> Files.isRegularFile(path) }
                    .filter { path: Path? ->
                        path!!.getFileName() != null && path.getFileName().toString().lowercase(Locale.getDefault())
                            .endsWith(".zip")
                    }
                    .forEach { path: Path? ->
                        val pkg = DlcUpdatePackage.fromZip(path)
                        if (pkg != null && isPackageCompatible(pkg)) {
                            packages.add(pkg)
                        }
                    }
            }
        } catch (ignored: IOException) {
        }
        return dedupeUpdatePackages(packages)
    }

    private fun dedupeUpdatePackages(packages: MutableList<DlcUpdatePackage?>?): MutableList<DlcUpdatePackage?> {
        if (packages == null || packages.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val best: MutableMap<kotlin.String?, DlcUpdatePackage?> = LinkedHashMap<kotlin.String?, DlcUpdatePackage?>()
        val times: MutableMap<kotlin.String?, Long?> = HashMap<kotlin.String?, Long?>()
        for (pkg in packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue
            }
            val info = pkg.getManifest().getVersion()
            val key =
                versionPlatform(info) + "|" + sourceBaseVersion(info) + "|" + targetBaseVersion(info) + "|" + sourceDlcVersion(
                    info
                ) + "|" + targetDlcVersion(info)
            val modified = getLastModifiedMillis(pkg.getArchivePath())
            val existing = times.get(key)
            if (existing == null || modified >= existing) {
                best.put(key, pkg)
                times.put(key, modified)
            }
        }
        return ArrayList<DlcUpdatePackage?>(best.values)
    }

    private fun getLastModifiedMillis(path: Path?): Long {
        if (path == null) {
            return 0L
        }
        try {
            return Files.getLastModifiedTime(path).toMillis()
        } catch (ignored: IOException) {
            return 0L
        }
    }

    private fun removeDuplicatePackages(
        updateDir: Path?,
        keepPath: Path?,
        versionInfo: DlcUpdateManifest.VersionInfo?
    ) {
        if (updateDir == null || keepPath == null || versionInfo == null) {
            return
        }
        try {
            Files.walk(updateDir).use { stream ->
                stream.filter { path: Path? ->
                    path!!.getFileName() != null && path.getFileName().toString().lowercase(
                        Locale.getDefault()
                    ).endsWith(".zip")
                }
                    .forEach { path: Path? ->
                        if (path == keepPath) {
                            return@forEach
                        }
                        val pkg = DlcUpdatePackage.fromZip(path)
                        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                            return@forEach
                        }
                        if (sameVersion(versionInfo, pkg.getManifest().getVersion())) {
                            deleteWithHelperFallback(path)
                        }
                    }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun sameVersion(left: DlcUpdateManifest.VersionInfo?, right: DlcUpdateManifest.VersionInfo?): Boolean {
        if (left == null || right == null) {
            return false
        }
        return sourceBaseVersion(left) == sourceBaseVersion(right)
                && targetBaseVersion(left) == targetBaseVersion(right)
                && sourceDlcVersion(left) == sourceDlcVersion(right)
                && targetDlcVersion(left) == targetDlcVersion(right)
                && versionPlatform(left) == versionPlatform(right)
    }

    private fun resolveUpdateChain(
        version: DlcUpdateVersion?, packages: MutableList<DlcUpdatePackage?>?,
        ignoreRollback: Boolean
    ): MutableList<DlcUpdatePackage?> {
        if (version == null || packages == null || packages.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom()!!.isBlank()) {
            val hasNewer = packages.stream().anyMatch { pkg: DlcUpdatePackage? ->
                val info = pkg!!.getManifest().getVersion()
                info != null && DlcUpdateService.rollbackMarkerMatchesSource(
                    version.getRollbackFrom(),
                    sourceBaseVersion(info),
                    sourceDlcVersion(info)
                )
            }
            if (!hasNewer) {
                return mutableListOf<DlcUpdatePackage?>()
            }
        }
        val remaining: MutableList<DlcUpdatePackage> = ArrayList<DlcUpdatePackage>(packages)
        val chain: MutableList<DlcUpdatePackage?> = ArrayList<DlcUpdatePackage?>()
        var currentBase: kotlin.String = DlcUpdateService.safeVersion(version.getBase())
        var currentDlc: kotlin.String = DlcUpdateService.safeVersion(version.getDlc())
        while (true) {
            val candidates: MutableList<DlcUpdatePackage> = ArrayList<DlcUpdatePackage>()
            for (pkg in remaining) {
                val info = pkg.getManifest().getVersion()
                if (info == null) {
                    continue
                }
                if (!hasVersionTransition(info)) {
                    continue
                }
                if (currentBase == sourceBaseVersion(info) && currentDlc == sourceDlcVersion(info)) {
                    candidates.add(pkg)
                }
            }
            if (candidates.isEmpty()) {
                break
            }
            candidates.sortWith(
                Comparator
                    .comparing<DlcUpdatePackage, kotlin.String>(
                        { pkg: DlcUpdatePackage -> targetBaseVersion(pkg.getManifest().getVersion()) },
                        DlcUpdateService::compareVersion
                    )
                    .thenComparing(
                        { pkg: DlcUpdatePackage -> targetDlcVersion(pkg.getManifest().getVersion()) },
                        DlcUpdateService::compareVersion
                    )
            )
            val chosen = candidates.get(0)
            chain.add(chosen)
            remaining.remove(chosen)
            val info = chosen.getManifest().getVersion()
            currentBase = targetBaseVersion(info)
            currentDlc = targetDlcVersion(info)
        }
        return chain
    }

    private fun resolveUpdateChainTo(
        version: DlcUpdateVersion?, packages: MutableList<DlcUpdatePackage?>?,
        targetBase: kotlin.String?, targetDlc: kotlin.String?, ignoreRollback: Boolean
    ): MutableList<DlcUpdatePackage?> {
        if (version == null || packages == null || packages.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val targetBaseVersion: kotlin.String = safeVersion(targetBase)
        val targetDlcVersion: kotlin.String = safeVersion(targetDlc)
        if (targetBaseVersion.isEmpty() || targetDlcVersion.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val startBase: kotlin.String = DlcUpdateService.safeVersion(version.getBase())
        val startDlc: kotlin.String = DlcUpdateService.safeVersion(version.getDlc())
        if (startBase == targetBaseVersion && startDlc == targetDlcVersion) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        if (!ignoreRollback && version.getRollbackFrom() != null && !version.getRollbackFrom()!!.isBlank()) {
            val hasNewer = packages.stream().anyMatch { pkg: DlcUpdatePackage? ->
                val info = pkg!!.getManifest().getVersion()
                info != null && DlcUpdateService.rollbackMarkerMatchesSource(
                    version.getRollbackFrom(),
                    sourceBaseVersion(info),
                    sourceDlcVersion(info)
                )
            }
            if (!hasNewer) {
                return mutableListOf<DlcUpdatePackage?>()
            }
        }
        val filtered: MutableList<DlcUpdatePackage> = ArrayList<DlcUpdatePackage>()
        for (pkg in packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue
            }
            val info = pkg.getManifest().getVersion()
            if (!hasVersionTransition(info)) {
                continue
            }
            filtered.add(pkg)
        }
        if (filtered.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val byOld: MutableMap<kotlin.String, MutableList<DlcUpdatePackage>> =
            HashMap<kotlin.String, MutableList<DlcUpdatePackage>>()
        for (pkg in filtered) {
            val info = pkg.getManifest().getVersion()
            val oldState: kotlin.String = stateKey(sourceBaseVersion(info), sourceDlcVersion(info))
            byOld.computeIfAbsent(oldState) { ArrayList<DlcUpdatePackage>() }.add(pkg)
        }
        val queue = ArrayDeque<PathNode>()
        val visited: MutableSet<kotlin.String?> = HashSet<kotlin.String?>()
        queue.add(PathNode(startBase, startDlc, ArrayList<DlcUpdatePackage?>()))
        visited.add(stateKey(startBase, startDlc))
        while (!queue.isEmpty()) {
            val node = queue.poll()
            if (node.base == targetBaseVersion && node.dlc == targetDlcVersion) {
                return node.path
            }
            val nextPkgs = byOld.get(stateKey(node.base, node.dlc))
            if (nextPkgs == null || nextPkgs.isEmpty()) {
                continue
            }
            for (pkg in nextPkgs) {
                val info = pkg.getManifest().getVersion()
                val nextBase: kotlin.String = targetBaseVersion(info)
                val nextDlc: kotlin.String = targetDlcVersion(info)
                val nextState: kotlin.String = stateKey(nextBase, nextDlc)
                if (visited.contains(nextState)) {
                    continue
                }
                val nextPath: MutableList<DlcUpdatePackage?> = ArrayList<DlcUpdatePackage?>(node.path)
                nextPath.add(pkg)
                visited.add(nextState)
                queue.add(PathNode(nextBase, nextDlc, nextPath))
            }
        }
        return mutableListOf<DlcUpdatePackage?>()
    }

    private fun resolveUpdateChainTo(
        version: DlcUpdateVersion?,
        packages: MutableList<DlcUpdatePackage?>?,
        targetBaseOld: kotlin.String?,
        targetDlcOld: kotlin.String?,
        targetBaseNew: kotlin.String?,
        targetDlcNew: kotlin.String?,
        ignoreRollback: Boolean
    ): MutableList<DlcUpdatePackage?> {
        if (version == null || packages == null || packages.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val oldBase: kotlin.String = safeVersion(targetBaseOld)
        val targetOld: kotlin.String = safeVersion(targetDlcOld)
        var newBase: kotlin.String? = optionalVersion(targetBaseNew)
        if (newBase == null) {
            newBase = oldBase
        } else {
            newBase = safeVersion(newBase)
        }
        var targetNew: kotlin.String? = optionalVersion(targetDlcNew)
        if (targetNew == null) {
            targetNew = targetOld
        } else {
            targetNew = safeVersion(targetNew)
        }
        if (oldBase.isEmpty() || targetOld.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        var targetPkg: DlcUpdatePackage? = null
        for (pkg in packages) {
            if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                continue
            }
            val info = pkg.getManifest().getVersion()
            if (!hasVersionTransition(info)) {
                continue
            }
            if (oldBase != sourceBaseVersion(info)) {
                continue
            }
            if (targetOld == sourceDlcVersion(info)
                && newBase == targetBaseVersion(info)
                && targetNew == targetDlcVersion(info)
            ) {
                targetPkg = pkg
                break
            }
        }
        if (targetPkg == null) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val currentBase: kotlin.String = DlcUpdateService.safeVersion(version.getBase())
        val currentDlc: kotlin.String = DlcUpdateService.safeVersion(version.getDlc())
        if (currentDlc == targetOld
            && currentBase == oldBase
        ) {
            return mutableListOf(targetPkg)
        }
        val prefix: MutableList<DlcUpdatePackage?> =
            resolveUpdateChainTo(version, packages, oldBase, targetOld, ignoreRollback)
        if (prefix.isEmpty()) {
            return mutableListOf<DlcUpdatePackage?>()
        }
        val chain: MutableList<DlcUpdatePackage?> = ArrayList<DlcUpdatePackage?>(prefix)
        chain.add(targetPkg)
        return chain
    }

    private fun matchesVersion(current: DlcUpdateVersion?, info: DlcUpdateManifest.VersionInfo?): Boolean {
        if (current == null || info == null) {
            return false
        }
        if (!hasVersionTransition(info)) {
            return false
        }
        if (!isPlatformCompatible(info)) {
            return false
        }
        val base: kotlin.String = DlcUpdateService.safeVersion(current.getBase())
        val dlc: kotlin.String = DlcUpdateService.safeVersion(current.getDlc())
        return base == sourceBaseVersion(info)
                && dlc == sourceDlcVersion(info)
    }

    private fun findMissingModifyTargets(manifest: DlcUpdateManifest?, gameDir: Path): MutableList<kotlin.String?> {
        if (manifest == null || manifest.getFileOps() == null) {
            return mutableListOf<kotlin.String?>()
        }
        val missing: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        for (entry in manifest.getFileOps()!!.getModify()) {
            if (entry == null) {
                continue
            }
            val target: Path? = resolveGamePath(gameDir, entry.getTargetPath())
            if (target == null || !Files.exists(target)) {
                missing.add(entry.getTargetPath())
            }
        }
        return missing
    }

    private fun applyDeleteOps(ops: DlcUpdateManifest.FileOps?, context: UpdateContext) {
        if (ops == null) {
            return
        }
        for (raw in ops.getDel()) {
            if (raw == null || raw.isBlank()) {
                continue
            }
            val target: Path? = DlcUpdateService.resolveGamePath(DlcPaths.gameDir(), raw)
            if (target == null) {
                continue
            }
            if (Files.exists(target)) {
                backupPath(raw, target, context)
                handleSpecialDelete(target, raw, context)
                deleteRecursive(target)
            }
        }
    }

    private fun applyAddOps(ops: DlcUpdateManifest.FileOps?, context: UpdateContext) {
        if (ops == null) {
            return
        }
        val addedRequiredFiles: MutableList<Path?> = ArrayList<Path?>()
        var hasRemoteAdd = false
        var hasRequiredAdd = false
        for (key in ops.getAdd().keys) {
            if (key == null) {
                continue
            }
            val normalized = key.replace('\\', '/')
            if (normalized.startsWith("remote/")) {
                hasRemoteAdd = true
            }
            if (normalized.startsWith("required/")) {
                hasRequiredAdd = true
            }
        }
        for (entry in ops.getAdd().entries) {
            val sourceKey = entry.key
            val add = entry.value
            if (add == null || sourceKey == null || sourceKey.isBlank()) {
                continue
            }
            val source: Path? = resolveStagingPath(context.stagingRoot, sourceKey)
            if (source == null || !Files.exists(source)) {
                continue
            }
            val target: Path? = DlcUpdateService.resolveGamePath(DlcPaths.gameDir(), add.getAppliedTarget())
            if (target == null) {
                continue
            }
            val sourceIsDir = add.isSourceDir() || Files.isDirectory(source)
            val targetIsDir = add.getAppliedTarget() != null && add.getAppliedTarget()!!.endsWith("/")
            if (sourceIsDir) {
                applyAddDirectory(source, target, add.getOverrideMode(), sourceKey, targetIsDir, context)
                if (isRemotePath(target, context.dlcRoot)) {
                    context.addedRemote = true
                }
            } else {
                applyAddFile(source, target, add.getOverrideMode(), sourceKey, targetIsDir, context)
            }
            if (isRequiredPath(target, context.dlcRoot)) {
                addedRequiredFiles.add(target)
            }
        }
        applyImplicitRemoteAndRequired(context, addedRequiredFiles, hasRemoteAdd, hasRequiredAdd)
        removeSkipMarker(addedRequiredFiles)
        if (!addedRequiredFiles.isEmpty()) {
            context.addedRequired = true
        }
    }

    private fun applyMoveOps(ops: DlcUpdateManifest.FileOps?, context: UpdateContext?) {
        if (ops == null || context == null) {
            return
        }
        if (ops.getMove().isEmpty()) {
            return
        }
        val gameDir: Path = DlcPaths.gameDir()
        for (entry in ops.getMove().entries) {
            val rawFrom = entry.key
            val rawTo = entry.value
            if (rawFrom == null || rawFrom.isBlank() || rawTo == null || rawTo.isBlank()) {
                continue
            }
            val from: Path? = resolveGamePath(gameDir, rawFrom)
            if (from == null || !Files.exists(from)) {
                continue
            }
            val toIsDirHint = rawTo.endsWith("/")
            var to: Path? = resolveGamePath(gameDir, rawTo)
            if (to == null) {
                continue
            }
            if (toIsDirHint || Files.isDirectory(to)) {
                to = to.resolve(from.getFileName().toString())
            }
            val destExisted = Files.exists(to)
            backupPath(toGameRelativePath(from), from, context)
            if (destExisted) {
                backupPath(toGameRelativePath(to), to, context)
                deleteRecursive(to)
            } else {
                context.meta!!.getCreatedPaths().add(toGameRelativePath(to))
            }
            try {
                if (to.getParent() != null) {
                    Files.createDirectories(to.getParent())
                }
                if (moveWithHelperFallback(from, to, true)) {
                    continue
                }
            } catch (ignored: IOException) {
                try {
                    copyRecursive(from, to)
                    deleteRecursive(from)
                } catch (ignored2: Exception) {
                }
            }
        }
    }

    private fun applyAddDirectory(
        source: Path?, target: Path, mode: DlcUpdateManifest.OverrideMode, rawSourceKey: kotlin.String?,
        targetIsDir: Boolean, context: UpdateContext
    ) {
        var targetIsDir = targetIsDir
        if (!targetIsDir && !Files.isDirectory(target)) {
            targetIsDir = true
        }
        if (!targetIsDir) {
            return
        }
        if (Files.exists(target)) {
            when (mode) {
                DlcUpdateManifest.OverrideMode.NONE -> {
                    return
                }

                DlcUpdateManifest.OverrideMode.ALL -> {
                    backupPath(toGameRelativePath(target), target, context)
                    syncDirectory(source, target)
                    return
                }

                DlcUpdateManifest.OverrideMode.FALSE -> {
                    copyDirectory(source, target, false)
                    return
                }

                DlcUpdateManifest.OverrideMode.TRUE -> {
                    backupPath(toGameRelativePath(target), target, context)
                    copyDirectory(source, target, true)
                    return
                }
            }
        } else {
            context.meta!!.getCreatedPaths().add(toGameRelativePath(target))
            copyRecursive(source, target)
        }
    }

    private fun applyAddFile(
        source: Path,
        target: Path,
        mode: DlcUpdateManifest.OverrideMode?,
        rawSourceKey: kotlin.String?,
        targetIsDir: Boolean,
        context: UpdateContext
    ) {
        var actualTarget: Path? = target
        if (targetIsDir || Files.isDirectory(target)) {
            actualTarget = target.resolve(source.getFileName().toString())
        }
        if (Files.exists(actualTarget)) {
            if (mode == DlcUpdateManifest.OverrideMode.FALSE || mode == DlcUpdateManifest.OverrideMode.NONE) {
                return
            }
            backupPath(toGameRelativePath(actualTarget), actualTarget, context)
        } else {
            context.meta!!.getCreatedPaths().add(toGameRelativePath(actualTarget))
        }
        try {
            if (actualTarget!!.getParent() != null) {
                Files.createDirectories(actualTarget.getParent())
            }
            if (!copyWithHelperFallback(source, actualTarget, true)) {
                return
            }
            if (isNoticePath(actualTarget, context.dlcRoot)) {
                resetNoticeNeverShow(actualTarget)
            }
            if (isRemotePath(actualTarget, context.dlcRoot)) {
                context.addedRemote = true
            }
        } catch (ignored: IOException) {
        }
    }

    private fun applyModifyOps(ops: DlcUpdateManifest.FileOps?, context: UpdateContext, forceMissing: Boolean) {
        if (ops == null) {
            return
        }
        for (entry in ops.getModify()) {
            if (entry == null) {
                continue
            }
            val target: Path? = DlcUpdateService.resolveGamePath(DlcPaths.gameDir(), entry.getTargetPath())
            if (target == null || !Files.exists(target)) {
                if (forceMissing) {
                    continue
                }
                throw IllegalStateException("Missing modify target")
            }
            backupPath(entry.getTargetPath(), target, context)
            val content: kotlin.String? = readString(target)
            if (content == null) {
                continue
            }
            val find = entry.getNormalizedFind()
            if (find == null || find.isEmpty()) {
                continue
            }
            val replaced: kotlin.String? = replaceOccurrences(content, find, entry.getReplace()!!, entry.getWhich())
            if (content != replaced) {
                writeString(target, replaced)
            }
            if (isNoticePath(target, context.dlcRoot)) {
                resetNoticeNeverShow(target)
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun applyUpdateOps(ops: DlcUpdateManifest.UpdateOps?, context: UpdateContext?) {
        if (ops == null || context == null || context.dlcRoot == null) {
            return
        }
        if (!ops.getRemote().isEmpty()) {
            applyRemoteUpdateOps(ops.getRemote(), context)
        }
        if (!ops.getRequired().isEmpty()) {
            applyRequiredUpdateOps(ops.getRequired(), context)
        }
        if (!ops.getInfo().isEmpty()) {
            applyInfoUpdateOps(ops.getInfo(), context)
        }
        if (!ops.getPreset().isEmpty()) {
            applyPresetUpdateOps(ops.getPreset(), context)
        }
    }

    private fun applyInfoUpdateOps(
        updates: MutableMap<kotlin.String, DlcUpdateManifest.UpdateEntry>,
        context: UpdateContext
    ) {
        val infoByIdentifier: MutableMap<kotlin.String?, Path?> = loadInfoConfigPathByIdentifier(context.dlcRoot)
        if (infoByIdentifier.isEmpty()) {
            return
        }
        for (update in updates.entries) {
            val identifier = update.key
            if (identifier == null || identifier.isBlank()) {
                continue
            }
            val infoPath = infoByIdentifier.get(identifier)
            if (infoPath == null || !Files.exists(infoPath)) {
                continue
            }
            val updateEntry = update.value
            if (updateEntry == null) {
                continue
            }
            backupPath(toGameRelativePath(infoPath), infoPath, context)
            updateInfoConfigFields(infoPath, updateEntry)
        }
    }

    private fun applyPresetUpdateOps(
        updates: MutableMap<kotlin.String, DlcUpdateManifest.UpdateEntry>,
        context: UpdateContext
    ) {
        val presetById: MutableMap<kotlin.String?, Path?> = loadPresetConfigPathById(context.dlcRoot)
        if (presetById.isEmpty()) {
            return
        }
        for (update in updates.entries) {
            val presetId = update.key
            if (presetId == null || presetId.isBlank()) {
                continue
            }
            val presetPath: Path? = findPresetConfigPath(presetById, presetId)
            if (presetPath == null || !Files.exists(presetPath)) {
                continue
            }
            val updateEntry = update.value
            if (updateEntry == null) {
                continue
            }
            backupPath(toGameRelativePath(presetPath), presetPath, context)
            updatePresetConfigFields(presetPath, updateEntry)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun applyRemoteUpdateOps(
        updates: MutableMap<kotlin.String, DlcUpdateManifest.UpdateEntry>,
        context: UpdateContext
    ) {
        val entries: MutableMap<kotlin.String?, DlcRemoteEntry?> = loadRemoteEntries(context.dlcRoot)
        if (entries.isEmpty()) {
            return
        }
        for (update in updates.entries) {
            val identifier = update.key
            if (identifier == null || identifier.isBlank()) {
                continue
            }
            var entry = entries.get(identifier)
            if (entry == null) {
                continue
            }
            val shouldRedownload = hasRemoteDownloadSourceChanges(entry, update.value)
            if (context != null && shouldRedownload) {
                context.addedRemote = true
            }
            val wasEnabled = isRemoteEnabled(entry)
            val oldTarget: Path? = entry.getTargetPath()
            if (shouldRedownload && wasEnabled && oldTarget != null) {
                uninstallDlcPath(oldTarget, context.dlcRoot, context)
            }
            if (shouldRedownload && oldTarget != null && Files.exists(oldTarget)) {
                backupAndDelete(oldTarget, context)
            }
            val updateEntry = update.value
            val configPath: Path? = entry.getConfigPath()
            if (configPath != null && Files.exists(configPath)) {
                backupPath(toGameRelativePath(configPath), configPath, context)
                updateRemoteConfigFields(configPath, updateEntry)
                entry = DlcRemoteLoader.loadSingle(configPath, context.dlcRoot)
                if (entry == null) {
                    continue
                }
            }
            if (!shouldRedownload) {
                continue
            }
            val downloaded = downloadRemoteEntry(entry, true)
            if (downloaded == null) {
                throw IOException("remote update download failed")
            }
            if (context != null && downloaded.path != null) {
                val created: kotlin.String = toGameRelativePath(downloaded.path)
                if (created != null && !created.isBlank() && !context.meta!!.getCreatedPaths().contains(created)) {
                    context.meta.getCreatedPaths().add(created)
                }
            }
            if (downloaded.fileName != null && !downloaded.fileName.isBlank()) {
                updateRemoteFileName(configPath, downloaded.fileName)
            }
            if (wasEnabled) {
                val applied: MutableList<Path?> = applyRemoteTargets(entry, downloaded.path, context)
                updateConfigState(entry, downloaded.path, applied, context)
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun applyRequiredUpdateOps(
        updates: MutableMap<kotlin.String, DlcUpdateManifest.UpdateEntry>,
        context: UpdateContext
    ) {
        val entries: MutableMap<kotlin.String?, DlcRequiredEntry?> = loadRequiredEntries(context.dlcRoot)
        val infoByIdentifier: MutableMap<kotlin.String?, Path?> = loadInfoConfigPathByIdentifier(context.dlcRoot)
        if (entries.isEmpty()) {
            return
        }
        for (update in updates.entries) {
            val identifier = update.key
            if (identifier == null || identifier.isBlank()) {
                continue
            }
            var entry = entries.get(identifier)
            if (entry == null) {
                continue
            }
            val infoPath = infoByIdentifier.get(identifier)
            val shouldRedownload = hasRequiredDownloadSourceChanges(entry, update.value)
            val oldFileName: kotlin.String? = resolveRequiredFileName(entry, context.dlcRoot)
            val appliedTargets: MutableList<AppliedTarget> = collectExistingRequiredTargets(entry, oldFileName)
            val wasApplied = !appliedTargets.isEmpty()
            if (shouldRedownload && wasApplied) {
                for (target in appliedTargets) {
                    val resolved: Path? = if (target.directory)
                        (if (oldFileName == null) null else target.path!!.resolve(oldFileName))
                    else
                        target.path
                    if (resolved != null) {
                        backupAndDelete(resolved, context)
                    }
                }
            }
            if (shouldRedownload && oldFileName != null) {
                val oldDownload = entry.resolveRequiredPath(oldFileName)
                if (oldDownload != null && Files.exists(oldDownload)) {
                    backupAndDelete(oldDownload, context)
                }
            }
            val updateEntry = update.value
            val configPath = entry.getConfigPath()
            if (configPath != null && Files.exists(configPath)) {
                backupPath(toGameRelativePath(configPath), configPath, context)
                updateRequiredConfigFields(configPath, updateEntry)
                syncInfoConfigFields(infoPath, updateEntry, context)
                entry = DlcRequiredLoader.loadSingle(configPath, context.dlcRoot)
                if (entry == null) {
                    continue
                }
            }
            if (!shouldRedownload) {
                continue
            }
            val downloaded = downloadRequiredEntry(entry, true)
            if (downloaded == null) {
                throw IOException("required update download failed")
            }
            if (context != null && downloaded.path != null) {
                val created: kotlin.String = toGameRelativePath(downloaded.path)
                if (created != null && !created.isBlank() && !context.meta!!.getCreatedPaths().contains(created)) {
                    context.meta.getCreatedPaths().add(created)
                }
            }
            if (downloaded.fileName != null && !downloaded.fileName.isBlank()) {
                updateRequiredFileName(configPath, downloaded.fileName)
                rewriteInfoFileWithNewFileName(infoPath, downloaded.fileName, context)
            }
            if (wasApplied) {
                applyRequiredTargets(downloaded.path, downloaded.fileName, appliedTargets, context)
            }
        }
    }

    private fun hasRemoteDownloadSourceChanges(
        entry: DlcRemoteEntry?,
        update: DlcUpdateManifest.UpdateEntry?
    ): Boolean {
        if (entry == null || update == null) {
            return false
        }
        if (update.getCurseforge() != null
            && normalizeNullable(update.getCurseforge()) != DlcUpdateService.normalizeNullable(entry.getCurseforgeFileId())
        ) {
            return true
        }
        if (update.getModrinth() != null
            && normalizeNullable(update.getModrinth()) != DlcUpdateService.normalizeNullable(entry.getModrinthVersionId())
        ) {
            return true
        }
        if (update.getFields().containsKey("download.directUrl")) {
            return normalizeStringList(readStringArray(update.getFields().get("download.directUrl"))) !=
                DlcUpdateService.normalizeStringList(entry.getDirectUrls())
        }
        return false
    }

    private fun hasRequiredDownloadSourceChanges(
        entry: DlcRequiredEntry?,
        update: DlcUpdateManifest.UpdateEntry?
    ): Boolean {
        if (entry == null || update == null) {
            return false
        }
        if (update.getCurseforge() != null
            && normalizeNullable(update.getCurseforge()) != normalizeNullable(entry.getCurseforgeFileId())
        ) {
            return true
        }
        if (update.getModrinth() != null
            && normalizeNullable(update.getModrinth()) != normalizeNullable(entry.getModrinthVersionId())
        ) {
            return true
        }
        if (update.getFields().containsKey("download.directUrl")) {
            return normalizeStringList(readStringArray(update.getFields().get("download.directUrl"))) !=
                normalizeStringList(entry.getDirectUrls().toMutableList())
        }
        return false
    }

    private fun normalizeNullable(value: kotlin.String?): kotlin.String? {
        if (value == null) {
            return null
        }
        val trimmed = value.trim { it <= ' ' }
        return if (trimmed.isEmpty()) "" else trimmed
    }

    private fun normalizeStringList(values: MutableList<kotlin.String?>?): MutableList<kotlin.String?> {
        if (values == null || values.isEmpty()) {
            return mutableListOf<kotlin.String?>()
        }
        val normalized: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        for (value in values) {
            if (value == null) {
                continue
            }
            val trimmed = value.trim { it <= ' ' }
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed)
            }
        }
        return normalized
    }

    private fun readStringArray(element: JsonElement?): MutableList<kotlin.String?> {
        val values: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        if (element == null || element.isJsonNull()) {
            return values
        }
        if (element.isJsonPrimitive()) {
            values.add(element.getAsString())
            return values
        }
        if (element.isJsonArray()) {
            for (child in element.getAsJsonArray()) {
                if (child != null && !child.isJsonNull() && child.isJsonPrimitive()) {
                    values.add(child.getAsString())
                }
            }
        }
        return values
    }

    private fun loadRemoteEntries(dlcRoot: Path?): MutableMap<kotlin.String?, DlcRemoteEntry?> {
        val map: MutableMap<kotlin.String?, DlcRemoteEntry?> = HashMap<kotlin.String?, DlcRemoteEntry?>()
        for (entry in DlcRemoteLoader.load(dlcRoot)) {
            if (entry == null) {
                continue
            }
            val id: kotlin.String? = entry.getDlcIdentifier()
            if (id != null && !id.isBlank()) {
                map.put(id, entry)
            }
        }
        return map
    }

    private fun loadRequiredEntries(dlcRoot: Path?): MutableMap<kotlin.String?, DlcRequiredEntry?> {
        val map: MutableMap<kotlin.String?, DlcRequiredEntry?> = HashMap<kotlin.String?, DlcRequiredEntry?>()
        val requiredDir = if (dlcRoot == null) null else dlcRoot.resolve("required")
        if (requiredDir == null || !Files.isDirectory(requiredDir)) {
            return map
        }
        try {
            Files.newDirectoryStream(requiredDir).use { stream ->
                for (path in stream) {
                    if (path.getFileName() == null) {
                        continue
                    }
                    val fileName = path.getFileName().toString()
                    if (!DcFormatUtil.isDcName(fileName)) {
                        continue
                    }
                    if (isNoticeConfig(fileName)) {
                        continue
                    }
                    val entry = DlcRequiredLoader.loadSingle(path, dlcRoot)
                    if (entry == null) {
                        continue
                    }
                    val id = entry.getIdentifier()
                    if (id != null && !id.isBlank()) {
                        map.put(id, entry)
                    }
                }
            }
        } catch (ignored: IOException) {
        }
        return map
    }

    private fun isRemoteEnabled(entry: DlcRemoteEntry?): Boolean {
        if (entry == null) {
            return false
        }
        var fileName: kotlin.String? = entry.getFileNameHint()
        if (fileName == null || fileName.isBlank()) {
            val target: Path? = entry.getTargetPath()
            if (target == null) {
                return false
            }
            fileName = target.getFileName().toString()
        }
        val type = if (entry.getType() == null) DlcType.UNKNOWN else entry.getType()
        if (type == DlcType.UNKNOWN) {
            val target: Path? = DlcPaths.resolveAppliedTarget(entry.getAppliedTargetRaw())
            if (target == null) {
                return false
            }
            val dirTarget =
                DlcPaths.isAppliedTargetDirectory(entry.getAppliedTargetRaw(), target, entry.getTargetPath())
            if (dirTarget) {
                return Files.exists(target.resolve(fileName))
            }
            return Files.exists(target)
        }
        val targets: MutableList<Path> = when (type) {
            DlcType.MOD -> mutableListOf(DlcPaths.modsDir())
            DlcType.RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots().toMutableList()
            DlcType.DATA_PACK -> DlcPathConfig.getDatapackRoots().toMutableList()
            DlcType.TACZ_PACK -> mutableListOf(DlcPaths.taczDir())
            else -> mutableListOf()
        }
        if (targets.isEmpty()) {
            return false
        }
        for (dir in targets) {
            if (!Files.exists(dir.resolve(fileName))) {
                return false
            }
        }
        return true
    }

    private fun isRequiredApplied(entry: DlcRequiredEntry?, dlcRoot: Path?): Boolean {
        if (entry == null || dlcRoot == null) {
            return false
        }
        val fileName: kotlin.String? = resolveRequiredFileName(entry, dlcRoot)
        if (fileName == null || fileName.isBlank()) {
            return false
        }
        for (target in entry.resolveAppliedTargets()) {
            val resolved: Path? = resolveTarget(target, fileName)
            if (resolved != null && Files.exists(resolved)) {
                return true
            }
        }
        return false
    }

    private fun collectExistingRequiredTargets(
        entry: DlcRequiredEntry?,
        fileName: kotlin.String?
    ): MutableList<AppliedTarget> {
        if (entry == null) {
            return mutableListOf()
        }
        val targets: MutableList<AppliedTarget> = ArrayList<AppliedTarget>()
        for (target in entry.resolveAppliedTargets()) {
            if (target == null) {
                continue
            }
            val dirTarget = Files.isDirectory(target)
                    || target.toString().endsWith("/")
                    || target.toString().endsWith("\\")
            if (dirTarget) {
                if (fileName != null && !fileName.isBlank()) {
                    val resolved = target.resolve(fileName).normalize()
                    if (Files.exists(resolved)) {
                        targets.add(AppliedTarget(target, true))
                    }
                }
            } else if (Files.exists(target)) {
                targets.add(AppliedTarget(target, false))
            }
        }
        return targets
    }

    private fun updateRemoteConfigFields(configPath: Path?, update: DlcUpdateManifest.UpdateEntry?) {
        if (configPath == null || update == null) {
            return
        }
        val obj = readJson(configPath)
        if (obj == null) {
            return
        }
        if (update.getCurseforge() != null && !update.getCurseforge()!!.isBlank()) {
            obj.addProperty("curseforge_fileid", update.getCurseforge())
        }
        if (update.getModrinth() != null && !update.getModrinth()!!.isBlank()) {
            obj.addProperty("modrinth_versionid", update.getModrinth())
        }
        applyEntryFieldUpdates(obj, update, FieldUpdateMode.NORMAL)
        writeJson(configPath, obj)
    }

    private fun updateRequiredConfigFields(configPath: Path?, update: DlcUpdateManifest.UpdateEntry?) {
        if (configPath == null || update == null) {
            return
        }
        val obj = readJson(configPath)
        if (obj == null) {
            return
        }
        if (update.getCurseforge() != null && !update.getCurseforge()!!.isBlank()) {
            obj.addProperty("curseforge_fileid", update.getCurseforge())
        }
        if (update.getModrinth() != null && !update.getModrinth()!!.isBlank()) {
            obj.addProperty("modrinth_versionid", update.getModrinth())
        }
        applyEntryFieldUpdates(obj, update, FieldUpdateMode.NORMAL)
        writeJson(configPath, obj)
    }

    private fun syncInfoConfigFields(infoPath: Path?, update: DlcUpdateManifest.UpdateEntry?, context: UpdateContext?) {
        if (infoPath == null || update == null || !Files.exists(infoPath)) {
            return
        }
        val info = readJson(infoPath)
        if (info == null) {
            return
        }
        if (applyEntryFieldUpdates(info, update, FieldUpdateMode.INFO_ALLOWED)) {
            backupPath(toGameRelativePath(infoPath), infoPath, context)
            writeJson(infoPath, info)
        }
    }

    private fun updateInfoConfigFields(infoPath: Path?, update: DlcUpdateManifest.UpdateEntry?) {
        if (infoPath == null || update == null || !Files.exists(infoPath)) {
            return
        }
        val info = readJson(infoPath)
        if (info == null) {
            return
        }
        if (applyEntryFieldUpdates(info, update, FieldUpdateMode.NORMAL)) {
            writeJson(infoPath, info)
        }
    }

    private fun updatePresetConfigFields(presetPath: Path?, update: DlcUpdateManifest.UpdateEntry?) {
        if (presetPath == null || update == null || !Files.exists(presetPath)) {
            return
        }
        val preset = readJson(presetPath)
        if (preset == null) {
            return
        }
        if (applyEntryFieldUpdates(preset, update, FieldUpdateMode.NORMAL)) {
            writeJson(presetPath, preset)
        }
    }

    private fun applyEntryFieldUpdates(
        obj: JsonObject?,
        update: DlcUpdateManifest.UpdateEntry?,
        mode: FieldUpdateMode?
    ): Boolean {
        if (obj == null || update == null || update.getFields().isEmpty()) {
            return false
        }
        var changed = false
        for (field in update.getFields().entries) {
            val path = field.key
            val value = field.value
            if (path == null || path.isBlank()) {
                continue
            }
            if (applyStructuredFieldPath(obj, path, value, if (mode == null) FieldUpdateMode.NORMAL else mode)) {
                changed = true
            }
        }
        return changed
    }

    private val INFO_ALLOWED_TABLES = mutableSetOf<kotlin.String?>(
        "basic", "displayname", "tip", "dependencies", "conflicts"
    )
    private val INFO_ALLOWED_BASIC_KEYS = mutableSetOf<kotlin.String?>(
        "identifier",
        "file_name",
        "type",
        "appliedTarget",
        "RedirectUrl",
        "isInList",
        "isHidden",
        "AllowEnableByAll",
        "dependencies",
        "conflicts",
        "alarm"
    )

    private fun applyStructuredFieldPath(
        root: JsonObject?,
        dottedPath: kotlin.String?,
        value: JsonElement?,
        mode: FieldUpdateMode?
    ): Boolean {
        if (root == null || dottedPath == null || dottedPath.isBlank()) {
            return false
        }
        val parts: Array<kotlin.String?> =
            dottedPath.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size < 2) {
            return false
        }
        val table = parts[0]!!.trim { it <= ' ' }.lowercase()
        if (table.isBlank()) {
            return false
        }
        if (mode == FieldUpdateMode.INFO_ALLOWED && !INFO_ALLOWED_TABLES.contains(table)) {
            return false
        }
        val keyPath = Arrays.copyOfRange(parts, 1, parts.size)
        if ("basic" == table || "download" == table) {
            if (mode == FieldUpdateMode.INFO_ALLOWED && "download" == table) {
                return false
            }
            var key = keyPath.joinToString(".").trim { it <= ' ' }
            if (key.isBlank()) {
                return false
            }
            if ("basic" == table) {
                key = normalizeBasicFieldKey(key)
            }
            if (mode == FieldUpdateMode.INFO_ALLOWED && !INFO_ALLOWED_BASIC_KEYS.contains(key)) {
                return false
            }
            upsertCaseInsensitive(root, key, safeCopy(value))
            return true
        }
        val containerKey = when (table) {
            "displayname" -> "DisplayName"
            "tip" -> "tip"
            "dependencies" -> "dependencies"
            "conflicts" -> "conflicts"
            else -> null
        }
        if (containerKey == null) {
            return false
        }
        val containerElement = DcConfigIO.getIgnoreCase(root, containerKey)
        val container: JsonObject?
        if (containerElement != null && containerElement.isJsonObject()) {
            container = containerElement.getAsJsonObject()
        } else {
            container = JsonObject()
            upsertCaseInsensitive(root, containerKey, container)
        }
        if (keyPath.size == 0) {
            return false
        }
        setNestedPath(container, keyPath, safeCopy(value))
        return true
    }

    private fun normalizeBasicFieldKey(key: kotlin.String?): kotlin.String {
        if (key == null) {
            return ""
        }
        if ("conflict".equals(key, ignoreCase = true)) {
            return "conflicts"
        }
        if ("dependency".equals(key, ignoreCase = true)) {
            return "dependencies"
        }
        if ("dlc_identifier".equals(key, ignoreCase = true)) {
            return "identifier"
        }
        if ("dlc_name".equals(key, ignoreCase = true)) {
            return "file_name"
        }
        if ("redirecturl".equals(key, ignoreCase = true)) {
            return "RedirectUrl"
        }
        return key
    }

    private fun findExistingKeyIgnoreCase(obj: JsonObject?, key: kotlin.String?): kotlin.String? {
        if (obj == null || key == null) {
            return null
        }
        if (obj.has(key)) {
            return key
        }
        for (candidate in obj.keySet()) {
            if (candidate != null && candidate.equals(key, ignoreCase = true)) {
                return candidate
            }
        }
        return null
    }

    private fun upsertCaseInsensitive(obj: JsonObject?, key: kotlin.String?, value: JsonElement?) {
        if (obj == null || key == null || key.isBlank()) {
            return
        }
        val existing: kotlin.String? = findExistingKeyIgnoreCase(obj, key)
        if (existing != null) {
            obj.add(existing, value)
        } else {
            obj.add(key, value)
        }
    }

    private fun setNestedPath(root: JsonObject?, path: Array<kotlin.String?>?, value: JsonElement?) {
        if (root == null || path == null || path.size == 0) {
            return
        }
        var current: JsonObject? = root
        for (i in path.indices) {
            val raw = path[i]
            val key = if (raw == null) "" else raw.trim { it <= ' ' }
            if (key.isBlank()) {
                return
            }
            if (i == path.size - 1) {
                upsertCaseInsensitive(current, key, value)
                return
            }
            val child = DcConfigIO.getIgnoreCase(current, key)
            val next: JsonObject?
            if (child != null && child.isJsonObject()) {
                next = child.getAsJsonObject()
            } else {
                next = JsonObject()
                upsertCaseInsensitive(current, key, next)
            }
            current = next
        }
    }

    private fun safeCopy(value: JsonElement?): JsonElement? {
        if (value == null || value.isJsonNull()) {
            return JsonNull.INSTANCE
        }
        return value.deepCopy()
    }

    private fun loadInfoConfigPathByIdentifier(dlcRoot: Path?): MutableMap<kotlin.String?, Path?> {
        val map: MutableMap<kotlin.String?, Path?> = HashMap<kotlin.String?, Path?>()
        if (dlcRoot == null) {
            return map
        }
        val infoDir = dlcRoot.resolve("info")
        if (!Files.isDirectory(infoDir)) {
            return map
        }
        try {
            Files.walk(infoDir).use { stream ->
                stream.filter { path: Path? -> Files.isRegularFile(path) }
                    .forEach { path: Path? ->
                        if (path!!.getFileName() == null) {
                            return@forEach
                        }
                        val fileName = path.getFileName().toString()
                        if (!DcFormatUtil.isDcName(fileName)) {
                            return@forEach
                        }
                        val baseName: kotlin.String = DcFormatUtil.stripConfigExtension(fileName)
                        if ("version".equals(baseName, ignoreCase = true) || "notice".equals(
                                baseName,
                                ignoreCase = true
                            )
                        ) {
                            return@forEach
                        }
                        val obj = readJson(path)
                        if (obj == null) {
                            return@forEach
                        }
                        var identifier: kotlin.String? = DcConfigIO.getStringIgnoreCase(obj, "identifier")
                        if (identifier == null || identifier.isBlank()) {
                            identifier = DcConfigIO.getStringIgnoreCase(obj, "dlc_identifier")
                        }
                        if (identifier == null || identifier.isBlank()) {
                            return@forEach
                        }
                        map.putIfAbsent(identifier, path)
                    }
            }
        } catch (ignored: IOException) {
        }
        return map
    }

    private fun loadPresetConfigPathById(dlcRoot: Path?): MutableMap<kotlin.String?, Path?> {
        val map: MutableMap<kotlin.String?, Path?> = LinkedHashMap<kotlin.String?, Path?>()
        if (dlcRoot == null) {
            return map
        }
        for (entry in DlcPresetLoader.loadAll(mutableListOf(dlcRoot))) {
            if (entry == null || entry.getSourcePath() == null) {
                continue
            }
            if (entry.getId() != null && !entry.getId()!!.isBlank()) {
                map.putIfAbsent(entry.getId(), entry.getSourcePath())
            }
            if (entry.getFileName() != null && !entry.getFileName()!!.isBlank()) {
                map.putIfAbsent(entry.getFileName(), entry.getSourcePath())
            }
        }
        return map
    }

    private fun findPresetConfigPath(presetById: MutableMap<kotlin.String?, Path?>?, key: kotlin.String?): Path? {
        if (presetById == null || presetById.isEmpty() || key == null || key.isBlank()) {
            return null
        }
        val exact = presetById.get(key)
        if (exact != null) {
            return exact
        }
        for (entry in presetById.entries) {
            val candidate = entry.key
            if (candidate != null && candidate.equals(key, ignoreCase = true)) {
                return entry.value
            }
        }
        return null
    }

    private fun updateRemoteFileName(configPath: Path?, fileName: kotlin.String?) {
        if (configPath == null || fileName == null || fileName.isBlank()) {
            return
        }
        val obj = readJson(configPath)
        if (obj == null) {
            return
        }
        obj.addProperty("file_name", fileName)
        writeJson(configPath, obj)
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot != null) {
            DlcRemoteLoader.loadSingle(configPath, dlcRoot)
        }
    }

    private fun updateRequiredFileName(configPath: Path?, fileName: kotlin.String?) {
        if (configPath == null || fileName == null || fileName.isBlank()) {
            return
        }
        val obj = readJson(configPath)
        if (obj == null) {
            return
        }
        obj.addProperty("file_name", fileName)
        writeJson(configPath, obj)
        val dlcRoot: Path? = dlcRoot
        if (dlcRoot != null) {
            DlcRequiredLoader.loadSingle(configPath, dlcRoot)
        }
    }

    private fun rewriteInfoFileWithNewFileName(infoPath: Path?, fileName: kotlin.String?, context: UpdateContext?) {
        if (infoPath == null || fileName == null || fileName.isBlank() || !Files.exists(infoPath)) {
            return
        }
        val info = readJson(infoPath)
        if (info == null) {
            return
        }
        upsertCaseInsensitive(info, "file_name", safeCopy(JsonPrimitive(fileName)))
        val infoDir = infoPath.getParent()
        if (infoDir == null) {
            backupPath(toGameRelativePath(infoPath), infoPath, context)
            writeJson(infoPath, info)
            return
        }
        val targetPath: Path? = DcFormatUtil.resolveConfigPath(infoDir, fileName)
        if (targetPath == null) {
            backupPath(toGameRelativePath(infoPath), infoPath, context)
            writeJson(infoPath, info)
            return
        }
        backupPath(toGameRelativePath(infoPath), infoPath, context)
        if (targetPath.normalize() != infoPath.normalize() && Files.exists(targetPath)) {
            backupPath(toGameRelativePath(targetPath), targetPath, context)
            check(deleteWithHelperFallback(targetPath)) { latestFailureMessage("Failed to replace info file: " + targetPath)!! }
        }
        if (targetPath.normalize() != infoPath.normalize()) {
            check(deleteWithHelperFallback(infoPath)) { latestFailureMessage("Failed to remove old info file: " + infoPath)!! }
        }
        writeJson(targetPath, info)
    }

    private fun readJson(path: Path?): JsonObject? {
        return DcConfigIO.read(path, layoutForPath(path))
    }

    private fun writeJson(path: Path?, obj: JsonObject?) {
        if (path == null || obj == null) {
            return
        }
        if (DcConfigIO.write(path, obj, layoutForPath(path))) {
            return
        }
        val target: Path = DcConfigIO.toDcPath(path) ?: path
        val serialized: kotlin.String = serializeJsonForPath(target, obj)
        val result = DlcFileOpsHelperClient.scheduleWriteText(target, serialized)
        if (result.ok) {
            markHelperScheduled()
            if (target != path) {
                deleteWithHelperFallback(path)
            }
            return
        }
        recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message, target, "write")
    }

    private fun layoutForPath(path: Path?): DcConfigIO.Layout {
        if (path == null) {
            return DcConfigIO.Layout.GENERIC
        }
        val normalized = path.toString().replace('\\', '/').lowercase()
        if (normalized.contains("/remote/")
            || normalized.contains("/required/")
        ) {
            return DcConfigIO.Layout.DLC_STRUCTURED
        }
        return DcConfigIO.Layout.GENERIC
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadRemoteEntry(entry: DlcRemoteEntry?, ignoreExistingFileName: Boolean = false): DownloadedFile? {
        if (entry == null) {
            return null
        }
        return downloadWithRetry(entry, DlcDownloadCandidate.resolver(entry), ignoreExistingFileName)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadRequiredEntry(
        entry: DlcRequiredEntry?,
        ignoreExistingFileName: Boolean = false
    ): DownloadedFile? {
        if (entry == null) {
            return null
        }
        return downloadWithRetry(entry, DlcDownloadCandidate.resolver(entry), ignoreExistingFileName)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadWithRetry(
        entry: Any?,
        resolver: DlcDownloadCandidate.Resolver,
        ignoreExistingFileName: Boolean
    ): DownloadedFile {
        val maxRetry = max(1, DlcPathConfig.getMaxRetryCount())
        var lastError: IOException? = null
        var candidates: MutableList<DlcDownloadCandidate>?
        while (!(resolver.next().also { candidates = it }).isEmpty()) {
            for (candidate in candidates!!) {
                for (attempt in 1..maxRetry) {
                    try {
                        return downloadCandidate(entry, candidate, ignoreExistingFileName)
                    } catch (ex: IOException) {
                        lastError = ex
                    }
                }
            }
        }
        if (lastError == null) {
            lastError = resolver.lastError()
        }
        if (lastError != null) {
            throw lastError
        }
        throw IOException("missing download url")
    }

    @Throws(IOException::class)
    private fun downloadCandidate(
        entry: Any?,
        candidate: DlcDownloadCandidate,
        ignoreExistingFileName: Boolean
    ): DownloadedFile {
        var fileName: kotlin.String? = null
        var fileNameExplicit = false
        if (entry is DlcRemoteEntry) {
            fileName = entry.getFileNameHint()
            fileNameExplicit = entry.isFileNameExplicit()
        } else if (entry is DlcRequiredEntry) {
            fileName = entry.getRequiredFileName()
            fileNameExplicit = fileName != null && !fileName.isBlank()
        }
        if (ignoreExistingFileName) {
            fileNameExplicit = false
            fileName = null
        }
        if (!fileNameExplicit) {
            if (candidate.fileNameOverride != null && !candidate.fileNameOverride.isBlank()) {
                fileName = candidate.fileNameOverride
            }
            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                val urlName = DlcDownloadUtil.extractFileNameFromUrl(candidate.url)
                if (DlcDownloadUtil.isLikelyFileName(urlName)) {
                    fileName = urlName
                }
            }
            if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
                val headerName: kotlin.String? = fetchHeaderFileName(candidate.url)
                if (DlcDownloadUtil.isLikelyFileName(headerName)) {
                    fileName = headerName
                }
            }
        }
        if (!DlcDownloadUtil.isLikelyFileName(fileName)) {
            throw IOException("invalid file name")
        }
        fileName = sanitizeFileName(fileName)
        notifyApplyProgress("Downloading " + fileName + "...", 0f)
        val target: Path?
        if (entry is DlcRemoteEntry) {
            target = entry.resolveTargetPath(fileName)
        } else if (entry is DlcRequiredEntry) {
            target = entry.resolveRequiredPath(fileName)
        } else {
            target = null
        }
        if (target == null) {
            throw IOException("invalid target path")
        }
        Files.createDirectories(target.getParent())
        val tempFile = target.resolveSibling(fileName + ".part")
        deleteWithHelperFallback(tempFile)
        downloadToFile(candidate.url, tempFile)
        if (!moveWithHelperFallback(tempFile, target, true)) {
            throw IOException(latestFailureMessage("failed to move downloaded file"))
        }
        return DownloadedFile(target, fileName)
    }

    @Throws(IOException::class)
    private fun downloadToFile(url: kotlin.String?, target: Path) {
        val parent = target.getParent()
        if (parent == null) {
            throw IOException("invalid target path")
        }
        val displayName = if (target.getFileName() == null) "download" else target.getFileName().toString()
        val downloaded = DlcDownloadService.download(
            url,
            parent,
            displayName,
            DlcDownloadService.ProgressListener { done: Long, total: Long ->
                val progress = if (total > 0) max(0f, min(1f, done / total.toFloat())) else -1f
                notifyApplyProgress("Downloading " + displayName + "...", progress)
            },
            null
        )
        if (downloaded != target) {
            if (!moveWithHelperFallback(downloaded, target, true)) {
                throw IOException(latestFailureMessage("failed to finalize downloaded file"))
            }
        }
    }

    private fun notifyApplyProgress(status: kotlin.String?, progress: Float) {
        val listener: ApplyProgressListener? = CURRENT_PROGRESS_LISTENER.get()
        if (listener != null) {
            listener.onProgress(status, progress)
        }
    }

    private fun fetchHeaderFileName(url: kotlin.String): kotlin.String? {
        var connection: HttpURLConnection? = null
        try {
            connection = URI.create(url).toURL().openConnection() as HttpURLConnection?
            connection!!.setInstanceFollowRedirects(true)
            connection.setRequestMethod("HEAD")
            connection.setConnectTimeout(DlcPathConfig.getConnectTimeoutMs())
            connection.setReadTimeout(DlcPathConfig.getTaskTimeoutMs())
            connection.setRequestProperty("User-Agent", "DLC-Manager")
            connection.connect()
            return DlcDownloadHeaderUtil.extractFileNameFromHeaders(connection)
        } catch (ignored: Exception) {
            return null
        } finally {
            if (connection != null) {
                connection.disconnect()
            }
        }
    }

    private fun applyRemoteTargets(entry: DlcRemoteEntry?, source: Path?, context: UpdateContext?): MutableList<Path?> {
        val targets: MutableList<Path?> = ArrayList<Path?>()
        if (entry == null || source == null) {
            return targets
        }
        val type = if (entry.getType() == null) DlcType.UNKNOWN else entry.getType()
        val fileName = source.getFileName().toString()
        try {
            if (type == DlcType.UNKNOWN) {
                val target: Path? = DlcPaths.resolveAppliedTarget(entry.getAppliedTargetRaw())
                if (target == null) {
                    return targets
                }
                val dirTarget = DlcPaths.isAppliedTargetDirectory(entry.getAppliedTargetRaw(), target, source)
                if (dirTarget) {
                    Files.createDirectories(target)
                    val resolved = target.resolve(fileName)
                    backupOrCreate(resolved, context)
                    if (!copyWithHelperFallback(source, resolved, true)) {
                        return targets
                    }
                    targets.add(resolved)
                } else {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent())
                    }
                    backupOrCreate(target, context)
                    if (!copyWithHelperFallback(source, target, true)) {
                        return targets
                    }
                    targets.add(target)
                }
                return targets
            }
            val targetDirs: MutableList<Path> = when (type) {
            DlcType.MOD -> mutableListOf(DlcPaths.modsDir())
                DlcType.RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots().toMutableList()
                DlcType.DATA_PACK -> DlcPathConfig.getDatapackRoots().toMutableList()
            DlcType.TACZ_PACK -> mutableListOf(DlcPaths.taczDir())
            else -> mutableListOf()
            }
            for (targetDir in targetDirs) {
                Files.createDirectories(targetDir)
                val target = targetDir.resolve(fileName)
                backupOrCreate(target, context)
                if (!copyWithHelperFallback(source, target, true)) {
                    return targets
                }
                targets.add(target)
            }
        } catch (ignored: IOException) {
        }
        return targets
    }

    private fun applyRequiredTargets(
        source: Path?,
        fileName: kotlin.String?,
        targets: MutableList<AppliedTarget>?,
        context: UpdateContext?
    ) {
        if (source == null || fileName == null || fileName.isBlank() || targets == null || targets.isEmpty()) {
            return
        }
        for (applied in targets) {
            if (applied == null || applied.path == null) {
                continue
            }
            val target: Path = if (applied.directory) applied.path.resolve(fileName) else applied.path
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent())
                }
                backupOrCreate(target, context)
                if (!copyWithHelperFallback(source, target, true)) {
                    continue
                }
            } catch (ignored: IOException) {
            }
        }
    }

    private fun backupOrCreate(target: Path?, context: UpdateContext?) {
        if (target == null || context == null) {
            return
        }
        if (Files.exists(target)) {
            backupPath(toGameRelativePath(target), target, context)
        } else {
            context.meta!!.getCreatedPaths().add(toGameRelativePath(target))
        }
    }

    private fun updateConfigState(
        entry: DlcRemoteEntry?,
        source: Path?,
        applied: MutableList<Path?>?,
        context: UpdateContext?
    ) {
        if (entry == null || source == null || applied == null) {
            return
        }
        val type = if (entry.getType() == null) DlcType.UNKNOWN else entry.getType()
        val key: kotlin.String? = DlcUpdateService.buildConfigKey(entry.getDlcRoot(), source, type)
        if (key == null || key.isBlank()) {
            return
        }
        var state = DlcConfig.getState(key)
        if (state == null) {
            state = DlcState()
        }
        state.enabled = true
        state.appliedTargets = applied.stream()
            .map<kotlin.String?> { obj: Path? -> DlcUpdateService.toGameRelativePath(obj) }
            .toList()
        DlcConfig.setState(key, state)
        DlcConfig.save()
    }

    private fun buildConfigKey(dlcRoot: Path?, source: Path?, type: DlcType?): kotlin.String? {
        if (dlcRoot == null || source == null || type == null) {
            return null
        }
        val rootId: kotlin.String = toGameRelativePath(dlcRoot).replace('\\', '/')
        val relative = dlcRoot.relativize(source).toString().replace('\\', '/')
        return type.name.lowercase() + ":" + rootId + "|" + relative
    }

    private fun resolveTarget(targetDir: Path?, fileName: kotlin.String?): Path? {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null
        }
        if (Files.isDirectory(targetDir) || targetDir.toString().endsWith("/") || targetDir.toString().endsWith("\\")) {
            return targetDir.resolve(fileName).normalize()
        }
        return targetDir.normalize()
    }

    private fun sanitizeFileName(name: kotlin.String?): kotlin.String? {
        if (name == null) {
            return null
        }
        val cleaned = DlcDownloadUtil.stripQuery(name)
        if (cleaned == null) {
            return null
        }
        return cleaned.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }

    private fun applyImplicitRemoteAndRequired(
        context: UpdateContext,
        addedRequiredFiles: MutableList<Path?>,
        hasRemoteAdd: Boolean,
        hasRequiredAdd: Boolean
    ) {
        if (context.stagingRoot == null || context.dlcRoot == null) {
            return
        }
        val remoteDir: Path = context.stagingRoot.resolve("remote")
        if (!hasRemoteAdd && Files.isDirectory(remoteDir)) {
            val target: Path = context.dlcRoot.resolve("remote")
            if (Files.exists(target)) {
                backupPath(toGameRelativePath(target), target, context)
            } else {
                context.meta!!.getCreatedPaths().add(toGameRelativePath(target))
            }
            copyDirectory(remoteDir, target, true)
            context.addedRemote = true
        }
        val requiredDir: Path = context.stagingRoot.resolve("required")
        if (!hasRequiredAdd && Files.isDirectory(requiredDir)) {
            val target: Path = context.dlcRoot.resolve("required")
            if (Files.exists(target)) {
                backupPath(toGameRelativePath(target), target, context)
            } else {
                context.meta!!.getCreatedPaths().add(toGameRelativePath(target))
            }
            copyDirectory(requiredDir, target, true)
            addedRequiredFiles.add(target)
            context.addedRequired = true
        }
    }

    private fun handleSpecialDelete(target: Path?, rawPath: kotlin.String?, context: UpdateContext) {
        if (context.dlcRoot == null || target == null) {
            return
        }
        val normalized = target.normalize()
        val dlcRoot: Path = context.dlcRoot.normalize()
        if (!normalized.startsWith(dlcRoot)) {
            return
        }
        if (isNoticePath(normalized, dlcRoot)) {
            return
        }
        if (isVersionPath(normalized, dlcRoot)) {
            return
        }
        if (isUnderDir(normalized, dlcRoot.resolve("required"))) {
            uninstallRequiredConfig(normalized, dlcRoot, context)
            return
        }
        if (isUnderDir(normalized, dlcRoot.resolve("remote"))) {
            uninstallRemoteConfig(normalized, dlcRoot, context)
            return
        }
        if (isUnderDir(normalized, dlcRoot.resolve("preset"))) {
            if (Files.isDirectory(normalized)) {
                uninstallPresetConfigsInDir(normalized, dlcRoot)
            } else if (normalized.getFileName() != null && DcFormatUtil.isDcName(normalized.getFileName().toString())) {
                uninstallPresetConfig(normalized, dlcRoot)
            }
            return
        }
        if (isUnderDir(normalized, dlcRoot.resolve("info"))) {
            return
        }
        uninstallDlcPath(normalized, dlcRoot, context)
    }

    private fun uninstallRequiredConfig(configPath: Path?, dlcRoot: Path?, context: UpdateContext?) {
        if (configPath == null || dlcRoot == null || !Files.exists(configPath)) {
            return
        }
        val entry = DlcRequiredLoader.loadSingle(configPath, dlcRoot)
        if (entry == null) {
            return
        }
        val requiredFileName: kotlin.String? = resolveRequiredFileName(entry, dlcRoot)
        deleteRequiredDownload(entry, dlcRoot, requiredFileName, context)
        for (target in entry.resolveAppliedTargets()) {
            if (target == null) {
                continue
            }
            if (Files.isDirectory(target)) {
                if (requiredFileName == null || requiredFileName.isBlank()) {
                    continue
                }
                val file = target.resolve(requiredFileName)
                if (Files.exists(file)) {
                    backupAndDelete(file, context)
                }
                continue
            }
            backupAndDelete(target, context)
        }
    }

    private fun uninstallRemoteConfig(configPath: Path?, dlcRoot: Path?, context: UpdateContext?) {
        if (configPath == null || dlcRoot == null || !Files.exists(configPath)) {
            return
        }
        val entry = DlcRemoteLoader.loadSingle(configPath, dlcRoot)
        if (entry == null) {
            return
        }
        val target: Path? = entry.getTargetPath()
        if (target == null) {
            return
        }
        if (context != null) {
            context.addedRemote = true
        }
        uninstallDlcPath(target, dlcRoot, context)
    }

    private fun uninstallPresetConfig(configPath: Path?, dlcRoot: Path?) {
        if (configPath == null || dlcRoot == null || !Files.exists(configPath)) {
            return
        }
        if (configPath.getFileName() == null || !DcFormatUtil.isDcName(configPath.getFileName().toString())) {
            return
        }
        val presetId: kotlin.String? = resolvePresetId(configPath, dlcRoot)
        if (presetId == null || presetId.isBlank()) {
            return
        }
        if (!CrossPlatformRuntime.isDedicatedServer()) {
            DlcPresetRuntimeState.setPresetEnabled(presetId, false)
        }
        val statuses: MutableMap<kotlin.String, DlcPresetStatus?> = LinkedHashMap<kotlin.String, DlcPresetStatus?>(
            DlcPresetStateStore.loadPresetStatus()
        )
        if (statuses.remove(presetId) != null) {
            DlcPresetStateStore.savePresetStatus(statuses)
        }
    }

    private fun uninstallPresetConfigsInDir(dir: Path?, dlcRoot: Path?) {
        if (dir == null || dlcRoot == null || !Files.isDirectory(dir)) {
            return
        }
        try {
            Files.walk(dir).use { stream ->
                stream.filter { path: Path? ->
                    path!!.getFileName() != null && DcFormatUtil.isDcName(
                        path.getFileName().toString()
                    )
                }
                    .forEach { path: Path? -> uninstallPresetConfig(path, dlcRoot) }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun resolvePresetId(configPath: Path?, dlcRoot: Path?): kotlin.String? {
        if (configPath == null || dlcRoot == null) {
            return null
        }
        val presetRoot = dlcRoot.resolve("preset").normalize()
        val normalized = configPath.normalize()
        val fallback: kotlin.String = DcFormatUtil.stripConfigExtension(configPath.getFileName().toString())
        if (!normalized.startsWith(presetRoot)) {
            return fallback
        }
        try {
            val relative = presetRoot.relativize(normalized).toString().replace('\\', '/')
            return DcFormatUtil.stripConfigExtension(relative)
        } catch (ignored: Exception) {
            return fallback
        }
    }

    private fun uninstallDlcPath(target: Path?, dlcRoot: Path?, context: UpdateContext?) {
        if (target == null || dlcRoot == null) {
            return
        }
        val rootId: kotlin.String = toGameRelativePath(dlcRoot).replace('\\', '/')
        val relative = dlcRoot.relativize(target).toString().replace('\\', '/')
        val suffix = ":" + rootId + "|" + relative
        for (entry in DlcConfig.getAllStates().entries) {
            val key: kotlin.String? = entry.key
            if (key == null) {
                continue
            }
            if (!key.contains(suffix)) {
                continue
            }
            val state: DlcState? = entry.value
            if (state != null && state.appliedTargets != null) {
                for (raw in state.appliedTargets) {
                    val applied: Path = resolveStoredPath(raw)
                    backupAndDelete(applied, context)
                }
                state.enabled = false
                state.appliedTargets = mutableListOf<kotlin.String?>()
                DlcConfig.setState(key, state)
            }
        }
        DlcConfig.save()
    }

    private fun deleteRequiredDownload(
        entry: DlcRequiredEntry?,
        dlcRoot: Path?,
        requiredFileName: kotlin.String?,
        context: UpdateContext?
    ) {
        if (entry == null || dlcRoot == null) {
            return
        }
        val requiredDir = dlcRoot.resolve("required")
        if (!Files.isDirectory(requiredDir)) {
            return
        }
        if (requiredFileName == null || requiredFileName.isBlank()) {
            return
        }
        val downloaded = requiredDir.resolve(requiredFileName)
        if (Files.exists(downloaded)) {
            backupAndDelete(downloaded, context)
        }
    }

    private fun resolveRequiredFileName(entry: DlcRequiredEntry?, dlcRoot: Path?): kotlin.String? {
        if (entry == null || dlcRoot == null) {
            return null
        }
        val fileName = entry.getRequiredFileName()
        if (fileName != null && !fileName.isBlank()) {
            return fileName
        }
        val requiredDir = dlcRoot.resolve("required")
        if (!Files.isDirectory(requiredDir)) {
            return null
        }
        val base = entry.getBaseName()
        if (base == null || base.isBlank()) {
            return null
        }
        try {
            Files.newDirectoryStream(requiredDir, base + ".*").use { stream ->
                for (file in stream) {
                    if (file != null && !DcFormatUtil.isDcName(file.getFileName().toString())) {
                        return file.getFileName().toString()
                    }
                }
            }
        } catch (ignored: IOException) {
        }
        return null
    }

    private fun backupAndDelete(target: Path?, context: UpdateContext?) {
        if (target == null) {
            return
        }
        if (Files.exists(target) && context != null) {
            backupPath(toGameRelativePath(target), target, context)
        }
        deleteRecursive(target)
    }

    private fun rollbackUpdate(context: UpdateContext?) {
        if (context == null || context.backupRoot == null || context.meta == null) {
            return
        }
        for (created in context.meta.getCreatedPaths()) {
            val target: Path? = DlcUpdateService.resolveGamePath(DlcPaths.gameDir(), created)
            if (context.dlcRoot != null && target != null && Files.exists(target)) {
                val dlcRoot: Path = context.dlcRoot
                val requiredRoot = dlcRoot.resolve("required")
                val remoteRoot = dlcRoot.resolve("remote")
                if (isUnderDir(target, requiredRoot)) {
                    if (Files.isDirectory(target)) {
                        uninstallConfigsInDir(target, dlcRoot, true)
                    } else if (target.getFileName() != null && DcFormatUtil.isDcName(target.getFileName().toString())) {
                        uninstallRequiredConfig(target, dlcRoot, null)
                    }
                } else if (isUnderDir(target, remoteRoot)) {
                    if (Files.isDirectory(target)) {
                        uninstallConfigsInDir(target, dlcRoot, false)
                    } else if (target.getFileName() != null && DcFormatUtil.isDcName(target.getFileName().toString())) {
                        uninstallRemoteConfig(target, dlcRoot, null)
                    }
                }
            }
            deleteRecursive(target)
        }
        for (restore in context.meta.getRestorePaths()) {
            val backup: Path = context.backupRoot.resolve(restore)
            val target: Path? = DlcUpdateService.resolveGamePath(DlcPaths.gameDir(), restore)
            if (Files.exists(backup)) {
                deleteRecursive(target)
                copyRecursive(backup, target)
            }
        }
    }

    private fun uninstallConfigsInDir(dir: Path?, dlcRoot: Path?, required: Boolean) {
        if (dir == null || dlcRoot == null || !Files.isDirectory(dir)) {
            return
        }
        try {
            Files.walk(dir).use { stream ->
                stream.filter { path: Path? ->
                    path!!.getFileName() != null && DcFormatUtil.isDcName(
                        path.getFileName().toString()
                    )
                }
                    .forEach { path: Path? ->
                        if (required) {
                            uninstallRequiredConfig(path, dlcRoot, null)
                        } else {
                            uninstallRemoteConfig(path, dlcRoot, null)
                        }
                    }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun loadRollbackChain(
        dlcRoot: Path?,
        currentBaseVersion: kotlin.String?,
        currentDlcVersion: kotlin.String?
    ): MutableList<DlcUpdateMeta?> {
        if (dlcRoot == null || currentDlcVersion == null) {
            return mutableListOf<DlcUpdateMeta?>()
        }
        val chain: MutableList<DlcUpdateMeta?> = ArrayList<DlcUpdateMeta?>()
        var workingBase: kotlin.String = safeVersion(currentBaseVersion)
        var workingDlc: kotlin.String = safeVersion(currentDlcVersion)
        for (i in 0..31) {
            val meta = loadMetaForVersion(dlcRoot, workingBase, workingDlc)
            if (meta == null) {
                break
            }
            val sourceDlc: kotlin.String = sourceDlcVersion(meta)
            val targetDlc: kotlin.String = targetDlcVersion(meta)
            val sourceBase: kotlin.String = sourceBaseVersion(meta)
            val targetBase: kotlin.String = targetBaseVersion(meta)
            if (sourceDlc.isBlank() || targetDlc.isBlank()) {
                break
            }
            if (workingDlc != targetDlc || workingBase != targetBase) {
                break
            }
            chain.add(meta)
            if (sourceDlc == workingDlc && sourceBase == workingBase) {
                break
            }
            workingDlc = sourceDlc
            workingBase = sourceBase
        }
        return chain
    }

    private fun backupPath(rawRelative: kotlin.String?, target: Path?, context: UpdateContext?) {
        if (context == null || target == null || rawRelative == null || rawRelative.isBlank()) {
            return
        }
        val normalized: kotlin.String = normalizePath(rawRelative, target)
        if (!context.meta!!.getRestorePaths().contains(normalized)) {
            context.meta.getRestorePaths().add(normalized)
        }
        val backupTarget: Path = context.backupRoot!!.resolve(normalized)
        copyRecursive(target, backupTarget)
    }

    private fun normalizePath(raw: kotlin.String?, target: Path?): kotlin.String {
        if (raw == null || raw.isBlank()) {
            return if (target == null) "" else target.toString()
        }
        var normalized = raw.replace('\\', '/')
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1)
        }
        return normalized
    }

    private fun resolveGamePath(gameDir: Path, rawPath: kotlin.String?): Path? {
        if (rawPath == null || rawPath.isBlank()) {
            return null
        }
        var path = Path.of(rawPath)
        if (!path.isAbsolute()) {
            path = gameDir.resolve(path)
        }
        return path.normalize()
    }

    private fun resolveStagingPath(stagingRoot: Path?, rawPath: kotlin.String?): Path? {
        if (stagingRoot == null || rawPath == null || rawPath.isBlank()) {
            return null
        }
        val path = stagingRoot.resolve(rawPath).normalize()
        if (!path.startsWith(stagingRoot)) {
            return null
        }
        return path
    }

    private fun isUnderDir(path: Path?, root: Path?): Boolean {
        if (path == null || root == null) {
            return false
        }
        return path.normalize().startsWith(root.normalize())
    }

    private fun isNoticePath(path: Path?, dlcRoot: Path?): Boolean {
        if (path == null || dlcRoot == null) {
            return false
        }
        val infoDir = dlcRoot.resolve("info")
        val notice: Path? = DcFormatUtil.resolveConfigPath(infoDir, "notice")
        if (notice == null) {
            return false
        }
        return path.normalize() == notice.normalize()
    }

    private fun isVersionPath(path: Path?, dlcRoot: Path?): Boolean {
        if (path == null || dlcRoot == null) {
            return false
        }
        val infoDir = dlcRoot.resolve("info")
        val dc = infoDir.resolve("version" + DcFormatUtil.DC_EXTENSION).normalize()
        val normalized = path.normalize()
        return normalized == dc
    }

    private fun isNoticeConfig(fileName: kotlin.String?): Boolean {
        if (fileName == null || fileName.isBlank()) {
            return false
        }
        return "notice".equals(DcFormatUtil.stripConfigExtension(fileName), ignoreCase = true)
    }

    private fun isRequiredPath(target: Path?, dlcRoot: Path?): Boolean {
        if (target == null || dlcRoot == null) {
            return false
        }
        return isUnderDir(target, dlcRoot.resolve("required"))
    }

    private fun isRemotePath(target: Path?, dlcRoot: Path?): Boolean {
        if (target == null || dlcRoot == null) {
            return false
        }
        return isUnderDir(target, dlcRoot.resolve("remote"))
    }

    private fun refreshPresetRuntimeState() {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return
        }
        try {
            DlcPresetRuntimeState.reload()
        } catch (ignored: Throwable) {
        }
    }

    private fun preparePresetUpdateSession(ops: DlcUpdateManifest.UpdateOps?, dlcRoot: Path?): PresetUpdateSession {
        if (CrossPlatformRuntime.isDedicatedServer() || !DlcPathConfig.isPresetFuncOn() || ops == null || ops.getPreset()
                .isEmpty()
        ) {
            return PresetUpdateSession.Companion.empty()
        }
        val presetById: MutableMap<kotlin.String?, Path?> = loadPresetConfigPathById(dlcRoot)
        if (presetById.isEmpty()) {
            return PresetUpdateSession.Companion.empty()
        }
        val temporarilyDisabled: MutableSet<kotlin.String?> = LinkedHashSet<kotlin.String?>()
        for (rawId in ops.getPreset().keys) {
            val presetPath: Path? = findPresetConfigPath(presetById, rawId)
            if (presetPath == null) {
                continue
            }
            val presetId: kotlin.String? = resolvePresetId(presetPath, dlcRoot)
            if (presetId == null || presetId.isBlank()) {
                continue
            }
            if (DlcPresetRuntimeState.isPresetEnabled(presetId)) {
                temporarilyDisabled.add(presetId)
            }
        }
        return PresetUpdateSession(temporarilyDisabled)
    }

    @Throws(IOException::class)
    private fun disablePresetUpdatesForApply(session: PresetUpdateSession?) {
        if (session == null || session.temporarilyDisabledPresetIds!!.isEmpty() || CrossPlatformRuntime.isDedicatedServer()) {
            return
        }
        scanDlcItemsForLocalIndex()
        var changed = false
        for (presetId in session.temporarilyDisabledPresetIds) {
            if (presetId == null || presetId.isBlank()) {
                continue
            }
            if (DlcPresetRuntimeState.isPresetEnabled(presetId)) {
                DlcPresetRuntimeState.setPresetEnabled(presetId, false)
                changed = true
            }
        }
        if (changed) {
            applyLocalPresetState()
        }
    }

    @Throws(IOException::class)
    private fun finalizePresetUpdateSession(session: PresetUpdateSession?) {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return
        }
        refreshPresetRuntimeState()
        if (session == null || session.temporarilyDisabledPresetIds!!.isEmpty()) {
            return
        }
        scanDlcItemsForLocalIndex()
        DlcPresetRuntimeState.restoreEnabledPresets(session.temporarilyDisabledPresetIds)
        applyLocalPresetState()
    }

    private fun restorePresetUpdatesAfterRollback(session: PresetUpdateSession?) {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return
        }
        try {
            refreshPresetRuntimeState()
            if (session == null || session.temporarilyDisabledPresetIds!!.isEmpty()) {
                return
            }
            scanDlcItemsForLocalIndex()
            DlcPresetRuntimeState.restoreEnabledPresets(session.temporarilyDisabledPresetIds)
            applyLocalPresetState()
        } catch (ignored: Throwable) {
        }
    }

    @Throws(IOException::class)
    private fun applyLocalPresetState() {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return
        }
        val items: MutableList<DlcItem> = scanDlcItemsForLocalIndex()
        if (DlcPathConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.applyOverridesToRuntime()
        }
        var pending = DlcPathConfig.isPresetFuncOn() && DlcPresetRuntimeState.hasPendingFileConflictChanges()
        for (item in items) {
            if (item.getId() == null || item.getId()!!.isBlank()) {
                continue
            }
            val override: Boolean? = DlcRuntimeState.getOverride(item.getId())
            if (override != null) {
                item.setEnabled(override)
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                pending = true
            }
        }
        if (pending) {
            val result = DlcApplier.apply(items, ApplyMode.RESTART_ONLY)
            val failureMessage: kotlin.String = extractApplyFailureMessage(result)
            if (!failureMessage.isBlank()) {
                throw IOException(failureMessage)
            }
            for (item in items) {
                if (item.getId() == null || item.getId()!!.isBlank()) {
                    continue
                }
                DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled())
                DlcRuntimeState.clearOverride(item.getId())
            }
        }
        if (DlcPathConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.syncStatusFromRuntime()
        }
    }

    private fun scanDlcItemsForLocalIndex(): MutableList<DlcItem> {
        val grouped: MutableMap<DlcType?, MutableList<DlcItem?>?> = DlcScanner().scan()
        val items: MutableList<DlcItem> = ArrayList<DlcItem>()
        for (group in grouped.values) {
            if (group == null) {
                continue
            }
            for (item in group) {
                if (item == null || item.getId() == null || item.getId()!!.isBlank()) {
                    continue
                }
                items.add(item)
                DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled())
            }
        }
        DlcRuntimeState.updateItemIndex(items)
        return items
    }

    private fun extractApplyFailureMessage(result: ApplyResult?): kotlin.String {
        if (result == null || result.messages == null || result.messages.isEmpty()) {
            return ""
        }
        val failures: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        for (message in result.messages) {
            if (message != null && message.startsWith("Failed to ")) {
                failures.add(message)
            }
        }
        return failures.joinToString("\n")
    }

    private fun resetNoticeNeverShow(noticePath: Path?) {
        val holder: JsonObjectHolder? = JsonObjectHolder.Companion.load(noticePath)
        if (holder == null || holder.obj == null) {
            return
        }
        holder.obj.addProperty("neverShow", false)
        holder.save(noticePath)
    }

    @JvmStatic
    fun deleteRecursive(path: Path?) {
        if (path == null || !Files.exists(path)) {
            return
        }
        try {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path?>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    forceDelete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    forceDelete(file)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                    forceDelete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (ignored: IOException) {
        }
    }

    private fun forceDelete(path: Path?) {
        if (path == null) {
            return
        }
        if (deleteWithHelperFallback(path)) {
            return
        }
        try {
            Files.setAttribute(path, "dos:readonly", false)
        } catch (ignored: Exception) {
        }
        try {
            val file = path.toFile()
            file.setWritable(true)
            file.setReadable(true)
            file.setExecutable(true, false)
        } catch (ignored: Exception) {
        }
        if (deleteWithHelperFallback(path)) {
            return
        }
        try {
            path.toFile().deleteOnExit()
        } catch (ignored: Exception) {
        }
    }

    private fun deleteWithHelperFallback(path: Path?): Boolean {
        if (path == null) {
            return false
        }
        try {
            return Files.deleteIfExists(path) || !Files.exists(path)
        } catch (ignored: IOException) {
            val result = DlcFileOpsHelperClient.scheduleDelete(path)
            if (result.ok) {
                markHelperScheduled()
                return true
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message, path, "delete")
            return false
        }
    }

    private fun copyWithHelperFallback(source: Path?, target: Path?, replaceExisting: Boolean): Boolean {
        if (source == null || target == null) {
            return false
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent())
            }
            if (replaceExisting) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.copy(source, target)
            }
            return true
        } catch (ignored: IOException) {
            val result = DlcFileOpsHelperClient.scheduleCopy(source, target, replaceExisting)
            if (result.ok) {
                markHelperScheduled()
                return true
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message, target, "copy")
            return false
        }
    }

    private fun moveWithHelperFallback(source: Path?, target: Path?, replaceExisting: Boolean): Boolean {
        if (source == null || target == null) {
            return false
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent())
            }
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
            return true
        } catch (ignored: IOException) {
            val result = DlcFileOpsHelperClient.scheduleMove(source, target, replaceExisting)
            if (result.ok) {
                markHelperScheduled()
                return true
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message, target, "move")
            return false
        }
    }

    private fun copyRecursive(source: Path?, target: Path?) {
        if (source == null || target == null) {
            return
        }
        try {
            if (Files.isDirectory(source)) {
                Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                        if (dir == null) {
                            return FileVisitResult.CONTINUE
                        }
                        val relative = source.relativize(dir)
                        try {
                            Files.createDirectories(target.resolve(relative))
                        } catch (ignored: IOException) {
                        }
                        return FileVisitResult.CONTINUE
                    }

                    @Throws(IOException::class)
                    override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                        if (file == null) {
                            return FileVisitResult.CONTINUE
                        }
                        val relative = source.relativize(file)
                        try {
                            Files.createDirectories(target.resolve(relative).getParent())
                            copyWithHelperFallback(file, target.resolve(relative), true)
                        } catch (ignored: IOException) {
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }
                })
            } else {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent())
                }
                copyWithHelperFallback(source, target, true)
            }
        } catch (ignored: IOException) {
        }
    }

    private fun syncDirectory(source: Path?, target: Path?) {
        if (source == null || target == null) {
            return
        }
        if (!Files.exists(target)) {
            copyRecursive(source, target)
            return
        }
        deleteExtras(source, target)
        copyRecursive(source, target)
    }

    private fun deleteExtras(source: Path?, target: Path?) {
        if (source == null || target == null || !Files.exists(target)) {
            return
        }
        try {
            Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file == null) {
                        return FileVisitResult.CONTINUE
                    }
                    val relative = target.relativize(file)
                    if (!Files.exists(source.resolve(relative))) {
                        forceDelete(file)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                    if (dir == null) {
                        return FileVisitResult.CONTINUE
                    }
                    if (dir == target) {
                        return FileVisitResult.CONTINUE
                    }
                    val relative = target.relativize(dir)
                    if (!Files.exists(source.resolve(relative))) {
                        forceDelete(dir)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (ignored: IOException) {
        }
    }

    private fun copyDirectory(source: Path?, target: Path?, override: Boolean) {
        if (source == null || target == null) {
            return
        }
        try {
            Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (dir == null) {
                        return FileVisitResult.CONTINUE
                    }
                    val relative = source.relativize(dir)
                    try {
                        Files.createDirectories(target.resolve(relative))
                    } catch (ignored: IOException) {
                    }
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file == null) {
                        return FileVisitResult.CONTINUE
                    }
                    val relative = source.relativize(file)
                    val dest = target.resolve(relative)
                    if (!override && Files.exists(dest)) {
                        return FileVisitResult.CONTINUE
                    }
                    try {
                        Files.createDirectories(dest.getParent())
                        copyWithHelperFallback(file, dest, true)
                    } catch (ignored: IOException) {
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (ignored: IOException) {
        }
    }

    private fun readString(path: Path): kotlin.String? {
        try {
            return Files.readString(path, StandardCharsets.UTF_8)
        } catch (ignored: IOException) {
            return null
        }
    }

    private fun readJsonString(obj: JsonObject?, key: kotlin.String?): kotlin.String {
        if (obj == null || key == null || key.isBlank() || !obj.has(key) || obj.get(key).isJsonNull()) {
            return ""
        }
        try {
            val value = obj.get(key).getAsString()
            return if (value == null) "" else value
        } catch (ignored: Exception) {
            return ""
        }
    }

    private fun writeString(path: Path, content: kotlin.String?) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent())
            }
            Files.writeString(path, if (content == null) "" else content, StandardCharsets.UTF_8)
        } catch (ignored: IOException) {
            val result = DlcFileOpsHelperClient.scheduleWriteText(path, if (content == null) "" else content)
            if (result.ok) {
                markHelperScheduled()
                return
            }
            recordFileOpFailure(CURRENT_UPDATE_CONTEXT.get(), result.message, path, "write")
        }
    }

    private fun replaceOccurrences(
        content: kotlin.String?,
        find: kotlin.String?,
        replace: kotlin.String,
        which: DlcUpdateManifest.WhichSpec?
    ): kotlin.String? {
        if (content == null || find == null || find.isEmpty()) {
            return content
        }
        if (which == null || which.isAll()) {
            return content.replace(find, replace)
        }
        val positions: MutableList<Int?> = ArrayList<Int?>()
        var index = 0
        while ((content.indexOf(find, index).also { index = it }) != -1) {
            positions.add(index)
            index += find.length
        }
        if (positions.isEmpty()) {
            return content
        }
        if (which.isLast()) {
            val pos: Int = positions.get(positions.size - 1)!!
            return content.substring(0, pos) + replace + content.substring(pos + find.length)
        }
        val targetIndexes: MutableSet<Int?> = LinkedHashSet<Int?>()
        for (idx in which.getIndexes()) {
            if (idx == null) {
                continue
            }
            if (idx <= 0) {
                continue
            }
            val target = idx - 1
            if (target >= 0 && target < positions.size) {
                targetIndexes.add(target)
            }
        }
        if (targetIndexes.isEmpty()) {
            return content
        }
        val builder = StringBuilder()
        var last = 0
        for (i in positions.indices) {
            val pos: Int = positions.get(i)!!
            if (!targetIndexes.contains(i)) {
                continue
            }
            builder.append(content, last, pos)
            builder.append(replace)
            last = pos + find.length
        }
        builder.append(content.substring(last))
        return builder.toString()
    }

    private fun safeVersion(value: kotlin.String?): kotlin.String {
        return if (value == null || value.isBlank()) "1.0.0" else value.trim { it <= ' ' }
    }

    @JvmStatic
    fun isRollbackMarkerActive(
        rollbackFrom: kotlin.String?,
        currentBase: kotlin.String?,
        currentDlc: kotlin.String?
    ): Boolean {
        val marker = parseVersionState(rollbackFrom, currentBase)
        if (marker == null) {
            return false
        }
        if (marker.explicitBase) {
            return !stateEquals(marker.base, marker.dlc, currentBase, currentDlc)
        }
        return safeVersion(marker.dlc) != safeVersion(currentDlc)
    }

    private fun sourceBaseVersion(meta: DlcUpdateMeta?): kotlin.String {
        return safeVersion(if (meta == null) null else meta.getBaseOld())
    }

    private fun targetBaseVersion(meta: DlcUpdateMeta?): kotlin.String {
        if (meta == null) {
            return safeVersion(null)
        }
        val target: kotlin.String? = optionalVersion(meta.getBaseNew())
        return if (target == null) sourceBaseVersion(meta) else safeVersion(target)
    }

    private fun sourceDlcVersion(meta: DlcUpdateMeta?): kotlin.String {
        return safeVersion(if (meta == null) null else meta.getDlcOld())
    }

    private fun targetDlcVersion(meta: DlcUpdateMeta?): kotlin.String {
        if (meta == null) {
            return safeVersion(null)
        }
        val target: kotlin.String? = optionalVersion(meta.getDlcNew())
        return if (target == null) sourceDlcVersion(meta) else safeVersion(target)
    }

    private fun metaPairKey(baseVersion: kotlin.String?, dlcVersion: kotlin.String?): kotlin.String {
        return safeVersion(baseVersion) + "+" + safeVersion(dlcVersion)
    }

    private fun resolveMetaRootByPair(dlcRoot: Path?, baseVersion: kotlin.String?, dlcVersion: kotlin.String?): Path? {
        return DlcTmpPaths.resolve(dlcRoot, metaPairKey(baseVersion, dlcVersion))
    }

    private fun resolveMetaPath(metaRoot: Path?): Path? {
        if (metaRoot == null) {
            return null
        }
        return DcFormatUtil.resolveConfigPath(metaRoot, "meta")
    }

    private fun loadMetaFromRoot(metaRoot: Path?): DlcUpdateMeta? {
        val metaPath: Path? = resolveMetaPath(metaRoot)
        if (metaPath == null) {
            return null
        }
        return DlcUpdateMeta.load(metaPath)
    }

    private fun loadMetaForVersion(
        dlcRoot: Path?,
        baseVersion: kotlin.String?,
        dlcVersion: kotlin.String?
    ): DlcUpdateMeta? {
        if (dlcRoot == null || dlcVersion == null || dlcVersion.isBlank()) {
            return null
        }
        val expectedBase: kotlin.String = safeVersion(baseVersion)
        val expectedDlc: kotlin.String = safeVersion(dlcVersion)
        val pairRoot: Path? = resolveMetaRootByPair(dlcRoot, expectedBase, expectedDlc)
        var meta = loadMetaFromRoot(pairRoot)
        if (meta != null && expectedBase == targetBaseVersion(meta) && expectedDlc == targetDlcVersion(meta)) {
            return meta
        }

        val legacyRoot: Path? = DlcTmpPaths.resolve(dlcRoot, expectedDlc)
        if (legacyRoot != pairRoot) {
            val legacy = loadMetaFromRoot(legacyRoot)
            if (legacy != null) {
                if (expectedDlc == targetDlcVersion(legacy) && (baseVersion == null || baseVersion.isBlank() || expectedBase == targetBaseVersion(
                        legacy
                    ))
                ) {
                    return legacy
                }
                if (meta == null) {
                    meta = legacy
                }
            }
        }

        val tmpRoot: Path? = DlcTmpPaths.root(dlcRoot)
        if (tmpRoot == null || !Files.isDirectory(tmpRoot)) {
            return meta
        }
        var dlcMatch = meta
        var dlcMatchTs = parseTimestamp(meta)
        try {
            Files.list(tmpRoot).use { stream ->
                for (child in Iterable { stream.iterator() }) {
                    if (child == null || !Files.isDirectory(child)) {
                        continue
                    }
                    val candidate = loadMetaFromRoot(child)
                    if (candidate == null || expectedDlc != targetDlcVersion(candidate)) {
                        continue
                    }
                    val candidateTs = parseTimestamp(candidate)
                    if (expectedBase == targetBaseVersion(candidate)) {
                        if (dlcMatch == null || candidateTs >= dlcMatchTs) {
                            dlcMatch = candidate
                            dlcMatchTs = candidateTs
                        }
                        continue
                    }
                    if (dlcMatch == null || candidateTs > dlcMatchTs) {
                        dlcMatch = candidate
                        dlcMatchTs = candidateTs
                    }
                }
            }
        } catch (ignored: IOException) {
        }
        return dlcMatch
    }

    private fun parseTimestamp(meta: DlcUpdateMeta?): Long {
        if (meta == null || meta.getTimestamp() == null || meta.getTimestamp()!!.isBlank()) {
            return Long.MIN_VALUE
        }
        try {
            return meta.getTimestamp()!!.trim { it <= ' ' }.toLong()
        } catch (ignored: NumberFormatException) {
            return Long.MIN_VALUE
        }
    }

    private fun sourceBaseVersion(info: DlcUpdateManifest.VersionInfo?): kotlin.String {
        return if (info == null) safeVersion(null) else safeVersion(info.getBase())
    }

    private fun versionPlatform(info: DlcUpdateManifest.VersionInfo?): kotlin.String {
        if (info == null) {
            return PLATFORM_UNIVERSAL
        }
        val raw = info.getPlatform()
        if (raw == null || raw.isBlank()) {
            return PLATFORM_UNIVERSAL
        }
        val normalized = raw.trim { it <= ' ' }.lowercase()
        if (PLATFORM_CLIENT == normalized || PLATFORM_SERVER == normalized || PLATFORM_UNIVERSAL == normalized) {
            return normalized
        }
        return PLATFORM_UNIVERSAL
    }

    private fun isPlatformCompatible(info: DlcUpdateManifest.VersionInfo?): Boolean {
        return isPlatformCompatible(versionPlatform(info))
    }

    private fun isPackageCompatible(pkg: DlcUpdatePackage?): Boolean {
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return false
        }
        return isPlatformCompatible(pkg.getManifest().getVersion())
    }

    private fun isPlatformCompatible(platform: kotlin.String?): Boolean {
        val normalized: kotlin.String =
            if (platform == null || platform.isBlank()) PLATFORM_UNIVERSAL else platform.trim { it <= ' ' }.lowercase()
        if (PLATFORM_UNIVERSAL == normalized) {
            return true
        }
        val dedicated = CrossPlatformRuntime.isDedicatedServer()
        if (dedicated) {
            return PLATFORM_SERVER == normalized
        }
        return PLATFORM_CLIENT == normalized
    }

    private fun targetBaseVersion(info: DlcUpdateManifest.VersionInfo?): kotlin.String {
        if (info == null) {
            return safeVersion(null)
        }
        val target: kotlin.String? = optionalVersion(info.getBaseNew())
        return if (target == null) sourceBaseVersion(info) else target
    }

    private fun sourceDlcVersion(info: DlcUpdateManifest.VersionInfo?): kotlin.String {
        return if (info == null) safeVersion(null) else safeVersion(info.getDlcOld())
    }

    private fun targetDlcVersion(info: DlcUpdateManifest.VersionInfo?): kotlin.String {
        if (info == null) {
            return safeVersion(null)
        }
        val target: kotlin.String? = optionalVersion(info.getDlcNew())
        return if (target == null) sourceDlcVersion(info) else target
    }

    private fun hasVersionTransition(info: DlcUpdateManifest.VersionInfo?): Boolean {
        if (info == null) {
            return false
        }
        return sourceBaseVersion(info) != targetBaseVersion(info) || sourceDlcVersion(info) != targetDlcVersion(info)
    }

    private fun stateKey(base: kotlin.String?, dlc: kotlin.String?): kotlin.String {
        return safeVersion(base) + "|" + safeVersion(dlc)
    }

    private fun optionalVersion(value: kotlin.String?): kotlin.String? {
        if (value == null) {
            return null
        }
        val trimmed = value.trim { it <= ' ' }
        return if (trimmed.isBlank()) null else trimmed
    }

    private fun formatCompactTransition(
        oldBase: kotlin.String?,
        oldDlc: kotlin.String?,
        newBase: kotlin.String?,
        newDlc: kotlin.String?
    ): kotlin.String {
        val sourceBase: kotlin.String = safeVersion(oldBase)
        val sourceDlc: kotlin.String = safeVersion(oldDlc)
        val targetBase: kotlin.String = safeVersion(newBase)
        val targetDlc: kotlin.String = safeVersion(newDlc)
        val baseChanged = sourceBase != targetBase
        val dlcChanged = sourceDlc != targetDlc
        if (baseChanged && dlcChanged) {
            return sourceBase + "+" + sourceDlc + " -> " + targetBase + "+" + targetDlc
        }
        if (baseChanged) {
            return sourceBase + " -> " + targetBase
        }
        if (dlcChanged) {
            return sourceDlc + " -> " + targetDlc
        }
        return sourceBase + "+" + sourceDlc + " -> " + targetBase + "+" + targetDlc
    }

    private fun parseVersionState(raw: kotlin.String?, defaultBase: kotlin.String?): VersionState? {
        if (raw == null || raw.isBlank()) {
            return null
        }
        val normalized = raw.trim { it <= ' ' }
        val plus = normalized.indexOf('+')
        if (plus >= 0) {
            val base = normalized.substring(0, plus).trim { it <= ' ' }
            val dlc = normalized.substring(plus + 1).trim { it <= ' ' }
            return VersionState(safeVersion(base), safeVersion(dlc), true)
        }
        return VersionState(safeVersion(defaultBase), safeVersion(normalized), false)
    }

    private fun rollbackMarkerMatchesSource(
        rollbackFrom: kotlin.String?,
        sourceBase: kotlin.String?,
        sourceDlc: kotlin.String?
    ): Boolean {
        val marker = parseVersionState(rollbackFrom, sourceBase)
        if (marker == null) {
            return false
        }
        if (marker.explicitBase) {
            return stateEquals(marker.base, marker.dlc, sourceBase, sourceDlc)
        }
        return safeVersion(marker.dlc) == safeVersion(sourceDlc)
    }

    private fun stateMatchesTarget(base: kotlin.String?, dlc: kotlin.String?, target: VersionState?): Boolean {
        if (target == null) {
            return false
        }
        if (safeVersion(dlc) != target.dlc) {
            return false
        }
        if (!target.explicitBase) {
            return true
        }
        return safeVersion(base) == target.base
    }

    private fun stateEquals(
        leftBase: kotlin.String?,
        leftDlc: kotlin.String?,
        rightBase: kotlin.String?,
        rightDlc: kotlin.String?
    ): Boolean {
        return safeVersion(leftBase) == safeVersion(rightBase)
                && safeVersion(leftDlc) == safeVersion(rightDlc)
    }

    private fun compareVersion(a: kotlin.String?, b: kotlin.String?): Int {
        val left: ParsedVersion = ParsedVersion.Companion.parse(a)
        val right: ParsedVersion = ParsedVersion.Companion.parse(b)
        val max = max(left.numericParts.size, right.numericParts.size)
        for (i in 0..<max) {
            val l = if (i < left.numericParts.size) left.numericParts[i] else 0
            val r = if (i < right.numericParts.size) right.numericParts[i] else 0
            if (l != r) {
                return Integer.compare(l, r)
            }
        }
        if (left.knownQualifier && right.knownQualifier) {
            if (left.qualifierRank != right.qualifierRank) {
                return Integer.compare(left.qualifierRank, right.qualifierRank)
            }
            if (left.qualifierNumber != right.qualifierNumber) {
                return Integer.compare(left.qualifierNumber, right.qualifierNumber)
            }
            val qualifierTextCompare = left.qualifierText.compareTo(right.qualifierText, ignoreCase = true)
            if (qualifierTextCompare != 0) {
                return qualifierTextCompare
            }
            return left.normalized.compareTo(right.normalized, ignoreCase = true)
        }
        if (left.knownQualifier != right.knownQualifier) {
            return if (left.knownQualifier) 1 else -1
        }
        return left.normalized.compareTo(right.normalized, ignoreCase = true)
    }

    @JvmStatic
    fun compareVersions(a: kotlin.String?, b: kotlin.String?): Int {
        return compareVersion(a, b)
    }

    private fun parseVersionPart(part: kotlin.String?): Int {
        if (part == null) {
            return 0
        }
        var normalized = part.trim { it <= ' ' }
        val idx = normalized.indexOf('-')
        if (idx >= 0) {
            normalized = normalized.substring(0, idx)
        }
        try {
            return normalized.toInt()
        } catch (ignored: NumberFormatException) {
            return 0
        }
    }

    private val dlcRoot: Path?
        get() = if (DlcPathConfig.getDlcRoots()
                .isEmpty()
        ) null else DlcPathConfig.getDlcRoots().get(0)

    private fun toGameRelativePath(path: Path?): kotlin.String {
        if (path == null) {
            return ""
        }
        val gameDir: Path = DlcPaths.gameDir().toAbsolutePath().normalize()
        val normalized = path.toAbsolutePath().normalize()
        try {
            val gameReal = gameDir.toRealPath()
            val pathReal = normalized.toRealPath()
            if (pathReal.startsWith(gameReal)) {
                return gameReal.relativize(pathReal).toString().replace('\\', '/')
            }
        } catch (ignored: Exception) {
        }
        try {
            if (normalized.startsWith(gameDir)) {
                return gameDir.relativize(normalized).toString().replace('\\', '/')
            }
        } catch (ignored: Exception) {
        }
        return normalized.toString().replace('\\', '/')
    }

    private fun resolveStoredPath(rawPath: kotlin.String?): Path {
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of("")
        }
        val path = Path.of(rawPath)
        if (path.isAbsolute()) {
            return path.normalize()
        }
        return DlcPaths.gameDir().resolve(path).normalize()
    }

    private fun recordFileOpFailure(context: UpdateContext?, operation: kotlin.String?, path: Path?) {
        if (context == null || path == null) {
            return
        }
        val relative: kotlin.String = toGameRelativePath(path)
        val label = if (relative == null || relative.isBlank()) path.toString() else relative
        recordFileOpFailure(context, "Failed to " + operation + ": " + label, path, operation)
    }

    private fun recordFileOpFailure(
        context: UpdateContext?,
        message: kotlin.String?,
        path: Path?,
        operation: kotlin.String?
    ) {
        if (context == null) {
            return
        }
        var finalMessage = message
        if (finalMessage == null || finalMessage.isBlank()) {
            val relative: kotlin.String = if (path == null) "" else toGameRelativePath(path)
            val label = if (relative == null || relative.isBlank())
                (if (path == null) "" else path.toString())
            else
                relative
            finalMessage = "Failed to " + operation + ": " + label
        }
        if (!context.failureMessages.contains(finalMessage)) {
            context.failureMessages.add(finalMessage)
        }
    }

    private fun markHelperScheduled() {
        val context: UpdateContext? = CURRENT_UPDATE_CONTEXT.get()
        if (context != null) {
            context.helperScheduled = true
        }
    }

    private fun latestFailureMessage(fallback: kotlin.String?): kotlin.String? {
        val context: UpdateContext? = CURRENT_UPDATE_CONTEXT.get()
        if (context == null || context.failureMessages.isEmpty()) {
            return fallback
        }
        return context.failureMessages.get(context.failureMessages.size - 1)
    }

    private fun serializeJsonForPath(path: Path?, obj: JsonObject?): kotlin.String {
        if (obj == null) {
            return ""
        }
        var source: JsonObject? = obj
        if (layoutForPath(path) == DcConfigIO.Layout.DLC_STRUCTURED && isInfoPathForWrite(path)) {
            source = stripDownloadKeysForInfo(obj)
        }
        if (layoutForPath(path) == DcConfigIO.Layout.DLC_STRUCTURED && !isNoticeConfigPathForWrite(path)) {
            return DcFormatUtil.write(source)
        }
        return DcFormatUtil.writeGeneric(source)
    }

    private fun isInfoPathForWrite(path: Path?): Boolean {
        if (path == null) {
            return false
        }
        val normalized = path.toString().replace('\\', '/').lowercase()
        return normalized.contains("/info/")
    }

    private fun isNoticeConfigPathForWrite(path: Path?): Boolean {
        if (path == null || path.getFileName() == null) {
            return false
        }
        val base: kotlin.String = DcFormatUtil.stripConfigExtension(path.getFileName().toString())
        return "notice".equals(base, ignoreCase = true)
    }

    private fun stripDownloadKeysForInfo(input: JsonObject?): JsonObject {
        val out = JsonObject()
        if (input == null) {
            return out
        }
        for (entry in input.entrySet()) {
            val key = entry.key
            if (key == null) {
                continue
            }
            if ("modrinth_projectid".equals(key, ignoreCase = true)
                || "modrinth_versionid".equals(key, ignoreCase = true)
                || "curseforge_projectid".equals(key, ignoreCase = true)
                || "curseforge_fileid".equals(key, ignoreCase = true)
                || "directUrl".equals(key, ignoreCase = true)
                || "priority".equals(key, ignoreCase = true)
            ) {
                continue
            }
            out.add(key, entry.value)
        }
        return out
    }

    private class PathNode(
        val base: kotlin.String,
        val dlc: kotlin.String,
        val path: MutableList<DlcUpdatePackage?>
    )

    private enum class FieldUpdateMode {
        NORMAL,
        INFO_ALLOWED
    }

    @JvmRecord
    private data class DownloadedFile(val path: Path?, val fileName: kotlin.String?)

    @JvmRecord
    private data class AppliedTarget(val path: Path?, val directory: Boolean)

    @JvmRecord
    private data class PresetUpdateSession(val temporarilyDisabledPresetIds: MutableSet<kotlin.String?>?) {
        companion object {
            fun empty(): PresetUpdateSession {
                return PresetUpdateSession(mutableSetOf<kotlin.String?>())
            }
        }
    }

    private class VersionState(base: kotlin.String?, dlc: kotlin.String?, val explicitBase: Boolean) {
        val base: kotlin.String
        val dlc: kotlin.String

        init {
            this.base = safeVersion(base)
            this.dlc = safeVersion(dlc)
        }
    }

    private class UpdatePromptSpan {
        var fromBase: kotlin.String? = null
        var fromDlc: kotlin.String? = null
        var toBase: kotlin.String? = null
        var toDlc: kotlin.String? = null
    }

    private class ParsedVersion(
        val normalized: kotlin.String,
        val numericParts: IntArray,
        val knownQualifier: Boolean,
        val qualifierRank: Int,
        val qualifierNumber: Int,
        qualifierText: kotlin.String?
    ) {
        val qualifierText: kotlin.String

        init {
            this.qualifierText = if (qualifierText == null) "" else qualifierText
        }

        companion object {
            private const val QUALIFIER_ALPHA = 0
            private const val QUALIFIER_BETA = 1
            private const val QUALIFIER_RC = 2
            private const val QUALIFIER_RELEASE = 3
            private const val QUALIFIER_HOTFIX = 4
            private const val QUALIFIER_BUILD = 5

            fun parse(value: kotlin.String?): ParsedVersion {
                val normalized: kotlin.String = safeVersion(value)
                val split: Int = findSuffixStart(normalized)
                val core = if (split < 0) normalized else normalized.substring(0, split)
                val suffix = if (split < 0) "" else normalized.substring(split)
                val numericParts: IntArray = parseNumericParts(core)
                val qualifier: Qualifier = parseQualifier(suffix)
                return ParsedVersion(
                    normalized,
                    numericParts,
                    qualifier.known,
                    qualifier.rank,
                    qualifier.number,
                    qualifier.text
                )
            }

            private fun findSuffixStart(value: kotlin.String): Int {
                for (i in 0..<value.length) {
                    val ch = value.get(i)
                    if ((ch >= '0' && ch <= '9') || ch == '.') {
                        continue
                    }
                    return i
                }
                return -1
            }

            private fun parseNumericParts(core: kotlin.String?): IntArray {
                val parts: Array<kotlin.String?> =
                    safeVersion(core).split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val parsed = IntArray(parts.size)
                for (i in parts.indices) {
                    parsed[i] = parseVersionPart(parts[i])
                }
                return parsed
            }

            private fun parseQualifier(suffix: kotlin.String?): Qualifier {
                if (suffix == null || suffix.isBlank()) {
                    return Qualifier(true, QUALIFIER_RELEASE, 0, "release")
                }
                var normalized = suffix.trim { it <= ' ' }
                while (!normalized.isEmpty()) {
                    val ch = normalized.get(0)
                    if (ch == '-' || ch == '_' || ch == '.') {
                        normalized = normalized.substring(1)
                        continue
                    }
                    break
                }
                if (normalized.isBlank()) {
                    return Qualifier(true, QUALIFIER_RELEASE, 0, "release")
                }
                val lower = normalized.lowercase()
                for (name in arrayOf<kotlin.String>("alpha", "beta", "rc", "hotfix", "fix", "build")) {
                    if (!lower.startsWith(name)) {
                        continue
                    }
                    var rest = normalized.substring(name.length)
                    var number = 0
                    if (!rest.isBlank()) {
                        rest = rest.trim { it <= ' ' }
                        while (!rest.isEmpty()) {
                            val ch = rest.get(0)
                            if (ch == '-' || ch == '_' || ch == '.') {
                                rest = rest.substring(1)
                                continue
                            }
                            break
                        }
                        if (!rest.isBlank()) {
                            val digits = StringBuilder()
                            var index = 0
                            while (index < rest.length && Character.isDigit(rest.get(index))) {
                                digits.append(rest.get(index))
                                index++
                            }
                            if (digits.length > 0) {
                                try {
                                    number = digits.toString().toInt()
                                } catch (ignored: NumberFormatException) {
                                    number = 0
                                }
                            }
                        }
                    }
                    val rank: Int = when (name) {
                        "alpha" -> QUALIFIER_ALPHA
                        "beta" -> QUALIFIER_BETA
                        "rc" -> QUALIFIER_RC
                        "hotfix", "fix" -> QUALIFIER_HOTFIX
                        "build" -> QUALIFIER_BUILD
                        else -> QUALIFIER_RELEASE
                    }
                    return Qualifier(true, rank, number, if ("fix" == name) "hotfix" else name)
                }
                return Qualifier(false, QUALIFIER_RELEASE, 0, normalized)
            }
        }
    }

    private class Qualifier(
        val known: Boolean,
        val rank: Int,
        val number: Int,
        text: kotlin.String?
    ) {
        val text: kotlin.String

        init {
            this.text = if (text == null) "" else text
        }
    }

    private class UpdateContext(
        val dlcRoot: Path?,
        val stagingRoot: Path?,
        val backupRoot: Path?,
        val meta: DlcUpdateMeta?
    ) {
        var addedRequired = false
        var addedRemote = false
        var helperScheduled = false
        val failureMessages: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
    }

    private class JsonObjectHolder(val obj: JsonObject?) {
        fun save(path: Path?) {
            if (path == null || obj == null) {
                return
            }
            DcConfigIO.write(path, obj, layoutForPath(path))
        }

        companion object {
            fun load(path: Path?): JsonObjectHolder? {
                if (path == null || !Files.exists(path)) {
                    return null
                }
                val obj = DcConfigIO.read(path, layoutForPath(path))
                if (obj == null) {
                    return null
                }
                return JsonObjectHolder(obj)
            }
        }
    }

    class UpdateCheckResult internal constructor(
        val current: DlcUpdateVersion?,
        updates: MutableList<DlcUpdatePackage?>?
    ) {
        val updates: MutableList<DlcUpdatePackage?>?

        init {
            this.updates = if (updates == null) mutableListOf<DlcUpdatePackage?>() else updates
        }

        fun hasUpdates(): Boolean {
            return updates != null && !updates.isEmpty()
        }
    }

    class UpdateApplyResult private constructor(
        val isSuccess: Boolean,
        val isMissing: Boolean,
        val message: kotlin.String?,
        missingPaths: MutableList<kotlin.String?>?,
        val addedRequired: Boolean,
        val addedRemote: Boolean,
        val isHelperScheduled: Boolean,
        val isDlcManagerUpdated: Boolean
    ) {
        val missingPaths: MutableList<kotlin.String?>

        init {
            this.missingPaths = if (missingPaths == null) mutableListOf<kotlin.String?>() else missingPaths
        }

        fun hasRequiredAdd(): Boolean {
            return addedRequired
        }

        fun hasRemoteAdd(): Boolean {
            return addedRemote
        }

        companion object {
            fun success(
                message: kotlin.String?,
                addedRequired: Boolean,
                addedRemote: Boolean,
                helperScheduled: Boolean,
                dlcManagerUpdated: Boolean
            ): UpdateApplyResult {
                return UpdateApplyResult(
                    true,
                    false,
                    message,
                    mutableListOf<kotlin.String?>(),
                    addedRequired,
                    addedRemote,
                    helperScheduled,
                    dlcManagerUpdated
                )
            }

            fun failed(message: kotlin.String?): UpdateApplyResult {
                return UpdateApplyResult(
                    false,
                    false,
                    message,
                    mutableListOf<kotlin.String?>(),
                    false,
                    false,
                    false,
                    false
                )
            }

            fun missing(missingPaths: MutableList<kotlin.String?>?): UpdateApplyResult {
                return UpdateApplyResult(false, true, "missing", missingPaths, false, false, false, false)
            }

            fun empty(): UpdateApplyResult {
                return UpdateApplyResult(
                    true,
                    false,
                    "empty",
                    mutableListOf<kotlin.String?>(),
                    false,
                    false,
                    false,
                    false
                )
            }
        }
    }

    fun interface ApplyProgressListener {
        fun onProgress(status: kotlin.String?, progress: Float)
    }

    class UpdateRollbackResult private constructor(
        val isSuccess: Boolean,
        val version: kotlin.String?,
        val message: kotlin.String?
    ) {
        companion object {
            fun success(version: kotlin.String?): UpdateRollbackResult {
                return UpdateRollbackResult(true, version, "ok")
            }

            fun failed(message: kotlin.String?): UpdateRollbackResult {
                return UpdateRollbackResult(false, null, message)
            }
        }
    }

    enum class ImportValidationResult {
        VALID,
        INVALID_PACKAGE,
        DLC_ROOT_MISSING,
        PLATFORM_MISMATCH,
        VERSION_MISMATCH;

        val isValid: Boolean
            get() = this == VALID
    }
}
