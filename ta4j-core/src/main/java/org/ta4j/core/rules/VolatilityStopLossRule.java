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
 * A stop-loss rule based on a volatility indicator.
 *
 * <p>
 * The stop-loss distance is derived from the supplied volatility indicator and
 * coefficient, yielding a volatility-scaled dollar stop.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class VolatilityStopLossRule extends BaseVolatilityStopLossRule {

    /**
     * Constructor with default close price as reference.
     *
     * @param series              the bar series
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     * @param coefficient         the coefficient to multiply volatility
     */
    public VolatilityStopLossRule(BarSeries series, Indicator<Num> volatilityIndicator, Number coefficient) {
        this(new ClosePriceIndicator(series), volatilityIndicator, coefficient);
    }

    /**
     * Constructor with default close price as reference and unit coefficient.
     *
     * @param series              the bar series
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     */
    public VolatilityStopLossRule(BarSeries series, Indicator<Num> volatilityIndicator) {
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
    public VolatilityStopLossRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator,
            Number coefficient) {
        super(referencePrice, BinaryOperationIndicator.product(volatilityIndicator, coefficient));
    }

    /**
     * Constructor with custom reference price and unit coefficient.
     *
     * @param referencePrice      the reference price indicator
     * @param volatilityIndicator the volatility indicator (for example, ATR or
     *                            standard deviation)
     */
    public VolatilityStopLossRule(Indicator<Num> referencePrice, Indicator<Num> volatilityIndicator) {
        this(referencePrice, volatilityIndicator, 1);
    }
}
