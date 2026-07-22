/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

/**
 * Exception thrown when an explicit batch request cannot legally return CPU
 * fallback values.
 *
 * @since 0.23.1
 */
public class AccelerationException extends RuntimeException {

    private final AccelerationDiagnosticCode code;

    /**
     * @param code    stable diagnostic code
     * @param message exception message
     * @since 0.23.1
     */
    public AccelerationException(AccelerationDiagnosticCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * @return stable diagnostic code
     * @since 0.23.1
     */
    public AccelerationDiagnosticCode code() {
        return code;
    }
}
