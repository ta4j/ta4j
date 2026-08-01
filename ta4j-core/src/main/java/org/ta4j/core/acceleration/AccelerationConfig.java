/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

/**
 * Immutable configuration for an explicit indicator batch request.
 *
 * <p>
 * The global JVM property {@code ta4j.acceleration} accepts
 * {@code off|cpu|auto|metal|cuda|hybrid}. {@code ta4j.acceleration.required}
 * controls whether preferred provider failures may fall back to CPU.
 *
 * @param mode           requested execution mode
 * @param required       whether a device/provider request must fail instead of
 *                       returning CPU fallback values
 * @param minimumSpeedup minimum predicted improvement required before a device
 *                       stage is selected, expressed as a fraction
 * @since 0.23.1
 */
public record AccelerationConfig(AccelerationMode mode, boolean required, double minimumSpeedup) {

    /** JVM property for launch-time mode selection. */
    public static final String MODE_PROPERTY = "ta4j.acceleration";

    /** JVM property for required-provider behavior. */
    public static final String REQUIRED_PROPERTY = "ta4j.acceleration.required";

    /** JVM property for provider crossover policy. */
    public static final String MINIMUM_SPEEDUP_PROPERTY = "ta4j.acceleration.minimumSpeedup";

    private static final double DEFAULT_MINIMUM_SPEEDUP = 0.10d;

    public AccelerationConfig {
        Objects.requireNonNull(mode, "mode must not be null");
        if (Double.isNaN(minimumSpeedup) || minimumSpeedup < 0d || minimumSpeedup >= 1d) {
            throw new IllegalArgumentException("minimumSpeedup must be in [0, 1)");
        }
    }

    /**
     * @return default CPU-safe configuration
     * @since 0.23.1
     */
    public static AccelerationConfig off() {
        return new AccelerationConfig(AccelerationMode.OFF, false, DEFAULT_MINIMUM_SPEEDUP);
    }

    /**
     * @return explicit CPU-only configuration
     * @since 0.23.1
     */
    public static AccelerationConfig cpu() {
        return new AccelerationConfig(AccelerationMode.CPU, false, DEFAULT_MINIMUM_SPEEDUP);
    }

    /**
     * @return automatic preferred acceleration configuration
     * @since 0.23.1
     */
    public static AccelerationConfig auto() {
        return new AccelerationConfig(AccelerationMode.AUTO, false, DEFAULT_MINIMUM_SPEEDUP);
    }

    /**
     * Parses JVM properties once through {@link IndicatorBatchEvaluator}.
     *
     * @return configuration from current system properties
     * @since 0.23.1
     */
    public static AccelerationConfig fromSystemProperties() {
        String rawMode = System.getProperty(MODE_PROPERTY);
        AccelerationMode mode = parseMode(rawMode);
        boolean required = Boolean.parseBoolean(System.getProperty(REQUIRED_PROPERTY, "false"));
        String rawMinimumSpeedup = System.getProperty(MINIMUM_SPEEDUP_PROPERTY,
                Double.toString(DEFAULT_MINIMUM_SPEEDUP));
        double minimumSpeedup = parseMinimumSpeedup(rawMinimumSpeedup);
        try {
            return new AccelerationConfig(mode, required, minimumSpeedup);
        } catch (IllegalArgumentException e) {
            throw invalidProperty(MINIMUM_SPEEDUP_PROPERTY, rawMinimumSpeedup, e);
        }
    }

    private static AccelerationMode parseMode(String value) {
        try {
            return AccelerationMode.parse(value);
        } catch (IllegalArgumentException e) {
            throw invalidProperty(MODE_PROPERTY, value, e);
        }
    }

    private static double parseMinimumSpeedup(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw invalidProperty(MINIMUM_SPEEDUP_PROPERTY, value, e);
        }
    }

    private static IllegalArgumentException invalidProperty(String propertyName, String value, RuntimeException cause) {
        return new IllegalArgumentException("Invalid value for system property %s: %s".formatted(propertyName, value),
                cause);
    }

    /**
     * @param requiredValue required-provider behavior
     * @return a copy with the supplied required flag
     * @since 0.23.1
     */
    public AccelerationConfig withRequired(boolean requiredValue) {
        return new AccelerationConfig(mode, requiredValue, minimumSpeedup);
    }

    /**
     * @param minimumSpeedupValue minimum provider improvement
     * @return a copy with the supplied crossover threshold
     * @since 0.23.1
     */
    public AccelerationConfig withMinimumSpeedup(double minimumSpeedupValue) {
        return new AccelerationConfig(mode, required, minimumSpeedupValue);
    }
}
