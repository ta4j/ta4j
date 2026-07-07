/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.adapters.LogReturnToPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.num.Num;

/**
 * Monte Carlo price forecast indicator backed by a log-return state indicator.
 *
 * <p>
 * This is the constructor-first path for users who build state from a
 * {@link LogReturnIndicator}. The price source is inferred from that log-return
 * indicator, so callers do not need to pass the same close-price indicator
 * again. Use {@link LogReturnToPriceForecastIndicator} directly when projecting
 * a custom log-return stream that cannot expose its source indicator.
 *
 * @since 0.22.9
 */
public final class MonteCarloPriceForecastIndicator extends LogReturnToPriceForecastIndicator {

    /**
     * Constructor using default Monte Carlo settings and a one-bar horizon.
     *
     * @param stateIndicator log-return state indicator
     * @since 0.22.9
     */
    public MonteCarloPriceForecastIndicator(ReturnForecastStateIndicator stateIndicator) {
        this(stateIndicator, 1);
    }

    /**
     * Constructor using default Monte Carlo settings for the requested horizon.
     *
     * @param stateIndicator log-return state indicator
     * @param horizon        forecast horizon in bars
     * @since 0.22.9
     */
    public MonteCarloPriceForecastIndicator(ReturnForecastStateIndicator stateIndicator, int horizon) {
        super(sourceIndicator(stateIndicator), new MonteCarloReturnProjectionIndicator(stateIndicator, horizon));
    }

    private static Indicator<Num> sourceIndicator(ReturnForecastStateIndicator stateIndicator) {
        ReturnForecastStateIndicator validated = Objects.requireNonNull(stateIndicator,
                "stateIndicator must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("stateIndicator must use ReturnRepresentation.LOG");
        }
        ReturnIndicator returnIndicator = validated.getReturnIndicator();
        if (returnIndicator instanceof LogReturnIndicator logReturns) {
            return logReturns.getSourceIndicator();
        }
        throw new IllegalArgumentException("stateIndicator must use a LogReturnIndicator to infer the price source");
    }
}
