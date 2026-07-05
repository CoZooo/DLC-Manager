package com.cozooo.dlc_manager.ui.packs;

public record DlcFxPackApplyDecision(Action action, String message, boolean allowRuntimeChanges) {
    public enum Action {
        NOOP,
        DISABLED,
        COMMIT_ORDER_ONLY,
        CONFIRM_REPLACEMENT,
        CONFIRM_RUNTIME,
        START_APPLY
    }

    public static DlcFxPackApplyDecision decide(boolean packEnabled,
                                                boolean busy,
                                                boolean hasPendingChanges,
                                                boolean hasOperationChanges,
                                                String replacementConfirmText,
                                                String runtimeConfirmText) {
        if (!packEnabled) {
            return new DlcFxPackApplyDecision(Action.DISABLED, "", false);
        }
        if (busy || !hasPendingChanges) {
            return new DlcFxPackApplyDecision(Action.NOOP, "", false);
        }
        if (!hasOperationChanges) {
            return new DlcFxPackApplyDecision(Action.COMMIT_ORDER_ONLY, "", false);
        }
        String replacement = normalize(replacementConfirmText);
        if (!replacement.isBlank()) {
            return new DlcFxPackApplyDecision(Action.CONFIRM_REPLACEMENT, replacement, false);
        }
        String runtime = normalize(runtimeConfirmText);
        if (!runtime.isBlank()) {
            return new DlcFxPackApplyDecision(Action.CONFIRM_RUNTIME, runtime, true);
        }
        return new DlcFxPackApplyDecision(Action.START_APPLY, "", false);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
