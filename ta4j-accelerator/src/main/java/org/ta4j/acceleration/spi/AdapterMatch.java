/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.spi;

import java.util.Objects;

import org.ta4j.core.Indicator;

/**
 * Result of asking an acceleration adapter whether it owns an indicator graph.
 *
 * @param supported        whether the graph is supported
 * @param indicator        typed indicator when supported
 * @param operationId      stable operation identifier
 * @param graphFingerprint diagnostic graph fingerprint
 * @param deviceEligible   whether a device provider may be considered
 * @param partitionSafe    whether experimental HYBRID partitioning is legal
 * @param rejectionReason  reason when unsupported
 * @param <T>              indicator value type
 * @since 0.23.1
 */
public record AdapterMatch<T>(boolean supported, Indicator<T> indicator, String operationId, String graphFingerprint,
        boolean deviceEligible, boolean partitionSafe, String rejectionReason) {

    public AdapterMatch {
        if (supported) {
            Objects.requireNonNull(indicator, "indicator must not be null when supported");
            Objects.requireNonNull(operationId, "operationId must not be null when supported");
            Objects.requireNonNull(graphFingerprint, "graphFingerprint must not be null when supported");
        } else {
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null when unsupported");
        }
    }

    /**
     * Creates a supported match.
     *
     * @param indicator        typed indicator
     * @param operationId      operation identifier
     * @param graphFingerprint diagnostic graph fingerprint
     * @param deviceEligible   whether a provider may be considered
     * @param partitionSafe    whether HYBRID partitioning is legal
     * @param <T>              indicator value type
     * @return supported match
     * @since 0.23.1
     */
    public static <T> AdapterMatch<T> supported(Indicator<T> indicator, String operationId, String graphFingerprint,
            boolean deviceEligible, boolean partitionSafe) {
        return new AdapterMatch<>(true, indicator, operationId, graphFingerprint, deviceEligible, partitionSafe, "");
    }

    /**
     * Creates an unsupported match.
     *
     * @param reason rejection reason
     * @param <T>    indicator value type
     * @return unsupported match
     * @since 0.23.1
     */
    public static <T> AdapterMatch<T> unsupported(String reason) {
        return new AdapterMatch<>(false, null, null, null, false, false, reason);
    }
}
