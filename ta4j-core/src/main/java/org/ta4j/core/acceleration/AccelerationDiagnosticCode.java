/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

/**
 * Stable reason codes emitted by indicator batch evaluation.
 *
 * @since 0.23.1
 */
public enum AccelerationDiagnosticCode {

    /** The request was evaluated by the canonical CPU path. */
    CPU_EVALUATED,

    /** Acceleration was disabled by configuration. */
    ACCELERATION_OFF,

    /** CPU execution was requested explicitly. */
    CPU_REQUESTED,

    /** No adapter claimed the indicator graph. */
    UNSUPPORTED_GRAPH,

    /**
     * A supported graph remained on CPU because device execution was not
     * beneficial.
     */
    CPU_FASTER,

    /** A preferred provider was absent or unavailable. */
    PROVIDER_UNAVAILABLE,

    /** A provider skeleton exists but does not implement the operation. */
    NOT_IMPLEMENTED,

    /** No beneficial device stage exists for a required-provider request. */
    NO_BENEFICIAL_DEVICE_STAGE,

    /** A required provider request could not fall back to CPU. */
    REQUIRED_PROVIDER_UNAVAILABLE,

    /** The backing series changed while an immutable batch was being evaluated. */
    STALE_SNAPSHOT,

    /** Experimental hybrid execution fell back to CPU. */
    HYBRID_FALLBACK,

    /** A provider was discovered without loading native code. */
    LAZY_PROVIDER_DISCOVERED,

    /** A provider loaded native code for an eligible request. */
    NATIVE_PROVIDER_INITIALIZED,

    /** Input range or configuration was invalid. */
    INVALID_REQUEST
}
