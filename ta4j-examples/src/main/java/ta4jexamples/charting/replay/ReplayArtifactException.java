/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

/**
 * Raised when a research artifact cannot back a replay. The message always
 * names the offending path or schema and the regeneration route, because the
 * replay never substitutes live or recomputed data.
 */
final class ReplayArtifactException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ReplayArtifactException(final String message) {
        super(message);
    }

    ReplayArtifactException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
