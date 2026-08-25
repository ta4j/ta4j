/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.List;
import java.util.Objects;
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
    private final int valueStartIndex;

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
        valueStartIndex = Math.max(0, getBarSeries().getBeginIndex());
        investedIntervals = buildInvestedIntervals(tradingRecord, openPositionHandling);
    }

    @Override
    protected Boolean calculate(int index) {
        int offset = index - valueStartIndex;
        if (offset < 0 || offset >= investedIntervals.length) {
            return Boolean.FALSE;
        }
        return investedIntervals[offset];
    }

    /**
     * Returns the precomputed invested flag directly for retained in-range bars,
     * bypassing the indicator cache ring. The flag array is an immutable snapshot
     * computed from the trading record at construction time, so no cache
     * synchronization is needed for those reads. Indexes pruned from a moving
     * series (below {@link #getBarSeries()}'s begin index) and out-of-range indexes
     * keep the inherited cached-path behavior so removed-bar remapping still
     * applies.
     *
     * @since 0.24.2
     */
    @Override
    public Boolean getValue(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        int endIndex = getBarSeries().getEndIndex();
        int offset = index - valueStartIndex;
        if (index >= beginIndex && index <= endIndex && offset >= 0 && offset < investedIntervals.length) {
            return investedIntervals[offset];
        }
        return super.getValue(index);
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        BarSeries series = getBarSeries();
        int seriesBegin = Math.max(0, series.getBeginIndex());
        int seriesEnd = series.getEndIndex();
        int size = seriesEnd < seriesBegin ? 0 : seriesEnd - seriesBegin + 1;
        boolean[] invested = new boolean[size];
        tradingRecord.getPositions().forEach(position -> markInvestedIntervals(position, invested));
        if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET) {
            List<Position> openPositions = AnalysisPositionSupport.openPositions(tradingRecord, seriesEnd);
            openPositions.forEach(position -> markInvestedIntervals(position, invested));
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested) {
        BarSeries series = getBarSeries();
        if (position == null || position.getEntry() == null) {
            return;
        }
        int entryIndex = position.getEntry().getIndex();
        int exitIndex = position.isClosed() ? position.getExit().getIndex() : series.getEndIndex();
        int start = Math.max(entryIndex + 1, series.getBeginIndex() + 1);
        int end = Math.min(exitIndex, series.getEndIndex());
        for (int i = start; i <= end; i++) {
            invested[i - valueStartIndex] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
