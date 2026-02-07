/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
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
 */
public class AverageTrueRangeStopGainRule extends AbstractRule {

    /**
     * The ATR indicator pre-multiplied with the multiple to give the gain threshold
     */
    private final transient Indicator<Num> stopGainThreshold;
    private final Indicator<Num> referencePrice;
    private final ATRIndicator atrIndicator;
    private final Number atrCoefficient;

    /**
     * Constructor defaulting the reference price to the close price
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(series, new ClosePriceIndicator(series), atrBarCount, atrCoefficient);
    }

    /**
     * Constructor with custom price indicator.
     *
     * @param series         the bar series
     * @param referencePrice the price indicator
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(final BarSeries series, final Indicator<Num> referencePrice,
            final int atrBarCount, final Number atrCoefficient) {
        this(referencePrice, new ATRIndicator(series, atrBarCount), atrCoefficient);
    }

    /**
     * Constructor with custom price and ATR indicators.
     *
     * @param referencePrice the price indicator
     * @param atrIndicator   ATR indicator
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(final Indicator<Num> referencePrice, final ATRIndicator atrIndicator,
            final Number atrCoefficient) {
        this.referencePrice = requireNonNull(referencePrice);
        this.atrIndicator = requireNonNull(atrIndicator);
        this.atrCoefficient = requireNonNull(atrCoefficient);
        this.stopGainThreshold = createStopGainThreshold();
    }

    /**
     * This rule uses the {@code tradingRecord}.
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        // No trading history
        if (tradingRecord == null) {
            return false;
        }
        // No position opened, no gain
        Position currentPosition = tradingRecord.getCurrentPosition();
        if (!currentPosition.isOpened()) {
            return false;
        }

        Num entryPrice = currentPosition.getEntry().getNetPrice();
        Num currentPrice = referencePrice.getValue(index);
        Num gainThreshold = stopGainThreshold.getValue(index);

        return currentPosition.getEntry().isBuy() ? currentPrice.isGreaterThanOrEqual(entryPrice.plus(gainThreshold))
                : currentPrice.isLessThanOrEqual(entryPrice.minus(gainThreshold));
    }

    private Indicator<Num> createStopGainThreshold() {
        return BinaryOperationIndicator.product(atrIndicator, atrCoefficient);
    }
}
