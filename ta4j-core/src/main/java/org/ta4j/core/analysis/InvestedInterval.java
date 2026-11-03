/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Indicates whether each bar interval is part of an invested position.
 *
 * <p>
 * The indicator marks index {@code i} as invested when the interval between
 * {@code i - 1} and {@code i} belongs to a position in the provided trading
 * record.
 *
 * @since 0.22.2
 */
public class InvestedInterval extends CachedIndicator<Boolean> {

    private final boolean[] investedIntervals;

    /**
     * The series begin index captured when the interval array was materialized.
     * Later rolling advances of the borrowed series must not rebase the lookup, or
     * intervals shift onto never-calculated bars.
     */
    private final int materializedBeginIndex;

    /**
     * Creates an indicator that reports invested intervals for the trading record.
     *
     * @param series        the bar series backing the indicator
     * @param tradingRecord the trading record used to detect invested intervals
     * @since 0.22.2
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Rejecting a window too large to "
            + "materialize is a fail-fast constructor contract; no partially initialized instance escapes")
    public InvestedInterval(BarSeries series, TradingRecord tradingRecord) {
        this(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates an indicator that reports invested intervals for the trading record.
     *
     * @param series               the bar series backing the indicator
     * @param tradingRecord        the trading record used to detect invested
     *                             intervals
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Rejecting a window too large to "
            + "materialize is a fail-fast constructor contract; no partially initialized instance escapes")
    public InvestedInterval(BarSeries series, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        super(series);
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        // The record's bounds are read before the series lock, and only the bounds
        // come from the series inside it; the record is traversed afterwards without
        // holding the series lock.
        Integer recordStartIndex = tradingRecord.getStartIndex();
        Integer recordEndIndex = tradingRecord.getEndIndex();
        AnalysisPositionSupport.Window window = series.withReadLock(() -> AnalysisPositionSupport.captureWindow(series,
                recordStartIndex, recordEndIndex, 0, 0, true, false, true));
        materializedBeginIndex = window.beginIndex();
        investedIntervals = buildInvestedIntervals(tradingRecord, openPositionHandling, window);
    }

    /**
     * Returns the captured flag without remapping pruned indices through the live
     * series cache.
     *
     * @since 0.25.1
     */
    @Override
    public Boolean getValue(int index) {
        return calculate(index);
    }

    @Override
    protected Boolean calculate(int index) {
        long position = (long) index - materializedBeginIndex;
        if (position < 0 || position >= investedIntervals.length) {
            return Boolean.FALSE;
        }
        return investedIntervals[(int) position];
    }

