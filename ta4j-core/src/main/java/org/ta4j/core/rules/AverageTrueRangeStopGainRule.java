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
 * An ATR-based stop-gain rule.
 *
 * <p>
 * Satisfied when a reference price (by default the close price) reaches the
 * gain threshold defined by a multiple of the Average True Range (ATR).
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.3
 */
public class AverageTrueRangeStopGainRule extends BaseVolatilityStopGainRule {

    private final Indicator<Num> referencePrice;
    private final ATRIndicator atrIndicator;
    private final Number atrCoefficient;

    /**
     * Constructor defaulting the reference price to the close price.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(new ClosePriceIndicator(series), new ATRIndicator(series, atrBarCount), atrCoefficient);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(final BarSeries series, final Indicator<Num> referencePrice,
            final int atrBarCount, final Number atrCoefficient) {
        this(referencePrice, new ATRIndicator(series, atrBarCount), atrCoefficient);
    }

    /**
     * Constructor with custom reference price and ATR indicator.
     *
     * @param referencePrice the reference price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     * @since 0.22.3
     */
    public AverageTrueRangeStopGainRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient) {
        this(new Config(referencePrice, requireNonNull(atrIndicator), atrCoefficient));
    }

    private AverageTrueRangeStopGainRule(Config config) {
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
