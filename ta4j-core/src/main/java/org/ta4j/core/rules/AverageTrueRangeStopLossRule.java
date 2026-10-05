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
 * A stop-loss rule based on Average True Range (ATR).
 *
 * <p>
 * This rule is satisfied when the reference price reaches the loss threshold as
 * determined by a given multiple of the prevailing average true range. It can
 * be used for both long and short positions.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.3
 */
public class AverageTrueRangeStopLossRule extends BaseVolatilityStopLossRule {

    private final Indicator<Num> referencePrice;
    private final ATRIndicator atrIndicator;
    private final Number atrCoefficient;

    /**
     * Constructor with default close price as reference.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeStopLossRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(new ClosePriceIndicator(series), new ATRIndicator(series, atrBarCount), atrCoefficient);
    }

    /**
     * Constructor with custom reference price.
     *
     * @param series         the bar series
     * @param referencePrice the reference price indicator
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeStopLossRule(final BarSeries series, final Indicator<Num> referencePrice,
            final int atrBarCount, final Number atrCoefficient) {
        this(referencePrice, new ATRIndicator(series, atrBarCount), atrCoefficient);
    }

    /**
     * Constructor with custom reference price and ATR indicator.
     *
     * @param referencePrice the reference price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the coefficient to multiply ATR
     * @since 0.22.3
     */
    public AverageTrueRangeStopLossRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient) {
        this(new Config(referencePrice, requireNonNull(atrIndicator), atrCoefficient));
    }

    private AverageTrueRangeStopLossRule(Config config) {
        super(config.referencePrice(),
                BinaryOperationIndicator.product(config.atrIndicator(), config.atrCoefficient()));
        this.referencePrice = config.referencePrice();
        this.atrIndicator = config.atrIndicator();
        this.atrCoefficient = config.atrCoefficient();
    }

    private record Config(Indicator<Num> referencePrice, ATRIndicator atrIndicator, Number atrCoefficient) {

        private Config {
            if (atrCoefficient == null || Double.isNaN(atrCoefficient.doubleValue())
                    || atrCoefficient.doubleValue() <= 0) {
                throw new IllegalArgumentException("atrCoefficient must be positive");
            }
        }
    }
}