    /**
     * @return invested flags over the captured materialized window, independent of
     *         later changes to the borrowed series bounds
     * @since 0.25.1
     */
    @Override
    public Stream<Boolean> stream() {
        return IntStream.range(0, investedIntervals.length).mapToObj(index -> investedIntervals[index]);
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling,
            AnalysisPositionSupport.Window window) {
        int beginIndex = window.beginIndex();
        if (beginIndex < 0 || window.isEmpty()) {
            return new boolean[0];
        }
        long span = (long) window.bufferEndIndex() - beginIndex + 1L;
        if (span >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invested interval range is too large to materialize: [" + beginIndex
                    + ", " + window.bufferEndIndex() + "]");
        }
        boolean[] invested = new boolean[(int) span];
        // Same position selection as the curves: bound to the logical end, treat exits
        // after it as open there, and drop those under IGNORE.
        for (Position position : AnalysisPositionSupport.positionsForAnalysis(tradingRecord, window.finalIndex(),
                openPositionHandling, EquityCurveMode.MARK_TO_MARKET)) {
            markInvestedIntervals(position, invested, beginIndex, window.endIndex(), openPositionHandling);
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested, int beginIndex, int endIndex,
            OpenPositionHandling handling) {
        if (FuturesPerformanceSupport.isFutures(position)) {
            markFuturesInvestedIntervals(position, invested, handling, endIndex, beginIndex);
            return;
        }
        int firstFill = firstExecutedFillIndex(position.getEntry(), endIndex);
        if (firstFill < 0)
            return;
        int finalExit = position.getExit() == null ? endIndex : lastExecutedFillIndex(position.getExit(), endIndex);
        if (handling == OpenPositionHandling.MARK_TO_MARKET && hasResidualExposure(position, endIndex))
            finalExit = endIndex;
        long startIndex = Math.max((long) firstFill + 1, (long) beginIndex + 1);
        long lastIndex = Math.min(finalExit, endIndex);
        for (long i = startIndex; i <= lastIndex; i++) {
            invested[(int) (i - beginIndex)] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    private static void markFuturesInvestedIntervals(Position position, boolean[] invested,
            OpenPositionHandling openPositionHandling, int finalIndex, int seriesBegin) {
        if (openPositionHandling != OpenPositionHandling.MARK_TO_MARKET && !position.isClosed()) {
            return;
        }
        List<TradeFill> entryFills = executedFills(position.getEntry(), finalIndex);
        if (entryFills.isEmpty()) {
            return;
        }
        List<TradeFill> exitFills = executedFills(position.getExit(), finalIndex);
        int lastIndex = finalIndex;
        if (openPositionHandling != OpenPositionHandling.MARK_TO_MARKET) {
            if (exitFills.isEmpty()) {
                return;
            }
            lastIndex = exitFills.get(exitFills.size() - 1).index();
        }

        long start = Math.max((long) seriesBegin + 1, 1);
        int end = lastIndex;
        if (start > end) {
            return;
        }

        NumFactory numFactory = position.getEntry().getAmount().getNumFactory();
        Num exposure = numFactory.zero();
        int entryCursor = 0;
        int exitCursor = 0;
        for (long intervalIndex = start; intervalIndex <= end; intervalIndex++) {
            int barIndex = (int) intervalIndex - 1;
            while (entryCursor < entryFills.size() && entryFills.get(entryCursor).index() <= barIndex) {
                TradeFill fill = entryFills.get(entryCursor++);
                exposure = exposure.plus(numFactory.numOf(fill.amount().getDelegate()));
            }
            while (exitCursor < exitFills.size() && exitFills.get(exitCursor).index() <= barIndex) {
                TradeFill fill = exitFills.get(exitCursor++);
                exposure = exposure.minus(numFactory.numOf(fill.amount().getDelegate()));
            }
            if (exposure.isPositive()) {
                invested[(int) (intervalIndex - seriesBegin)] = true;
            }
        }
    }

    private static List<TradeFill> executedFills(Trade trade, int finalIndex) {
        List<TradeFill> fills = new ArrayList<>();
        if (trade == null) {
            return fills;
        }
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                fills.add(fill);
            }
        }
        fills.sort(Comparator.comparingInt(TradeFill::index)
                .thenComparing(TradeFill::time, Comparator.nullsFirst(Comparator.naturalOrder())));
        return fills;
    }

    private static boolean hasResidualExposure(Position position, int finalIndex) {
        if (position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return false;
        }
        Trade entry = position.getEntry();
        Num entryAmount = executedAmount(entry, finalIndex);
        if (!entryAmount.isPositive()) {
            return false;
        }
        Trade exit = position.getExit();
        return exit == null || entryAmount.isGreaterThan(executedAmount(exit, finalIndex));
    }

    private static Num executedAmount(Trade trade, int finalIndex) {
        NumFactory numFactory = trade.getAmount().getNumFactory();
        Num amount = numFactory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                amount = amount.plus(numFactory.numOf(fill.amount().getDelegate()));
            }
        }
        return amount;
    }

    private static int firstExecutedFillIndex(Trade trade, int finalIndex) {
        int firstIndex = Integer.MAX_VALUE;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                firstIndex = Math.min(firstIndex, fill.index());
            }
        }
        return firstIndex == Integer.MAX_VALUE ? -1 : firstIndex;
    }

    private static int lastExecutedFillIndex(Trade trade, int finalIndex) {
        int lastIndex = -1;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                lastIndex = Math.max(lastIndex, fill.index());
            }
        }
        return lastIndex;
    }
}
