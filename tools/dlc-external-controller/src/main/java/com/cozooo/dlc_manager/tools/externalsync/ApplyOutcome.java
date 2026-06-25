package com.cozooo.dlc_manager.tools.externalsync;

import java.util.List;

/**
 * Outcome of a remote {@code apply} request: the server's
 * {@code requiresRestart} flag plus the human-readable message lines the
 * server reports (which items were enabled/disabled, conflicts, etc.).
 * <p>
 * {@code messages} is empty when talking to an older server that does not
 * include a {@code messages} array in its apply response.
 */
public record ApplyOutcome(boolean requiresRestart, List<String> messages) {

    /** Empty messages convenience for callers that only need the flag. */
    public static ApplyOutcome flagOnly(boolean requiresRestart) {
        return new ApplyOutcome(requiresRestart, List.of());
    }
}
