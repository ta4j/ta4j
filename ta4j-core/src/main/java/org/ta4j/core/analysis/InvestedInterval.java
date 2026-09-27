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
    private final SeriesSnapshots.CapturedSeries capturedSeries;
    private volatile BarSeries exposedBarSeries;
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
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        capturedSeries = SeriesSnapshots.capture(series);
        valueStartIndex = Math.max(0, capturedSeries.beginIndex());
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
     * Returns the precomputed invested flag for the given absolute bar index:
     * {@code Boolean.TRUE} while a position was held over that bar,
     * {@code Boolean.FALSE} otherwise, including for indices outside the series
     * range captured at construction (bars already pruned, or bars appended later).
     *
     * @since 0.25.1
     */
    @Override
    public Boolean getValue(int index) {
        // The flags are fully precomputed, so bypass the indicator cache.
        return calculate(index);
    }

    /**
     * Returns a detached copy of the bars the invested intervals were computed
     * from, with the source series' absolute indexing. The bar set is the one
     * retained at construction; bar contents are copied on first request (under the
     * read lock of a {@code ConcurrentBarSeries}). The same instance is returned
     * afterwards, and mutating it cannot reach the source series.
     *
     * @return the detached backing series snapshot
     * @since 0.25.1
     */
    @Override
    public BarSeries getBarSeries() {
        BarSeries snapshot = exposedBarSeries;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = exposedBarSeries;
                if (snapshot == null) {
                    snapshot = capturedSeries.toDetachedSeries();
                    exposedBarSeries = snapshot;
                }
            }
        }
        return snapshot;
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        int seriesBegin = Math.max(0, capturedSeries.beginIndex());
        int seriesEnd = capturedSeries.endIndex();
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
        if (position == null || position.getEntry() == null) {
            return;
        }
        int entryIndex = position.getEntry().getIndex();
        int exitIndex = position.isClosed() ? position.getExit().getIndex() : capturedSeries.endIndex();
        int start = Math.max(entryIndex + 1, capturedSeries.beginIndex() + 1);
        int end = Math.min(exitIndex, capturedSeries.endIndex());
        for (int i = start; i <= end; i++) {
            invested[i - valueStartIndex] = true;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
