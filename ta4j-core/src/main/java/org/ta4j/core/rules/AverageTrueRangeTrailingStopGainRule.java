/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

import static java.util.Objects.requireNonNull;

/**
 * A trailing stop-gain rule based on Average True Range (ATR).
 *
 * <p>
 * The stop-gain distance is derived from ATR and trails the most favorable
 * price by that distance, triggering on retracement.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class AverageTrueRangeTrailingStopGainRule extends BaseVolatilityTrailingStopGainRule {

    /**
     * Constructor with default close price as reference.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeTrailingStopGainRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(series, new ClosePriceIndicator(series), atrBarCount, atrCoefficient, Integer.MAX_VALUE);
    }

    /**
     * Constructor with custom reference price.
     *
     * @param series         the bar series
     * @param referencePrice the reference price indicator
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     * @param barCount       the number of bars to look back for the calculation
     */
    public AverageTrueRangeTrailingStopGainRule(final BarSeries series, final Indicator<Num> referencePrice,
            final int atrBarCount, final Number atrCoefficient, int barCount) {
        super(referencePrice, createStopGainThreshold(series, atrBarCount, atrCoefficient), barCount);
    }

    /**
     * Constructor with custom reference price and default bar count.
     *
     * @param series         the bar series
     * @param referencePrice the reference price indicator
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeTrailingStopGainRule(final BarSeries series, final Indicator<Num> referencePrice,
            final int atrBarCount, final Number atrCoefficient) {
        this(series, referencePrice, atrBarCount, atrCoefficient, Integer.MAX_VALUE);
    }

    /**
     * Constructor with custom reference price and ATR indicator.
     *
     * @param referencePrice the reference price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the coefficient to multiply ATR
     * @since 0.22.2
     */
    public AverageTrueRangeTrailingStopGainRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient) {
        this(referencePrice, atrIndicator, atrCoefficient, Integer.MAX_VALUE);
    }

    /**
     * Constructor with custom reference price, ATR indicator, and lookback.
     *
     * @param referencePrice the reference price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the coefficient to multiply ATR
     * @param barCount       the number of bars to look back for trailing
     *                       calculation
     * @since 0.22.2
     */
    public AverageTrueRangeTrailingStopGainRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient, int barCount) {
        super(referencePrice, BinaryOperationIndicator.product(requireNonNull(atrIndicator), atrCoefficient), barCount);
    }

    private static Indicator<Num> createStopGainThreshold(BarSeries series, int atrBarCount, Number atrCoefficient) {
        return BinaryOperationIndicator.product(new ATRIndicator(series, atrBarCount), atrCoefficient);
    }
}
