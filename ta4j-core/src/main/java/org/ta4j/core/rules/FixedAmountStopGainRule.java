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
 * A fixed-amount stop-gain rule.
 *
 * <p>
 * Satisfied when the price reaches a fixed absolute gain distance from entry.
 * This models flat-dollar profit targets (for example, "$5 above entry").
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.3
 */
public class FixedAmountStopGainRule extends AbstractRule implements StopGainPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The absolute gain amount. */
    private final Num gainAmount;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param gainAmount     the absolute gain amount
     */
    public FixedAmountStopGainRule(Indicator<Num> priceIndicator, Number gainAmount) {
        this(priceIndicator, toNumGainAmount(priceIndicator, gainAmount));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param gainAmount     the absolute gain amount
     */
    public FixedAmountStopGainRule(Indicator<Num> priceIndicator, Num gainAmount) {
        if (priceIndicator == null) {
            throw new IllegalArgumentException("priceIndicator must not be null");
        }
        if (Num.isNaNOrNull(gainAmount) || gainAmount.isZero() || gainAmount.isNegative()) {
            throw new IllegalArgumentException("gainAmount must be positive");
        }
        this.priceIndicator = priceIndicator;
        this.gainAmount = gainAmount;
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord == null) {
            StopRuleTrace.traceUnavailable(this, index, "noTradingRecord");
            return false;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        if (!currentPosition.isOpened()) {
            StopRuleTrace.traceUnavailable(this, index, "noOpenPosition");
            return false;
        }

        Num entryPrice = currentPosition.getEntry().getNetPrice();
        Num currentPrice = priceIndicator.getValue(index);
        boolean buy = currentPosition.getEntry().isBuy();
        Num stopPrice = StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, buy);
        boolean satisfied = buy ? currentPrice.isGreaterThanOrEqual(stopPrice)
                : currentPrice.isLessThanOrEqual(stopPrice);
        String reason = satisfied ? "stopReached" : buy ? "priceBelowStop" : "priceAboveStop";
        StopRuleTrace.traceDecision(this, index, satisfied, buy, currentPrice, entryPrice, stopPrice, "gainAmount",
                gainAmount, reason);
        return satisfied;
    }

    /**
     * Returns the stop-gain price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-gain price, or {@code null} if unavailable
     * @since 0.22.3
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
        return StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, position.getEntry().isBuy());
    }

    private static Num toNumGainAmount(Indicator<Num> priceIndicator, Number gainAmount) {
        if (priceIndicator == null) {
            throw new IllegalArgumentException("priceIndicator must not be null");
        }
        if (gainAmount == null) {
            throw new IllegalArgumentException("gainAmount must be positive");
        }
        return priceIndicator.getBarSeries().numFactory().numOf(gainAmount);
    }
}
