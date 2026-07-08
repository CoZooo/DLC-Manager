package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxPackApplyFlowTest {
    @Test
    void startUsesLocalTwoStageApplyOrder() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        actions.pending = true;
        actions.operationChanges = true;
        actions.replacementText = "replace";
        actions.runtimeText = "runtime";
        DlcFxPackApplyFlow flow = new DlcFxPackApplyFlow(actions);

        flow.start();
        assertEquals(List.of("confirmReplacement:replace"), actions.events);

        actions.events.clear();
        actions.runReplacementConfirm();
        assertEquals(List.of("confirmRuntime:runtime:true"), actions.events);

        actions.events.clear();
        actions.runRuntimeConfirm();
        assertEquals(List.of("startApply:true"), actions.events);
    }

    @Test
    void orderOnlyCommitsWithoutApply() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        actions.pending = true;
        actions.operationChanges = false;
        DlcFxPackApplyFlow flow = new DlcFxPackApplyFlow(actions);

        flow.start();

        assertEquals(List.of("commitOrderOnly"), actions.events);
    }

    @Test
    void disabledReportsDisabledAndNoopDoesNothing() {
        FakeActions actions = new FakeActions();
        DlcFxPackApplyFlow flow = new DlcFxPackApplyFlow(actions);

        flow.start();
        assertEquals(List.of("disabled"), actions.events);

        actions.events.clear();
        actions.enabled = true;
        flow.start();
        assertEquals(List.of(), actions.events);
    }

    private static final class FakeActions implements DlcFxPackApplyFlow.Actions {
        private final List<String> events = new ArrayList<>();
        private boolean enabled;
        private boolean pending;
        private boolean operationChanges;
        private String replacementText = "";
        private String runtimeText = "";
        private Runnable replacementConfirm = () -> {};
        private Runnable runtimeConfirm = () -> {};

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean busy() {
            return false;
        }

        @Override
        public boolean hasPendingChanges() {
            return pending;
        }

        @Override
        public boolean hasOperationChanges() {
            return operationChanges;
        }

        @Override
        public String replacementConfirmText() {
            return replacementText;
        }

        @Override
        public String runtimeConfirmText() {
            return runtimeText;
        }

        @Override
        public void disabled() {
            events.add("disabled");
        }

        @Override
        public void commitOrderOnly() {
            events.add("commitOrderOnly");
        }

        @Override
        public void confirmReplacement(String message, Runnable confirm) {
            events.add("confirmReplacement:" + message);
            replacementConfirm = confirm;
        }

        @Override
        public void confirmRuntime(String message, boolean allowRuntimeChanges, Runnable confirm) {
            events.add("confirmRuntime:" + message + ":" + allowRuntimeChanges);
            runtimeConfirm = confirm;
        }

        @Override
        public void startApply(boolean allowRuntimeChanges) {
            events.add("startApply:" + allowRuntimeChanges);
        }

        private void runReplacementConfirm() {
            replacementConfirm.run();
        }

        private void runRuntimeConfirm() {
            runtimeConfirm.run();
        }
    }
}
