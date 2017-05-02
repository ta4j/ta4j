/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.Period;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;
import ta4jexamples.strategies.GlobalExtremaStrategy;
import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.strategies.RSI2Strategy;

/**
 * Walk-forward optimization example.
 * <p>
 * @see http://en.wikipedia.org/wiki/Walk_forward_optimization
 * @see http://www.futuresmag.com/2010/04/01/can-your-system-do-the-walk
 */
public class WalkForward {

    /**
     * @param series the time series
     * @return a map (key: strategy, value: name) of trading strategies
     */
    public static Map<Strategy, String> buildStrategiesMap(TimeSeries series) {
        HashMap<Strategy, String> strategies = new HashMap<Strategy, String>();
        strategies.put(CCICorrectionStrategy.buildStrategy(series), "CCI Correction");
        strategies.put(GlobalExtremaStrategy.buildStrategy(series), "Global Extrema");
        strategies.put(MovingMomentumStrategy.buildStrategy(series), "Moving Momentum");
        strategies.put(RSI2Strategy.buildStrategy(series), "RSI-2");
        return strategies;
    }

    public static void main(String[] args) {
        // Splitting the series into slices
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();
        List<TimeSeries> subseries = series.split(Period.hours(6), Period.weeks(1));

        // Building the map of strategies
        Map<Strategy, String> strategies = buildStrategiesMap(series);

        // The analysis criterion
        AnalysisCriterion profitCriterion = new TotalProfitCriterion();

        for (TimeSeries slice : subseries) {
            // For each sub-series...
            System.out.println("Sub-series: " + slice.getSeriesPeriodDescription());
            for (Map.Entry<Strategy, String> entry : strategies.entrySet()) {
                Strategy strategy = entry.getKey();
                String name = entry.getValue();
                // For each strategy...
                TradingRecord tradingRecord = slice.run(strategy);
                double profit = profitCriterion.calculate(slice, tradingRecord);
                System.out.println("\tProfit for " + name + ": " + profit);
            }
            Strategy bestStrategy = profitCriterion.chooseBest(slice, new ArrayList<Strategy>(strategies.keySet()));
            System.out.println("\t\t--> Best strategy: " + strategies.get(bestStrategy) + "\n");
        }
    }
}
