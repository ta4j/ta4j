/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.ta4j.core.aggregator.BaseBarSeriesAggregator;
import org.ta4j.core.aggregator.BarSeriesAggregator;
import org.ta4j.core.aggregator.DurationBarAggregator;
import org.ta4j.core.aggregator.BarAggregator;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.Bar;

/**
 * Common utilities and helper methods for {@link BarSeries}.
 */
public final class BarSeriesUtils {

    /**
     * Sorts the Bars by {@link Bar#getEndTime()} in ascending sequence (lower
     * values before higher values).
     */
    public static final Comparator<Bar> sortBarsByTime = (b1, b2) -> b1.getEndTime().isAfter(b2.getEndTime()) ? 1 : -1;

    private BarSeriesUtils() {
    }

    /**
     * Aggregates a list of bars by {@code timePeriod}. The new {@code timePeriod}
     * must be a multiplication of the actual time period.
     *
     * @param barSeries            the barSeries
     * @param timePeriod           the target time period that aggregated bars
     *                             should have
     * @param aggregatedSeriesName the name of the aggregated barSeries
     * @return the aggregated barSeries
     */
    public static BarSeries aggregateBars(BarSeries barSeries, Duration timePeriod, String aggregatedSeriesName) {
        final BarAggregator durationAggregator = new DurationBarAggregator(timePeriod, true);
        final BarSeriesAggregator seriesAggregator = new BaseBarSeriesAggregator(durationAggregator);
        return seriesAggregator.aggregate(barSeries, aggregatedSeriesName);
    }

