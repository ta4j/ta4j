/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

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
            markInvestedIntervals(position, invested, beginIndex, window.endIndex());
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested, int beginIndex, int endIndex) {
        long startIndex = Math.max((long) position.getEntry().getIndex() + 1, (long) beginIndex + 1);
        long lastIndex = position.isClosed() ? Math.min(position.getExit().getIndex(), endIndex) : endIndex;
        for (long i = startIndex; i <= lastIndex; i++) {
            invested[(int) (i - beginIndex)] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
