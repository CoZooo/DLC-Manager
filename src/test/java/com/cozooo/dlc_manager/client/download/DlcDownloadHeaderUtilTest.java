package com.cozooo.dlc_manager.client.download;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcDownloadHeaderUtilTest {
    @Test
    void parsesQuotedContentDispositionFilename() {
        assertEquals("file.jar", DlcDownloadHeaderUtil.parseContentDisposition("attachment; filename=\"file.jar\""));
        assertEquals("file.jar", DlcDownloadHeaderUtil.parseContentDisposition("attachment; filename=file.jar; size=1"));
    }

    @Test
    void parsesRfc5987FilenameStar() {
        assertEquals("中文.jar", DlcDownloadHeaderUtil.parseContentDisposition("attachment; filename*=UTF-8''%E4%B8%AD%E6%96%87.jar"));
    }

    @Test
    void stripsQueryAndReturnsNullForMissingHeader() {
        assertEquals("file.jar", DlcDownloadHeaderUtil.parseContentDisposition("attachment; filename=\"file.jar?download=1\""));
        assertNull(DlcDownloadHeaderUtil.parseContentDisposition(null));
        assertNull(DlcDownloadHeaderUtil.parseContentDisposition("attachment"));
    }
}
