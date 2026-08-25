/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.Level;
import org.junit.Test;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.NumberOfBarsCriterion;
import org.ta4j.core.criteria.commissions.CommissionsCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.num.NaN;
import org.ta4j.core.walkforward.WalkForwardConfig;

public class BacktestExecutorTest {

    private static final NumFactory DECIMAL_NUM_FACTORY = DecimalNumFactory.getInstance();

    private NumFactory numFactory = DoubleNumFactory.getInstance();

    private Num numOf(Number value) {
        return numFactory.numOf(value);
    }

    private void runWithNumFactory(NumFactory factory, Runnable test) {
        NumFactory previousFactory = numFactory;
        numFactory = factory;
        try {
            test.run();
        } finally {
            numFactory = previousFactory;
        }
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
            Strategy runtimeStrategy = result.runtimeReport().strategyRuntimes().get(i).strategy();

            assertNotSame(strategies.get(i), runtimeStrategy);
            assertEquals(strategies.get(i).getName(), runtimeStrategy.getName());
            assertEquals(strategies.get(i).getUnstableBars(), runtimeStrategy.getUnstableBars());
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
    public void executeWithRuntimeReportAcceptsPositionSizer() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        BacktestExecutor executor = new BacktestExecutor(series);
        PositionSizer positionSizer = context -> numFactory.numOf(context.signalIndex() + 1);

        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), positionSizer,
                Trade.TradeType.BUY);
        TradingRecord tradingRecord = result.tradingStatements().getFirst().getTradingRecord();
        Position position = tradingRecord.getPositions().getFirst();

        assertEquals(1, result.tradingStatements().size());
        assertEquals(numFactory.one(), position.getEntry().getAmount());
        assertEquals(numFactory.one(), position.getExit().getAmount());
    }

    @Test
    public void executeWithRuntimeReportAcceptsPositionSizerWithDecimalNum() {
        runWithNumFactory(DECIMAL_NUM_FACTORY, this::executeWithRuntimeReportAcceptsPositionSizer);
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
                    if (completed == 2) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted while simulating callback delay", e);
                        }
                    }
                    lastCompletedCount.set(completed);
                });

        assertEquals(strategies.size(), result.tradingStatements().size());
        assertEquals(strategies.size(), callbackCount.get());
        assertEquals(strategies.size(), lastCompletedCount.get());
    }

    @Test
    public void batchingDecisionUsesConfiguredThreshold() {
        assertFalse(BacktestExecutor.usesBatchedExecution(1000));
        assertTrue(BacktestExecutor.usesBatchedExecution(1001));
    }

    @Test
    public void effectiveBatchSizeCapsVeryLargeWorkloads() {
        assertEquals(500, BacktestExecutor.effectiveBatchSize(1500, 500));
        assertEquals(250, BacktestExecutor.effectiveBatchSize(5001, 500));
        assertEquals(100, BacktestExecutor.effectiveBatchSize(5001, 100));
    }

    @Test
    public void batchedExecutionVisitsEveryIndexExactlyOnce() {
        int itemCount = 7;
        AtomicIntegerArray visits = new AtomicIntegerArray(itemCount);

        BacktestExecutor.forEachBatchIndex(itemCount, 3, index -> visits.incrementAndGet(index));

        for (int index = 0; index < itemCount; index++) {
            assertEquals(1, visits.get(index));
        }
    }

    @Test
    public void batchedExecutionRejectsInvalidBatchSizes() {
        assertThrows(IllegalArgumentException.class, () -> BacktestExecutor.forEachBatchIndex(1, 0, index -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> BacktestExecutor.forEachBatchIndex(1, -1, index -> {
        }));
    }

    @Test
    public void constructorWithTradeExecutionModelUsesConfiguredExecutionPrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12).build();
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        BacktestExecutor executor = new BacktestExecutor(series, new TradeOnCurrentCloseModel());

        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numFactory.one());

        TradingRecord tradingRecord = result.tradingStatements().getFirst().getTradingRecord();
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(series.getBar(0).getClosePrice(), position.getEntry().getPricePerAsset());
        assertEquals(series.getBar(1).getClosePrice(), position.getExit().getPricePerAsset());
    }

    @Test
    public void constructorWithCostModelsAndTradeExecutionModelUsesBoth() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12).build();
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        LinearTransactionCostModel costModel = new LinearTransactionCostModel(0.1);
        BacktestExecutor executor = new BacktestExecutor(series, costModel, new ZeroCostModel(),
                new TradeOnCurrentCloseModel());

        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numFactory.one());

        TradingRecord tradingRecord = result.tradingStatements().getFirst().getTradingRecord();
        Position position = tradingRecord.getPositions().getFirst();
        Trade entry = position.getEntry();
        Trade exit = position.getExit();

        assertSame(costModel, tradingRecord.getTransactionCostModel());
        assertEquals(series.getBar(0).getClosePrice(), entry.getPricePerAsset());
        assertEquals(series.getBar(1).getClosePrice(), exit.getPricePerAsset());
        assertEquals(costModel.calculate(entry.getPricePerAsset(), entry.getAmount()), entry.getCost());
        assertEquals(costModel.calculate(exit.getPricePerAsset(), exit.getAmount()), exit.getCost());
    }

    @Test
    public void constructorWithSeriesManagerUsesItsTradingRecordFactory() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        Strategy strategyOne = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy strategyTwo = new BaseStrategy(new FixedRule(2), new FixedRule(3));
        AtomicInteger createdRecords = new AtomicInteger();
        BarSeriesManager.TradingRecordFactory tradingRecordFactory = (tradeType, startIndex, endIndex,
                transactionCostModel, holdingCostModel) -> {
            createdRecords.incrementAndGet();
            return new TrackingTradingRecord(tradeType, startIndex, endIndex, transactionCostModel, holdingCostModel);
        };
        BarSeriesManager seriesManager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel(), tradingRecordFactory);
        BacktestExecutor executor = new BacktestExecutor(seriesManager);

        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategyOne, strategyTwo),
                numFactory.one());

        assertEquals(2, createdRecords.get());
        assertEquals(2, result.tradingStatements().size());
        assertTrue(result.tradingStatements()
                .stream()
                .map(statement -> statement.getTradingRecord())
                .allMatch(TrackingTradingRecord.class::isInstance));
    }

    @Test
    public void executeAndKeepTopKWithLowerIsBetterCriterion() {
        // Create a series with enough bars for different holding periods
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        // Create strategies with different holding periods (number of bars)
        // Strategy 1: Buy at 0, sell at 1 -> 2 bars
        Strategy strategy1 = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        // Strategy 2: Buy at 0, sell at 2 -> 3 bars
        Strategy strategy2 = new BaseStrategy(new FixedRule(0), new FixedRule(2));
        // Strategy 3: Buy at 0, sell at 3 -> 4 bars
        Strategy strategy3 = new BaseStrategy(new FixedRule(0), new FixedRule(3));
        // Strategy 4: Buy at 0, sell at 4 -> 5 bars
        Strategy strategy4 = new BaseStrategy(new FixedRule(0), new FixedRule(4));
        // Strategy 5: Buy at 0, sell at 5 -> 6 bars
        Strategy strategy5 = new BaseStrategy(new FixedRule(0), new FixedRule(5));
        // Strategy 6: Buy at 0, sell at 6 -> 7 bars (worst for NumberOfBarsCriterion)
        Strategy strategy6 = new BaseStrategy(new FixedRule(0), new FixedRule(6));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3, strategy4, strategy5, strategy6);

        BacktestExecutor executor = new BacktestExecutor(series);
        NumberOfBarsCriterion criterion = new NumberOfBarsCriterion();
        int topK = 3;

        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, numOf(1), Trade.TradeType.BUY,
                criterion, topK, null);

        // Should return top 3 strategies
        assertEquals(topK, result.tradingStatements().size());

        // Verify ordering: best (lowest number of bars) should be first
        var statements = result.tradingStatements();
        Num bars1 = criterion.calculate(series, statements.get(0).getTradingRecord());
        Num bars2 = criterion.calculate(series, statements.get(1).getTradingRecord());
        Num bars3 = criterion.calculate(series, statements.get(2).getTradingRecord());

        // Verify ascending order (best/lowest first) for lower-is-better criterion
        assertTrue("First strategy should have lowest number of bars", bars1.isLessThanOrEqual(bars2));
        assertTrue("Second strategy should have fewer bars than third", bars2.isLessThanOrEqual(bars3));

        // Verify we got the actual top performers (lowest bars)
        assertTrue("Top strategy should have <= 4 bars", bars1.isLessThanOrEqual(numOf(4)));
    }

    @Test
    public void executeAndKeepTopKWithCommissionsCriterion() {
        // Create a series with enough bars for different numbers of trades
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        // Create strategies with different numbers of trades (more trades = more
        // commissions)
        // Strategy 1: Single trade (buy at 0, sell at 6) -> 2 trades (entry + exit) =
        // lowest commissions
        Strategy strategy1 = new BaseStrategy(new FixedRule(0), new FixedRule(6));
        // Strategy 2: Two trades (buy at 0, sell at 3, buy at 4, sell at 6) -> 4 trades
        Strategy strategy2 = new BaseStrategy(new FixedRule(0, 4), new FixedRule(3, 6));
        // Strategy 3: Three trades (buy at 0, sell at 2, buy at 3, sell at 4, buy at 5,
        // sell at 6) -> 6 trades
        Strategy strategy3 = new BaseStrategy(new FixedRule(0, 3, 5), new FixedRule(2, 4, 6));
        // Strategy 4: Four trades -> 8 trades = highest commissions
        Strategy strategy4 = new BaseStrategy(new FixedRule(0, 2, 4, 5), new FixedRule(1, 3, 5, 6));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3, strategy4);

        // Use transaction costs so commissions are non-zero
        double transactionFee = 0.01; // 1% fee
        BacktestExecutor executor = new BacktestExecutor(series, new LinearTransactionCostModel(transactionFee),
                new ZeroCostModel(), new TradeOnNextOpenModel());

        CommissionsCriterion criterion = new CommissionsCriterion();
        int topK = 2;

        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, numOf(100), Trade.TradeType.BUY,
                criterion, topK, null);

        // Should return top 2 strategies
        assertEquals(topK, result.tradingStatements().size());

        // Verify ordering: best (lowest commissions) should be first
        var statements = result.tradingStatements();
        Num commissions1 = criterion.calculate(series, statements.get(0).getTradingRecord());
        Num commissions2 = criterion.calculate(series, statements.get(1).getTradingRecord());

        // Verify ascending order (best/lowest first) for lower-is-better criterion
        assertTrue("First strategy should have lowest commissions", commissions1.isLessThanOrEqual(commissions2));

        // Verify we got the actual top performers (lowest commissions)
        // Strategy 1 should have the lowest commissions (only 2 trades)
        assertTrue("Top strategy should have lowest commissions", commissions1.isLessThanOrEqual(commissions2));
    }

    @Test
    public void executeAndKeepTopKWithCommissionsCriterionWithDecimalNum() {
        runWithNumFactory(DECIMAL_NUM_FACTORY, this::executeAndKeepTopKWithCommissionsCriterion);
    }

    @Test
    public void executeAndKeepTopKWithHigherIsBetterCriterion() {
        // Create a series with increasing prices to generate different returns
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        // Create strategies using the same pattern as
        // executeWithRuntimeReportCollectsMetrics
        // which we know produces trades
        Strategy strategy1 = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategy2 = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));
        Strategy strategy3 = new BaseStrategy(new FixedRule(0, 4), new FixedRule(1, 2));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3);

        BacktestExecutor executor = new BacktestExecutor(series);
        GrossReturnCriterion criterion = new GrossReturnCriterion();

        // First, execute all strategies to get their returns
        BacktestExecutionResult fullResult = executor.executeWithRuntimeReport(strategies, numOf(1),
                Trade.TradeType.BUY, null);
        List<Num> allReturns = new ArrayList<>();
        for (var statement : fullResult.tradingStatements()) {
            Num returnValue = criterion.calculate(series, statement.getTradingRecord());
            if (!returnValue.isNaN()) {
                allReturns.add(returnValue);
            }
        }

        // Skip test if no strategies produced trades
        if (allReturns.isEmpty()) {
            return;
        }

        // Now test executeAndKeepTopK
        int topK = Math.min(2, allReturns.size());
        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, numOf(1), Trade.TradeType.BUY,
                criterion, topK, null);

        // Should return top K strategies
        assertEquals(topK, result.tradingStatements().size());

        // Verify ordering: best (highest return) should be first
        var statements = result.tradingStatements();
        Num return1 = criterion.calculate(series, statements.get(0).getTradingRecord());
        Num return2 = criterion.calculate(series, statements.get(1).getTradingRecord());

        // Verify descending order (best first) - this is the key test for the fix
        // This verifies that criterion.betterThan() is used correctly for
        // higher-is-better criteria
        assertFalse("First strategy should have executed trades", return1.isNaN());
        if (topK > 1) {
            assertFalse("Second strategy should have executed trades", return2.isNaN());
            assertTrue("First strategy should have highest return: " + return1 + " >= " + return2,
                    return1.isGreaterThanOrEqual(return2));
        }
    }

    @Test
    public void executeAndKeepTopKWithTopKGreaterThanStrategies() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        Strategy strategy1 = new BaseStrategy(new FixedRule(0), new FixedRule(2));
        Strategy strategy2 = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        Strategy strategy3 = new BaseStrategy(new FixedRule(0), new FixedRule(4));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3);

        BacktestExecutor executor = new BacktestExecutor(series);
        GrossReturnCriterion criterion = new GrossReturnCriterion();
        int topK = 10; // More than number of strategies

        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, numOf(1), Trade.TradeType.BUY,
                criterion, topK, null);

        // Should return all strategies (min of topK and strategy count)
        assertEquals(strategies.size(), result.tradingStatements().size());
    }

    @Test
    public void executeAndKeepTopKSkipsNaNStrategies() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();

        Strategy strategyWithOneTrade = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy strategyWithTwoTrades = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategyWithoutTrades = new BaseStrategy(new FixedRule(), new FixedRule());

        List<Strategy> strategies = List.of(strategyWithOneTrade, strategyWithTwoTrades, strategyWithoutTrades);

        BacktestExecutor executor = new BacktestExecutor(series);
        AnalysisCriterion criterion = new NaNPenalizingCriterion();

        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, numOf(1), Trade.TradeType.BUY,
                criterion, 2, null);

        assertEquals(2, result.tradingStatements().size());

        Num firstScore = criterion.calculate(series, result.tradingStatements().get(0).getTradingRecord());
        Num secondScore = criterion.calculate(series, result.tradingStatements().get(1).getTradingRecord());

        assertFalse(firstScore.isNaN());
        assertFalse(secondScore.isNaN());
        assertTrue(firstScore.isGreaterThanOrEqual(secondScore));
    }

    @Test
    public void executeAndKeepTopKSkipsNaNStrategiesWithDecimalNum() {
        runWithNumFactory(DECIMAL_NUM_FACTORY, this::executeAndKeepTopKSkipsNaNStrategies);
    }

    @Test
    public void executeAndKeepTopKIsolatesThrowingStrategy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        Strategy oneTrade = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy twoTrades = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy threeTrades = new BaseStrategy(new FixedRule(0, 2, 4), new FixedRule(1, 3, 5));
        Strategy throwing = new ThrowingStrategy(new FixedRule(0), new FixedRule(1), new IllegalStateException("boom"));

        List<Strategy> strategies = List.of(oneTrade, throwing, threeTrades, twoTrades);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, numOf(1), Trade.TradeType.BUY,
                new NumberOfBarsCriterion(), 3, null);

        // Healthy strategies are ranked and returned; the throwing one is skipped
        assertEquals(3, result.tradingStatements().size());
        // NumberOfBarsCriterion: the lower the criterion value, the better
        assertSame(oneTrade, result.tradingStatements().get(0).getStrategy());
        assertSame(twoTrades, result.tradingStatements().get(1).getStrategy());
        assertSame(threeTrades, result.tradingStatements().get(2).getStrategy());
        assertEquals(3, result.runtimeReport().strategyCount());

        List<BacktestExecutionResult.StrategyFailure> failures = executor.getStrategyFailures();
        assertEquals(1, failures.size());
        assertSame(throwing, failures.get(0).strategy());
        assertEquals("boom", failures.get(0).cause().getMessage());
    }

    @Test
    public void executeAndKeepTopKExcludesFailedDurationsFromRuntimeReport() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        Strategy healthy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy throwing = new ThrowingStrategy(new FixedRule(0), new FixedRule(1), new IllegalStateException("boom"));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeAndKeepTopK(List.of(healthy, throwing), numOf(1),
                Trade.TradeType.BUY, new NumberOfBarsCriterion(), 2, null);

        BacktestRuntimeReport report = result.runtimeReport();
        assertEquals(1, report.strategyCount());
        // The failing duration must not pollute the aggregated statistics: with
        // one successful strategy, min and max coincide.
        assertEquals(report.minStrategyRuntime(), report.maxStrategyRuntime());
        assertEquals(1, result.strategyFailures().size());
    }

    @Test
    public void executeAndKeepTopKExcludesCriterionFailuresFromRuntimeReport() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        Strategy healthy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy noTrades = new BaseStrategy(new FixedRule(-1), new FixedRule(-1));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeAndKeepTopK(List.of(healthy, noTrades), numOf(1),
                Trade.TradeType.BUY, new ThrowingOnEmptyRecordCriterion(), 2, null);

        BacktestRuntimeReport report = result.runtimeReport();
        // The criterion threw after the no-trade strategy's statement was
        // generated; its duration must not enter the aggregated statistics:
        // with one successful strategy, min and max coincide.
        assertEquals(1, report.strategyCount());
        assertEquals(report.minStrategyRuntime(), report.maxStrategyRuntime());
        assertEquals(1, result.strategyFailures().size());
        assertEquals("criterion boom", result.strategyFailures().get(0).cause().getMessage());
    }

    @Test
    public void executeWithRuntimeReportExcludesFailedDurations() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        Strategy healthy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy throwing = new ThrowingStrategy(new FixedRule(0), new FixedRule(1), new IllegalStateException("boom"));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(healthy, throwing), numOf(1));

        BacktestRuntimeReport report = result.runtimeReport();
        assertEquals(1, report.strategyCount());
        assertEquals(report.minStrategyRuntime(), report.maxStrategyRuntime());
        assertEquals(1, result.strategyFailures().size());
    }

    @Test
    public void executeAndKeepTopKThrowsWhenAllStrategiesFail() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();

        Strategy throwingOne = new ThrowingStrategy(new FixedRule(0), new FixedRule(1),
                new IllegalStateException("boom"));
        Strategy throwingTwo = new ThrowingStrategy(new FixedRule(0), new FixedRule(1),
                new IllegalStateException("boom"));

        BacktestExecutor executor = new BacktestExecutor(series);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> executor.executeAndKeepTopK(List.of(throwingOne, throwingTwo), numOf(1), Trade.TradeType.BUY,
                        new NumberOfBarsCriterion(), 2, null));

        assertTrue(exception.getMessage().contains("All 2 strategies failed"));
        assertEquals(2, executor.getStrategyFailures().size());
    }

    @Test
    public void executeWithRuntimeReportIsolatesThrowingStrategy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();

        Strategy oneTrade = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        Strategy twoTrades = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy threeTrades = new BaseStrategy(new FixedRule(0, 2, 4), new FixedRule(1, 3, 5));
        Strategy throwing = new ThrowingStrategy(new FixedRule(0), new FixedRule(1), new IllegalStateException("boom"));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor
                .executeWithRuntimeReport(List.of(oneTrade, throwing, threeTrades, twoTrades), numOf(1));

        assertEquals(3, result.tradingStatements().size());
        assertEquals(3, result.runtimeReport().strategyCount());
        assertEquals(1, result.strategyFailures().size());
        assertSame(throwing, result.strategyFailures().getFirst().strategy());
        assertEquals("boom", result.strategyFailures().getFirst().cause().getMessage());

        List<BacktestExecutionResult.StrategyFailure> failures = executor.getStrategyFailures();
        assertEquals(1, failures.size());
        assertSame(throwing, failures.get(0).strategy());
        assertEquals("boom", failures.get(0).cause().getMessage());
    }

    @Test
    public void executionResultFailureMetadataIsolatedFromConcurrentExecutions() throws Exception {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14, 15, 16).build();
        int failureCount = 3;
        RuntimeException firstFailure = new IllegalStateException("first execution");
        Strategy throwing = new ThrowingStrategy(new FixedRule(0), new FixedRule(1), firstFailure);
        Strategy healthy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        List<Strategy> strategies = new ArrayList<>(failureCount + 1);
        for (int i = 0; i < failureCount; i++) {
            strategies.add(throwing);
        }
        strategies.add(healthy);

        // Block the first execution between publishing its failure ledger and
        // assembling its result, so the clearing execution deterministically
        // overlaps that window instead of racing it.
        CountDownLatch ledgerPublished = new CountDownLatch(1);
        CountDownLatch ledgerCleared = new CountDownLatch(1);
        BacktestExecutor executor = new BacktestExecutor(series) {
            @Override
            void afterFailureLedgerPublished() {
                ledgerPublished.countDown();
                try {
                    if (!ledgerCleared.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting for the clearing execution");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };

        TraceTestLogger traceLogger = new TraceTestLogger();
        traceLogger.open();
        traceLogger.setLoggerLevel(BacktestExecutor.class, Level.OFF);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<BacktestExecutionResult> firstExecution = pool
                    .submit(() -> executor.executeWithRuntimeReport(strategies, numOf(1)));

            assertTrue("first execution did not publish its failure ledger",
                    ledgerPublished.await(10, TimeUnit.SECONDS));

            // The first execution is now blocked after publishing: clearing the
            // shared ledger overlaps its result assembly by construction.
            executor.executeWithRuntimeReport(List.of(), numOf(1));
            assertTrue("clearing execution did not clear the shared ledger", executor.getStrategyFailures().isEmpty());
            ledgerCleared.countDown();

            BacktestExecutionResult result = firstExecution.get(10, TimeUnit.SECONDS);
            assertEquals(failureCount, result.strategyFailures().size());
            assertSame(throwing, result.strategyFailures().getFirst().strategy());
            assertSame(firstFailure, result.strategyFailures().getFirst().cause());
        } finally {
            ledgerCleared.countDown();
            pool.shutdownNow();
            traceLogger.close();
        }
    }

    @Test
    public void executeWithRuntimeReportThrowsWhenAllStrategiesFail() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();

        Strategy throwingOne = new ThrowingStrategy(new FixedRule(0), new FixedRule(1),
                new IllegalStateException("boom-one"));
        Strategy throwingTwo = new ThrowingStrategy(new FixedRule(0), new FixedRule(1),
                new IllegalStateException("boom-two"));

        BacktestExecutor executor = new BacktestExecutor(series);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> executor.executeWithRuntimeReport(List.of(throwingOne, throwingTwo), numOf(1)));

        assertTrue(exception.getMessage().contains("All 2 strategies failed"));
        assertEquals(2, executor.getStrategyFailures().size());
        // The aggregate must retain the original failures: the first recorded
        // failure is attached as the cause and the rest are suppressed, so
        // callers keep the original stack traces.
        assertNotNull(exception.getCause());
        assertEquals(1, exception.getSuppressed().length);
        Set<String> retainedMessages = Set.of(exception.getCause().getMessage(),
                exception.getSuppressed()[0].getMessage());
        assertEquals(Set.of("boom-one", "boom-two"), retainedMessages);
    }

    @Test
    public void concurrentExecutionsKeepFailureLedgersIsolated() throws Exception {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        CountDownLatch blockedStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocked = new CountDownLatch(1);
        Strategy blocked = new BlockingThrowingStrategy(new FixedRule(0), new FixedRule(1),
                new IllegalStateException("blocked"), blockedStarted, releaseBlocked);
        Strategy healthy = new BaseStrategy(new FixedRule(), new FixedRule());
        Strategy immediateFailure = new ThrowingStrategy(new FixedRule(0), new FixedRule(1),
                new IllegalStateException("immediate"));
        BacktestExecutor executor = new BacktestExecutor(series);
        AtomicReference<BacktestExecutionResult> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();

        Thread firstExecution = new Thread(() -> {
            try {
                firstResult.set(executor.executeWithRuntimeReport(List.of(blocked, healthy), numOf(1)));
            } catch (Throwable failure) {
                firstFailure.set(failure);
            }
        });
        firstExecution.start();
        try {
            assertTrue(blockedStarted.await(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,
                    () -> executor.executeWithRuntimeReport(List.of(immediateFailure), numOf(1)));
        } finally {
            releaseBlocked.countDown();
            firstExecution.join(10_000);
        }

        assertFalse(firstExecution.isAlive());
        assertEquals(null, firstFailure.get());
        assertEquals(1, firstResult.get().tradingStatements().size());
        assertSame(healthy, firstResult.get().tradingStatements().get(0).getStrategy());
    }

    @Test
    public void emptyExecutionClearsPreviousFailures() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        Strategy throwing = new ThrowingStrategy(new FixedRule(0), new FixedRule(1), new IllegalStateException("boom"));
        BacktestExecutor executor = new BacktestExecutor(series);

        assertThrows(IllegalStateException.class, () -> executor.executeWithRuntimeReport(List.of(throwing), numOf(1)));
        executor.executeWithRuntimeReport(List.of(), numOf(1));

        assertTrue(executor.getStrategyFailures().isEmpty());
    }

    /**
     * Strategy that pauses before throwing, allowing deterministic overlap of two
     * executor calls.
     */
    private static final class BlockingThrowingStrategy extends BaseStrategy {

        private final RuntimeException failure;
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingThrowingStrategy(Rule entryRule, Rule exitRule, RuntimeException failure,
                CountDownLatch started, CountDownLatch release) {
            super(entryRule, exitRule);
            this.failure = failure;
            this.started = started;
            this.release = release;
        }

        @Override
        public boolean shouldOperate(int index, TradingRecord tradingRecord) {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to fail", e);
            }
            throw failure;
        }
    }

    @Test
    public void executeAndKeepTopKAcceptsPositionSizer() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        Strategy smallestEntry = new BaseStrategy(new FixedRule(0), new FixedRule(3));
        Strategy middleEntry = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        Strategy largestEntry = new BaseStrategy(new FixedRule(2), new FixedRule(3));
        List<Strategy> strategies = List.of(largestEntry, middleEntry, smallestEntry);
        PositionSizer positionSizer = context -> numFactory.numOf(context.signalIndex() + 1);
        BacktestExecutor executor = new BacktestExecutor(series, new LinearTransactionCostModel(0.01),
                new ZeroCostModel(), new TradeOnCurrentCloseModel());

        BacktestExecutionResult result = executor.executeAndKeepTopK(strategies, positionSizer, Trade.TradeType.BUY,
                new CommissionsCriterion(), 1, null);
        TradingRecord tradingRecord = result.tradingStatements().getFirst().getTradingRecord();
        Position position = tradingRecord.getPositions().getFirst();

        assertEquals(1, result.tradingStatements().size());
        assertSame(smallestEntry, result.tradingStatements().getFirst().getStrategy());
        assertEquals(numFactory.one(), position.getEntry().getAmount());
        assertEquals(numFactory.one(), position.getExit().getAmount());
    }

    @Test
    public void executeAndKeepTopKAcceptsPositionSizerWithDecimalNum() {
        runWithNumFactory(DECIMAL_NUM_FACTORY, this::executeAndKeepTopKAcceptsPositionSizer);
    }

    @Test
    public void executeWalkForwardRunsStrategyAcrossFolds() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        Strategy strategy = new BaseStrategy(new FixedRule(4, 8, 12), new FixedRule(5, 9, 13));
        WalkForwardConfig config = new WalkForwardConfig(4, 4, 4, 0, 0, 4, 2, List.of(1), 1, List.of(1), 3L);
        BacktestExecutor executor = new BacktestExecutor(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel());

        StrategyWalkForwardExecutionResult result = executor.executeWalkForward(strategy, numOf(1), Trade.TradeType.BUY,
                config);

        BarSeries resultSeries = result.barSeries();
        assertSame(series, resultSeries);
        assertEquals(series.getBarCount(), resultSeries.getBarCount());
        assertFalse(result.folds().isEmpty());
        assertEquals(result.folds().size(), result.runtimeReport().foldRuntimes().size());
    }

    @Test
    public void executeWalkForwardAcceptsPositionSizer() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        Strategy strategy = new BaseStrategy(new FixedRule(4, 8, 12), new FixedRule(5, 9, 13));
        WalkForwardConfig config = new WalkForwardConfig(4, 4, 4, 0, 0, 4, 2, List.of(1), 1, List.of(1), 3L);
        PositionSizer positionSizer = context -> numFactory.numOf(context.entryIndex());
        BacktestExecutor executor = new BacktestExecutor(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel());

        StrategyWalkForwardExecutionResult result = executor.executeWalkForward(strategy, positionSizer,
                Trade.TradeType.BUY, config);

        assertFalse(result.folds().isEmpty());
        assertEquals(result.folds().size(), result.runtimeReport().foldRuntimes().size());
    }

    @Test
    public void executeWithWalkForwardAcceptsPositionSizer() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        Strategy strategy = new BaseStrategy(new FixedRule(4, 8, 12), new FixedRule(5, 9, 13));
        WalkForwardConfig config = new WalkForwardConfig(4, 4, 4, 0, 0, 4, 2, List.of(1), 1, List.of(1), 3L);
        PositionSizer positionSizer = context -> numFactory.numOf(context.entryIndex());
        BacktestExecutor executor = new BacktestExecutor(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel());

        BacktestExecutor.BacktestAndWalkForwardResult result = executor.executeWithWalkForward(strategy, positionSizer,
                Trade.TradeType.BUY, config);

        assertEquals(1, result.backtest().tradingStatements().size());
        assertFalse(result.walkForward().folds().isEmpty());
    }

    @Test
    public void executeWithWalkForwardReturnsCombinedBacktestAndWalkForwardOutputs() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        Strategy strategy = new BaseStrategy(new FixedRule(4, 8, 12), new FixedRule(5, 9, 13));
        WalkForwardConfig config = new WalkForwardConfig(4, 4, 4, 0, 0, 4, 2, List.of(1), 1, List.of(1), 3L);
        BacktestExecutor executor = new BacktestExecutor(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel());

        BacktestExecutor.BacktestAndWalkForwardResult result = executor.executeWithWalkForward(strategy, numOf(1),
                Trade.TradeType.BUY, config);

        assertEquals(1, result.backtest().tradingStatements().size());
        assertFalse(result.walkForward().folds().isEmpty());
        BarSeries backtestSeries = result.backtest().barSeries();
        BarSeries walkForwardSeries = result.walkForward().barSeries();
        assertSame(backtestSeries, walkForwardSeries);
        assertEquals(backtestSeries.getBarCount(), walkForwardSeries.getBarCount());
        assertEquals(backtestSeries.getName(), walkForwardSeries.getName());
    }

    @Test
    public void executeWithWalkForwardReturnsCombinedBacktestAndWalkForwardOutputsWithDecimalNum() {
        runWithNumFactory(DECIMAL_NUM_FACTORY,
                this::executeWithWalkForwardReturnsCombinedBacktestAndWalkForwardOutputs);
    }

    private static final class NaNPenalizingCriterion implements AnalysisCriterion {

        @Override
        public Num calculate(BarSeries series, Position position) {
            if (!position.isClosed()) {
                return NaN.NaN;
            }
            return series.numFactory().numOf(2);
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            int tradeCount = tradingRecord.getTrades().size();
            if (tradeCount == 0) {
                return NaN.NaN;
            }
            return series.numFactory().numOf(tradeCount);
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            if (criterionValue1.isNaN()) {
                return false;
            }
            if (criterionValue2.isNaN()) {
                return true;
            }
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }

    private static final class TrackingTradingRecord extends BaseTradingRecord {

        private TrackingTradingRecord(Trade.TradeType tradeType, int startIndex, int endIndex,
                CostModel transactionCostModel, CostModel holdingCostModel) {
            super(tradeType, startIndex, endIndex, transactionCostModel, holdingCostModel);
        }
    }

    /**
     * Strategy whose execution fails on the first bar, used to verify that a
     * throwing strategy is isolated from healthy strategies.
     */
    private static final class ThrowingStrategy extends BaseStrategy {

        private final RuntimeException failure;

        private ThrowingStrategy(Rule entryRule, Rule exitRule, RuntimeException failure) {
            super(entryRule, exitRule);
            this.failure = failure;
        }

        @Override
        public boolean shouldOperate(int index, TradingRecord tradingRecord) {
            throw failure;
        }
    }

    /**
     * Criterion that throws for empty trading records, used to verify that
     * criterion failures after statement generation are excluded from runtime
     * report aggregation.
     */
    private static final class ThrowingOnEmptyRecordCriterion implements AnalysisCriterion {

        @Override
        public Num calculate(BarSeries series, Position position) {
            return series.numFactory().numOf(1);
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            int tradeCount = tradingRecord.getTrades().size();
            if (tradeCount == 0) {
                throw new IllegalStateException("criterion boom");
            }
            return series.numFactory().numOf(tradeCount);
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }

}
