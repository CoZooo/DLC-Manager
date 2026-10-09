package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiPageTest {
    @Test void pageIdsAreUniqueResourceNames() {
        var ids = new HashSet<String>();
        for (var page : DlcUiPage.values()) {
            assertTrue(page.id().matches("[a-z][a-z0-9_]*"), page.name());
            assertTrue(ids.add(page.id()), page.id());
        }
    }

    @Test void existingTemplatesKeepTheirResourceNames() {
        assertEquals("page", DlcUiPage.PAGE.id());
        assertEquals("page", DlcUiPage.PAGE.template());
        assertEquals("manager", DlcUiPage.MANAGER.id());
        assertEquals("manager", DlcUiPage.MANAGER.template());
        assertEquals("server_manager", DlcUiPage.SERVER_MANAGER.id());
        assertEquals("manager", DlcUiPage.SERVER_MANAGER.template());
        assertEquals(Set.of(DlcUiPage.MANAGER, DlcUiPage.SERVER_MANAGER),
            Arrays.stream(DlcUiPage.values()).filter(page -> page.template().equals("manager")).collect(Collectors.toSet()));
        assertTrue(Arrays.stream(DlcUiPage.values()).allMatch(page -> Set.of("page", "manager").contains(page.template())));
    }

    @Test void sharedScreensHaveIndependentBusinessResourceIds() {
        assertNotEquals(DlcUiPage.LOCAL_PRESETS.id(), DlcUiPage.SERVER_PRESETS.id());
        assertNotEquals(DlcUiPage.LOCAL_MODPACK_UPDATE.id(), DlcUiPage.SERVER_MODPACK_UPDATE.id());
        assertNotEquals(DlcUiPage.REMOTE_DOWNLOAD.id(), DlcUiPage.REMOTE_PACK_DOWNLOAD.id());
        assertNotEquals(DlcUiPage.SERVER_MANAGER.id(), DlcUiPage.SERVER_TOOLS.id());
        assertNotEquals(DlcUiPage.LOCAL_PACK_URL.id(), DlcUiPage.SERVER_PACK_URL.id());
        assertNotEquals(DlcUiPage.LOCAL_UPDATE_URL.id(), DlcUiPage.SERVER_UPDATE_URL.id());
    }
}
