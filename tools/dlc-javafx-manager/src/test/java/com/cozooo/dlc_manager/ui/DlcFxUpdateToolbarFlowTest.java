package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxUpdateToolbarFlowTest {
    @Test
    void toolbarButtonsDispatchThroughSharedFlow() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        actions.promptUrl = " https://example.invalid/update.zip ";
        actions.promptFileName = "release.zip";
        DlcFxUpdateToolbarFlow flow = new DlcFxUpdateToolbarFlow(actions);

        flow.refresh();
        flow.importPackage();
        flow.importUrl();
        flow.extraAction();

        assertEquals(List.of("refresh", "importPackage", "promptUrl", "importUrl:https://example.invalid/update.zip:release.zip", "extraAction"), actions.events);
    }

    @Test
    void disabledButtonsDoNotOpenDialogs() {
        FakeActions actions = new FakeActions();
        DlcFxUpdateToolbarFlow flow = new DlcFxUpdateToolbarFlow(actions);

        flow.refresh();
        flow.importPackage();
        flow.importUrl();
        flow.extraAction();

        assertEquals(List.of("disabled", "disabled", "disabled", "disabled"), actions.events);
    }

    @Test
    void blankUrlStopsAtValidationAndDoesNotStartImport() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        actions.promptUrl = "  ";
        DlcFxUpdateToolbarFlow flow = new DlcFxUpdateToolbarFlow(actions);

        flow.importUrl();

        assertEquals(List.of("promptUrl", "emptyUrl"), actions.events);
    }

    private static final class FakeActions implements DlcFxUpdateToolbarFlow.Actions {
        private final List<String> events = new ArrayList<>();
        private boolean enabled;
        private String promptUrl = "";
        private String promptFileName = "";

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void disabled() {
            events.add("disabled");
        }

        @Override
        public void refresh() {
            events.add("refresh");
        }

        @Override
        public void importPackage() {
            events.add("importPackage");
        }

        @Override
        public void promptUrlImport(DlcFxUpdateToolbarFlow.UrlImportPrompt prompt) {
            events.add("promptUrl");
            prompt.submit(promptUrl, promptFileName);
        }

        @Override
        public void emptyUrl() {
            events.add("emptyUrl");
        }

        @Override
        public void importUrl(String url, String fileName) {
            events.add("importUrl:" + url + ":" + fileName);
        }

        @Override
        public void extraAction() {
            events.add("extraAction");
        }
    }
}
