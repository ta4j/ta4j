/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
    public InvestedInterval(BarSeries series, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        super(series);
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        final int[] beginIndex = new int[1];
        final boolean[][] intervals = new boolean[1][];
        Runnable action = () -> {
            beginIndex[0] = getBarSeries().getBeginIndex();
            intervals[0] = buildInvestedIntervals(tradingRecord, openPositionHandling, beginIndex[0]);
        };
        series.withReadLock(action);
        materializedBeginIndex = beginIndex[0];
        investedIntervals = intervals[0];
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
            int beginIndex) {
        BarSeries series = getBarSeries();
        int analysisEndIndex = Math.max(series.getEndIndex(), tradingRecord.getEndIndex(series));
        if (beginIndex < 0) {
            return new boolean[0];
        }
        long span = (long) analysisEndIndex - beginIndex + 1L;
        if (span <= 0L) {
            return new boolean[0];
        }
        if (span >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invested interval range is too large to materialize: [" + beginIndex
                    + ", " + analysisEndIndex + "]");
        }
        int size = (int) span;
        boolean[] invested = new boolean[size];
        tradingRecord.getPositions().forEach(position -> markInvestedIntervals(position, invested, beginIndex));
        if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET) {
            List<Position> openPositions = AnalysisPositionSupport.openPositions(tradingRecord, analysisEndIndex);
            openPositions.forEach(position -> markInvestedIntervals(position, invested, beginIndex));
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested, int beginIndex) {
        if (position == null || position.getEntry() == null) {
            return;
        }
        long investedEndIndex = (long) beginIndex + invested.length - 1L;
        long startLong = Math.max((long) position.getEntry().getIndex() + 1, (long) beginIndex + 1);
        if (startLong > investedEndIndex) {
            return;
        }
        long exitIndex = position.isClosed() ? position.getExit().getIndex() : investedEndIndex;
        long endIndex = Math.min(exitIndex, investedEndIndex);
        for (long i = startLong; i <= endIndex; i++) {
            invested[(int) (i - beginIndex)] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
