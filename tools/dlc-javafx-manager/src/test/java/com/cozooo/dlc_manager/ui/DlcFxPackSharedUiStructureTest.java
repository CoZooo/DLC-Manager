package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import com.cozooo.dlc_manager.local.DlcFxLocalPackTab;
import com.cozooo.dlc_manager.server.DlcFxServerPackTab;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxPackSharedUiStructureTest {
    @Test
    void localAndRemotePackTabsUseSharedPackGroupCell() {
        assertTrue(hasNestedType(DlcFxLocalPackTab.class, "PackCellAdapter"));
        assertTrue(hasNestedType(DlcFxServerPackTab.class, "PackCellAdapter"));
        assertTrue(Arrays.stream(DlcFxPackGroupCell.class.getDeclaredClasses()).noneMatch(Class::isAnonymousClass));
    }

    @Test
    void localAndRemotePackTabsUseSharedImportFlow() throws Exception {
        assertEquals(DlcFxPackImportFlow.class, fieldType(DlcFxLocalPackTab.class, "importFlow"));
        assertEquals(DlcFxPackImportFlow.class, fieldType(DlcFxServerPackTab.class, "importFlow"));
    }

    private static boolean hasNestedType(Class<?> owner, String simpleName) {
        return Arrays.stream(owner.getDeclaredClasses())
                .anyMatch(type -> simpleName.equals(type.getSimpleName()) && !Modifier.isStatic(type.getModifiers()));
    }

    private static Class<?> fieldType(Class<?> owner, String fieldName) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(fieldName);
        return field.getType();
    }
}
