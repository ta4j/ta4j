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
 * @since 0.22.2
 */
abstract class BaseVolatilityStopLossRule extends AbstractRule implements StopLossPriceModel {

    private final Indicator<Num> referencePrice;
    private final Indicator<Num> stopLossThreshold;

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
                    return currentPrice.isLessThan(StopLossRule.stopLossPriceFromDistance(entryPrice, threshold, true));
                }
                return currentPrice.isGreaterThan(StopLossRule.stopLossPriceFromDistance(entryPrice, threshold, false));
            }
        }
        return false;
    }

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
        return StopLossRule.stopLossPriceFromDistance(entryPrice, threshold, position.getEntry().isBuy());
    }
}
