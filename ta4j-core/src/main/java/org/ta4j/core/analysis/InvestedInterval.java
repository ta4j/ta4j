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
        investedIntervals = buildInvestedIntervals(tradingRecord, openPositionHandling);
    }

    @Override
    protected Boolean calculate(int index) {
        int position = index - getBarSeries().getBeginIndex();
        if (position < 0 || position >= investedIntervals.length) {
            return Boolean.FALSE;
        }
        return investedIntervals[position];
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        BarSeries series = getBarSeries();
        int beginIndex = series.getBeginIndex();
        int size = series.getBarCount() == 0 ? 0 : series.getEndIndex() - beginIndex + 1;
        boolean[] invested = new boolean[size];
        tradingRecord.getPositions().forEach(position -> markInvestedIntervals(position, invested));
        if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET) {
            List<Position> openPositions = AnalysisPositionSupport.openPositions(tradingRecord, series.getEndIndex());
            openPositions.forEach(position -> markInvestedIntervals(position, invested));
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested) {
        BarSeries series = getBarSeries();
        if (position == null || position.getEntry() == null) {
            return;
        }
        int beginIndex = series.getBeginIndex();
        int seriesEnd = series.getEndIndex();
        long startLong = Math.max((long) position.getEntry().getIndex() + 1, (long) beginIndex + 1);
        if (startLong > seriesEnd) {
            return;
        }
        int exitIndex = position.isClosed() ? position.getExit().getIndex() : seriesEnd;
        int start = (int) startLong;
        int end = Math.min(exitIndex, seriesEnd);
        for (long i = start; i <= end; i++) {
            invested[(int) i - beginIndex] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
