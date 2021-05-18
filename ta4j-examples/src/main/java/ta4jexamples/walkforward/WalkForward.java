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
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;
import ta4jexamples.strategies.GlobalExtremaStrategy;
import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.strategies.RSI2Strategy;

/**
 * Walk-forward optimization example.
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
     *
     * @param series        the bar series to get split begin indexes of
     * @param splitDuration the duration between 2 splits
     * @return a list of begin indexes after split
     */
    public static List<Integer> getSplitBeginIndexes(BarSeries series, Duration splitDuration) {
        ArrayList<Integer> beginIndexes = new ArrayList<>();

        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();

        // Adding the first begin index
        beginIndexes.add(beginIndex);

        // Building the first interval before next split
        ZonedDateTime beginInterval = series.getFirstBar().getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(splitDuration);

        for (int i = beginIndex; i <= endIndex; i++) {
            // For each bar...
            ZonedDateTime barTime = series.getBar(i).getEndTime();
            if (barTime.isBefore(beginInterval) || !barTime.isBefore(endInterval)) {
                // Bar out of the interval
                if (!endInterval.isAfter(barTime)) {
                    // Bar after the interval
                    // --> Adding a new begin index
                    beginIndexes.add(i);
                }

                // Building the new interval before next split
                beginInterval = endInterval.isBefore(barTime) ? barTime : endInterval;
                endInterval = beginInterval.plus(splitDuration);
            }
        }
        return beginIndexes;
    }

    /**
     * Returns a new bar series which is a view of a subset of the current series.
     *
     * The new series has begin and end indexes which correspond to the bounds of
     * the sub-set into the full series.<br>
     * The bar of the series are shared between the original bar series and the
     * returned one (i.e. no copy).
     *
     * @param series     the bar series to get a sub-series of
     * @param beginIndex the begin index (inclusive) of the bar series
     * @param duration   the duration of the bar series
     * @return a constrained {@link BarSeries bar series} which is a sub-set of the
     *         current series
     */
    public static BarSeries subseries(BarSeries series, int beginIndex, Duration duration) {

        // Calculating the sub-series interval
        ZonedDateTime beginInterval = series.getBar(beginIndex).getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(duration);

        // Checking bars belonging to the sub-series (starting at the provided index)
        int subseriesNbBars = 0;
        int endIndex = series.getEndIndex();
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each bar...
            ZonedDateTime barTime = series.getBar(i).getEndTime();
            if (barTime.isBefore(beginInterval) || !barTime.isBefore(endInterval)) {
                // Bar out of the interval
                break;
            }
            // Bar in the interval
            // --> Incrementing the number of bars in the subseries
            subseriesNbBars++;
        }

        return series.getSubSeries(beginIndex, beginIndex + subseriesNbBars);
    }

    /**
     * Splits the bar series into sub-series lasting sliceDuration.<br>
     * The current bar series is splitted every splitDuration.<br>
     * The last sub-series may last less than sliceDuration.
     *
     * @param series        the bar series to split
     * @param splitDuration the duration between 2 splits
     * @param sliceDuration the duration of each sub-series
     * @return a list of sub-series
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
     * @param series the bar series
     * @return a map (key: strategy, value: name) of trading strategies
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
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        List<BarSeries> subseries = splitSeries(series, Duration.ofHours(6), Duration.ofDays(7));

        // Building the map of strategies
        Map<Strategy, String> strategies = buildStrategiesMap(series);

        // The analysis criterion
        AnalysisCriterion returnCriterion = new GrossReturnCriterion();

        for (BarSeries slice : subseries) {
            // For each sub-series...
            System.out.println("Sub-series: " + slice.getSeriesPeriodDescription());
            BarSeriesManager sliceManager = new BarSeriesManager(slice);
            for (Map.Entry<Strategy, String> entry : strategies.entrySet()) {
                Strategy strategy = entry.getKey();
                String name = entry.getValue();
                // For each strategy...
                TradingRecord tradingRecord = sliceManager.run(strategy);
                Num profit = returnCriterion.calculate(slice, tradingRecord);
                System.out.println("\tProfit for " + name + ": " + profit);
            }
            Strategy bestStrategy = returnCriterion.chooseBest(sliceManager, TradeType.BUY,
                    new ArrayList<Strategy>(strategies.keySet()));
            System.out.println("\t\t--> Best strategy: " + strategies.get(bestStrategy) + "\n");
        }
    }

}
