package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcResourceHistory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DlcRequiredInstaller {
    private DlcRequiredInstaller() { }
    @FunctionalInterface public interface BeforeWrite { void accept(Path path) throws IOException; }
    private record Member(String name, ZipEntry entry) { }

    public static boolean isInstalled(DlcRequiredEntry entry) throws IOException {
        String name = DlcRequiredService.resolveExistingFileName(entry);
        if (name == null) return false;
        Path source = entry.resolveRequiredPath(name);
        if (source == null || !Files.isRegularFile(source)) return false;
        if (!entry.shouldUnzip(name)) {
            for (Path target : entry.resolveInstallationTargets(name)) {
                safe(target);
                if (!Files.isRegularFile(target) || !DlcFileMatch.matches(source, target, entry.getMatchMode())) return false;
            }
            return true;
        }
        try (ZipFile zip = new ZipFile(source.toFile())) {
            List<Member> members = members(zip);
            for (Path base : entry.resolveInstallationTargets(name)) {
                safe(base);
                if (!Files.isDirectory(base)) return false;
                for (Member member : members) {
                    Path target = base.resolve(member.name).normalize();
                    safe(target);
                    if (member.entry.isDirectory()) {
                        if (!Files.isDirectory(target)) return false;
                    } else {
                        if (!Files.isRegularFile(target)) return false;
                        if (!"name".equals(entry.getMatchMode())) {
                            if (member.entry.getSize() >= 0 && Files.size(target) != member.entry.getSize()) return false;
                            try (var input = zip.getInputStream(member.entry)) {
                                if (!DlcFileMatch.hash(input).equals(DlcFileMatch.hash(target))) return false;
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    public static List<Path> paths(DlcRequiredEntry entry) throws IOException {
        String name = DlcRequiredService.resolveExistingFileName(entry);
        if (name == null) return List.of();
        if (!entry.shouldUnzip(name)) return entry.resolveInstallationTargets(name);
        Path source = entry.resolveRequiredPath(name);
        if (source == null || !Files.isRegularFile(source)) return List.of();
        List<Path> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            List<Member> members = members(zip);
            for (Path base : entry.resolveInstallationTargets(name)) {
                if (members.isEmpty()) result.add(base);
                for (Member member : leaves(members)) result.add(base.resolve(member.name).normalize());
            }
        }
        return result;
    }

    public static boolean install(DlcRequiredEntry entry, BeforeWrite beforeWrite) throws IOException {
        String name = DlcRequiredService.resolveExistingFileName(entry);
        if (name == null) throw new IOException("missing file name");
        entry.setResolvedFileName(name);
        Path source = entry.resolveRequiredPath(name);
        if (source == null || !Files.isRegularFile(source)) throw new IOException("missing required source");
        List<Path> targets = entry.resolveInstallationTargets(name);
        boolean changed = targets.isEmpty();
        if (entry.shouldUnzip(name) && !targets.isEmpty()) {
            List<Member> members;
            try (ZipFile zip = new ZipFile(source.toFile())) { members = members(zip); }
            Path staging = DlcZipUtil.createUniqueDir(entry.getDlcRoot().resolve("tmp/_staging"), "required");
            try {
                DlcZipUtil.extractZip(source, staging);
                List<Path> relative = leaves(members).stream().map(member -> Path.of(member.name)).toList();
                if (relative.isEmpty()) relative = List.of(Path.of(""));
                for (Path target : targets) {
                    safe(target);
                    if (Files.exists(target) && !Files.isDirectory(target)) throw new IOException("Required unzip target is not a directory: " + target);
                    for (Path path : relative) preflight(staging.resolve(path), target.resolve(path), source);
                }
                DlcResourceHistory.downloaded(entry.getDlcRoot(), entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), source);
                for (Path target : targets) for (Path path : relative) {
                    changed |= apply(entry, source, staging.resolve(path), target.resolve(path), beforeWrite);
                }
            } finally { DlcZipUtil.deleteRecursive(staging); }
        } else {
            for (Path target : targets) preflight(source, target, source);
            for (Path target : targets) changed |= apply(entry, source, source, target, beforeWrite);
        }
        if (targets.isEmpty()) DlcResourceHistory.downloaded(entry.getDlcRoot(), entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), source);
        DlcResourceHistory.complete(entry.getDlcRoot(), "required", entry.getHistoryKey());
        return changed;
    }

    private static boolean apply(DlcRequiredEntry entry, Path archive, Path source, Path target, BeforeWrite beforeWrite) throws IOException {
        if (Files.isDirectory(source) && Files.isDirectory(target)) return false;
        String mode = Files.isDirectory(source) ? "hash" : entry.getMatchMode();
        boolean replace = Files.isDirectory(source) ? !Files.isDirectory(target) : !Files.isRegularFile(target) || !DlcFileMatch.matches(source, target, mode);
        if (replace) beforeWrite.accept(target);
        DlcResourceHistory.prepare(entry.getDlcRoot(), "required", entry.getHistoryKey(), entry.getIdentifier(), entry.getConfigPath(), archive, source, target, replace, mode);
        if (replace) {
            Files.createDirectories(target.getParent());
            DlcResourceHistory.replace(source, target);
        }
        return replace;
    }

    private static void preflight(Path source, Path target, Path archive) throws IOException {
        safe(target);
        if (archive.toAbsolutePath().normalize().startsWith(target.toAbsolutePath().normalize())) throw new IOException("Required installation would overwrite its source");
        for (Path parent = target.getParent(); parent != null; parent = parent.getParent()) {
            if (Files.exists(parent) && !Files.isDirectory(parent)) throw new IOException("Required target parent is not a directory: " + parent);
        }
        if (!Files.exists(source)) throw new IOException("Missing extracted resource: " + source);
    }

    private static void safe(Path path) throws IOException {
        Path anchor = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        while (anchor != null && !target.startsWith(anchor)) anchor = anchor.getParent();
        for (Path part = target; part != null && !part.equals(anchor); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Cannot install required resource through a symbolic link: " + part);
        }
    }

    private static List<Member> members(ZipFile zip) throws IOException {
        List<Member> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName().replace('\\', '/');
            if (name.startsWith("__MACOSX/") || name.startsWith("._") || name.equals(".DS_Store") || name.endsWith("/.DS_Store")) continue;
            if (name.isBlank() || name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.indexOf('\u0000') >= 0
                || Arrays.asList(name.split("/")).contains("..")) throw new IOException("Invalid required archive path: " + name);
            Path path;
            try { path = Path.of(name).normalize(); }
            catch (InvalidPathException exception) { throw new IOException("Invalid required archive path: " + name, exception); }
            String normalized = path.toString().replace('\\', '/');
            if (normalized.isBlank()) continue;
            if (!seen.add(normalized)) throw new IOException("Duplicate required archive path: " + name);
            result.add(new Member(normalized, entry));
        }
        Set<String> files = new HashSet<>();
        for (Member member : result) if (!member.entry.isDirectory()) files.add(member.name);
        for (Member member : result) for (Path parent = Path.of(member.name).getParent(); parent != null; parent = parent.getParent()) {
            if (files.contains(parent.toString().replace('\\', '/'))) throw new IOException("Conflicting required archive paths: " + member.name);
        }
        return result;
    }

    private static List<Member> leaves(List<Member> members) {
        Set<String> parents = new HashSet<>();
        for (Member member : members) for (Path parent = Path.of(member.name).getParent(); parent != null; parent = parent.getParent()) parents.add(parent.toString().replace('\\', '/'));
        return members.stream().filter(member -> !member.entry.isDirectory() || !parents.contains(member.name)).toList();
    }
}
