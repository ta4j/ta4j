/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

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
 */
public class AverageTrueRangeStopLossRule extends AbstractRule {

    /**
     * The ATR-based stop loss threshold.
     */
    private final transient Indicator<Num> stopLossThreshold;

    /**
     * The reference price indicator.
     */
    private final Indicator<Num> referencePrice;
    private final int atrBarCount;
    private final Number atrCoefficient;

    /**
     * Constructor with default close price as reference.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeStopLossRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(series, new ClosePriceIndicator(series), atrBarCount, atrCoefficient);
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
        this.referencePrice = referencePrice;
        this.atrBarCount = atrBarCount;
        this.atrCoefficient = atrCoefficient;
        this.stopLossThreshold = createStopLossThreshold(series);
    }

    /**
     * Checks if the stop loss condition is satisfied.
     *
     * <p>
     * For long positions: satisfied when the reference price is less than the
     * current trade's entry price (net of fees) minus the ATR-based stop loss
     * threshold. For short positions: satisfied when the reference price is greater
     * than the current trade's entry price (net of fees) plus the ATR-based stop
     * loss threshold.
     *
     * <p>
     * This rule uses the {@code tradingRecord}.
     *
     * @param index         the current bar index
     * @param tradingRecord the trading record
     * @return true if the stop loss condition is satisfied, false otherwise
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord != null && !tradingRecord.isClosed()) {
            Num entryPrice = tradingRecord.getCurrentPosition().getEntry().getNetPrice();
            Num currentPrice = this.referencePrice.getValue(index);
            Num threshold = this.stopLossThreshold.getValue(index);

            if (tradingRecord.getCurrentPosition().getEntry().isBuy()) {
                return currentPrice.isLessThan(entryPrice.minus(threshold));
            } else {
                return currentPrice.isGreaterThan(entryPrice.plus(threshold));
            }
        }
        return false;
    }

    private Indicator<Num> createStopLossThreshold(BarSeries series) {
        return BinaryOperationIndicator.product(new ATRIndicator(series, atrBarCount), atrCoefficient);
    }
}
