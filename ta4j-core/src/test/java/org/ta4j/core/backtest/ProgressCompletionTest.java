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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class ProgressCompletionTest {

    @Test
    public void noOpDoesNothing() {
        Consumer<Integer> callback = ProgressCompletion.noOp();
        assertNotNull(callback);

        // Should not throw any exceptions
        callback.accept(1);
        callback.accept(100);
        callback.accept(1000);
    }

    @Test
    public void loggingWithStringLoggerName() {
        Consumer<Integer> callback = ProgressCompletion.logging("test.logger");
        assertNotNull(callback);

        // Should not throw
        callback.accept(1);
        callback.accept(100);
    }

    @Test
    public void loggingWithClass() {
        Consumer<Integer> callback = ProgressCompletion.logging(ProgressCompletionTest.class);
        assertNotNull(callback);

        // Should not throw
        callback.accept(1);
        callback.accept(100);
    }

    @Test
    public void loggingWithLogger() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger);
        assertNotNull(callback);

        // Should not throw
        callback.accept(100);
    }

    @Test
    public void loggingWithNullLoggerThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.logging((Logger) null));
    }

    @Test
    public void loggingWithInvalidIntervalThrowsException() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.logging(logger, 0));
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.logging(logger, -1));
    }

    @Test
    public void loggingWithCustomInterval() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 50);
        assertNotNull(callback);

        // Should not throw
        callback.accept(50);
        callback.accept(100);
    }

    @Test
    public void loggingLogsAtIntervalMilestones() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 100);
        ProgressCompletion.withTotalStrategies(callback, 500);

        // Should not throw - logs at 100, 200, 300, etc.
        callback.accept(100);
        callback.accept(200);
        callback.accept(300);
    }

    @Test
    public void loggingLogsAtPercentageMilestones() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 1000); // Large interval
        ProgressCompletion.withTotalStrategies(callback, 400);

        // Should not throw - logs at 25%, 50%, 75%, 100%
        callback.accept(100); // 25%
        callback.accept(200); // 50%
        callback.accept(300); // 75%
        callback.accept(400); // 100%
    }

    @Test
    public void loggingWithoutTotalStrategiesLogsOnlyCount() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 100);

        // Without total, should only log count at intervals
        callback.accept(100);
        callback.accept(200);
        callback.accept(250); // Should not log (not an interval)
    }

    @Test
    public void loggingWithTotalStrategiesLogsPercentage() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 100);
        ProgressCompletion.withTotalStrategies(callback, 1000);

        // Should not throw
        callback.accept(100);
    }

    @Test
    public void withTotalStrategiesReturnsNullForNullCallback() {
        Consumer<Integer> result = ProgressCompletion.withTotalStrategies(null, 100);
        assertNull(result);
    }

    @Test
    public void withTotalStrategiesReturnsSameCallbackForNonLoggingCallback() {
        AtomicInteger count = new AtomicInteger(0);
        Consumer<Integer> originalCallback = completed -> count.incrementAndGet();

        Consumer<Integer> result = ProgressCompletion.withTotalStrategies(originalCallback, 100);
        assertNotNull(result);

        // Should still work
        result.accept(1);
        assertEquals(1, count.get());
    }

    @Test
    public void loggingHandlesEdgeCases() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 100);
        ProgressCompletion.withTotalStrategies(callback, 1);

        // Single strategy - should not throw
        callback.accept(1);
    }

    @Test
    public void loggingHandlesExactIntervalAndMilestoneOverlap() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 250);
        ProgressCompletion.withTotalStrategies(callback, 1000);

        // 250 is both an interval (250 % 250 == 0) and 25% milestone
        // Should not throw
        callback.accept(250);
    }

    @Test
    public void loggingDoesNotLogBetweenMilestones() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.logging(logger, 1000); // Large interval
        ProgressCompletion.withTotalStrategies(callback, 400);

        // Should not throw
        callback.accept(100); // 25%
        callback.accept(150); // Between milestones
        callback.accept(199); // Between milestones
        callback.accept(200); // 50%
    }

    @Test
    public void loggingWorksWithBacktestExecutor() {
        // Integration test to ensure it works with actual BacktestExecutor
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        Consumer<Integer> callback = ProgressCompletion.logging(ProgressCompletionTest.class);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, callback);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void noOpWorksWithBacktestExecutor() {
        // Integration test to ensure noOp works with actual BacktestExecutor
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        Consumer<Integer> callback = ProgressCompletion.noOp();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, callback);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void defaultNoOpWhenNullCallback() {
        // Integration test to verify default noOp behavior
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        BacktestExecutor executor = new BacktestExecutor(series);
        // Pass null - should use default noOp
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, null);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }
}
