package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackPendingActionModel;

import java.util.Map;
import java.util.function.Function;

public final class DlcFxPackVersionSelectionModel<T> {
    private final Map<String, String> selectedVersionByIdentifier;
    private final DlcFxPackPendingActionModel<T> pendingModel;
    private final Function<T, String> identifierResolver;
    private final Function<T, String> versionKeyResolver;

    public DlcFxPackVersionSelectionModel(Map<String, String> selectedVersionByIdentifier,
                                          DlcFxPackPendingActionModel<T> pendingModel,
                                          Function<T, String> identifierResolver,
                                          Function<T, String> versionKeyResolver) {
        this.selectedVersionByIdentifier = selectedVersionByIdentifier;
        this.pendingModel = pendingModel;
        this.identifierResolver = identifierResolver;
        this.versionKeyResolver = versionKeyResolver;
    }

    public void select(T entry) {
        String identifier = safe(identifierResolver.apply(entry));
        if (identifier.isBlank()) {
            return;
        }
        selectedVersionByIdentifier.put(identifier, safe(versionKeyResolver.apply(entry)));
        pendingModel.remove(identifier);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
