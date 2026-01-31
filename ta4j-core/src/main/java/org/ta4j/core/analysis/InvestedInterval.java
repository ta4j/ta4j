/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.Objects;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;

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
        if (index < 0 || index >= investedIntervals.length) {
            return Boolean.FALSE;
        }
        return investedIntervals[index];
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        var series = getBarSeries();
        var size = Math.max(series.getEndIndex() + 1, 0);
        var invested = new boolean[size];
        tradingRecord.getPositions().forEach(position -> markInvestedIntervals(position, invested));
        if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET) {
            var openPositions = AnalysisPositionSupport.openPositions(tradingRecord, series.getEndIndex());
            openPositions.forEach(position -> markInvestedIntervals(position, invested));
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested) {
        var series = getBarSeries();
        if (position == null || position.getEntry() == null) {
            return;
        }
        var entryIndex = position.getEntry().getIndex();
        var exitIndex = position.isClosed() ? position.getExit().getIndex() : series.getEndIndex();
        var start = Math.max(entryIndex + 1, series.getBeginIndex() + 1);
        var end = Math.min(exitIndex, invested.length - 1);
        for (var i = start; i <= end; i++) {
            invested[i] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
