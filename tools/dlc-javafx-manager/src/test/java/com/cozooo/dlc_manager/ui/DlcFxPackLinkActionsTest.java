package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DlcFxPackLinkActionsTest {
    @Test
    void extractsLastHttpUrlLineLikeLocalPackDialog() {
        String message = "Open external link\nhttps://first.example\nnote\nhttp://second.example";

        assertEquals("http://second.example", DlcFxPackLinkActions.extractLastUrlLine(message));
    }

    @Test
    void ignoresInlineUrlsAndBlankMessages() {
        assertNull(DlcFxPackLinkActions.extractLastUrlLine("text https://example.test"));
        assertNull(DlcFxPackLinkActions.extractLastUrlLine(""));
        assertNull(DlcFxPackLinkActions.extractLastUrlLine(null));
    }
}
