/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.ta4j.core.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class AnalysisPositionSupport {

    private AnalysisPositionSupport() {
    }

    /** Bounds and source data captured for one curve materialization. */
    record Window(int beginIndex, int bufferEndIndex, int endIndex, int finalIndex, int carryStartIndex,
            boolean carriesPrunedHistory, BarWindowSnapshot bars) {

        boolean isEmpty() {
            return bufferEndIndex < beginIndex;
        }

        boolean carriesBeforeWindow(Position position) {
            Trade exit = position.getExit();
            return carriesPrunedHistory && exit != null && exit.getIndex() < beginIndex
                    && exit.getIndex() >= carryStartIndex;
        }

        /** Captures the complete bars under the series read scope. */
        Window withBars(BarSeries series) {
            return new Window(beginIndex, bufferEndIndex, endIndex, finalIndex, carryStartIndex,
                    carriesPrunedHistory, BarWindowSnapshot.capture(series, beginIndex, bufferEndIndex));
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
     * @param recordStart    the record's logical start
     * @param recordEnd      the record's logical end, or {@code null} if open-ended
     * @param startIndex     requested first index
     * @param requestedFinal final index used unless a record or series end is
     *                       requested
     * @param useRecordEnd   use the record's logical end, clamped to the series
     *                       end, as final index
     * @param useSeriesEnd   use the logical series end as final index
     * @param padToSeriesEnd materialize through the series end even when the
     *                       analysis window ends earlier
     */
    static Window captureWindow(BarSeries series, Integer recordStart, Integer recordEnd, int startIndex,
            int requestedFinal, boolean useRecordEnd, boolean useSeriesEnd, boolean padToSeriesEnd) {
        int seriesBegin = series.getBeginIndex();
        int seriesEndIndex = series.getEndIndex();
        int recordFinal = recordEnd == null ? seriesEndIndex : Math.min(recordEnd, seriesEndIndex);
        int finalIndex = useRecordEnd ? recordFinal : useSeriesEnd ? seriesEndIndex : requestedFinal;
        int beginIndex = Math.max(Math.max(Math.max(0, startIndex), seriesBegin),
                recordStart == null ? 0 : recordStart);
        int carryStartIndex = Math.max(Math.max(0, startIndex), recordStart == null ? 0 : recordStart);
        boolean carriesPrunedHistory = seriesBegin > 0 && series.getRemovedBarsCount() == seriesBegin;
        int requestedEnd = padToSeriesEnd ? Math.max(seriesEndIndex, finalIndex) : finalIndex;
        int bufferEndIndex = Math.min(requestedEnd, seriesEndIndex);
        if (bufferEndIndex < beginIndex) {
            return new Window(beginIndex, beginIndex - 1, beginIndex - 1, finalIndex, carryStartIndex,
                    carriesPrunedHistory, null);
        }
        return new Window(beginIndex, bufferEndIndex, Math.min(finalIndex, bufferEndIndex), finalIndex,
                carryStartIndex, carriesPrunedHistory, null);
    }

    /**
     * Captures attempted before a curve gives up on a series that keeps evicting
     * its window.
     */
    private static final int MAX_MATERIALIZE_ATTEMPTS = 8;

    /**
     * Builds a curve over a captured window from bar data and precomputed holding
     * costs.
     */
    @FunctionalInterface
    interface CurveBuilder<T> {

        /**
         * Runs inside the series read scope, so it must only read bars and trades.
         *
         * @param window       the captured window
         * @param positions    the positions to analyse
         * @param holdingCosts each analysed position's holding cost through its end
         *                     index, keyed by identity
         * @return the built curve
         */
        T build(Window window, List<Position> positions, Map<Position, Num> holdingCosts);
    }

    /**
     * Materializes a curve without evaluating user code under the series read lock.
     * The record bounds are read first, with no lock held; the window is captured
     * in one short read scope; holding costs are computed unlocked; the curve is
     * built under a second short read scope while every captured bar value is
     * verified. A changed or evicted window is captured again.
     *
     * @throws IllegalStateException if the window was evicted or changed during
     *                               every attempt
     */
    static <T> T materialize(PerformanceIndicator curve, BarSeries series, TradingRecord record, int startIndex,
            int requestedFinal, boolean useRecordEnd, boolean useSeriesEnd, boolean padToSeriesEnd,
            OpenPositionHandling handling, CurveBuilder<T> builder) {
        for (int attempt = 0; attempt < MAX_MATERIALIZE_ATTEMPTS; attempt++) {
            Integer recordStartIndex = record.getStartIndex();
            Integer recordEndIndex = useRecordEnd ? record.getEndIndex() : null;
            Window window = series.withReadLock(() -> captureWindow(series, recordStartIndex, recordEndIndex,
                    startIndex, requestedFinal, useRecordEnd, useSeriesEnd, padToSeriesEnd).withBars(series));
            List<Position> positions = positionsForAnalysis(record, window.finalIndex(), handling,
                    curve.getEquityCurveMode());
            Map<Position, Num> holdingCosts = new IdentityHashMap<>();
            for (Position position : positions) {
                Num holdingCost = holdingCostInWindow(curve, position, window.finalIndex(), window,
                        curve instanceof CashFlow || curve instanceof CumulativePnL);
                if (holdingCost != null) {
                    holdingCosts.put(position, holdingCost);
                }
            }
            T built = series.withReadLock(() -> {
                if (!window.bars().isUnchangedIn(series)) {
                    return null;
                }
                T candidate = builder.build(window, positions, holdingCosts);
                return window.bars().isUnchangedIn(series) ? candidate : null;
            });
            if (built != null) {
                return built;
            }
        }
        throw new IllegalStateException(
                "Bar series '" + series.getName() + "' evicted or changed the analysis window during each of "
                        + MAX_MATERIALIZE_ATTEMPTS + " attempts; retry once retention is stable");
    }

    /**
     * Applies a later position to an already materialized curve, in a short read
     * scope, only while the bars the curve was captured from are unchanged. A curve
     * holds values computed from one bar history; pricing a new position from bars
     * replaced or evicted since would mix two histories. The update is staged on a
     * copy and published only if the bars are still unchanged after it ran, since a
     * bar may change its fields before its mutation is published.
     *
     * @param series the analysed series
     * @param window the curve's captured window
     * @param values the curve's values, replaced only by a verified update
     * @param update the bar-only update, applied to the staged values
     * @throws IllegalStateException if a captured bar was evicted, replaced or
     *                               updated since the curve was materialized
     */
    static void updateCapturedCurve(BarSeries series, Window window, OffsetNumBuffer values,
            Consumer<OffsetNumBuffer> update) {
        boolean applied = series.withReadLock(() -> {
            if (!window.bars().isUnchangedIn(series)) {
                return false;
            }
            OffsetNumBuffer staged = values.copy();
            update.accept(staged);
            if (!window.bars().isUnchangedIn(series)) {
                return false;
            }
            values.replaceWith(staged);
            return true;
        });
        if (!applied) {
            throw new IllegalStateException("Bar series '" + series.getName()
                    + "' changed inside this curve's window since it was materialized; build a new curve to "
                    + "analyse the changed bars");
        }
    }

    /**
     * Returns the holding cost a curve charges a position, or {@code null} when
     * the position does not reach the captured window. Historical realized
     * positions are priced only by curves that carry retained history.
     *
     * @param curve the curve supplying the end-index convention
     * @param position the position
     * @param finalIndex index open positions are marked through
     * @param window the captured window
     * @param carryPrunedHistory whether realized positions lost to pruning are
     *        carried into the curve
     * @return the holding cost through the position's end in the window, or null
     */
    static Num holdingCostInWindow(PerformanceIndicator curve, Position position, int finalIndex, Window window,
            boolean carryPrunedHistory) {
        Trade entry = position.getEntry();
        if (entry == null || entry.getIndex() > finalIndex || entry.getIndex() > window.bufferEndIndex()) {
            return null;
        }
        int endIndex = curve.determineEndIndex(position, finalIndex, window.bufferEndIndex());
        if (endIndex < window.beginIndex()) {
            return carryPrunedHistory && window.carriesBeforeWindow(position)
                    ? holdingCostThrough(position, endIndex)
                    : null;
        }
        return holdingCostThrough(position, endIndex);
    }

    /**
     * Returns a position's holding cost accrued through {@code endIndex}. A
     * position that closes after {@code endIndex} is priced as if still open there:
     * cost models such as
     * {@link org.ta4j.core.analysis.cost.LinearBorrowingCostModel} charge a closed
     * position for its whole hold, which would leak carry from bars after the
     * analysis window into it.
     *
     * @param position a position with an entry
     * @param endIndex the last index whose carry is charged
     * @return the holding cost through {@code endIndex}
     */
    static Num holdingCostThrough(Position position, int endIndex) {
        Trade exit = position.getExit();
        if (exit == null || exit.getIndex() <= endIndex) {
            return position.getHoldingCost(endIndex);
        }
        Position openAtEnd = new Position(position.getEntry(), position.getTransactionCostModel(),
                position.getHoldingCostModel());
        return openAtEnd.getHoldingCost(endIndex);
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
         * @param previousPrice the previous mark, or the position's valuation basis for
         *                      the first mark
         */
        void accept(int index, Num netPrice, Num previousPrice);
    }

    /** The exit mark of a position and the mark that preceded it. */
    record ExitMark(Num netPrice, Num previousPrice) {
    }

    /**
     * Returns the price a mark-to-market curve measures a position's gains from:
     * its net entry price, or, for an entry predating the window, its value at the
     * window's first close net of holding cost accrued by then. Following the
     * period-return convention, a window is credited only with the change in value
     * from its beginning market value, never with gains earned before it; realized
     * curves keep the entry price as cost basis.
     *
     * @param curve            the curve supplying cost conventions
     * @param series           the analysed series
     * @param position         the position, with an entry
     * @param holdingCost      the position's holding cost through {@code endIndex}
     * @param endIndex         the exit or final marked index
     * @param windowStartIndex the window's first index
     * @return the valuation basis
     */
    static Num valuationBasis(PerformanceIndicator curve, BarSeries series, Position position, Num holdingCost,
            int endIndex, int windowStartIndex) {
        Trade entry = position.getEntry();
        int entryIndex = entry.getIndex();
        if (entryIndex >= windowStartIndex) {
            return entry.getNetPrice();
        }
        NumFactory numFactory = series.numFactory();
        Num accruedCost = costPerPeriod(holdingCost, entryIndex, endIndex, numFactory)
                .multipliedBy(numFactory.numOf((long) windowStartIndex - entryIndex));
        return curve.addCost(series.getBar(windowStartIndex).getClosePrice(), accruedCost, entry.isBuy());
    }

    private static Num costPerPeriod(Num holdingCost, int entryIndex, int endIndex, NumFactory numFactory) {
        long heldPeriods = (long) endIndex - entryIndex;
        return heldPeriods <= 0L ? numFactory.zero() : holdingCost.dividedBy(numFactory.numOf(heldPeriods));
    }

    /**
     * Marks a position to market at every held close inside the window and prices
     * its exit. Each mark is the close net of holding cost accrued since entry; the
     * exit at {@code endIndex} uses the exit price (or that bar's close for an open
     * position) with the full accrued cost. The first mark is measured against the
     * {@link #valuationBasis valuation basis}: an entry predating the window is
     * valued at the window's first close, so its first mark is the bar after the
     * window start, and a position that exits on the window start is only priced by
     * its exit.
     *
     * @param curve            the curve supplying cost conventions
     * @param series           the analysed series
     * @param position         the position, with an entry
     * @param holdingCost      the position's holding cost through {@code endIndex}
     * @param endIndex         the exit or final marked index
     * @param windowStartIndex the window's first index
     * @param lastMarkIndex    last index that may be marked before the exit
     * @param marks            receives each intermediate mark in index order
     * @return the exit mark
     */
    static ExitMark markToMarket(PerformanceIndicator curve, BarSeries series, Position position, Num holdingCost,
            int endIndex, int windowStartIndex, int lastMarkIndex, MarkConsumer marks) {
        NumFactory numFactory = series.numFactory();
        Trade entry = position.getEntry();
        boolean isLong = entry.isBuy();
        int entryIndex = entry.getIndex();
        Num costPerPeriod = costPerPeriod(holdingCost, entryIndex, endIndex, numFactory);
        Num previousPrice = valuationBasis(curve, series, position, holdingCost, endIndex, windowStartIndex);
        long firstMarkIndex = (long) Math.max(entryIndex, windowStartIndex) + 1L;
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
