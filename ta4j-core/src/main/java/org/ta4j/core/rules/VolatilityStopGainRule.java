/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * A stop-gain rule based on a volatility indicator.
 *
 * <p>
 * The stop-gain distance is derived from the supplied volatility indicator and
 * coefficient, yielding a volatility-scaled dollar gain target.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class VolatilityStopGainRule extends BaseVolatilityStopGainRule {

    /**
     * Constructor with default close price as reference.
     *
     * @param series              the bar series
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     * @param coefficient         the coefficient to multiply volatility
     */
    public VolatilityStopGainRule(BarSeries series, Indicator<Num> volatilityIndicator, Number coefficient) {
        this(new ClosePriceIndicator(series), volatilityIndicator, coefficient);
    }

    /**
     * Constructor with default close price as reference and unit coefficient.
     *
     * @param series              the bar series
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     */
    public VolatilityStopGainRule(BarSeries series, Indicator<Num> volatilityIndicator) {
        this(new ClosePriceIndicator(series), volatilityIndicator, 1);
    }

    /**
     * Constructor with custom reference price.
     *
     * @param referencePrice      the reference price indicator
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     * @param coefficient         the coefficient to multiply volatility
     */
    public VolatilityStopGainRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator,
            Number coefficient) {
        super(referencePrice, createStopGainThreshold(volatilityIndicator, coefficient));
    }

    /**
     * Constructor with custom reference price and unit coefficient.
     *
     * @param referencePrice      the reference price indicator
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     */
    public VolatilityStopGainRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator) {
        this(referencePrice, volatilityIndicator, 1);
    }

    /**
     * Builds the volatility-scaled stop-gain threshold indicator.
     *
     * @param volatilityIndicator volatility source indicator
     * @param coefficient         volatility multiplier
     * @return indicator representing stop-gain distance
     */
    private static Indicator<Num> createStopGainThreshold(Indicator<Num> volatilityIndicator, Number coefficient) {
        if (volatilityIndicator == null) {
            throw new IllegalArgumentException("volatilityIndicator must not be null");
        }
        if (coefficient == null || Double.isNaN(coefficient.doubleValue()) || coefficient.doubleValue() <= 0) {
            throw new IllegalArgumentException("coefficient must be positive");
        }
        return BinaryOperationIndicator.product(volatilityIndicator, coefficient);
    }
}
