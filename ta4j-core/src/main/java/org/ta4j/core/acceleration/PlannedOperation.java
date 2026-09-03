/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;

/**
 * A claimed calculation: the kernel request providers execute plus the
 * core-owned decoder that reconstructs domain values from raw output.
 *
 * @param request kernel request built from primitives only
 * @param decoder core-owned raw-to-domain reconstruction
 * @since 0.24.2
 */
public record PlannedOperation(KernelRequest request, OperationDecoder decoder) {

    /**
     * Validates a planned operation.
     *
     * @since 0.24.2
     */
    public PlannedOperation {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(decoder, "decoder must not be null");
    }
}
