/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
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
    private final BarSeries exposedBarSeries;
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
        super(snapshotSeries(series));
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        exposedBarSeries = snapshotSeries(series);
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
     * Returns the precomputed invested flag for a retained in-range bar.
     *
     * <p>
     * The returned series is a detached view. Mutating it cannot alter the captured
     * flag array or the calculation series.
     *
     * @since 0.24.2
     */
    @Override
    public Boolean getValue(int index) {
        int offset = index - valueStartIndex;
        if (index >= valueStartIndex && index <= valueEndIndex && offset >= 0 && offset < investedIntervals.length) {
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
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "getBarSeries returns a detached snapshot")
    public BarSeries getBarSeries() {
        return exposedBarSeries;
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

    private static BarSeries snapshotSeries(final BarSeries barSeries) {
        BarSeries series = Objects.requireNonNull(barSeries);
        List<Bar> copiedBars = new ArrayList<>(series.getBarData().size());
        for (Bar bar : series.getBarData()) {
            copiedBars.add(new BaseBar(bar.getTimePeriod(), bar.getBeginTime(), bar.getEndTime(), bar.getOpenPrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.getAmount(),
                    bar.getTrades()));
        }
        return new BaseBarSeriesBuilder().withName(series.getName())
                .withNumFactory(series.numFactory())
                .withBars(copiedBars)
                .withBeginIndex(Math.max(0, series.getBeginIndex()))
                .withMaxBarCount(series.getMaximumBarCount())
                .build();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
