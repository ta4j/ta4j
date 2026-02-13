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
 * @since 0.22.2
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
        boolean satisfied = false;
        if (tradingRecord != null) {
            var currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {
                Num entryPrice = currentPosition.getEntry().getNetPrice();
                Num currentPrice = priceIndicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = currentPrice
                            .isGreaterThanOrEqual(StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, true));
                } else {
                    satisfied = currentPrice
                            .isLessThanOrEqual(StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, false));
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /**
     * Returns the stop-gain price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-gain price, or {@code null} if unavailable
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
        return StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, position.getEntry().isBuy());
    }

    @Override
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}. Current price: {}", getTraceDisplayName(), index, isSatisfied,
                    priceIndicator.getValue(index));
        }
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
