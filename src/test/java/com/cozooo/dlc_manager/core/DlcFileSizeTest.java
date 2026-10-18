package com.cozooo.dlc_manager.core;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcFileSizeTest {
    @Test void usesBinaryUnitsAtExactBoundariesAndPreservesUnknownSizes() {
        assertEquals(1024L, DlcFileSize.KIB);
        assertEquals(1048576L, DlcFileSize.MIB);
        assertEquals(1073741824L, DlcFileSize.GIB);
        assertEquals("--", DlcFileSize.format(-1));
        assertEquals("0 B", DlcFileSize.format(0));
        assertEquals("1.5 KiB", DlcFileSize.formatProgress(1536, -1));
        assertEquals("1.5 KiB/2.0 KiB", DlcFileSize.formatProgress(1536, 2048));
        assertEquals("1023 B", DlcFileSize.format(1023));
        assertEquals("1.0 KiB", DlcFileSize.format(1024));
        assertEquals("1.5 MiB", DlcFileSize.format(1572864));
        assertEquals("1.0 GiB", DlcFileSize.format(DlcFileSize.GIB));
        assertEquals("1.0 TiB", DlcFileSize.format(DlcFileSize.GIB * 1024));
    }

    @Test void formattingDoesNotDependOnSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : new Locale[]{Locale.CHINA, Locale.US, Locale.GERMANY, Locale.FRANCE}) {
                Locale.setDefault(locale);
                assertEquals("1.5 KiB", DlcFileSize.format(1536));
            }
        } finally { Locale.setDefault(previous); }
    }

    @Test void configurationRoundingUsesBytesWithoutOverflow() {
        assertEquals(0, DlcFileSize.ceilMiB(-1));
        assertEquals(0, DlcFileSize.ceilMiB(0));
        assertEquals(1, DlcFileSize.ceilMiB(1));
        assertEquals(1, DlcFileSize.ceilMiB(DlcFileSize.MIB));
        assertEquals(2, DlcFileSize.ceilMiB(DlcFileSize.MIB + 1));
        assertEquals(8796093022208L, DlcFileSize.ceilMiB(Long.MAX_VALUE));
    }
}
