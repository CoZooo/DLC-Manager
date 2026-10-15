package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import com.cozooo.dlc_manager.core.DlcFileMatch
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcResourceHistory
import java.io.IOException
import java.nio.file.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object DlcRequiredInstaller {
    fun interface BeforeWrite { @Throws(IOException::class) fun accept(path: Path) }
    private data class Member(val name: String, val entry: ZipEntry)

    @JvmStatic @Throws(IOException::class)
    fun isInstalled(entry: DlcRequiredEntry): Boolean {
        val name = DlcRequiredService.resolveExistingFileName(entry) ?: return false
        val source = entry.resolveRequiredPath(name) ?: return false
        if (!Files.isRegularFile(source)) return false
        if (!entry.shouldUnzip(name)) {
            for (target in entry.resolveInstallationTargets(name)) {
                safe(target)
                if (!Files.isRegularFile(target) || !DlcFileMatch.matches(source, target, entry.getMatchMode())) return false
            }
            return true
        }
        ZipFile(source.toFile()).use { zip ->
            val members = members(zip)
            for (base in entry.resolveInstallationTargets(name)) {
                safe(base)
                if (!Files.isDirectory(base)) return false
                for (member in members) {
                    val target = base.resolve(member.name).normalize()
                    safe(target)
                    if (member.entry.isDirectory) {
                        if (!Files.isDirectory(target)) return false
                    } else {
                        if (!Files.isRegularFile(target)) return false
                        if (entry.getMatchMode() != "name") {
                            if (member.entry.size >= 0 && Files.size(target) != member.entry.size) return false
                            zip.getInputStream(member.entry).use { input ->
                                if (DlcFileMatch.hash(input) != DlcFileMatch.hash(target)) return false
                            }
                        }
                    }
                }
            }
            return true
        }
    }

    @JvmStatic @Throws(IOException::class)
    fun paths(entry: DlcRequiredEntry): List<Path> {
        val name = DlcRequiredService.resolveExistingFileName(entry) ?: return emptyList()
        if (!entry.shouldUnzip(name)) return entry.resolveInstallationTargets(name)
        val source = entry.resolveRequiredPath(name) ?: return emptyList()
        if (!Files.isRegularFile(source)) return emptyList()
        val result = mutableListOf<Path>()
        ZipFile(source.toFile()).use { zip ->
            val members = members(zip)
            for (base in entry.resolveInstallationTargets(name)) {
                if (members.isEmpty()) result.add(base)
                for (member in leaves(members)) result.add(base.resolve(member.name).normalize())
            }
        }
        return result
    }

    @JvmStatic @Throws(IOException::class)
    fun install(entry: DlcRequiredEntry, beforeWrite: BeforeWrite): Boolean {
        val name = DlcRequiredService.resolveExistingFileName(entry) ?: throw IOException("missing file name")
        entry.setResolvedFileName(name)
        val source = entry.resolveRequiredPath(name) ?: throw IOException("missing required source")
        if (!Files.isRegularFile(source)) throw IOException("missing required source")
        val targets = entry.resolveInstallationTargets(name)
        var changed = targets.isEmpty()
        if (entry.shouldUnzip(name) && targets.isNotEmpty()) {
            val members = ZipFile(source.toFile()).use { members(it) }
            val staging = DlcZipUtil.createUniqueDir(entry.getDlcRoot()!!.resolve("tmp/_staging"), "required")
            try {
                DlcZipUtil.extractZip(source, staging)
                val relative = leaves(members).map { Path.of(it.name) }.ifEmpty { listOf(Path.of("")) }
                for (target in targets) {
                    safe(target)
                    if (Files.exists(target) && !Files.isDirectory(target)) throw IOException("Required unzip target is not a directory: $target")
                    for (path in relative) preflight(staging.resolve(path), target.resolve(path), source)
                }
                DlcResourceHistory.downloaded(entry.getDlcRoot()!!, entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), source)
                for (target in targets) for (path in relative) {
                    changed = apply(entry, source, staging.resolve(path), target.resolve(path), beforeWrite) || changed
                }
            } finally { DlcZipUtil.deleteRecursive(staging) }
        } else {
            for (target in targets) preflight(source, target, source)
            for (target in targets) changed = apply(entry, source, source, target, beforeWrite) || changed
        }
        if (targets.isEmpty()) DlcResourceHistory.downloaded(entry.getDlcRoot()!!, entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), source)
        DlcResourceHistory.complete(entry.getDlcRoot()!!, "required", entry.getHistoryKey())
        return changed
    }

    private fun apply(entry: DlcRequiredEntry, archive: Path, source: Path, target: Path, beforeWrite: BeforeWrite): Boolean {
        if (Files.isDirectory(source) && Files.isDirectory(target)) return false
        val mode = if (Files.isDirectory(source)) "hash" else entry.getMatchMode()
        val replace = if (Files.isDirectory(source)) !Files.isDirectory(target)
            else !Files.isRegularFile(target) || !DlcFileMatch.matches(source, target, mode)
        if (replace) beforeWrite.accept(target)
        DlcResourceHistory.prepare(entry.getDlcRoot()!!, "required", entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), archive, source, target, replace, mode)
        if (replace) {
            Files.createDirectories(target.parent)
            DlcResourceHistory.replace(source, target)
        }
        return replace
    }

    private fun preflight(source: Path, target: Path, archive: Path) {
        safe(target)
        if (archive.toAbsolutePath().normalize().startsWith(target.toAbsolutePath().normalize())) throw IOException("Required installation would overwrite its source")
        var parent = target.parent
        while (parent != null) {
            if (Files.exists(parent) && !Files.isDirectory(parent)) throw IOException("Required target parent is not a directory: $parent")
            parent = parent.parent
        }
        if (!Files.exists(source)) throw IOException("Missing extracted resource: $source")
    }

    private fun safe(path: Path) {
        var anchor: Path? = DlcPaths.gameDir().toAbsolutePath().normalize()
        val target = path.toAbsolutePath().normalize()
        while (anchor != null && !target.startsWith(anchor)) anchor = anchor.parent
        var part: Path? = target
        while (part != null && part != anchor) {
            if (Files.isSymbolicLink(part)) throw IOException("Cannot install required resource through a symbolic link: $part")
            part = part.parent
        }
    }

    private fun members(zip: ZipFile): List<Member> {
        val result = mutableListOf<Member>()
        val seen = hashSetOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name.replace('\\', '/')
            if (name.startsWith("__MACOSX/") || name.startsWith("._") || name == ".DS_Store" || name.endsWith("/.DS_Store")) continue
            if (name.isBlank() || name.startsWith('/') || name.matches(Regex("^[A-Za-z]:.*")) || name.indexOf('\u0000') >= 0 || name.split('/').contains("..")) {
                throw IOException("Invalid required archive path: $name")
            }
            val normalized = try { Path.of(name).normalize().toString().replace('\\', '/') }
                catch (exception: InvalidPathException) { throw IOException("Invalid required archive path: $name", exception) }
            if (normalized.isBlank()) continue
            if (!seen.add(normalized)) throw IOException("Duplicate required archive path: $name")
            result.add(Member(normalized, entry))
        }
        val files = result.filter { !it.entry.isDirectory }.map { it.name }.toSet()
        for (member in result) {
            var parent = Path.of(member.name).parent
            while (parent != null) {
                if (files.contains(parent.toString().replace('\\', '/'))) throw IOException("Conflicting required archive paths: ${member.name}")
                parent = parent.parent
            }
        }
        return result
    }

    private fun leaves(members: List<Member>): List<Member> {
        val parents = hashSetOf<String>()
        for (member in members) {
            var parent = Path.of(member.name).parent
            while (parent != null) {
                parents.add(parent.toString().replace('\\', '/'))
                parent = parent.parent
            }
        }
        return members.filter { !it.entry.isDirectory || !parents.contains(it.name) }
    }
}
