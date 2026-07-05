package com.cozooo.dlc_manager.ui.packs;

import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class DlcFxPackPendingActionModel<T> {
    public enum Action {
        INSTALL,
        UNINSTALL,
        UPGRADE,
        DOWNGRADE,
        PENDING_INSTALL,
        PENDING_UNINSTALL,
        PENDING_UPGRADE,
        PENDING_DOWNGRADE
    }

    private final Function<T, String> identifierResolver;
    private final Function<T, String> versionResolver;
    private final Map<String, T> pendingInstalls = new LinkedHashMap<>();
    private final Set<String> pendingUninstalls = new LinkedHashSet<>();

    public DlcFxPackPendingActionModel(Function<T, String> identifierResolver,
                                       Function<T, String> versionResolver) {
        this.identifierResolver = identifierResolver;
        this.versionResolver = versionResolver;
    }

    public void toggle(T entry, T installedEntry) {
        toggleWithInstalledVersion(entry, version(installedEntry));
    }

    public void toggleWithInstalledVersion(T entry, String installedVersion) {
        String identifier = identifier(entry);
        if (identifier.isBlank()) {
            return;
        }
        if (pendingUninstalls.remove(identifier)) {
            pendingInstalls.remove(identifier);
        } else if (Objects.equals(pendingInstalls.get(identifier), entry)) {
            pendingInstalls.remove(identifier);
        } else if (sameVersion(entry, installedVersion)) {
            pendingUninstalls.add(identifier);
            pendingInstalls.remove(identifier);
        } else {
            pendingInstalls.put(identifier, entry);
            pendingUninstalls.remove(identifier);
        }
    }

    public Action action(T entry, T installedEntry) {
        return actionWithInstalledVersion(entry, version(installedEntry));
    }

    public Action actionWithInstalledVersion(T entry, String installedVersion) {
        String identifier = identifier(entry);
        int compare = compare(entry, installedVersion);
        boolean installed = installedVersion != null && !installedVersion.isBlank();
        if (Objects.equals(pendingInstalls.get(identifier), entry)) {
            if (!installed) {
                return Action.PENDING_INSTALL;
            }
            if (compare > 0) {
                return Action.PENDING_UPGRADE;
            }
            if (compare < 0) {
                return Action.PENDING_DOWNGRADE;
            }
            return Action.PENDING_INSTALL;
        }
        if (pendingUninstalls.contains(identifier)) {
            return Action.PENDING_UNINSTALL;
        }
        if (installed && compare == 0) {
            return Action.UNINSTALL;
        }
        if (installed) {
            return compare > 0 ? Action.UPGRADE : Action.DOWNGRADE;
        }
        return Action.INSTALL;
    }

    public void remove(String identifier) {
        pendingInstalls.remove(identifier);
        pendingUninstalls.remove(identifier);
    }

    public void clear() {
        pendingInstalls.clear();
        pendingUninstalls.clear();
    }

    public boolean hasOperationChanges() {
        return !pendingInstalls.isEmpty() || !pendingUninstalls.isEmpty();
    }

    public Map<String, T> pendingInstalls() {
        return pendingInstalls;
    }

    public Set<String> pendingUninstallIds() {
        return pendingUninstalls;
    }

    public static String translationKey(Action action) {
        return switch (action) {
            case INSTALL -> "dlc_manager.pack.install";
            case UNINSTALL -> "dlc_manager.pack.uninstall";
            case UPGRADE -> "dlc_manager.pack.upgrade";
            case DOWNGRADE -> "dlc_manager.pack.downgrade";
            case PENDING_INSTALL -> "dlc_manager.pack.pending_install";
            case PENDING_UNINSTALL -> "dlc_manager.pack.pending_uninstall";
            case PENDING_UPGRADE -> "dlc_manager.pack.pending_upgrade";
            case PENDING_DOWNGRADE -> "dlc_manager.pack.pending_downgrade";
        };
    }

    private boolean sameVersion(T entry, String installedVersion) {
        return installedVersion != null && !installedVersion.isBlank() && Objects.equals(version(entry), installedVersion);
    }

    private int compare(T entry, String installedVersion) {
        if (installedVersion == null || installedVersion.isBlank()) {
            return 0;
        }
        return DlcPackMetadata.compareVersionStrings(version(entry), installedVersion);
    }

    private String identifier(T entry) {
        return entry == null ? "" : safe(identifierResolver.apply(entry));
    }

    private String version(T entry) {
        return entry == null ? "" : safe(versionResolver.apply(entry));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
