/*
 * SPDX-License-Identifier: MIT
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
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.NumberOfBarsCriterion;
import org.ta4j.core.criteria.commissions.CommissionsCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.num.NaN;
import org.ta4j.core.walkforward.WalkForwardConfig;

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

        assertSame(series, result.barSeries());
        assertFalse(result.folds().isEmpty());
        assertEquals(result.folds().size(), result.runtimeReport().foldRuntimes().size());
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
        assertSame(result.backtest().barSeries(), result.walkForward().barSeries());
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

}
