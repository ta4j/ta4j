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
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class BacktestExecutorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BacktestExecutorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void executeWithRuntimeReportCollectsMetrics() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        Strategy strategyOne = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategyTwo = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));
        Strategy strategyThree = new BaseStrategy(new FixedRule(0, 4), new FixedRule(1, 2));

        List<Strategy> strategies = List.of(strategyOne, strategyTwo, strategyThree);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        assertEquals(strategies.size(), result.tradingStatements().size());
        assertEquals(strategies.size(), result.runtimeReport().strategyCount());
        assertEquals(strategies.size(), result.runtimeReport().strategyRuntimes().size());

        for (int i = 0; i < strategies.size(); i++) {
            assertSame(strategies.get(i), result.runtimeReport().strategyRuntimes().get(i).strategy());
        }

        assertFalse(result.runtimeReport()
                .strategyRuntimes()
                .stream()
                .anyMatch(strategyRuntime -> strategyRuntime.runtime().isNegative()));
        assertFalse(result.runtimeReport().overallRuntime().isNegative());

        assertTrue(result.runtimeReport()
                .maxStrategyRuntime()
                .compareTo(result.runtimeReport().minStrategyRuntime()) >= 0);
    }

    @Test
    public void executeWithRuntimeReportHandlesEmptyStrategies() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 6, 7).build();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(), numOf(1));

        assertTrue(result.tradingStatements().isEmpty());
        assertEquals(0, result.runtimeReport().strategyCount());
        assertTrue(result.runtimeReport().strategyRuntimes().isEmpty());
        assertEquals(result.runtimeReport().overallRuntime(), result.runtimeReport().minStrategyRuntime());
        assertEquals(result.runtimeReport().overallRuntime(), result.runtimeReport().maxStrategyRuntime());
        assertEquals(result.runtimeReport().overallRuntime(), result.runtimeReport().averageStrategyRuntime());
        assertEquals(result.runtimeReport().overallRuntime(), result.runtimeReport().medianStrategyRuntime());
    }

    @Test
    public void executeWithProgressCallback() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        Strategy strategyOne = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategyTwo = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));
        Strategy strategyThree = new BaseStrategy(new FixedRule(0, 4), new FixedRule(1, 2));

        List<Strategy> strategies = List.of(strategyOne, strategyTwo, strategyThree);
        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicInteger lastCompletedCount = new AtomicInteger(0);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1), Trade.TradeType.BUY,
                completed -> {
                    callbackCount.incrementAndGet();
                    lastCompletedCount.set(completed);
                });

        assertEquals(strategies.size(), result.tradingStatements().size());
        assertEquals(strategies.size(), callbackCount.get());
        assertEquals(strategies.size(), lastCompletedCount.get());
    }

    @Test
    public void executeWithLargeStrategyCountUsesBatchProcessing() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        // Create more than PARALLEL_THRESHOLD (1000) strategies to trigger batched
        // processing
        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        AtomicInteger progressUpdateCount = new AtomicInteger(0);
        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1), Trade.TradeType.BUY,
                completed -> progressUpdateCount.incrementAndGet());

        assertEquals(strategies.size(), result.tradingStatements().size());
        assertEquals(strategies.size(), result.runtimeReport().strategyCount());
        assertEquals(strategies.size(), progressUpdateCount.get());
    }

    @Test
    public void executeWithCustomBatchSize() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        // Create more than PARALLEL_THRESHOLD strategies
        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        int customBatchSize = 250;
        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1), Trade.TradeType.BUY,
                null, customBatchSize);

        assertEquals(strategies.size(), result.tradingStatements().size());
        assertEquals(strategies.size(), result.runtimeReport().strategyCount());
    }
}
