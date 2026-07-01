package com.cozooo.dlc_manager.ui.packs;

import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxPackReplacementConfirmModel {
    private DlcFxPackReplacementConfirmModel() {
    }

    public enum Kind {
        UPGRADE,
        DOWNGRADE,
        REPLACE
    }

    public record Line(Kind kind, String displayName, String installedVersion, String targetVersion) {
    }

    public static <T> List<Line> lines(Iterable<T> entries,
                                       Predicate<T> pendingInstallPredicate,
                                       Function<T, String> installedVersionResolver,
                                       Function<T, String> displayNameResolver) {
        return lines(entries, pendingInstallPredicate, installedVersionResolver, DlcFxPackReplacementConfirmModel::reflectVersion, displayNameResolver);
    }

    public static <T> List<Line> lines(Iterable<T> entries,
                                       Predicate<T> pendingInstallPredicate,
                                       Function<T, String> installedVersionResolver,
                                       Function<T, String> targetVersionResolver,
                                       Function<T, String> displayNameResolver) {
        List<Line> out = new ArrayList<>();
        if (entries == null) {
            return out;
        }
        for (T entry : entries) {
            if (entry == null || pendingInstallPredicate == null || !pendingInstallPredicate.test(entry)) {
                continue;
            }
            String installedVersion = installedVersionResolver == null ? null : installedVersionResolver.apply(entry);
            if (installedVersion == null || installedVersion.isBlank()) {
                continue;
            }
            String targetVersion = targetVersionResolver == null ? "" : targetVersionResolver.apply(entry);
            int compare = DlcPackMetadata.compareVersionStrings(targetVersion, installedVersion);
            Kind kind = compare > 0 ? Kind.UPGRADE : compare < 0 ? Kind.DOWNGRADE : Kind.REPLACE;
            String displayName = displayNameResolver == null ? "" : displayNameResolver.apply(entry);
            out.add(new Line(kind, displayName, installedVersion, targetVersion));
        }
        return out;
    }

    public static String message(List<Line> lines, BiFunction<String, Object[], String> translator) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        List<String> rendered = new ArrayList<>();
        for (Line line : lines) {
            if (line == null) {
                continue;
            }
            rendered.add(renderLine(line, translator));
        }
        return rendered.isEmpty() ? null : String.join("\n", rendered);
    }

    private static String renderLine(Line line, BiFunction<String, Object[], String> translator) {
        String key = switch (line.kind()) {
            case UPGRADE -> "dlc_manager.pack.apply_confirm_upgrade";
            case DOWNGRADE -> "dlc_manager.pack.apply_confirm_downgrade";
            case REPLACE -> "dlc_manager.pack.apply_confirm_replace";
        };
        Object[] args = line.kind() == Kind.REPLACE
            ? new Object[] { line.displayName(), line.installedVersion() }
            : new Object[] { line.displayName(), line.installedVersion(), line.targetVersion() };
        return translator == null ? key : translator.apply(key, args);
    }

    private static String reflectVersion(Object entry) {
        try {
            var method = entry.getClass().getDeclaredMethod("version");
            method.setAccessible(true);
            Object value = method.invoke(entry);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }
}
