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
 * A trailing stop-loss rule based on a volatility indicator.
 *
 * <p>
 * The stop-loss distance is derived from the supplied volatility indicator and
 * coefficient, yielding a volatility-scaled dollar trailing stop.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class VolatilityTrailingStopLossRule extends BaseVolatilityTrailingStopLossRule {

    /**
     * Constructor with default close price as reference.
     *
     * @param series              the bar series
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     * @param coefficient         the coefficient to multiply volatility
     */
    public VolatilityTrailingStopLossRule(BarSeries series, Indicator<Num> volatilityIndicator, Number coefficient) {
        this(new ClosePriceIndicator(series), volatilityIndicator, coefficient, Integer.MAX_VALUE);
    }

    /**
     * Constructor with default close price as reference and unit coefficient.
     *
     * @param series              the bar series
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     */
    public VolatilityTrailingStopLossRule(BarSeries series, Indicator<Num> volatilityIndicator) {
        this(new ClosePriceIndicator(series), volatilityIndicator, 1, Integer.MAX_VALUE);
    }

    /**
     * Constructor with custom reference price.
     *
     * @param referencePrice      the reference price indicator
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     * @param coefficient         the coefficient to multiply volatility
     * @param barCount            the number of bars to look back for the
     *                            calculation
     */
    public VolatilityTrailingStopLossRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator,
            Number coefficient, int barCount) {
        super(referencePrice, BinaryOperationIndicator.product(volatilityIndicator, coefficient), barCount);
    }

    /**
     * Constructor with custom reference price.
     *
     * @param referencePrice      the reference price indicator
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     * @param coefficient         the coefficient to multiply volatility
     */
    public VolatilityTrailingStopLossRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator,
            Number coefficient) {
        this(referencePrice, volatilityIndicator, coefficient, Integer.MAX_VALUE);
    }

    /**
     * Constructor with custom reference price and unit coefficient.
     *
     * @param referencePrice      the reference price indicator
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     */
    public VolatilityTrailingStopLossRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator) {
        this(referencePrice, volatilityIndicator, 1, Integer.MAX_VALUE);
    }
}
