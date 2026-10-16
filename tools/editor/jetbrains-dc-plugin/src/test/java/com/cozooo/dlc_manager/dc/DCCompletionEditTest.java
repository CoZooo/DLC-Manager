package com.cozooo.dlc_manager.dc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DCCompletionEditTest {
    @Test void enterAndTabPreserveExistingValueAndTrailingComment() {
        String original = "moDe = \"hash\" # keep";
        for (String platformResult : new String[]{"momodeDe = \"hash\" # keep", "mode = \"hash\" # keep"}) {
            int end = DCCompletionContributor.replacementEnd(original, platformResult.length(), 4);
            StringBuilder text = new StringBuilder(platformResult);
            text.replace(0, end, "mode");
            assertEquals("mode = \"hash\" # keep", text.toString());
        }
    }
    @Test void protocolPositionsUseUtf16AcrossAllLineEndings() {
        String text = "a😀\r\nb\rc\nd";
        for (int offset : new int[]{0, 1, 3, 5, 6, 7, 8, 9, 10})
            assertEquals(offset, DCLanguageServer.offset(text, DCLanguageServer.position(text, offset)));
    }
}
