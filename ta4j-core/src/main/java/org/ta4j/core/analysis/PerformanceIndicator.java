/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared contract for performance indicators derived from trading records.
 *
 * @since 0.22.2
 */
public interface PerformanceIndicator extends Indicator<Num> {

    /**
     * Returns the equity curve mode that influences open position handling.
     *
     * @return the equity curve mode
     * @since 0.22.2
     */
    EquityCurveMode getEquityCurveMode();

    /**
     * Calculates indicator values for a single position.
     *
     * @param position   the position
     * @param finalIndex index up until values of open positions are considered
     * @since 0.22.2
     */
    void calculatePosition(Position position, int finalIndex);

    /**
     * Calculates indicator values based on the provided trading record.
     *
     * @param tradingRecord        the trading record
     * @param finalIndex           index up until values of open positions are
     *                             considered
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    default void calculate(TradingRecord tradingRecord, int finalIndex, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(tradingRecord);
        Objects.requireNonNull(openPositionHandling);
        var effectiveOpenPositionHandling = getEffectiveOpenPositionHandling(openPositionHandling);
        var positions = AnalysisPositionSupport.positionsForAnalysis(tradingRecord, finalIndex,
                effectiveOpenPositionHandling, getEquityCurveMode());
        positions.forEach(position -> calculatePosition(position, finalIndex));
    }

    /**
     * Derives the open-position handling from the equity curve mode to keep
     * realized-only curves from leaking unrealized P&amp;L into the calculation.
     *
     * <p>
     * When the equity curve is realized-only, we force
     * {@link OpenPositionHandling#IGNORE} regardless of the caller preference. For
     * all other modes we defer to the requested handling so callers can opt into
     * mark-to-market behavior.
     * </p>
     *
     * @param openPositionHandling the requested handling for open positions
     * @return the effective handling aligned with the equity curve mode
     */
    private OpenPositionHandling getEffectiveOpenPositionHandling(OpenPositionHandling openPositionHandling) {
        return getEquityCurveMode() == EquityCurveMode.REALIZED ? OpenPositionHandling.IGNORE : openPositionHandling;
    }

    /**
     * Determines the valid final index to be considered.
     *
     * @param position   the position
     * @param finalIndex index up until cash flows of open positions are considered
     * @param maxIndex   maximal valid index
     * @since 0.22.2
     */
    default int determineEndIndex(Position position, int finalIndex, int maxIndex) {
        var idx = finalIndex;
        // After closing of position, no further accrual necessary
        if (position.getExit() != null) {
            idx = Math.min(position.getExit().getIndex(), finalIndex);
        }
        // Accrual at most until maximal index of asset data
        if (idx > maxIndex) {
            idx = maxIndex;
        }
        return idx;
    }

    /**
     * Adjusts (intermediate) price to incorporate trading costs.
     *
     * @param rawPrice    the gross asset price
     * @param holdingCost share of the holding cost per period
     * @param isLongTrade true, if the entry trade type is BUY
     * @since 0.22.2
     */
    default Num addCost(Num rawPrice, Num holdingCost, boolean isLongTrade) {
        if (isLongTrade) {
            return rawPrice.minus(holdingCost);
        } else {
            return rawPrice.plus(holdingCost);
        }
    }

    /**
     * Computes the average holding cost per period for the given position.
     *
     * @param position   the position
     * @param endIndex   index up until cash flows of open positions are considered
     * @param numFactory the {@link Num} factory
     * @return the average holding cost per period, or zero when no periods elapsed
     * @since 0.22.2
     */
    default Num averageHoldingCostPerPeriod(Position position, int endIndex, NumFactory numFactory) {
        var periods = Math.max(0, endIndex - position.getEntry().getIndex());
        if (periods == 0) {
            return numFactory.zero();
        }
        var holdingCost = position.getHoldingCost(endIndex);
        return holdingCost.dividedBy(numFactory.numOf(periods));
    }

    /**
     * Resolves the exit price for a position at the given end index.
     *
     * @param position the position
     * @param endIndex index up until values of open positions are considered
     * @param series   the bar series
     * @return the exit price if an exit exists within the end index, otherwise the
     *         bar close
     * @since 0.22.2
     */
    default Num resolveExitPrice(Position position, int endIndex, BarSeries series) {
        var exit = position.getExit();
        if (exit != null && exit.getIndex() <= endIndex) {
            return exit.getNetPrice();
        }
        return series.getBar(endIndex).getClosePrice();
    }

    /**
     * Pads a list up to and including the specified end index using the provided
     * pad value.
     *
     * @param values   the list to pad
     * @param endIndex the last required index
     * @param padValue the value to append while padding
     * @since 0.22.2
     */
    default void padToEndIndex(List<Num> values, int endIndex, Num padValue) {
        if (endIndex >= values.size()) {
            padToSize(values, endIndex + 1, padValue);
        }
    }

    /**
     * Pads a list up to the required size using the provided pad value.
     *
     * @param values       the list to pad
     * @param requiredSize the required list size
     * @param padValue     the value to append while padding
     * @since 0.22.2
     */
    default void padToSize(List<Num> values, int requiredSize, Num padValue) {
        if (requiredSize > values.size()) {
            values.addAll(Collections.nCopies(requiredSize - values.size(), padValue));
        }
    }

}
