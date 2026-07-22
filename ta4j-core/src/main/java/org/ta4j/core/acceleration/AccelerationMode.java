/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Locale;

/**
 * Requested indicator batch execution policy.
 *
 * <p>
 * Modes describe which resources are eligible for an explicit batch evaluation
 * request. They do not change scalar {@code Indicator#getValue(int)} or
 * {@code Indicator#stream()} semantics.
 *
 * @since 0.23.1
 */
public enum AccelerationMode {

    /** Disable acceleration and evaluate through scalar CPU calls. */
    OFF,

    /** Evaluate through scalar or batch CPU code only. */
    CPU,

    /** Let an accelerator module choose a profitable available backend. */
    AUTO,

    /** Prefer a Metal provider when it is available, supported, and beneficial. */
    METAL,

    /** Prefer a CUDA provider when it is available, supported, and beneficial. */
    CUDA,

    /** Experimental explicit CPU plus GPU partitioning for eligible adapters. */
    HYBRID;

    /**
     * Parses a JVM property value.
     *
     * @param value property text; {@code null} and blank values mean {@link #OFF}
     * @return parsed mode
     * @throws IllegalArgumentException when the value is not a supported mode
     * @since 0.23.1
     */
    public static AccelerationMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
        case "OFF", "FALSE", "DISABLED", "NONE" -> OFF;
        case "CPU" -> CPU;
        case "AUTO", "TRUE", "ON" -> AUTO;
        case "METAL" -> METAL;
        case "CUDA" -> CUDA;
        case "HYBRID" -> HYBRID;
        default -> throw new IllegalArgumentException("Unsupported ta4j.acceleration mode '%s'".formatted(value));
        };
    }

    /**
     * @return whether this mode can require a non-CPU provider
     * @since 0.23.1
     */
    public boolean canUseDevice() {
        return this == AUTO || this == METAL || this == CUDA || this == HYBRID;
    }
}
