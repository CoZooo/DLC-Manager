package com.cozooo.dlc_manager.client.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcServerVersionUtilTest {
    @Test
    void parsesVersionPairFromText() {
        DlcServerVersionUtil.VersionPair pair = DlcServerVersionUtil.parse("server version 1.2.0+1.0.6 ready");

        assertEquals("1.2.0", pair.base);
        assertEquals("1.0.6", pair.dlc);
    }

    @Test
    void supportsNonStandardNumericLeadingVersionParts() {
        DlcServerVersionUtil.VersionPair pair = DlcServerVersionUtil.parse("26.1.x-beta+1.0.0_rc1");

        assertEquals("26.1.x-beta", pair.base);
        assertEquals("1.0.0_rc1", pair.dlc);
    }

    @Test
    void returnsNullForMissingOrInvalidText() {
        assertNull(DlcServerVersionUtil.parse(null));
        assertNull(DlcServerVersionUtil.parse(" "));
        assertNull(DlcServerVersionUtil.parse("no version here"));
        assertNull(DlcServerVersionUtil.parse("base+1.0.0"));
    }
}
