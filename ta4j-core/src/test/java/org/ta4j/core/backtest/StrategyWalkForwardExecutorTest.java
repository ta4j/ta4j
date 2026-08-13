/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRunResult;
import org.ta4j.core.walkforward.WalkForwardSplit;

public class StrategyWalkForwardExecutorTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    public void executeProducesFoldResultsAndRuntimeReport() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        WalkForwardConfig config = walkForwardConfig();
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series, new ZeroCostModel(),
                new ZeroCostModel(), new TradeOnCurrentCloseModel());

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, Trade.TradeType.BUY, numFactory.two(),
                config);
        List<WalkForwardSplit> expectedSplits = new AnchoredExpandingWalkForwardSplitter().split(series, config);

        assertEquals(expectedSplits.size(), result.folds().size());
        assertEquals(expectedSplits.size(), result.runtimeReport().foldRuntimes().size());
        assertTrue(result.holdoutFold().isPresent());
        assertFalse(result.inSampleFolds().isEmpty());
        assertFalse(result.outOfSampleFolds().isEmpty());
    }

    @Test
    public void executeWithPositionSizerUsesDynamicAmount() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        WalkForwardConfig config = walkForwardConfig();
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);
        PositionSizer positionSizer = context -> numFactory.numOf(context.entryIndex());

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, Trade.TradeType.BUY, positionSizer,
                config);

        assertEquals(new AnchoredExpandingWalkForwardSplitter().split(series, config).size(), result.folds().size());
        for (StrategyWalkForwardExecutionResult.FoldResult fold : result.folds()) {
            if (!fold.tradingRecord().getPositions().isEmpty()) {
                Position position = fold.tradingRecord().getPositions().getFirst();
                assertEquals(numFactory.numOf(position.getEntry().getIndex()), position.getEntry().getAmount());
                assertEquals(position.getEntry().getAmount(), position.getExit().getAmount());
            }
        }
    }

    @Test
    public void executeReportsProgressPerFold() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        WalkForwardConfig config = walkForwardConfig();
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);
        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicInteger lastCompleted = new AtomicInteger(0);

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, Trade.TradeType.BUY, numFactory.one(),
                config, completed -> {
                    callbackCount.incrementAndGet();
                    lastCompleted.set(completed);
                });

        assertEquals(result.folds().size(), callbackCount.get());
        assertEquals(result.folds().size(), lastCompleted.get());
    }

    @Test
    public void executeUsesStartingTypeAndUnitAmountByDefault() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE, Trade.TradeType.SELL);
        WalkForwardConfig config = walkForwardConfig();
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series, new ZeroCostModel(),
                new ZeroCostModel(), new TradeOnCurrentCloseModel());

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, config);
        StrategyWalkForwardExecutionResult.FoldResult tradedFold = result.folds()
                .stream()
                .filter(fold -> !fold.tradingRecord().getPositions().isEmpty())
                .findFirst()
                .orElse(null);

        assertNotNull(tradedFold);
        Trade entry = tradedFold.tradingRecord().getPositions().getFirst().getEntry();
        assertEquals(Trade.TradeType.SELL, entry.getType());
        assertEquals(series.numFactory().one(), entry.getAmount());
    }

    @Test
    public void resultHelperViewsPartitionFolds() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        WalkForwardConfig config = walkForwardConfig();
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, config);
        int foldCount = result.folds().size();
        int inSampleCount = result.inSampleFolds().size();
        int outOfSampleCount = result.outOfSampleFolds().size();

        assertEquals(foldCount, inSampleCount + outOfSampleCount);
        assertEquals(result.holdoutFold().isPresent(), outOfSampleCount > 0);
    }

    @Test
    public void resultExposesFoldTradingRecords() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, walkForwardConfig());

        assertEquals(result.folds().size(), result.tradingRecords().size());
        for (StrategyWalkForwardExecutionResult.FoldResult fold : result.folds()) {
            assertSame(fold.tradingRecord(), fold.tradingStatement().getTradingRecord());
        }
    }

    @Test
    public void resultCopiesBarSeriesAndAccessorReturnsSnapshots() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, walkForwardConfig());
        BarSeries firstSnapshot = result.barSeries();
        BarSeries secondSnapshot = result.barSeries();
        series.barBuilder().closePrice(250).add();

        assertNotSame(series, firstSnapshot);
        assertNotSame(firstSnapshot, secondSnapshot);
        assertEquals(48, firstSnapshot.getBarCount());
        assertEquals(48, secondSnapshot.getBarCount());
        assertEquals(48, result.barSeries().getBarCount());
    }

    @Test
    public void resultCriterionHelpersUseAnalysisCriterionOnFoldTradingRecords() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);
        StrategyWalkForwardExecutionResult result = executor.execute(strategy, walkForwardConfig());
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();

        List<Num> expectedAll = result.folds()
                .stream()
                .map(fold -> criterion.calculate(series, fold.tradingRecord()))
                .toList();

        assertEquals(expectedAll, result.criterionValues(criterion));
        assertEquals(result.inSampleFolds().size(), result.inSampleCriterionValues(criterion).size());
        assertEquals(result.outOfSampleFolds().size(), result.outOfSampleCriterionValues(criterion).size());
        assertEquals(result.folds().size(), result.criterionValuesByFold(criterion).size());
        for (StrategyWalkForwardExecutionResult.FoldResult fold : result.folds()) {
            assertEquals(criterion.calculate(series, fold.tradingRecord()),
                    result.criterionValuesByFold(criterion).get(fold.split().foldId()));
        }

        if (result.holdoutFold().isPresent()) {
            Num expectedHoldout = criterion.calculate(series, result.holdoutFold().orElseThrow().tradingRecord());
            assertTrue(result.holdoutCriterionValue(criterion).isPresent());
            assertEquals(expectedHoldout, result.holdoutCriterionValue(criterion).orElseThrow());
        } else {
            assertFalse(result.holdoutCriterionValue(criterion).isPresent());
        }
    }

    @Test
    public void executeIsolatesFailingFoldAndContinues() {
        BarSeries series = buildSeries(48);
        WalkForwardConfig config = walkForwardConfig();
        List<WalkForwardSplit> splits = new AnchoredExpandingWalkForwardSplitter().split(series, config);
        WalkForwardSplit failingSplit = splits.get(1);
        Rule throwingRule = (index, tradingRecord) -> {
            if (index >= failingSplit.testStart() && index <= failingSplit.testEnd()) {
                throw new IllegalStateException("synthetic fold evaluation failure");
            }
            return true;
        };
        Strategy strategy = new BaseStrategy(throwingRule, throwingRule);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series, new ZeroCostModel(),
                new ZeroCostModel(), new TradeOnCurrentCloseModel());

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, Trade.TradeType.BUY, numFactory.two(),
                config);

        assertEquals(1, result.foldFailures().size());
        WalkForwardRunResult.FoldFailure failure = result.foldFailures().get(0);
        assertEquals(failingSplit.foldId(), failure.foldId());
        assertTrue(failure.cause() instanceof IllegalStateException);
        assertEquals("synthetic fold evaluation failure", failure.cause().getMessage());
        assertEquals(splits.size() - 1, result.folds().size());
        assertFalse(result.folds().stream().anyMatch(f -> f.split().foldId().equals(failingSplit.foldId())));
        assertEquals(result.folds().size(), result.runtimeReport().foldRuntimes().size());
    }

    @Test
    public void executeAllFoldsFailingRecordsEveryFailureWithoutAborting() {
        BarSeries series = buildSeries(48);
        WalkForwardConfig config = walkForwardConfig();
        Rule alwaysThrowing = (index, tradingRecord) -> {
            throw new IllegalStateException("synthetic fold evaluation failure");
        };
        Strategy strategy = new BaseStrategy(alwaysThrowing, alwaysThrowing);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);

        StrategyWalkForwardExecutionResult result = executor.execute(strategy, config);

        List<WalkForwardSplit> expectedSplits = new AnchoredExpandingWalkForwardSplitter().split(series, config);
        assertEquals(expectedSplits.size(), result.foldFailures().size());
        assertTrue(result.foldFailures().stream()
                .allMatch(f -> f.cause() instanceof IllegalStateException
                        && "synthetic fold evaluation failure".equals(f.cause().getMessage())));
        assertTrue(result.folds().isEmpty());
        assertNotNull(result.runtimeReport());
    }

    private BarSeries buildSeries(int bars) {
        double[] data = new double[bars];
        for (int i = 0; i < bars; i++) {
            data[i] = 100 + (i * 0.5);
        }
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    private static WalkForwardConfig walkForwardConfig() {
        return new WalkForwardConfig(12, 6, 6, 0, 0, 6, 3, List.of(2), 1, List.of(1), 42L);
    }
}
