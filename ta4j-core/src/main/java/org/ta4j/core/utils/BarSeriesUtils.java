/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.ConvertibleBaseBarBuilder;
import org.ta4j.core.aggregator.BarAggregator;
import org.ta4j.core.aggregator.BarSeriesAggregator;
import org.ta4j.core.aggregator.BaseBarSeriesAggregator;
import org.ta4j.core.aggregator.DurationBarAggregator;
import org.ta4j.core.num.Num;

/**
 * Common utilities and helper methods for BarSeries.
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
     * Aggregates a list of bars by <code>timePeriod</code>. The new
     * <code>timePeriod</code> must be a multiplication of the actual time period.
     * 
     * @param barSeries            the barSeries
     * @param timePeriod           time period to aggregate
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
            boolean isSameBar = bar.getBeginTime().isEqual(newBar.getBeginTime())
                    && bar.getEndTime().isEqual(newBar.getEndTime())
                    && bar.getTimePeriod().equals(newBar.getTimePeriod());
            if (isSameBar && !bar.equals(newBar))
                return bars.set(i, newBar);
        }
        return null;
    }

    /**
     * Finds possibly missing bars. The returned list contains the
     * <code>endTime</code> of each missing bar. A bar is possibly missing if: (1)
     * the subsequent bar starts not with the end time of the previous bar or (2) if
     * any open, high, low price is missing.
     * 
     * <b>Note:</b> Market closing times (e.g., weekends, holidays) will lead to
     * wrongly detected missing bars and should be ignored by the client.
     * 
     * @param barSeries       the barSeries
     * @param findOnlyNaNBars find only bars with undefined prices
     * @return the list of possibly missing bars
     */
    public static List<ZonedDateTime> findMissingBars(BarSeries barSeries, boolean findOnlyNaNBars) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return new ArrayList<>();
        Duration duration = bars.iterator().next().getTimePeriod();
        List<ZonedDateTime> missingBars = new ArrayList<>();
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
     * by conversionFunction. The returned barSeries inherits
     * <code>beginIndex</code>, <code>endIndex</code> and
     * <code>maximumBarCount</code> from the provided barSeries.
     * 
     * @param barSeries          the BarSeries
     * @param conversionFunction the conversionFunction
     * @return new cloned BarSeries with bars converted by conversionFunction
     */
    public static BarSeries convertBarSeries(BarSeries barSeries, Function<Number, Num> conversionFunction) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return barSeries;
        List<Bar> convertedBars = new ArrayList<>();
        for (int i = barSeries.getBeginIndex(); i <= barSeries.getEndIndex(); i++) {
            Bar bar = bars.get(i);
            Bar convertedBar = new ConvertibleBaseBarBuilder<Number>(conversionFunction::apply)
                    .timePeriod(bar.getTimePeriod())
                    .endTime(bar.getEndTime())
                    .openPrice(bar.getOpenPrice().getDelegate())
                    .highPrice(bar.getHighPrice().getDelegate())
                    .lowPrice(bar.getLowPrice().getDelegate())
                    .closePrice(bar.getClosePrice().getDelegate())
                    .volume(bar.getVolume().getDelegate())
                    .amount(bar.getAmount().getDelegate())
                    .trades(bar.getTrades())
                    .build();
            convertedBars.add(convertedBar);
        }
        BarSeries convertedBarSeries = new BaseBarSeries(barSeries.getName(), convertedBars, conversionFunction);
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
     * Adds <code>newBars</code> to <code>barSeries</code>.
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
            Collections.sort(bars, BarSeriesUtils.sortBarsByTime);
        }
        return bars;
    }

}
