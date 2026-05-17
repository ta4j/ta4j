/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule.
 *
 * <p>
 * Satisfied when the price reaches the trailing loss threshold.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class TrailingStopLossRule extends AbstractRule implements StopLossPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The barCount. */
    private final int barCount;

    /** the loss-distance as percentage. */
    private final Num lossPercentage;

    /**
     * Constructor.
     *
     * @param indicator      the (close price) indicator
     * @param lossPercentage the loss percentage
     * @param barCount       the number of bars to look back for the calculation
     */
    public TrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage, int barCount) {
        this.priceIndicator = Objects.requireNonNull(indicator, "priceIndicator");
        if (Num.isNaNOrNull(lossPercentage) || lossPercentage.isNegative()) {
            throw new IllegalArgumentException("lossPercentage must be >= 0");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        this.barCount = barCount;
        this.lossPercentage = lossPercentage;
    }

    /**
     * Constructor.
     *
     * @param indicator      the (close price) indicator
     * @param lossPercentage the loss percentage
     */
    public TrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage) {
        this(indicator, lossPercentage, Integer.MAX_VALUE);
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition != null && currentPosition.isOpened() && currentPosition.getEntry() != null) {
                Num currentPrice = priceIndicator.getValue(index);
                int positionIndex = currentPosition.getEntry().getIndex();

                if (index < positionIndex || Num.isNaNOrNull(currentPrice)) {
                    traceIsSatisfied(index, false);
                    return false;
                }

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuySatisfied(currentPrice, index, positionIndex);
                } else {
                    satisfied = isSellSatisfied(currentPrice, index, positionIndex);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuySatisfied(Num currentPrice, int index, int positionIndex) {
        Num highestCloseNum = highestValue(windowStartIndex(index, positionIndex), index);
        if (Num.isNaNOrNull(highestCloseNum)) {
            return false;
        }
        Num currentStopLossLimitActivation = StopLossRule.stopLossPrice(highestCloseNum, lossPercentage, true);
        return currentPrice.isLessThanOrEqual(currentStopLossLimitActivation);
    }

    private boolean isSellSatisfied(Num currentPrice, int index, int positionIndex) {
        Num lowestCloseNum = lowestValue(windowStartIndex(index, positionIndex), index);
        if (Num.isNaNOrNull(lowestCloseNum)) {
            return false;
        }
        Num currentStopLossLimitActivation = StopLossRule.stopLossPrice(lowestCloseNum, lossPercentage, false);
        return currentPrice.isGreaterThanOrEqual(currentStopLossLimitActivation);
    }

    /**
     * Returns the stop-loss price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-loss price, or {@code null} if unavailable
     * @since 0.22.3
     */
    @Override
    public Num stopPrice(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return null;
        }
        // If the entry bar was evicted by max-bar-count retention, fall back to
        // the first retained bar as the anchor (an approximation of entry-time stop).
        int entryIndex = retainedStartIndex(position.getEntry().getIndex());
        if (position.getEntry().isBuy()) {
            Num highestCloseNum = priceIndicator.getValue(entryIndex);
            if (Num.isNaNOrNull(highestCloseNum)) {
                return null;
            }
            return StopLossRule.stopLossPrice(highestCloseNum, lossPercentage, true);
        }
        Num lowestCloseNum = priceIndicator.getValue(entryIndex);
        if (Num.isNaNOrNull(lowestCloseNum)) {
            return null;
        }
        return StopLossRule.stopLossPrice(lowestCloseNum, lossPercentage, false);
    }

    private int windowStartIndex(int index, int positionIndex) {
        int activeBarCount = Math.min(index - positionIndex + 1, barCount);
        return retainedStartIndex(index - activeBarCount + 1);
    }

    private int retainedStartIndex(int requestedStartIndex) {
        return Math.max(requestedStartIndex, priceIndicator.getBarSeries().getBeginIndex());
    }

    private Num highestValue(int startIndex, int endIndex) {
        Num highest = null;
        for (int index = startIndex; index <= endIndex; index++) {
            Num candidate = priceIndicator.getValue(index);
            if (!Num.isNaNOrNull(candidate) && (highest == null || candidate.isGreaterThan(highest))) {
                highest = candidate;
            }
        }
        return highest;
    }

    private Num lowestValue(int startIndex, int endIndex) {
        Num lowest = null;
        for (int index = startIndex; index <= endIndex; index++) {
            Num candidate = priceIndicator.getValue(index);
            if (!Num.isNaNOrNull(candidate) && (lowest == null || candidate.isLessThan(lowest))) {
                lowest = candidate;
            }
        }
        return lowest;
    }

    @Override
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}. Current price: {}", getTraceDisplayName(), index, isSatisfied,
                    priceIndicator.getValue(index));
        }
    }
}
