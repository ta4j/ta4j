/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import java.util.Objects;

/**
 * Describes how a forecast distribution is represented.
 *
 * <p>
 * Empirical forecasts summarize a finite collection of simulated paths,
 * bootstrap draws, neighbors, or other represented values. Analytic forecasts
 * describe a named fitted distribution without pretending that it contains a
 * sample collection. Unavailable support is reserved for unstable forecasts.
 *
 * @since 0.23.1
 */
public sealed interface ForecastSupport
        permits ForecastSupport.Unavailable, ForecastSupport.Empirical, ForecastSupport.Analytic {

    /**
     * Returns the support used by unstable forecasts.
     *
     * @return unavailable support
     * @since 0.23.1
     */
    static ForecastSupport unavailable() {
        return Unavailable.INSTANCE;
    }

    /**
     * Creates empirical support for a represented value collection.
     *
     * @param count positive represented value count
     * @return empirical support
     * @since 0.23.1
     */
    static ForecastSupport empirical(int count) {
        return new Empirical(count);
    }

    /**
     * Creates analytic support for a named distribution assumption.
     *
     * @param assumption nonblank distribution assumption
     * @return analytic support
     * @since 0.23.1
     */
    static ForecastSupport analytic(String assumption) {
        return new Analytic(assumption);
    }

    /**
     * Support for an unstable forecast.
     *
     * @since 0.23.1
     */
    enum Unavailable implements ForecastSupport {
        /** Singleton unavailable support. */
        INSTANCE
    }

    /**
     * Empirical distribution support.
     *
     * @param count represented distribution value count
     * @since 0.23.1
     */
    record Empirical(int count) implements ForecastSupport {

        /** Creates empirical support. */
        public Empirical {
            if (count <= 0) {
                throw new IllegalArgumentException("count must be > 0");
            }
        }
    }

    /**
     * Analytic distribution support.
     *
     * @param assumption distribution assumption identifier
     * @since 0.23.1
     */
    record Analytic(String assumption) implements ForecastSupport {

        /** Creates analytic support. */
        public Analytic {
            assumption = Objects.requireNonNull(assumption, "assumption must not be null").trim();
            if (assumption.isEmpty()) {
                throw new IllegalArgumentException("assumption must not be blank");
            }
        }
    }
}
