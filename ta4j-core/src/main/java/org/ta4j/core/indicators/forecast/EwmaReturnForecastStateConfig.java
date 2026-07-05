/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

/**
 * Configuration for EWMA return state estimation.
 *
 * @param initializationBarCount number of valid return observations required
 *                               before the state is stable
 * @param decayFactor            EWMA decay in {@code (0, 1)}
 * @param driftMode              drift assumption
 * @since 0.22.9
 */
public record EwmaReturnForecastStateConfig(int initializationBarCount, double decayFactor, DriftMode driftMode) {

    /**
     * Creates an EWMA state configuration.
     *
     * @since 0.22.9
     */
    public EwmaReturnForecastStateConfig {
        if (initializationBarCount < 1) {
            throw new IllegalArgumentException("initializationBarCount must be >= 1");
        }
        if (Double.isNaN(decayFactor) || decayFactor <= 0d || decayFactor >= 1d) {
            throw new IllegalArgumentException("decayFactor must be in (0, 1)");
        }
        driftMode = Objects.requireNonNull(driftMode, "driftMode must not be null");
    }

    /**
     * Returns a builder with default values.
     *
     * @return builder
     * @since 0.22.9
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EwmaReturnForecastStateConfig}.
     *
     * @since 0.22.9
     */
    public static final class Builder {

        private int initializationBarCount = 30;
        private double decayFactor = 0.94d;
        private DriftMode driftMode = DriftMode.ZERO;

        private Builder() {
        }

        /**
         * Sets the initialization bar count.
         *
         * @param initializationBarCount initialization bar count
         * @return this builder
         * @since 0.22.9
         */
        public Builder initializationBarCount(int initializationBarCount) {
            this.initializationBarCount = initializationBarCount;
            return this;
        }

        /**
         * Sets the EWMA decay factor.
         *
         * @param decayFactor decay factor in {@code (0, 1)}
         * @return this builder
         * @since 0.22.9
         */
        public Builder decayFactor(double decayFactor) {
            this.decayFactor = decayFactor;
            return this;
        }

        /**
         * Sets the drift mode.
         *
         * @param driftMode drift mode
         * @return this builder
         * @since 0.22.9
         */
        public Builder driftMode(DriftMode driftMode) {
            this.driftMode = driftMode;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return configuration
         * @since 0.22.9
         */
        public EwmaReturnForecastStateConfig build() {
            return new EwmaReturnForecastStateConfig(initializationBarCount, decayFactor, driftMode);
        }
    }
}
