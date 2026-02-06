/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Generic execution intent metadata for live trading systems.
 *
 * <p>
 * This is intentionally minimal so adapters can map their decision objects
 * without leaking platform-specific details into ta4j core.
 * </p>
 *
 * @param intentId      unique intent identifier
 * @param side          execution side
 * @param createdAt     intent creation timestamp (UTC)
 * @param correlationId optional correlation id for external systems
 * @since 0.22.2
 */
public record ExecutionIntent(String intentId, ExecutionSide side, Instant createdAt,
        String correlationId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 8030047863134194008L;

    public ExecutionIntent {
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("intentId must be non-blank");
        }
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(createdAt, "createdAt");
        if (correlationId != null && correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must be non-blank when provided");
        }
    }
}
