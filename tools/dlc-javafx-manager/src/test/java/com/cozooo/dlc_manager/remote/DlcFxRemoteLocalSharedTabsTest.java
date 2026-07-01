package com.cozooo.dlc_manager.remote;

import com.cozooo.dlc_manager.ui.DlcFxRemoteDownloadShell;
import com.cozooo.dlc_manager.ui.DlcFxRequiredListShell;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class DlcFxRemoteLocalSharedTabsTest {
    @Test
    void remoteDownloadAndRequiredTabsAreSplitLikeLocalAndUseSharedShells() throws Exception {
        assertEquals(DlcFxRemoteDownloadShell.class, shellFieldType(DlcFxRemoteDownloadTab.class));
        assertEquals(DlcFxRequiredListShell.class, shellFieldType(DlcFxRemoteRequiredTab.class));
    }

    @Test
    void legacyCombinedRemoteRequiredTabIsNotRestored() {
        Path legacy = Path.of(
                "src/main/java/com/cozooo/dlc_manager/remote/DlcFxRemoteRemoteRequiredTab.java");

        assertFalse(Files.exists(legacy), "Remote download and required resources must stay split into local-like tabs");
    }

    @Test
    void localAndRemoteRequiredTabsDelegateSelectionActionsToSharedFlow() {
        assertFalse(hasDeclaredMethod(DlcFxRemoteRequiredTab.class, "installSelected"));
        assertFalse(hasDeclaredMethod(DlcFxRemoteRequiredTab.class, "installOrApply"));
    }

    private static Class<?> shellFieldType(Class<?> tabClass) throws NoSuchFieldException {
        Field field = tabClass.getDeclaredField("shell");
        return field.getType();
    }

    private static boolean hasDeclaredMethod(Class<?> owner, String name) {
        return Arrays.stream(owner.getDeclaredMethods()).map(Method::getName).anyMatch(name::equals);
    }
}
