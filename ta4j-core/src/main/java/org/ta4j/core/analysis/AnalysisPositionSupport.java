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
     * @param beginIndex     first absolute index of the curve
     * @param bufferEndIndex last absolute index materialized; record-driven curves
     *                       pad through the series end so later indices carry the
     *                       final value forward
     * @param endIndex       last absolute index of the analysis window: the
     *                       record's logical end or requested final index; below
     *                       {@code beginIndex} when empty
     * @param seriesEndIndex the logical series end: no position is priced after it,
     *                       so bars beyond the window never reach a curve
     * @param finalIndex     index open positions are marked through
     */
    record Window(int beginIndex, int bufferEndIndex, int endIndex, int seriesEndIndex, int finalIndex) {

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
     * @param useRecordEnd   use the record's logical end as final index
     * @param useSeriesEnd   use the logical series end as final index
     * @param padToSeriesEnd materialize through the series end even when the
     *                       analysis window ends earlier
     */
    static Window captureWindow(BarSeries series, TradingRecord record, int startIndex, int requestedFinal,
            boolean useRecordEnd, boolean useSeriesEnd, boolean padToSeriesEnd) {
        int seriesEndIndex = series.getEndIndex();
        int finalIndex = useRecordEnd ? record.getEndIndex(series) : useSeriesEnd ? seriesEndIndex : requestedFinal;
        int beginIndex = Math.max(Math.max(0, startIndex), series.getBeginIndex());
        int requestedEnd = padToSeriesEnd ? Math.max(seriesEndIndex, finalIndex) : finalIndex;
        int bufferEndIndex = Math.min(requestedEnd, seriesEndIndex);
        if (bufferEndIndex < beginIndex) {
            return new Window(beginIndex, beginIndex - 1, beginIndex - 1, seriesEndIndex, finalIndex);
        }
        return new Window(beginIndex, bufferEndIndex, Math.min(finalIndex, bufferEndIndex), seriesEndIndex, finalIndex);
    }

    /**
     * Allocates the value buffer for a captured window.
     */
    static OffsetNumBuffer buffer(Window window, Num initialValue, Num neutral) {
        return window.isEmpty() ? OffsetNumBuffer.empty(neutral)
                : new OffsetNumBuffer(window.beginIndex(), window.bufferEndIndex(), initialValue, neutral);
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
