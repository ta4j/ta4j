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
 * Shared stop-gain logic for volatility-based stop rules.
 *
 * @since 0.22.3
 */
abstract class BaseVolatilityStopGainRule extends AbstractRule implements StopGainPriceModel {

    private final Indicator<Num> referencePrice;
    private final Indicator<Num> stopGainThreshold;

    protected BaseVolatilityStopGainRule(Indicator<Num> referencePrice, Indicator<Num> stopGainThreshold) {
        if (referencePrice == null) {
            throw new IllegalArgumentException("referencePrice must not be null");
        }
        if (stopGainThreshold == null) {
            throw new IllegalArgumentException("stopGainThreshold must not be null");
        }
        this.referencePrice = referencePrice;
        this.stopGainThreshold = stopGainThreshold;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord == null) {
            StopRuleTrace.traceUnavailable(this, index, "noTradingRecord");
            return false;
        }
        Position position = tradingRecord.getCurrentPosition();
        if (!position.isOpened()) {
            StopRuleTrace.traceUnavailable(this, index, "noOpenPosition");
            return false;
        }

        Num entryPrice = position.getEntry().getNetPrice();
        Num currentPrice = referencePrice.getValue(index);
        Num threshold = stopGainThreshold.getValue(index);
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(currentPrice) || Num.isNaNOrNull(threshold)) {
            StopRuleTrace.traceUnavailable(this, index, "nanInput");
            return false;
        }

        boolean buy = position.getEntry().isBuy();
        Num stopPrice = StopGainRule.stopGainPriceFromDistance(entryPrice, threshold, buy);
        boolean satisfied = buy ? currentPrice.isGreaterThanOrEqual(stopPrice)
                : currentPrice.isLessThanOrEqual(stopPrice);
        String reason = satisfied ? "stopReached" : buy ? "priceBelowStop" : "priceAboveStop";
        StopRuleTrace.traceDecision(this, index, satisfied, buy, currentPrice, entryPrice, stopPrice, "gainAmount",
                threshold, reason);
        return satisfied;
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
        Num threshold = stopGainThreshold.getValue(position.getEntry().getIndex());
        if (Num.isNaNOrNull(threshold)) {
            return null;
        }
        return StopGainRule.stopGainPriceFromDistance(entryPrice, threshold, position.getEntry().isBuy());
    }
}
