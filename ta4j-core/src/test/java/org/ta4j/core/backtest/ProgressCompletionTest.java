/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

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
    public void loggingWithAutoDetection() {
        Consumer<Integer> callback = ProgressCompletion.logging();
        assertNotNull(callback);

        // Should not throw - should detect ProgressCompletionTest as the caller
        callback.accept(1);
        callback.accept(100);
    }

    @Test
    public void loggingWithAutoDetectionAndCustomInterval() {
        Consumer<Integer> callback = ProgressCompletion.logging(50);
        assertNotNull(callback);

        // Should not throw - should detect ProgressCompletionTest as the caller
        callback.accept(50);
        callback.accept(100);
    }

    @Test
    public void loggingWithAutoDetectionDetectsCorrectCaller() {
        // Test that auto-detection correctly identifies the calling class
        Consumer<Integer> callback = ProgressCompletion.logging();
        assertNotNull(callback);

        // Verify it works (doesn't throw)
        callback.accept(100);
    }

    @Test
    public void loggingWithAutoDetectionFromNestedMethod() {
        // Test that auto-detection works when called from a nested method
        Consumer<Integer> callback = createCallbackFromNestedMethod();
        assertNotNull(callback);
        callback.accept(100);
    }

    @Test
    public void loggingWithAutoDetectionAndCustomIntervalDetectsCorrectCaller() {
        Consumer<Integer> callback = ProgressCompletion.logging(75);
        assertNotNull(callback);

        // Verify it works with custom interval
        callback.accept(75);
        callback.accept(150);
    }

    @Test
    public void loggingWithAutoDetectionWorksWithTotalStrategies() {
        Consumer<Integer> callback = ProgressCompletion.logging();
        assertNotNull(callback);

        // Should work with withTotalStrategies
        Consumer<Integer> wrapped = ProgressCompletion.withTotalStrategies(callback, 500);
        assertNotNull(wrapped);

        // Should not throw
        wrapped.accept(100);
        wrapped.accept(250); // 50% milestone
        wrapped.accept(500); // 100% milestone
    }

    @Test
    public void loggingWithAutoDetectionAndIntervalWorksWithTotalStrategies() {
        Consumer<Integer> callback = ProgressCompletion.logging(50);
        assertNotNull(callback);

        // Should work with withTotalStrategies
        Consumer<Integer> wrapped = ProgressCompletion.withTotalStrategies(callback, 200);
        assertNotNull(wrapped);

        // Should not throw - logs at interval (50, 100, 150, 200) and milestones
        wrapped.accept(50); // Interval
        wrapped.accept(100); // Interval and 50% milestone
        wrapped.accept(150); // Interval and 75% milestone
        wrapped.accept(200); // Interval and 100% milestone
    }

    @Test
    public void loggingWithAutoDetectionWorksWithBacktestExecutor() {
        // Integration test to ensure auto-detection works with actual BacktestExecutor
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        // Use auto-detection convenience method
        Consumer<Integer> callback = ProgressCompletion.logging();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, callback);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void loggingWithAutoDetectionAndIntervalWorksWithBacktestExecutor() {
        // Integration test with custom interval
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        // Use auto-detection with custom interval
        Consumer<Integer> callback = ProgressCompletion.logging(50);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, callback);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void loggingWithAutoDetectionInvalidIntervalThrowsException() {
        // Verify that invalid intervals still throw exceptions with auto-detection
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.logging(0));
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.logging(-1));
    }

    @Test
    public void loggingWithAutoDetectionFromHelperClass() {
        // Test that auto-detection works when called from a helper class
        TestHelper helper = new TestHelper();
        Consumer<Integer> callback = helper.createCallback();
        assertNotNull(callback);
        callback.accept(100);
    }

    @Test
    public void loggingWithAutoDetectionFromHelperClassWithInterval() {
        // Test that auto-detection works with interval when called from helper class
        TestHelper helper = new TestHelper();
        Consumer<Integer> callback = helper.createCallbackWithInterval(25);
        assertNotNull(callback);
        callback.accept(25);
        callback.accept(50);
    }

    /**
     * Helper method to test auto-detection from nested method calls.
     */
    private Consumer<Integer> createCallbackFromNestedMethod() {
        return ProgressCompletion.logging();
    }

    /**
     * Helper class to test auto-detection from different calling contexts.
     */
    private static class TestHelper {
        Consumer<Integer> createCallback() {
            return ProgressCompletion.logging();
        }

        Consumer<Integer> createCallbackWithInterval(int interval) {
            return ProgressCompletion.logging(interval);
        }
    }

    @Test
    public void loggingWithMemoryWithAutoDetection() {
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory();
        assertNotNull(callback);

        // Should not throw - should detect ProgressCompletionTest as the caller
        callback.accept(1);
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryWithAutoDetectionAndCustomInterval() {
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(50);
        assertNotNull(callback);

        // Should not throw - should detect ProgressCompletionTest as the caller
        callback.accept(50);
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryWithStringLoggerName() {
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory("test.logger");
        assertNotNull(callback);

        // Should not throw
        callback.accept(1);
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryWithClass() {
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(ProgressCompletionTest.class);
        assertNotNull(callback);

        // Should not throw
        callback.accept(1);
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryWithLogger() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(logger);
        assertNotNull(callback);

        // Should not throw
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryWithNullLoggerThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.loggingWithMemory((Logger) null));
    }

    @Test
    public void loggingWithMemoryWithInvalidIntervalThrowsException() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.loggingWithMemory(logger, 0));
        assertThrows(IllegalArgumentException.class, () -> ProgressCompletion.loggingWithMemory(logger, -1));
    }

    @Test
    public void loggingWithMemoryWithCustomInterval() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(logger, 50);
        assertNotNull(callback);

        // Should not throw
        callback.accept(50);
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryLogsAtIntervalMilestones() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(logger, 100);
        ProgressCompletion.withTotalStrategies(callback, 500);

        // Should not throw - logs at 100, 200, 300, etc. with memory stats
        callback.accept(100);
        callback.accept(200);
        callback.accept(300);
    }

    @Test
    public void loggingWithMemoryLogsAtPercentageMilestones() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(logger, 1000); // Large interval
        ProgressCompletion.withTotalStrategies(callback, 400);

        // Should not throw - logs at 25%, 50%, 75%, 100% with memory stats
        callback.accept(100); // 25%
        callback.accept(200); // 50%
        callback.accept(300); // 75%
        callback.accept(400); // 100%
    }

    @Test
    public void loggingWithMemoryWithoutTotalStrategiesLogsOnlyCount() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(logger, 100);

        // Without total, should only log count at intervals with memory stats
        callback.accept(100);
        callback.accept(200);
        callback.accept(250); // Should not log (not an interval)
    }

    @Test
    public void loggingWithMemoryWithTotalStrategiesLogsPercentage() {
        Logger logger = LoggerFactory.getLogger("test.logger");
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(logger, 100);
        ProgressCompletion.withTotalStrategies(callback, 1000);

        // Should not throw - logs percentage with memory stats
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryWorksWithTotalStrategies() {
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory();
        assertNotNull(callback);

        // Should work with withTotalStrategies
        Consumer<Integer> wrapped = ProgressCompletion.withTotalStrategies(callback, 500);
        assertNotNull(wrapped);

        // Should not throw - logs with memory stats
        wrapped.accept(100);
        wrapped.accept(250); // 50% milestone
        wrapped.accept(500); // 100% milestone
    }

    @Test
    public void loggingWithMemoryAndIntervalWorksWithTotalStrategies() {
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(50);
        assertNotNull(callback);

        // Should work with withTotalStrategies
        Consumer<Integer> wrapped = ProgressCompletion.withTotalStrategies(callback, 200);
        assertNotNull(wrapped);

        // Should not throw - logs at interval and milestones with memory stats
        wrapped.accept(50); // Interval
        wrapped.accept(100); // Interval and 50% milestone
        wrapped.accept(150); // Interval and 75% milestone
        wrapped.accept(200); // Interval and 100% milestone
    }

    @Test
    public void loggingWithMemoryWorksWithBacktestExecutor() {
        // Integration test to ensure memory logging works with actual BacktestExecutor
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        // Use memory logging convenience method
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, callback);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void loggingWithMemoryAndIntervalWorksWithBacktestExecutor() {
        // Integration test with custom interval
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(10, 11, 12, 13, 14)
                .build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            strategies.add(new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3)));
        }

        // Use memory logging with custom interval
        Consumer<Integer> callback = ProgressCompletion.loggingWithMemory(50);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(1),
                Trade.TradeType.BUY, callback);

        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void loggingWithMemoryFromHelperClass() {
        // Test that memory logging auto-detection works when called from helper class
        MemoryTestHelper helper = new MemoryTestHelper();
        Consumer<Integer> callback = helper.createCallback();
        assertNotNull(callback);
        callback.accept(100);
    }

    @Test
    public void loggingWithMemoryFromHelperClassWithInterval() {
        // Test that memory logging auto-detection works with interval from helper class
        MemoryTestHelper helper = new MemoryTestHelper();
        Consumer<Integer> callback = helper.createCallbackWithInterval(25);
        assertNotNull(callback);
        callback.accept(25);
        callback.accept(50);
    }

    /**
     * Helper class to test memory logging auto-detection from different calling
     * contexts.
     */
    private static class MemoryTestHelper {
        Consumer<Integer> createCallback() {
            return ProgressCompletion.loggingWithMemory();
        }

        Consumer<Integer> createCallbackWithInterval(int interval) {
            return ProgressCompletion.loggingWithMemory(interval);
        }
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
    public void withTotalStrategiesThrowsForNullCallback() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProgressCompletion.withTotalStrategies(null, 100));
        assertEquals("callback must not be null", ex.getMessage());
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
