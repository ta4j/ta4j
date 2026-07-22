/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

/**
 * One typed batch-evaluation diagnostic.
 *
 * @param code        stable diagnostic code
 * @param message     human-readable explanation
 * @param providerId  provider identifier, or {@code null} when not applicable
 * @param operationId adapter/provider operation identifier, or {@code null}
 *                    when not applicable
 * @since 0.23.1
 */
public record AccelerationDiagnostic(AccelerationDiagnosticCode code, String message, String providerId,
        String operationId) {

    public AccelerationDiagnostic {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * Creates a diagnostic without provider or operation metadata.
     *
     * @param code    stable diagnostic code
     * @param message human-readable explanation
     * @return diagnostic
     * @since 0.23.1
     */
    public static AccelerationDiagnostic of(AccelerationDiagnosticCode code, String message) {
        return new AccelerationDiagnostic(code, message, null, null);
    }
}
