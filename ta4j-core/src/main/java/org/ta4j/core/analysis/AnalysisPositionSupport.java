/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class AnalysisPositionSupport {

    private AnalysisPositionSupport() {
    }

    /**
     * Bounds a curve captures once, under the series read scope, when it
     * materializes.
     *
     * @param beginIndex          first absolute index of the curve
     * @param bufferEndIndex      last absolute index materialized; record-driven
     *                            curves pad through the series end so later indices
     *                            carry the final value forward
     * @param endIndex            last absolute index of the analysis window: the
     *                            record's logical end or requested final index,
     *                            extended to a trailing exit beyond the logical
     *                            series end; below {@code beginIndex} when empty
     * @param addressableEndIndex last raw index positions may be priced at
     * @param finalIndex          index open positions are marked through
     */
    record Window(int beginIndex, int bufferEndIndex, int endIndex, int addressableEndIndex, int finalIndex) {

        boolean isEmpty() {
            return bufferEndIndex < beginIndex;
        }
    }

    /** A captured window paired with the values materialized over it. */
    record Curve(Window window, OffsetNumBuffer values) {
    }

    /**
     * Captures the window a curve covers. Must run inside the series read scope so
     * every bound describes one state of the series.
     *
     * @param series         the analysed series
     * @param record         the trading record
     * @param startIndex     requested first index, clamped to the series begin
     * @param requestedFinal final index used unless a record or series end is
     *                       requested
     * @param useRecordEnd   derive the final index from the record and its trailing
     *                       exits
     * @param useSeriesEnd   use the logical series end as final index
     * @param padToSeriesEnd materialize through the series end even when the
     *                       analysis window ends earlier
     */
    static Window captureWindow(BarSeries series, TradingRecord record, int startIndex, int requestedFinal,
            boolean useRecordEnd, boolean useSeriesEnd, boolean padToSeriesEnd) {
        int addressableEndIndex = addressableEndIndex(series);
        int finalIndex = useRecordEnd ? analysisEndIndex(series, record, addressableEndIndex)
                : useSeriesEnd ? series.getEndIndex() : requestedFinal;
        int beginIndex = Math.max(Math.max(0, startIndex), series.getBeginIndex());
        int requestedEnd = padToSeriesEnd ? Math.max(series.getEndIndex(), finalIndex) : finalIndex;
        if (beginIndex > addressableEndIndex || requestedEnd < beginIndex) {
            return new Window(beginIndex, beginIndex - 1, beginIndex - 1, addressableEndIndex, finalIndex);
        }
        int bufferEndIndex = Math.min(requestedEnd, addressableEndIndex);
        return new Window(beginIndex, bufferEndIndex, Math.min(finalIndex, bufferEndIndex), addressableEndIndex,
                finalIndex);
    }

    /**
     * Allocates the value buffer for a captured window.
     */
    static OffsetNumBuffer buffer(Window window, Num initialValue, Num neutral) {
        return window.isEmpty() ? OffsetNumBuffer.empty(neutral)
                : new OffsetNumBuffer(window.beginIndex(), window.bufferEndIndex(), initialValue, neutral);
    }

    /**
     * Returns the last index addressable in the series' raw bar storage. For a live
     * series this equals {@link BarSeries#getEndIndex()}; for builder-constrained
     * or rolling-window series it can lie beyond the logical window end, where
     * trailing bars remain readable for analyses that must price exits landing
     * there.
     *
     * @param series the bar series
     * @return the last addressable index, or {@code -1} for an empty series
     */
    static int addressableEndIndex(BarSeries series) {
        int logicalEndIndex = series.getEndIndex();
        List<Bar> rawBars = series.getBarData();
        if (rawBars.isEmpty()) {
            return logicalEndIndex;
        }
        long rawLastIndex = (long) series.getRemovedBarsCount() + rawBars.size() - 1;
        return rawLastIndex > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawLastIndex;
    }

    /** Receives one mark-to-market price of a position. */
    @FunctionalInterface
    interface MarkConsumer {

        /**
         * @param index         the marked bar index
         * @param netPrice      the bar close net of holding cost accrued since entry
         * @param previousPrice the previous mark, or the net entry price for the first
         *                      mark
         */
        void accept(int index, Num netPrice, Num previousPrice);
    }

    /** The exit mark of a position and the mark that preceded it. */
    record ExitMark(Num netPrice, Num previousPrice) {
    }

    /**
     * Marks a position to market at every held close inside the window and prices
     * its exit. Each mark is the close net of holding cost accrued since entry; the
     * exit at {@code endIndex} uses the exit price (or that bar's close for an open
     * position) with the full accrued cost. Marks start at the later of the bar
     * after entry and {@code windowStartIndex}, so an entry predating the window is
     * marked once at the window start against its entry price, and a position that
     * exits on the window start is only priced by its exit.
     *
     * @param curve            the curve supplying cost conventions
     * @param series           the analysed series
     * @param position         the position, with an entry
     * @param endIndex         the exit or final marked index
     * @param windowStartIndex first index that may be marked
     * @param lastMarkIndex    last index that may be marked before the exit
     * @param marks            receives each intermediate mark in index order
     * @return the exit mark
     */
    static ExitMark markToMarket(PerformanceIndicator curve, BarSeries series, Position position, int endIndex,
            int windowStartIndex, int lastMarkIndex, MarkConsumer marks) {
        NumFactory numFactory = series.numFactory();
        Trade entry = position.getEntry();
        boolean isLong = entry.isBuy();
        int entryIndex = entry.getIndex();
        Num costPerPeriod = curve.averageHoldingCostPerPeriod(position, endIndex, numFactory);
        Num previousPrice = entry.getNetPrice();
        long firstMarkIndex = Math.max((long) entryIndex + 1L, windowStartIndex);
        for (long index = firstMarkIndex; index < endIndex && index <= lastMarkIndex; index++) {
            Num accruedCost = costPerPeriod.multipliedBy(numFactory.numOf(index - entryIndex));
            Num netPrice = curve.addCost(series.getBar((int) index).getClosePrice(), accruedCost, isLong);
            marks.accept((int) index, netPrice, previousPrice);
            previousPrice = netPrice;
        }
        Num accruedExitCost = costPerPeriod.multipliedBy(numFactory.numOf((long) endIndex - entryIndex));
        Num netExitPrice = curve.addCost(curve.resolveExitPrice(position, endIndex, series), accruedExitCost, isLong);
        return new ExitMark(netExitPrice, previousPrice);
    }

    /**
     * Extends only materialized analysis windows to actual position activity in
     * addressable raw storage; logical execution and benchmark bounds stay
     * unchanged.
     */
    static int analysisEndIndex(BarSeries series, TradingRecord record, int addressableEndIndex) {
        int logicalEndIndex = series.getEndIndex();
        int endIndex = record.getEndIndex(series);
        if (addressableEndIndex <= logicalEndIndex) {
            return endIndex;
        }
        boolean unboundedRecord = record.getEndIndex() == null;
        for (Position position : record.getPositions()) {
            Trade activity = position.getExit();
            if (activity == null && unboundedRecord) {
                activity = position.getEntry();
            }
            if (activity != null && activity.getIndex() > logicalEndIndex
                    && activity.getIndex() <= addressableEndIndex) {
                endIndex = Math.max(endIndex, activity.getIndex());
            }
        }
        return endIndex;
    }

    static List<Position> positionsForAnalysis(TradingRecord record, int finalIndex,
            OpenPositionHandling openPositionHandling, EquityCurveMode equityCurveMode) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling");
        Objects.requireNonNull(equityCurveMode, "equityCurveMode");
        List<Position> positions = new ArrayList<>();
        for (Position position : record.getPositions()) {
            if (shouldIncludePosition(position, finalIndex, openPositionHandling, equityCurveMode)) {
                positions.add(position);
            }
        }
        if (shouldIncludeOpenPositions(openPositionHandling, equityCurveMode)) {
            positions.addAll(openPositions(record, finalIndex));
        }
        return positions;
    }

    static boolean shouldIncludePosition(Position position, int finalIndex, OpenPositionHandling openPositionHandling,
            EquityCurveMode equityCurveMode) {
        if (position == null || position.getEntry() == null) {
            return false;
        }
        int entryIndex = position.getEntry().getIndex();
        if (entryIndex > finalIndex) {
            return false;
        }
        if (!shouldIncludeOpenPositions(openPositionHandling, equityCurveMode)) {
            Trade exit = position.getExit();
            boolean isOpenAtFinalIndex = exit == null || exit.getIndex() > finalIndex;
            return !isOpenAtFinalIndex;
        }
        return true;
    }

    static boolean shouldIncludeOpenPositions(OpenPositionHandling openPositionHandling,
            EquityCurveMode equityCurveMode) {
        return equityCurveMode != EquityCurveMode.REALIZED
                && openPositionHandling == OpenPositionHandling.MARK_TO_MARKET;
    }

    static List<Position> openPositions(TradingRecord record, int finalIndex) {
        List<Position> openPositions = record.getOpenPositions();
        if (!openPositions.isEmpty()) {
            return openPositionsWithinRange(openPositions, finalIndex);
        }
        List<Position> positions = new ArrayList<>();
        Position current = record.getCurrentPosition();
        if (current != null && current.isOpened() && current.getEntry() != null
                && current.getEntry().getIndex() <= finalIndex) {
            positions.add(current);
        }
        return positions;
    }

    private static List<Position> openPositionsWithinRange(List<Position> openPositions, int finalIndex) {
        List<Position> positions = new ArrayList<>();
        for (Position openPosition : openPositions) {
            if (openPosition == null || !openPosition.isOpened()) {
                continue;
            }
            if (openPosition.getEntry().getIndex() > finalIndex) {
                continue;
            }
            positions.add(openPosition);
        }
        return positions;
    }
}
