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
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.ProgressCompletion;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import ta4jexamples.loaders.AdaptiveJsonBarsSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
public class TopStrategiesExampleBacktest {

    // PERFORMANCE NOTE: The current ranges generate ~10,000+ strategies.
    // BacktestExecutor automatically uses batch processing for large strategy
    // counts (>1000)
    // to prevent memory exhaustion. If execution is still too slow, consider:
    // 1. Increasing INCREMENT values to reduce grid density
    // 2. Narrowing MIN/MAX ranges based on preliminary results
    // 3. Using coarser increments for initial exploration, then fine-tuning
    // promising regions
    private static final int RSI_BARCOUNT_INCREMENT = 7;
    private static final int RSI_BARCOUNT_MIN = 7;
    private static final int RSI_BARCOUNT_MAX = 49;

    private static final int MOMENTUM_TIMEFRAME_INCREMENT = 100;
    private static final int MOMENTUM_TIMEFRAME_MIN = 100;
    private static final int MOMENTUM_TIMEFRAME_MAX = 400;

    private static final int OVERBOUGHT_THRESHOLD_INCREMENT = 300;
    private static final int OVERBOUGHT_THRESHOLD_MIN = 0;
    private static final int OVERBOUGHT_THRESHOLD_MAX = 1300;

    private static final int OVERSOLD_THRESHOLD_INCREMENT = 200;
    private static final int OVERSOLD_THRESHOLD_MIN = -2000;
    private static final int OVERSOLD_THRESHOLD_MAX = 0;

    private static final double DECAY_FACTOR_INCREMENT = 0.02;
    private static final double DECAY_FACTOR_MIN = 0.9;
    private static final double DECAY_FACTOR_MAX = 1;

    private static final Logger LOG = LogManager.getLogger(TopStrategiesExampleBacktest.class);

    public static void main(String[] args) {
        // Load the bar series
        String jsonOhlcResourceFile = "Coinbase-ETHUSD-Daily-2016-2025.json";

        BarSeries series = null;
        try (InputStream resourceStream = TopStrategiesExampleBacktest.class.getClassLoader()
                .getResourceAsStream(jsonOhlcResourceFile)) {
            series = AdaptiveJsonBarsSerializer.loadSeries(resourceStream);
        } catch (IOException ex) {
            LOG.error("IOException while loading resource: {} - {}", jsonOhlcResourceFile, ex.getMessage());
        }

        Objects.requireNonNull(series, "Bar series was null");

        // Create multiple strategies to test
        List<Strategy> strategies = createStrategies(series);

        LOG.debug("Testing {} strategies...", strategies.size());

        // Run backtest on all strategies with progress logging to this class's logger
        BacktestExecutionResult result = new BacktestExecutor(series).executeWithRuntimeReport(strategies,
                series.numFactory().numOf(1), Trade.TradeType.BUY,
                ProgressCompletion.loggingWithMemory(TopStrategiesExampleBacktest.class));

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

            // Use stored criterion scores if available, otherwise calculate
            Num netProfit = statement.getCriterionScore(netProfitCriterion)
                    .orElseGet(() -> netProfitCriterion.calculate(result.barSeries(), statement.getTradingRecord()));
            Num expectancy = statement.getCriterionScore(expectancyCriterion)
                    .orElseGet(() -> expectancyCriterion.calculate(result.barSeries(), statement.getTradingRecord()));

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

        for (int rsiBarCount = RSI_BARCOUNT_MIN; rsiBarCount <= RSI_BARCOUNT_MAX; rsiBarCount += RSI_BARCOUNT_INCREMENT) {
            for (int timeFrame = MOMENTUM_TIMEFRAME_MIN; timeFrame <= MOMENTUM_TIMEFRAME_MAX; timeFrame += MOMENTUM_TIMEFRAME_INCREMENT) {
                for (int oversoldThreshold = OVERSOLD_THRESHOLD_MIN; oversoldThreshold <= OVERSOLD_THRESHOLD_MAX; oversoldThreshold += OVERSOLD_THRESHOLD_INCREMENT) {
                    for (int overboughtThreshold = OVERBOUGHT_THRESHOLD_MIN; overboughtThreshold <= OVERBOUGHT_THRESHOLD_MAX; overboughtThreshold += OVERBOUGHT_THRESHOLD_INCREMENT) {
                        if (oversoldThreshold < overboughtThreshold) {
                            for (double decayFactor = DECAY_FACTOR_MIN; decayFactor <= DECAY_FACTOR_MAX; decayFactor += DECAY_FACTOR_INCREMENT) {
                                try {
                                    Strategy strategy = createStrategy(series, rsiBarCount, timeFrame,
                                            oversoldThreshold, overboughtThreshold, decayFactor);
                                    strategies.add(strategy);
                                } catch (Exception e) {
                                    // Skip invalid strategy combinations
                                    LOG.debug(
                                            "Skipping invalid strategy combination: rsiBarCount={}, timeFrame={}, oversoldThreshold={}, overboughtThreshold={}, decayFactor={}: {}",
                                            rsiBarCount, timeFrame, oversoldThreshold, overboughtThreshold, decayFactor,
                                            e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }

        return strategies;
    }

    private static Strategy createStrategy(BarSeries series, int rsiBarCount, int timeFrame, int oversoldThreshold,
            int overboughtThreshold, double decayFactor) {
        Objects.requireNonNull(series, "Series cannot be null");

        if (rsiBarCount <= 0) {
            throw new IllegalArgumentException("rsiBarCount should be positive");
        }
        if (timeFrame <= 0) {
            throw new IllegalArgumentException("timeFrame should be positive");
        }

        final ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        NetMomentumIndicator rsiM = NetMomentumIndicator
                .forRsiWithDecay(new RSIIndicator(closePriceIndicator, rsiBarCount), timeFrame, decayFactor);
        Rule entryRule = new CrossedUpIndicatorRule(rsiM, oversoldThreshold);
        Rule exitRule = new CrossedDownIndicatorRule(rsiM, overboughtThreshold);

        String strategyName = "Entry Crossed Up: {rsiBarCount=" + rsiBarCount + ", timeFrame=" + timeFrame
                + ", oversoldThreshold=" + oversoldThreshold + "}, Exit Crossed Down: {rsiBarCount=" + rsiBarCount
                + ", timeFrame=" + timeFrame + ", overboughtThreshold=" + overboughtThreshold + ", decayFactor="
                + decayFactor + "}";

        return new BaseStrategy(strategyName, entryRule, exitRule);
    }
}
