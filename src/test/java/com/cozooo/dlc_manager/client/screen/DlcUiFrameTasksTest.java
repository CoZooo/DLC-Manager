package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiFrameTasksTest {
    @Test void deletingTheLastCharacterPresentsTheEmptyInputBeforeRestoringAllResults() {
        var applications = new DlcUiFrameTasks();
        var search = new DlcUiAsyncSearch<String>(Runnable::run, applications);
        var events = new ArrayList<String>();
        var input = new DlcUiTextInput(null) {
            @Override protected void clampScroll() {}
        };
        input.value = "a";
        input.setSelectionRange(1, 1);
        var entries = List.of(new DlcUiAsyncSearch.Entry<>("item", "item"));
        var binding = new DlcUiInputBinding(input::getValue, () -> true,
            query -> search.request(query, entries, matches -> {
                assertEquals(Set.of("item"), matches);
                events.add("results");
            }));
        input.addEventListener("input", event -> binding.run());
        assertTrue(input.deleteBackward());
        assertEquals("", input.getValue());
        assertTrue(events.isEmpty());
        events.add("painted");
        applications.runPending();
        assertEquals(List.of("painted"), events, "Restoring all results must not block the first empty-input frame");
        events.add("presented");
        applications.runPending();
        assertEquals(List.of("painted", "presented", "results"), events);
    }

    @Test void workEnqueuedWhileApplyingResultsWaitsForAnotherFrame() {
        var applications = new DlcUiFrameTasks();
        var events = new ArrayList<String>();
        applications.execute(() -> {
            events.add("first");
            applications.execute(() -> events.add("second"));
        });
        applications.runPending();
        assertTrue(events.isEmpty());
        applications.runPending();
        assertEquals(List.of("first"), events);
        applications.runPending();
        assertEquals(List.of("first", "second"), events);
    }

    @Test void cancelledAndSupersededResultsCannotApplyAfterPainting() {
        var applications = new DlcUiFrameTasks();
        var search = new DlcUiAsyncSearch<String>(Runnable::run, applications);
        var applied = new ArrayList<Set<String>>();
        var entries = List.of(new DlcUiAsyncSearch.Entry<>("item", "item"));
        search.request("item", entries, applied::add);
        search.request("missing", entries, applied::add);
        applications.runPending();
        assertTrue(applied.isEmpty());
        applications.runPending();
        assertEquals(List.of(Set.of()), applied);
        search.request("", entries, applied::add);
        search.cancel();
        applications.runPending();
        applications.runPending();
        assertEquals(List.of(Set.of()), applied);
        applications.execute(() -> fail("A closed screen must discard staged applications"));
        applications.runPending();
        applications.execute(() -> fail("A closed screen must discard pending applications"));
        applications.clear();
        applications.runPending();
    }
}
