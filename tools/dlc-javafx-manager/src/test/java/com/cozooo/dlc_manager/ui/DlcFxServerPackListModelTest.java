package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import com.cozooo.dlc_manager.client.server.DlcServerPackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxServerPackListModelTest {
    @Test
    void groupsVersionsAndPrefersInstalledVersionForDisplay() {
        DlcServerPackInfo oldVersion = pack("rail", "1.0.0", "rail-1.zip#rail@1.0.0", true);
        DlcServerPackInfo newVersion = pack("rail", "2.0.0", "rail-2.zip#rail@2.0.0", false);

        List<DlcFxServerPackListModel.Group> groups = DlcFxServerPackListModel.groups(
            List.of(newVersion, oldVersion),
            List.of("rail-2.zip#rail@2.0.0")
        );

        assertEquals(1, groups.size());
        assertEquals("rail", groups.getFirst().identifier());
        assertEquals("1.0.0", groups.getFirst().displayEntry().version());
        assertEquals("rail-1.zip#rail@1.0.0", groups.getFirst().orderKey());
        assertEquals(List.of("rail-1.zip#rail@1.0.0"), DlcFxServerPackListModel.normalizedOrder(List.of("rail-2.zip#rail@2.0.0"), groups));
    }

    @Test
    void keepsSavedOrderForExistingIdentifiers() {
        DlcServerPackInfo first = pack("a", "1", "a.zip#a@1", false);
        DlcServerPackInfo second = pack("b", "1", "b.zip#b@1", false);

        List<DlcFxServerPackListModel.Group> groups = DlcFxServerPackListModel.groups(
            List.of(first, second),
            List.of("b.zip#b@1", "a.zip#a@1")
        );

        assertEquals(List.of("b", "a"), groups.stream().map(DlcFxServerPackListModel.Group::identifier).toList());
        assertEquals(List.of("b.zip#b@1", "a.zip#a@1"), DlcFxServerPackListModel.normalizedOrder(List.of("b.zip#b@1", "a.zip#a@1"), groups));
    }

    private static DlcServerPackInfo pack(String id, String version, String key, boolean installed) {
        DlcServerPackInfo info = new DlcServerPackInfo();
        info.identifier = id;
        info.version = version;
        info.archivePath = key.substring(0, key.indexOf('#'));
        info.orderKey = key;
        info.displayName = id;
        info.installed = installed;
        info.installedVersion = installed ? version : "";
        return info;
    }
}
