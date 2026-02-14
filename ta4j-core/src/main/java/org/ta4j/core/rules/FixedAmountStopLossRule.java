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
 * A fixed-amount stop-loss rule.
 *
 * <p>
 * Satisfied when the price reaches a fixed absolute distance from the entry
 * price. This models flat-dollar stops (for example, "$5 below entry").
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * <p>
 * See
 * <a href="https://www.investopedia.com/terms/s/stop-lossorder.asp">Stop-loss
 * orders</a> for background on stop-loss concepts.
 *
 * @since 0.22.2
 */
public class FixedAmountStopLossRule extends AbstractRule implements StopLossPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The absolute loss amount. */
    private final Num lossAmount;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param lossAmount     the absolute loss amount
     */
    public FixedAmountStopLossRule(Indicator<Num> priceIndicator, Number lossAmount) {
        this(priceIndicator, toNumLossAmount(priceIndicator, lossAmount));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param lossAmount     the absolute loss amount
     */
    public FixedAmountStopLossRule(Indicator<Num> priceIndicator, Num lossAmount) {
        if (priceIndicator == null) {
            throw new IllegalArgumentException("priceIndicator must not be null");
        }
        if (Num.isNaNOrNull(lossAmount) || lossAmount.isZero() || lossAmount.isNegative()) {
            throw new IllegalArgumentException("lossAmount must be positive");
        }
        this.priceIndicator = priceIndicator;
        this.lossAmount = lossAmount;
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        if (tradingRecord != null) {
            var currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {
                Num entryPrice = currentPosition.getEntry().getNetPrice();
                Num currentPrice = priceIndicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = currentPrice
                            .isLessThanOrEqual(StopLossRule.stopLossPriceFromDistance(entryPrice, lossAmount, true));
                } else {
                    satisfied = currentPrice.isGreaterThanOrEqual(
                            StopLossRule.stopLossPriceFromDistance(entryPrice, lossAmount, false));
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /**
     * Returns the stop-loss price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-loss price, or {@code null} if unavailable
     * @since 0.22.2
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
        return StopLossRule.stopLossPriceFromDistance(entryPrice, lossAmount, position.getEntry().isBuy());
    }

    @Override
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}. Current price: {}", getTraceDisplayName(), index, isSatisfied,
                    priceIndicator.getValue(index));
        }
    }

    private static Num toNumLossAmount(Indicator<Num> priceIndicator, Number lossAmount) {
        if (priceIndicator == null) {
            throw new IllegalArgumentException("priceIndicator must not be null");
        }
        if (lossAmount == null) {
            throw new IllegalArgumentException("lossAmount must be positive");
        }
        return priceIndicator.getBarSeries().numFactory().numOf(lossAmount);
    }
}
