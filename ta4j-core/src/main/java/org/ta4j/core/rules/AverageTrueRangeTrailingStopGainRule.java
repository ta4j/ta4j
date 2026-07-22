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
 * @since 0.22.3
 */
public class AverageTrueRangeTrailingStopGainRule extends BaseVolatilityTrailingStopGainRule {

    private final Indicator<Num> referencePrice;
    private final ATRIndicator atrIndicator;
    private final Number atrCoefficient;
    private final int barCount;

    /**
     * Constructor with default close price as reference.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeTrailingStopGainRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(validatedConfig(series, atrBarCount, atrCoefficient, Integer.MAX_VALUE));
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
        this(validatedConfig(referencePrice, series, atrBarCount, atrCoefficient, barCount));
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
        this(validatedConfig(referencePrice, series, atrBarCount, atrCoefficient, Integer.MAX_VALUE));
    }

    /**
     * Constructor with custom reference price and ATR indicator.
     *
     * @param referencePrice the reference price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the coefficient to multiply ATR
     * @since 0.22.3
     */
    public AverageTrueRangeTrailingStopGainRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient) {
        this(validatedConfig(referencePrice, atrIndicator, atrCoefficient, Integer.MAX_VALUE));
    }

    /**
     * Constructor with custom reference price, ATR indicator, and lookback.
     *
     * @param referencePrice the reference price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the coefficient to multiply ATR
     * @param barCount       the number of bars to look back for trailing
     *                       calculation
     * @since 0.22.3
     */
    public AverageTrueRangeTrailingStopGainRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient, int barCount) {
        this(validatedConfig(referencePrice, atrIndicator, atrCoefficient, barCount));
    }

    private AverageTrueRangeTrailingStopGainRule(Config config) {
        super(config.referencePrice(), config.stopGainThreshold(), config.barCount());
        this.referencePrice = config.referencePrice();
        this.atrIndicator = config.atrIndicator();
        this.atrCoefficient = config.atrCoefficient();
        this.barCount = config.barCount();
    }

    private static Config validatedConfig(Indicator<Num> referencePrice, ATRIndicator atrIndicator,
            Number atrCoefficient, int barCount) {
        return validatedConfig(referencePrice, atrIndicator, atrCoefficient,
                createStopGainThreshold(atrIndicator, atrCoefficient), barCount);
    }

    private static Config validatedConfig(Indicator<Num> referencePrice, ATRIndicator atrIndicator,
            Number atrCoefficient, Indicator<Num> stopGainThreshold, int barCount) {
        if (referencePrice == null) {
            throw new IllegalArgumentException("referencePrice must not be null");
        }
        return new Config(referencePrice, atrIndicator, atrCoefficient, stopGainThreshold, barCount);
    }

    private static Config validatedConfig(BarSeries series, int atrBarCount, Number atrCoefficient, int barCount) {
        return validatedConfig(new ClosePriceIndicator(series), series, atrBarCount, atrCoefficient, barCount);
    }

    private static Config validatedConfig(Indicator<Num> referencePrice, BarSeries series, int atrBarCount,
            Number atrCoefficient, int barCount) {
        ATRIndicator atrIndicator = new ATRIndicator(series, atrBarCount);
        return validatedConfig(referencePrice, atrIndicator, atrCoefficient,
                createStopGainThreshold(atrIndicator, atrCoefficient), barCount);
    }

    private static Indicator<Num> createStopGainThreshold(ATRIndicator atrIndicator, Number atrCoefficient) {
        return BinaryOperationIndicator.product(requireNonNull(atrIndicator),
                requirePositiveAtrCoefficient(atrCoefficient));
    }

    private static Number requirePositiveAtrCoefficient(Number atrCoefficient) {
        if (atrCoefficient == null || Double.isNaN(atrCoefficient.doubleValue()) || atrCoefficient.doubleValue() <= 0) {
            throw new IllegalArgumentException("atrCoefficient must be positive");
        }
        return atrCoefficient;
    }

    private record Config(Indicator<Num> referencePrice, ATRIndicator atrIndicator, Number atrCoefficient,
            Indicator<Num> stopGainThreshold, int barCount) {
    }
}
