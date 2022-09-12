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
 * * BarSeries 的常用实用程序和辅助方法。
 */
public final class BarSeriesUtils {

    /**
     * Sorts the Bars by {@link Bar#getEndTime()} in ascending sequence (lower values before higher values).
     * * 按 {@link Bar#getEndTime()} 升序（较低值在较高值之前）对条形图进行排序。
     */
    public static final Comparator<Bar> sortBarsByTime = (b1, b2) -> b1.getEndTime().isAfter(b2.getEndTime()) ? 1 : -1;

    private BarSeriesUtils() {
    }

    /**
     * Aggregates a list of bars by <code>timePeriod</code>. The new <code>timePeriod</code> must be a multiplication of the actual time period.
     * * 按 <code>timePeriod</code> 聚合柱形列表。 新的 <code>timePeriod</code> 必须是实际时间段的乘积。
     * 
     * @param barSeries            the barSeries
     *                             酒吧系列
     *
     * @param timePeriod           time period to aggregate
     *                             汇总的时间段
     *
     * @param aggregatedSeriesName the name of the aggregated barSeries
     *                             聚合 barSeries 的名称
     * @return the aggregated barSeries
     * @return 聚合的 barSeries
     */
    public static BarSeries aggregateBars(BarSeries barSeries, Duration timePeriod, String aggregatedSeriesName) {
        final BarAggregator durationAggregator = new DurationBarAggregator(timePeriod, true);
        final BarSeriesAggregator seriesAggregator = new BaseBarSeriesAggregator(durationAggregator);
        return seriesAggregator.aggregate(barSeries, aggregatedSeriesName);
    }

    /**
     * We can assume that finalized bar data will be never changed afterwards by the
      marketdata provider. It is rare, but depending on the exchange, they reserve
      the right to make updates to finalized bars. This method finds and replaces
      potential bar data that was changed afterwards by the marketdata provider. It
      can also be uses to check bar data equality over different marketdata
      providers. This method does <b>not</b> add missing bars but replaces an
      existing bar with its new bar.
     我们可以假设最终的柱状数据将永远不会被
     市场数据提供商。 很少见，但根据交易所，他们会保留
     对最终酒吧进行更新的权利。 此方法查找并替换
     之后由市场数据提供者更改的潜在柱数据。 它
     也可用于检查不同市场数据的柱数据相等性
     提供者。 此方法<b>不</b>添加缺失的条，但替换
     现有酒吧及其新酒吧。
     * 
     * @param barSeries the barSeries
     *                  酒吧系列
     * @param newBar    the bar which has precedence over the same existing bar
     *                  优先于相同现有栏的栏
     * @return the previous bar replaced by newBar, or null if there was no replacement.
     * @return 用 newBar 替换的前一个 bar，如果没有替换，则返回 null。
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
      <code>endTime</code> of each missing bar. A bar is possibly missing if: (1)
      the subsequent bar starts not with the end time of the previous bar or (2) if
      any open, high, low price is missing.
     查找可能丢失的条形图。 返回的列表包含
     <code>endTime</code> 每个缺失柱。 如果出现以下情况，则可能缺少条： (1)
     下一个柱不以前一个柱的结束时间开始，或者 (2) 如果
     缺少任何开盘价、最高价、最低价。
     * 
     * <b>Note:</b> Market closing times (e.g., weekends, holidays) will lead to
      wrongly detected missing bars and should be ignored by the client.
     <b>注意：</b> 市场收盘时间（例如周末、节假日）将导致
     错误地检测到缺失的柱线，客户应忽略。
     * 
     * @param barSeries       the barSeries
     *                        酒吧系列
     * @param findOnlyNaNBars find only bars with undefined prices
     *                        仅查找价格未定义的柱
     *
     * @return the list of possibly missing bars
     *              可能缺失的酒吧列表
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
                    // 收市时间也被视为缺失柱
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
      by conversionFunction. The returned barSeries inherits
      <code>beginIndex</code>, <code>endIndex</code> and
      <code>maximumBarCount</code> from the provided barSeries.
     获取从提供的 barSeries 克隆的新 BarSeries，并转换了条形
     通过转换函数。 返回的 barSeries 继承
     <code>beginIndex</code>、<code>endIndex</code> 和
     <code>maximumBarCount</code> 来自提供的 barSeries。
     * 
     * @param barSeries          the BarSeries
     *                           酒吧系列
     *
     * @param conversionFunction the conversionFunction
     *                           转换函数
     *
     * @return new cloned BarSeries with bars converted by conversionFunction
     * @return 新克隆的 BarSeries 以及由 conversionFunction 转换的条形
     */
    public static BarSeries convertBarSeries(BarSeries barSeries, Function<Number, Num> conversionFunction) {
        List<Bar> bars = barSeries.getBarData();
        if (bars == null || bars.isEmpty())
            return barSeries;
        List<Bar> convertedBars = new ArrayList<>();
        for (int i = barSeries.getBeginIndex(); i <= barSeries.getEndIndex(); i++) {
            Bar bar = bars.get(i);
            Bar convertedBar = new ConvertibleBaseBarBuilder<Number>(conversionFunction::apply)
                    .timePeriod(bar.getTimePeriod()).endTime(bar.getEndTime())
                    .openPrice(bar.getOpenPrice().getDelegate()).highPrice(bar.getHighPrice().getDelegate())
                    .lowPrice(bar.getLowPrice().getDelegate()).closePrice(bar.getClosePrice().getDelegate())
                    .volume(bar.getVolume().getDelegate()).amount(bar.getAmount().getDelegate()).trades(bar.getTrades())
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
     * 查找 barSeries 中的重叠条。
     * 
     * @param barSeries the bar series with bar data
     *                  带有条形数据的条形系列
     * @return overlapping bars
     * @return 重叠条
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
     * * 将 <code>newBars</code> 添加到 <code>barSeries</code>。
     * 
     * @param barSeries the BarSeries
     *                  酒吧系列
     * @param newBars   the new bars to be added
     *                  要添加的新酒吧
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
     * Sorts the Bars by {@link Bar#getEndTime()} in ascending sequence (lower times before higher times).
     * * 按 {@link Bar#getEndTime()} 升序（较低时间在较高时间之前）对条形图进行排序。
     * 
     * @param bars the bars
     *             酒吧
     * @return the sorted bars
     *          排序的条形图
     */
    public static List<Bar> sortBars(List<Bar> bars) {
        if (!bars.isEmpty()) {
            Collections.sort(bars, BarSeriesUtils.sortBarsByTime);
        }
        return bars;
    }

}
