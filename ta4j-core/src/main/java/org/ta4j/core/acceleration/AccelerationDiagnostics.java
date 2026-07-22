/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.List;
import java.util.Objects;

/**
 * Immutable execution plan and fallback metadata for one batch request.
 *
 * @param requestedMode     requested execution mode
 * @param effectiveMode     mode that actually produced the returned values
 * @param backendId         backend identifier such as {@code cpu},
 *                          {@code metal}, or {@code cuda}
 * @param operationId       adapter/provider operation identifier, or
 *                          {@code scalar-cpu}
 * @param nativeInitialized whether this request initialized native code
 * @param diagnostics       typed diagnostics in emission order
 * @since 0.23.1
 */
public record AccelerationDiagnostics(AccelerationMode requestedMode, AccelerationMode effectiveMode, String backendId,
        String operationId, boolean nativeInitialized, List<AccelerationDiagnostic> diagnostics) {

    public AccelerationDiagnostics {
        Objects.requireNonNull(requestedMode, "requestedMode must not be null");
        Objects.requireNonNull(effectiveMode, "effectiveMode must not be null");
        Objects.requireNonNull(backendId, "backendId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }

    /**
     * @param code diagnostic code to find
     * @return true when this diagnostics payload contains {@code code}
     * @since 0.23.1
     */
    public boolean hasCode(AccelerationDiagnosticCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.code() == code);
    }
}
