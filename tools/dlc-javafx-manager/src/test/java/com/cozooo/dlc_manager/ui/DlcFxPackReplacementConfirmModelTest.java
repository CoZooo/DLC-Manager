package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPackReplacementConfirmModelTest {
    @Test
    void skipsEntriesThatAreNotPendingInstallsOrNotInstalled() {
        List<DlcFxPackReplacementConfirmModel.Line> lines = DlcFxPackReplacementConfirmModel.lines(
            List.of(item("rail", "2.0.0", "Rail")),
            item -> false,
            item -> "1.0.0",
            item -> item.name
        );

        assertTrue(lines.isEmpty());

        lines = DlcFxPackReplacementConfirmModel.lines(
            List.of(item("rail", "2.0.0", "Rail")),
            item -> true,
            item -> null,
            item -> item.name
        );

        assertTrue(lines.isEmpty());
    }

    @Test
    void classifiesUpgradeDowngradeAndReplaceUsingLocalVersionRules() {
        List<DlcFxPackReplacementConfirmModel.Line> lines = DlcFxPackReplacementConfirmModel.lines(
            List.of(
                item("rail", "2.0.0", "Rail"),
                item("road", "0.9.0", "Road"),
                item("ship", "1.0.0", "Ship")
            ),
            item -> true,
            item -> Map.of("rail", "1.0.0", "road", "1.0.0", "ship", "1.0.0").get(item.id),
            item -> item.name
        );

        assertEquals(3, lines.size());
        assertEquals(DlcFxPackReplacementConfirmModel.Kind.UPGRADE, lines.get(0).kind());
        assertEquals(DlcFxPackReplacementConfirmModel.Kind.DOWNGRADE, lines.get(1).kind());
        assertEquals(DlcFxPackReplacementConfirmModel.Kind.REPLACE, lines.get(2).kind());
    }

    @Test
    void formatsLinesWithProvidedTranslator() {
        String message = DlcFxPackReplacementConfirmModel.message(
            List.of(
                new DlcFxPackReplacementConfirmModel.Line(DlcFxPackReplacementConfirmModel.Kind.UPGRADE, "Rail", "1.0.0", "2.0.0"),
                new DlcFxPackReplacementConfirmModel.Line(DlcFxPackReplacementConfirmModel.Kind.REPLACE, "Ship", "1.0.0", "1.0.0")
            ),
            (key, args) -> key + ":" + String.join(",", java.util.Arrays.stream(args).map(String::valueOf).toList())
        );

        assertEquals(
            "dlc_manager.pack.apply_confirm_upgrade:Rail,1.0.0,2.0.0\n"
                + "dlc_manager.pack.apply_confirm_replace:Ship,1.0.0",
            message
        );
    }

    private static Entry item(String id, String version, String name) {
        return new Entry(id, version, name);
    }

    private record Entry(String id, String version, String name) {
    }
}
