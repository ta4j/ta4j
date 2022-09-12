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
package ta4jexamples.walkforward;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;
import ta4jexamples.strategies.GlobalExtremaStrategy;
import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.strategies.RSI2Strategy;

/**
 * Walk-forward optimization example.
 * * 前进优化示例。
 *
 * @see <a href="http://en.wikipedia.org/wiki/Walk_forward_optimization">
 *      http://en.wikipedia.org/wiki/Walk_forward_optimization</a>
 * @see <a href=
 *      "http://www.futuresmag.com/2010/04/01/can-your-system-do-the-walk">
 *      http://www.futuresmag.com/2010/04/01/can-your-system-do-the-walk</a>
 */
public class WalkForward {

    /**
     * Builds a list of split indexes from splitDuration.
     * * 从 splitDuration 构建拆分索引列表。
     *
     * @param series        the bar series to get split begin indexes of
     *                      获取拆分开始索引的条形系列
     * @param splitDuration the duration between 2 splits
     *                      2次分裂之间的持续时间
     * @return a list of begin indexes after split
     * @return 拆分后的开始索引列表
     */
    public static List<Integer> getSplitBeginIndexes(BarSeries series, Duration splitDuration) {
        ArrayList<Integer> beginIndexes = new ArrayList<>();

        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();

        // Adding the first begin index
        // 添加第一个开始索引
        beginIndexes.add(beginIndex);

        // Building the first interval before next split
        // 在下一次拆分之前构建第一个区间
        ZonedDateTime beginInterval = series.getFirstBar().getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(splitDuration);

        for (int i = beginIndex; i <= endIndex; i++) {
            // For each bar...
            // 对于每个柱...
            ZonedDateTime barTime = series.getBar(i).getEndTime();
            if (barTime.isBefore(beginInterval) || !barTime.isBefore(endInterval)) {
                // Bar out of the interval
                // 超出区间
                if (!endInterval.isAfter(barTime)) {
                    // Bar after the interval
                    // 间隔后的条形图
                    // --> Adding a new begin index
                    // --> 添加一个新的开始索引
                    beginIndexes.add(i);
                }

                // Building the new interval before next split
                // 在下一次拆分之前建立新的区间
                beginInterval = endInterval.isBefore(barTime) ? barTime : endInterval;
                endInterval = beginInterval.plus(splitDuration);
            }
        }
        return beginIndexes;
    }

    /**
     * Returns a new bar series which is a view of a subset of the current series.
     * * 返回一个新的条形序列，它是当前序列子集的视图。
     *
     * The new series has begin and end indexes which correspond to the bounds of the sub-set into the full series.<br>
     * * 新系列具有开始和结束索引，它们对应于子集到完整系列的范围。<br>
     *
     * The bar of the series are shared between the original bar series and the returned one (i.e. no copy).
     * * 该系列的酒吧在原始酒吧系列和退回的酒吧之间共享（即没有副本）。
     *
     * @param series     the bar series to get a sub-series of
     *                   酒吧系列得到一个子系列
     *
     * @param beginIndex the begin index (inclusive) of the bar series
     *                   条形系列的开始索引（包括）
     *
     * @param duration   the duration of the bar series
     *                   酒吧系列的持续时间
     *
     * @return a constrained {@link BarSeries bar series} which is a sub-set of the current series
     * * @return 一个受约束的 {@link BarSeries bar series}，它是当前系列的子集
     */
    public static BarSeries subseries(BarSeries series, int beginIndex, Duration duration) {

        // Calculating the sub-series interval
        // 计算子序列间隔
        ZonedDateTime beginInterval = series.getBar(beginIndex).getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(duration);

        // Checking bars belonging to the sub-series (starting at the provided index)
        // 检查属于子系列的柱（从提供的索引开始）
        int subseriesNbBars = 0;
        int endIndex = series.getEndIndex();
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each bar...
            // 对于每个柱...
            ZonedDateTime barTime = series.getBar(i).getEndTime();
            if (barTime.isBefore(beginInterval) || !barTime.isBefore(endInterval)) {
                // Bar out of the interval
                // 超出区间
                break;
            }
            // Bar in the interval
            // 区间中的条形图
            // --> Incrementing the number of bars in the subseries
            // --> 增加子系列中柱的数量
            subseriesNbBars++;
        }

