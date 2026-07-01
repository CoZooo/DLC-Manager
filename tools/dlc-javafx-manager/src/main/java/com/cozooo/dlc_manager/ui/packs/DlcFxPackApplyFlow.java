package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackApplyDecision;

public final class DlcFxPackApplyFlow {
    public interface Actions {
        boolean enabled();
        boolean busy();
        boolean hasPendingChanges();
        boolean hasOperationChanges();
        String replacementConfirmText();
        String runtimeConfirmText();
        void disabled();
        void commitOrderOnly();
        void confirmReplacement(String message, Runnable confirm);
        void confirmRuntime(String message, boolean allowRuntimeChanges, Runnable confirm);
        void startApply(boolean allowRuntimeChanges);
    }

    private final Actions actions;

    public DlcFxPackApplyFlow(Actions actions) {
        this.actions = actions;
    }

    public void start() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(
            actions.enabled(),
            actions.busy(),
            actions.hasPendingChanges(),
            actions.hasOperationChanges(),
            actions.replacementConfirmText(),
            null
        );
        dispatch(decision);
    }

    private void afterReplacementConfirmed() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(
            actions.enabled(),
            actions.busy(),
            actions.hasPendingChanges(),
            actions.hasOperationChanges(),
            null,
            actions.runtimeConfirmText()
        );
        dispatch(decision);
    }

    private void dispatch(DlcFxPackApplyDecision decision) {
        switch (decision.action()) {
            case DISABLED -> actions.disabled();
            case NOOP -> {
            }
            case COMMIT_ORDER_ONLY -> actions.commitOrderOnly();
            case CONFIRM_REPLACEMENT -> actions.confirmReplacement(decision.message(), this::afterReplacementConfirmed);
            case CONFIRM_RUNTIME -> actions.confirmRuntime(
                decision.message(),
                decision.allowRuntimeChanges(),
                () -> actions.startApply(decision.allowRuntimeChanges())
            );
            case START_APPLY -> actions.startApply(decision.allowRuntimeChanges());
        }
    }
}
