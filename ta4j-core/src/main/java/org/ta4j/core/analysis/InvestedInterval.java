/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.Bar;
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
    private volatile BarSeries exposedBarSeries;
    private final int valueStartIndex;
    private final int valueEndIndex;

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
        this(series, tradingRecord, openPositionHandling, false);
    }

    /**
     * Internal factory. Creates the indicator over an already detached, privately
     * owned snapshot without copying it again; used by {@link EquityCurveCache},
     * whose snapshots are never shared.
     */
    static InvestedInterval overOwnedSnapshot(BarSeries ownedSnapshot, TradingRecord tradingRecord,
            OpenPositionHandling openPositionHandling) {
        return new InvestedInterval(ownedSnapshot, tradingRecord, openPositionHandling, true);
    }

    private InvestedInterval(BarSeries series, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling,
            boolean seriesIsOwnedSnapshot) {
        super(seriesIsOwnedSnapshot ? series : SeriesSnapshots.deepCopy(series));
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        valueStartIndex = Math.max(0, super.getBarSeries().getBeginIndex());
        valueEndIndex = super.getBarSeries().getEndIndex();
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
     * {@code Boolean.FALSE} otherwise — including for indices outside the captured
     * range {@code [valueStartIndex, valueEndIndex]}, such as bars pruned before
     * the indicator was created or indices beyond its end.
     *
     * @since 0.24.2
     */
    @Override
    public Boolean getValue(int index) {
        int offset = index - valueStartIndex;
        if (offset >= 0 && offset < investedIntervals.length) {
            return investedIntervals[offset];
        }
        return Boolean.FALSE;
    }

    /**
     * Returns the detached snapshot series the invested intervals were computed
     * from: it mirrors the calculation series' absolute indexing but owns private
     * bar data, so in-place edits of the original bars cannot alter the published
     * intervals.
     *
     * @return the detached backing series snapshot
     * @since 0.24.2
     */
    @Override
    public BarSeries getBarSeries() {
        BarSeries snapshot = exposedBarSeries;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = exposedBarSeries;
                if (snapshot == null) {
                    snapshot = SeriesSnapshots.deepCopy(super.getBarSeries());
                    exposedBarSeries = snapshot;
                }
            }
        }
        return snapshot;
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        BarSeries series = super.getBarSeries();
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
        BarSeries series = super.getBarSeries();
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
