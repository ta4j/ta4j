/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package ta4jexamples.backtest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import ta4jexamples.loaders.CsvTradesLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates how to use the getTopStrategies API to find and rank the best
 * strategies from a backtest using multiple criteria.
 *
 * <p>
 * This example:
 * <ul>
 * <li>Creates multiple strategies with different parameters
 * <li>Runs them all through a backtest
 * <li>Uses getTopStrategies to get the top 20 strategies sorted by NetProfit
 * first, then by Expectancy for any ties
 * </ul>
 */
public class TopStrategiesExample {

    private static final Logger LOG = LogManager.getLogger(TopStrategiesExample.class);

    public static void main(String[] args) {
        // Load the bar series
        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        // Create multiple strategies to test
        List<Strategy> strategies = createStrategies(series);

        LOG.debug("Testing {} strategies...", strategies.size());

        // Run backtest on all strategies
        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1));

        LOG.debug("Backtest complete. Execution stats: {}", result.runtimeReport());

        // Get the top 20 strategies sorted by NetProfit first, then Expectancy for
        // ties
        AnalysisCriterion netProfitCriterion = new NetProfitCriterion();
        AnalysisCriterion expectancyCriterion = new ExpectancyCriterion();

        List<TradingStatement> topStrategies = result.getTopStrategies(20, netProfitCriterion, expectancyCriterion);

        // Display the top strategies
        LOG.debug("=== Top {} Strategies ===", topStrategies.size());
        for (int i = 0; i < topStrategies.size(); i++) {
            TradingStatement statement = topStrategies.get(i);
            Strategy strategy = statement.getStrategy();

            Num netProfit = netProfitCriterion.calculate(result.barSeries(), statement.getTradingRecord());
            Num expectancy = expectancyCriterion.calculate(result.barSeries(), statement.getTradingRecord());

            LOG.debug("{}. {}", (i + 1), strategy.getName());
            LOG.debug("    Net Profit: {}", netProfit);
            LOG.debug("    Expectancy: {}", expectancy);
            LOG.debug("    Positions:  {}", statement.getTradingRecord().getPositionCount());
        }
    }

    /**
     * Creates a variety of strategies using different moving average periods for
     * testing.
     *
     * @param series the bar series
     * @return a list of strategies to test
     */
    private static List<Strategy> createStrategies(BarSeries series) {
        List<Strategy> strategies = new ArrayList<>();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // Test various combinations of short and long moving averages
        int[] shortPeriods = { 5, 8, 10, 12, 15, 20 };
        int[] longPeriods = { 20, 25, 30, 40, 50, 60 };

        for (int shortPeriod : shortPeriods) {
            for (int longPeriod : longPeriods) {
                if (shortPeriod >= longPeriod) {
                    continue; // Skip if short period is not actually shorter
                }

                // Create SMA crossover strategy
                SMAIndicator shortSma = new SMAIndicator(closePrice, shortPeriod);
                SMAIndicator longSma = new SMAIndicator(closePrice, longPeriod);

                Strategy smaStrategy = new BaseStrategy(String.format("SMA(%d,%d)", shortPeriod, longPeriod),
                        new CrossedUpIndicatorRule(shortSma, longSma), new CrossedDownIndicatorRule(shortSma, longSma));
                strategies.add(smaStrategy);

                // Create EMA crossover strategy
                EMAIndicator shortEma = new EMAIndicator(closePrice, shortPeriod);
                EMAIndicator longEma = new EMAIndicator(closePrice, longPeriod);

                Strategy emaStrategy = new BaseStrategy(String.format("EMA(%d,%d)", shortPeriod, longPeriod),
                        new CrossedUpIndicatorRule(shortEma, longEma), new CrossedDownIndicatorRule(shortEma, longEma));
                strategies.add(emaStrategy);
            }
        }

        return strategies;
    }
}
