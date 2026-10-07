package com.cozooo.dlc_manager.ui.packs;

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

final class DlcFxPackImportFlowTest {
    @Test
    void refreshAndImportAreDispatchedThroughSharedFlow() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        DlcFxPackImportFlow flow = new DlcFxPackImportFlow(actions);

        flow.refresh();
        flow.importArchive();

        assertEquals(List.of("refresh", "importArchive"), actions.events);
    }

    @Test
    void disabledActionsReportDisabledWithoutOpeningImportUi() {
        FakeActions actions = new FakeActions();
        DlcFxPackImportFlow flow = new DlcFxPackImportFlow(actions);

        flow.refresh();
        flow.importArchive();
        flow.importUrl();

        assertEquals(List.of("disabled", "disabled", "disabled"), actions.events);
    }

    @Test
    void urlImportUsesLocalPromptThenStartsWithTrimmedUrl() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        actions.promptUrl = " https://example.invalid/pack.zip ";
        actions.promptFileName = "  pack.zip  ";
        DlcFxPackImportFlow flow = new DlcFxPackImportFlow(actions);

        flow.importUrl();

        assertEquals(List.of("promptUrl", "importUrl:https://example.invalid/pack.zip:pack.zip"), actions.events);
    }

    @Test
    void blankUrlReportsEmptyUrlAndDoesNotStartImport() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        actions.promptUrl = "   ";
        DlcFxPackImportFlow flow = new DlcFxPackImportFlow(actions);

        flow.importUrl();

        assertEquals(List.of("promptUrl", "emptyUrl"), actions.events);
    }

    private static final class FakeActions implements DlcFxPackImportFlow.Actions {
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
        public void importArchive() {
            events.add("importArchive");
        }

        @Override
        public void promptUrlImport(DlcFxPackImportFlow.UrlImportPrompt prompt) {
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
    }
}
