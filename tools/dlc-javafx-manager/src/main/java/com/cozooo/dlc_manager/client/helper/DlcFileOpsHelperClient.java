package com.cozooo.dlc_manager.client.helper;

import java.nio.file.Path;

/**
 * No-op stub of {@code DlcFileOpsHelperClient} for the standalone JavaFX manager.
 * <p>
 * The in-game client delegates locked-file operations (delete/copy/move on
 * running-game files) to an external FileOps Helper process.  The JavaFX
 * manager runs while the game is closed, so direct {@link java.nio.file.Files}
 * operations always succeed and the helper is never required.  Every schedule
 * method therefore returns a failure result, which makes
 * {@code DlcApplier} fall back to the standard NIO path.
 */
public final class DlcFileOpsHelperClient {

    private DlcFileOpsHelperClient() {
    }

    public enum HelperState {
        NOT_REQUIRED,
        READY,
        UNSUPPORTED
    }

    public record FileOpResult(boolean ok, boolean helperScheduled, String message) {
        public static FileOpResult success(boolean helperScheduled) {
            return new FileOpResult(true, helperScheduled, "");
        }

        public static FileOpResult failure(String message) {
            return new FileOpResult(false, false, message == null ? "" : message);
        }
    }

    public static boolean shouldUseHelper() {
        return false;
    }

    public static HelperState detectState() {
        return HelperState.NOT_REQUIRED;
    }

    public static FileOpResult scheduleDelete(Path path) {
        return FileOpResult.failure("helper not available in standalone mode");
    }

    public static FileOpResult scheduleCopy(Path source, Path target, boolean replaceExisting) {
        return FileOpResult.failure("helper not available in standalone mode");
    }

    public static FileOpResult scheduleMove(Path source, Path target, boolean replaceExisting) {
        return FileOpResult.failure("helper not available in standalone mode");
    }

    public static FileOpResult scheduleWriteText(Path target, String content) {
        return FileOpResult.failure("helper not available in standalone mode");
    }

    public static boolean cancelPendingDelete(Path target) {
        return false;
    }

    public static boolean cancelPendingOperation(Path target, String requiredType) {
        return false;
    }
}