        return series.getSubSeries(beginIndex, beginIndex + subseriesNbBars);
    }

    /**
     * Splits the bar series into sub-series lasting sliceDuration.<br>
     * * 将条形系列拆分为持续 sliceDuration 的子系列。<br>
     *
     * The current bar series is splitted every splitDuration.<br>
     * * 当前条形系列在每个 splitDuration 中拆分。<br>
     *
     * The last sub-series may last less than sliceDuration.
     * * 最后一个子系列的持续时间可能少于 sliceDuration。
     *
     * @param series        the bar series to split
     *                      要拆分的酒吧系列
     *
     * @param splitDuration the duration between 2 splits
     *                      2次分裂之间的持续时间
     *
     * @param sliceDuration the duration of each sub-series
     *                      每个子系列的持续时间
     *
     * @return a list of sub-series
     * * @return 子系列列表
     */
    public static List<BarSeries> splitSeries(BarSeries series, Duration splitDuration, Duration sliceDuration) {
        ArrayList<BarSeries> subseries = new ArrayList<>();
        if (splitDuration != null && !splitDuration.isZero() && sliceDuration != null && !sliceDuration.isZero()) {

            List<Integer> beginIndexes = getSplitBeginIndexes(series, splitDuration);
            for (Integer subseriesBegin : beginIndexes) {
                subseries.add(subseries(series, subseriesBegin, sliceDuration));
            }
        }
        return subseries;
    }

    /**
     * @param series the bar series 酒吧系列
     * @return a map (key: strategy, value: name) of trading strategies
     * * @return 交易策略的映射（键：策略，值：名称）
     */
    public static Map<Strategy, String> buildStrategiesMap(BarSeries series) {
        HashMap<Strategy, String> strategies = new HashMap<>();
        strategies.put(CCICorrectionStrategy.buildStrategy(series), "CCI Correction");
        strategies.put(GlobalExtremaStrategy.buildStrategy(series), "Global Extrema");
        strategies.put(MovingMomentumStrategy.buildStrategy(series), "Moving Momentum");
        strategies.put(RSI2Strategy.buildStrategy(series), "RSI-2");
        return strategies;
    }

    public static void main(String[] args) {
        // Splitting the series into slices
        // 将序列分割成切片
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        List<BarSeries> subseries = splitSeries(series, Duration.ofHours(6), Duration.ofDays(7));

        // Building the map of strategies
        // 构建策略图
        Map<Strategy, String> strategies = buildStrategiesMap(series);

        // The analysis criterion
        // 分析标准
        AnalysisCriterion returnCriterion = new GrossReturnCriterion();

        for (BarSeries slice : subseries) {
            // For each sub-series...
            // 对于每个子系列...
            System.out.println("Sub-series 子系列: " + slice.getSeriesPeriodDescription());
            BarSeriesManager sliceManager = new BarSeriesManager(slice);
            for (Map.Entry<Strategy, String> entry : strategies.entrySet()) {
                Strategy strategy = entry.getKey();
                String name = entry.getValue();
                // For each strategy...
                // 对于每个策略...
                TradingRecord tradingRecord = sliceManager.run(strategy);
                Num profit = returnCriterion.calculate(slice, tradingRecord);
                System.out.println("\tProfit for 利润为 " + name + ": " + profit);
            }
            Strategy bestStrategy = returnCriterion.chooseBest(sliceManager, TradeType.BUY,
                    new ArrayList<Strategy>(strategies.keySet()));
            System.out.println("\t\t--> Best strategy 最佳策略: " + strategies.get(bestStrategy) + "\n");
        }
    }

}
