/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Shared stop-loss logic for volatility-based stop rules.
 *
 * @since 0.22.3
 */
abstract class BaseVolatilityStopLossRule extends AbstractRule implements StopLossPriceModel {

    private final Indicator<Num> referencePrice;
    private final Indicator<Num> stopLossThreshold;

    /**
     * Constructor.
     *
     * @param referencePrice    reference price indicator
     * @param stopLossThreshold volatility-scaled stop-loss threshold indicator
     */
    protected BaseVolatilityStopLossRule(Indicator<Num> referencePrice, Indicator<Num> stopLossThreshold) {
        if (referencePrice == null) {
            throw new IllegalArgumentException("referencePrice must not be null");
        }
        if (stopLossThreshold == null) {
            throw new IllegalArgumentException("stopLossThreshold must not be null");
        }
        this.referencePrice = referencePrice;
        this.stopLossThreshold = stopLossThreshold;
    }

    /**
     * Evaluates whether the stop-loss condition is satisfied for the current open
     * position.
     *
     * @param index         current bar index
     * @param tradingRecord trading record containing the open position
     * @return {@code true} when stop-loss condition is satisfied
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord != null && !tradingRecord.isClosed()) {
            Position position = tradingRecord.getCurrentPosition();
            if (position.isOpened()) {
                Num entryPrice = position.getEntry().getNetPrice();
                Num currentPrice = referencePrice.getValue(index);
                Num threshold = stopLossThreshold.getValue(index);
                if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(currentPrice) || Num.isNaNOrNull(threshold)) {
                    return false;
                }

                if (position.getEntry().isBuy()) {
                    return currentPrice
                            .isLessThanOrEqual(StopLossRule.stopLossPriceFromDistance(entryPrice, threshold, true));
                }
                return currentPrice
                        .isGreaterThanOrEqual(StopLossRule.stopLossPriceFromDistance(entryPrice, threshold, false));
            }
        }
        return false;
    }

    /**
     * Returns the initial stop-loss price at position entry.
     *
     * @param series   the bar series
     * @param position the position being evaluated
     * @return stop-loss price at entry, or {@code null} if unavailable
     */
    @Override
    public Num stopPrice(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return null;
        }
        Num entryPrice = position.getEntry().getNetPrice();
        if (Num.isNaNOrNull(entryPrice)) {
            return null;
        }
        Num threshold = stopLossThreshold.getValue(position.getEntry().getIndex());
        if (Num.isNaNOrNull(threshold)) {
            return null;
        }
        // stopPrice models the initial stop at entry time, so threshold is read at
        // the entry index rather than the current evaluation index.
        return StopLossRule.stopLossPriceFromDistance(entryPrice, threshold, position.getEntry().isBuy());
    }
}
