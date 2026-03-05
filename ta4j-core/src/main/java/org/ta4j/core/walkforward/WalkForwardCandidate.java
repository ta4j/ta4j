/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.Objects;

/**
 * Candidate configuration/context for walk-forward tuning.
 *
 * @param <C>     context type passed to prediction provider
 * @param id      stable candidate identifier
 * @param context candidate context
 * @since 0.22.4
 */
public record WalkForwardCandidate<C>(String id, C context) {

    /**
     * Creates a validated candidate.
     */
    public WalkForwardCandidate {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
