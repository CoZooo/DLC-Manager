package com.cozooo.dlc_manager.ui.common;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import javafx.scene.Node;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxRichTextRendererTest {
    @Test
    void formattedSupportsMinecraftSectionLongHexColor() {
        TextFlow flow = DlcFxRichTextRenderer.formatted("A \u00A7x\u00A71\u00A72\u00A73\u00A74\u00A75\u00A76Hex");

        Text colored = textAt(flow, 1);
        assertEquals("Hex", colored.getText());
        assertTrue(colored.getStyle().contains("#123456"));
    }

    @Test
    void formattedSupportsMinecraftAmpersandLongHexColor() {
        TextFlow flow = DlcFxRichTextRenderer.formatted("A &x&F&F&A&A&0&0Gold");

        Text colored = textAt(flow, 1);
        assertEquals("Gold", colored.getText());
        assertTrue(colored.getStyle().contains("#FFAA00"));
    }

    @Test
    void formattedSupportsMinecraftSectionLegacyColors() {
        TextFlow flow = DlcFxRichTextRenderer.formatted("A \u00A74Red");

        Text colored = textAt(flow, 1);
        assertEquals("Red", colored.getText());
        assertTrue(colored.getStyle().contains("#AA0000"));
    }

    private static Text textAt(TextFlow flow, int index) {
        Node node = flow.getChildren().get(index);
        assertTrue(node instanceof Text);
        return (Text) node;
    }
}