    /**
     * We can assume that finalized bar data will be never changed afterwards by the
     * marketdata provider. It is rare, but depending on the exchange, they reserve
     * the right to make updates to finalized bars. This method finds and replaces
     * potential bar data that was changed afterwards by the marketdata provider. It
     * can also be uses to check bar data equality over different marketdata
     * providers. This method does <b>not</b> add missing bars but replaces an
     * existing bar with its new bar.
     *
     * @param barSeries the barSeries
     * @param newBar    the bar which has precedence over the same existing bar
     * @return the previous bar replaced by newBar, or null if there was no
     *         replacement.
     */
    public static Bar replaceBarIfChanged(BarSeries barSeries, Bar newBar) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return null;
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            boolean isSameBar = bar.getBeginTime().equals(newBar.getBeginTime())
                    && bar.getEndTime().equals(newBar.getEndTime())
                    && bar.getTimePeriod().equals(newBar.getTimePeriod());
            if (isSameBar && !bar.equals(newBar))
                return bars.set(i, newBar);
        }
        return null;
    }

    /**
     * Finds possibly missing bars. The returned list contains the {@code endTime}
     * of each missing bar. A bar is possibly missing if: (1) the subsequent bar
     * starts not with the end time of the previous bar or (2) if any open, high,
     * low price is missing.
     *
     * <b>Note:</b> Market closing times (e.g., weekends, holidays) will lead to
     * wrongly detected missing bars and should be ignored by the client.
     *
     * @param barSeries       the barSeries
     * @param findOnlyNaNBars find only bars with undefined prices
     * @return the list of possibly missing bars
     */
    public static List<Instant> findMissingBars(BarSeries barSeries, boolean findOnlyNaNBars) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return new ArrayList<>();
        Duration duration = bars.iterator().next().getTimePeriod();
        List<Instant> missingBars = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            if (!findOnlyNaNBars) {
                Bar nextBar = i + 1 < bars.size() ? bars.get(i + 1) : null;
                Duration incDuration = Duration.ZERO;
                if (nextBar != null) {
                    // market closing times are also treated as missing bars
                    while (nextBar.getBeginTime().minus(incDuration).isAfter(bar.getEndTime())) {
                        missingBars.add(bar.getEndTime().plus(incDuration).plus(duration));
                        incDuration = incDuration.plus(duration);
                    }
                }
            }
            boolean noFullData = bar.getOpenPrice().isNaN() || bar.getHighPrice().isNaN() || bar.getLowPrice().isNaN();
            if (noFullData) {
                missingBars.add(bar.getEndTime());
            }
        }
        return missingBars;
    }

    /**
     * Gets a new BarSeries cloned from the provided barSeries with bars converted
     * by conversionFunction. The returned barSeries inherits {@code beginIndex},
     * {@code endIndex} and {@code maximumBarCount} from the provided barSeries.
     *
     * @param barSeries  the BarSeries
     * @param numFactory produces numbers used in converted barsŚeries; with this,
     *                   we can convert a {@link Number} to a {@link Num Num
     *                   implementation}
     * @return new cloned BarSeries with bars converted by the Num function of num
     */
    public static BarSeries convertBarSeries(BarSeries barSeries, NumFactory numFactory) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return barSeries;
        var convertedBarSeries = new BaseBarSeriesBuilder().withName(barSeries.getName())
                .withNumFactory(numFactory)
                .build();
        for (int i = barSeries.getBeginIndex(); i <= barSeries.getEndIndex(); i++) {
            Bar bar = bars.get(i);
            convertedBarSeries.barBuilder()
                    .timePeriod(bar.getTimePeriod())
                    .endTime(bar.getEndTime())
                    .openPrice(bar.getOpenPrice().getDelegate())
                    .highPrice(bar.getHighPrice().getDelegate())
                    .lowPrice(bar.getLowPrice().getDelegate())
                    .closePrice(bar.getClosePrice().getDelegate())
                    .volume(bar.getVolume().getDelegate())
                    .amount(bar.getAmount().getDelegate())
                    .trades(bar.getTrades())
                    .add();
        }

        if (barSeries.getMaximumBarCount() > 0) {
            convertedBarSeries.setMaximumBarCount(barSeries.getMaximumBarCount());
        }

        return convertedBarSeries;
    }

    /**
     * Finds overlapping bars within barSeries.
     *
     * @param barSeries the bar series with bar data
     * @return overlapping bars
     */
    public static List<Bar> findOverlappingBars(BarSeries barSeries) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return new ArrayList<>();
        Duration period = bars.iterator().next().getTimePeriod();
        List<Bar> overlappingBars = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            Bar nextBar = i + 1 < bars.size() ? bars.get(i + 1) : null;
            if (nextBar != null) {
                if (bar.getEndTime().isAfter(nextBar.getBeginTime())
                        || bar.getBeginTime().plus(period).isBefore(nextBar.getBeginTime())) {
                    overlappingBars.add(nextBar);
                }
            }
        }
        return overlappingBars;
    }

    /**
     * Adds {@code newBars} to {@code barSeries}.
     *
     * @param barSeries the BarSeries
     * @param newBars   the new bars to be added
     */
    public static void addBars(BarSeries barSeries, List<Bar> newBars) {
        if (newBars != null && !newBars.isEmpty()) {
            sortBars(newBars);
            for (Bar bar : newBars) {
                if (barSeries.isEmpty() || bar.getEndTime().isAfter(barSeries.getLastBar().getEndTime())) {
                    barSeries.addBar(bar);
                }
            }
        }
    }

    /**
     * Sorts the Bars by {@link Bar#getEndTime()} in ascending sequence (lower times
     * before higher times).
     *
     * @param bars the bars
     * @return the sorted bars
     */
    public static List<Bar> sortBars(List<Bar> bars) {
        if (!bars.isEmpty()) {
            bars.sort(BarSeriesUtils.sortBarsByTime);
        }
        return bars;
    }

    /**
     * Computes the elapsed time between bar end times in years.
     *
     * @param series        the bar series
     * @param previousIndex the previous index
     * @param currentIndex  the current index
     * @return the elapsed time in years, clamped to zero for non-positive deltas
     */
    public static Num deltaYears(BarSeries series, int previousIndex, int currentIndex) {
        var endPrev = series.getBar(previousIndex).getEndTime();
        var endNow = series.getBar(currentIndex).getEndTime();
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        var numFactory = series.numFactory();
        return seconds <= 0 ? numFactory.zero()
                : numFactory.numOf(seconds).dividedBy(numFactory.numOf(TimeConstants.SECONDS_PER_YEAR));
    }

}
