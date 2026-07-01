/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardSplit;

public class BarSeriesManagerTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries seriesForRun;

    private BarSeriesManager manager;

    private Strategy strategy;

    private final Num HUNDRED = numOf(100);

    public BarSeriesManagerTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        seriesForRun = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        seriesForRun.barBuilder().endTime(Instant.parse("2013-01-01T05:00:00Z")).closePrice(1d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2013-08-01T05:00:00Z")).closePrice(2d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2013-10-01T05:00:00Z")).closePrice(3d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2013-12-01T05:00:00Z")).closePrice(4d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2014-02-01T05:00:00Z")).closePrice(5d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2015-01-01T05:00:00Z")).closePrice(6d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2015-08-01T05:00:00Z")).closePrice(7d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2015-10-01T05:00:00Z")).closePrice(8d).add();
        seriesForRun.barBuilder().endTime(Instant.parse("2015-12-01T05:00:00Z")).closePrice(7d).add();

        manager = new BarSeriesManager(seriesForRun, new TradeOnCurrentCloseModel());

        strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstableBars(2); // Strategy would need a real test class
    }

    @Test
    public void runOnWholeSeries() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d)
                .build();
        manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        List<Position> allPositions = manager.run(strategy).getPositions();
        assertEquals(2, allPositions.size());
    }

    @Test
    public void runOnWholeSeriesWithAmount() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d)
                .build();
        manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        List<Position> allPositions = manager.run(strategy, TradeType.BUY, HUNDRED).getPositions();

        assertEquals(2, allPositions.size());
        assertEquals(HUNDRED, allPositions.get(0).getEntry().getAmount());
        assertEquals(HUNDRED, allPositions.get(1).getEntry().getAmount());

    }

    @Test
    public void runWithPositionSizerUsesDynamicEntryAmount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 30, 40).build();
        BarSeriesManager localManager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        PositionSizer positionSizer = context -> numFactory.numOf(context.signalIndex());

        TradingRecord tradingRecord = localManager.run(oneTradeStrategy, TradeType.BUY, positionSizer);
        Position position = tradingRecord.getPositions().getFirst();

        assertEquals(1, tradingRecord.getPositionCount());
        assertEquals(1, position.getEntry().getIndex());
        assertEquals(TradeType.BUY, position.getEntry().getType());
        assertEquals(numFactory.one(), position.getEntry().getAmount());
        assertEquals(2, position.getExit().getIndex());
        assertEquals(numFactory.one(), position.getExit().getAmount());
    }

    @Test
    public void runWithPositionSizerUsesStrategyStartingTypeAndRange() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 30, 40, 50).build();
        BarSeriesManager localManager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(1, 2), new FixedRule(3), TradeType.SELL);
        PositionSizer positionSizer = context -> {
            assertSame(oneTradeStrategy, context.strategy());
            assertSame(series, context.barSeries());
            assertEquals(TradeType.SELL, context.tradeType());
            return context.entryPrice().dividedBy(numFactory.numOf(10));
        };

        TradingRecord tradingRecord = localManager.run(oneTradeStrategy, positionSizer, 2, 3);
        Position position = tradingRecord.getPositions().getFirst();

        assertEquals(1, tradingRecord.getPositionCount());
        assertEquals(2, position.getEntry().getIndex());
        assertEquals(TradeType.SELL, position.getEntry().getType());
        assertEquals(numFactory.numOf(3), position.getEntry().getAmount());
        assertEquals(3, position.getExit().getIndex());
        assertEquals(numFactory.numOf(3), position.getExit().getAmount());
    }

    @Test
    public void runWithPositionSizerAndCustomExecutionModelFallsBackToNextOpenEstimate() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        TradeExecutionModel model = new TradeExecutionModel() {

            @Override
            public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
                tradingRecord.operate(index + 1, barSeries.getBar(index + 1).getOpenPrice(), amount);
            }
        };
        BarSeriesManager localManager = new BarSeriesManager(series, model);
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));

        int[] contextEntryIndex = new int[] { -1 };
        Num[] contextEntryPrice = new Num[] { null };
        PositionSizer positionSizer = context -> {
            contextEntryIndex[0] = context.entryIndex();
            contextEntryPrice[0] = context.entryPrice();
            return numFactory.one();
        };

        localManager.run(oneTradeStrategy, TradeType.BUY, positionSizer);

        assertEquals(1, contextEntryIndex[0]);
        assertEquals(series.getBar(1).getOpenPrice(), contextEntryPrice[0]);
    }

    @Test
    public void runWithPositionSizerUsesCustomExecutionModelEstimate() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10d).closePrice(11d).volume(10d).add();
        series.barBuilder().openPrice(20d).closePrice(21d).volume(10d).add();
        series.barBuilder().openPrice(30d).closePrice(31d).volume(10d).add();
        TradeExecutionModel model = new TradeExecutionModel() {
            @Override
            public TradeExecutionModel.ExecutionTarget estimateEntryTarget(int signalIndex, BarSeries barSeries, TradeType tradeType) {
                return new TradeExecutionModel.ExecutionTarget(signalIndex,
                        barSeries.getBar(signalIndex).getClosePrice().plus(numFactory.one()));
            }

            @Override
            public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
                // no-op
            }
        };
        BarSeriesManager localManager = new BarSeriesManager(series, model);
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));

        int[] contextEntryIndex = new int[] { -1 };
        Num[] contextEntryPrice = new Num[] { null };
        PositionSizer positionSizer = context -> {
            contextEntryIndex[0] = context.entryIndex();
            contextEntryPrice[0] = context.entryPrice();
            return numFactory.one();
        };

        localManager.run(oneTradeStrategy, TradeType.BUY, positionSizer);

        assertEquals(1, contextEntryIndex[0]);
        assertEquals(series.getBar(1).getClosePrice().plus(numFactory.one()), contextEntryPrice[0]);
    }

    @Test
    public void runWithPositionSizerUnresolvableTargetFallsBackSafely() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d).build();
        TradeExecutionModel model = new TradeExecutionModel() {
            @Override
            public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
                // no-op
            }
        };
        BarSeriesManager localManager = new BarSeriesManager(series, model);
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));

        int[] contextSignalIndex = new int[] { -1 };
        int[] contextEntryIndex = new int[] { -1 };
        PositionSizer positionSizer = context -> {
            contextSignalIndex[0] = context.signalIndex();
            contextEntryIndex[0] = context.entryIndex();
            return numFactory.one();
        };

        localManager.run(oneTradeStrategy, TradeType.BUY, positionSizer);

        assertEquals(1, contextSignalIndex[0]);
        assertEquals(1, contextEntryIndex[0]);
    }

    @Test
    public void runWithPositionSizerContextEstimatesStopLimitEntry() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10d).highPrice(10d).lowPrice(10d).closePrice(10d).volume(10d).add();
        series.barBuilder().openPrice(15d).highPrice(15d).lowPrice(10d).closePrice(15d).volume(10d).add();
        series.barBuilder().openPrice(20d).highPrice(20d).lowPrice(20d).closePrice(20d).volume(10d).add();
        StopLimitExecutionModel executionModel = new StopLimitExecutionModel(numFactory.zero(), numOf(0.2),
                numFactory.one(), 1, TradeExecutionModel.PriceSource.CURRENT_CLOSE);
        BarSeriesManager localManager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                executionModel);
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        PositionSizer positionSizer = context -> {
            assertEquals(0, context.signalIndex());
            assertEquals(1, context.entryIndex());
            assertEquals(numOf(12), context.entryPrice());
            return numFactory.one();
        };

        localManager.run(oneTradeStrategy, TradeType.BUY, positionSizer);
    }

    @Test
    public void positionSizerFixedFactoriesUseDefaultAndCustomAmounts() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 30).build();
        BarSeriesManager localManager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));

        TradingRecord unitRecord = localManager.run(oneTradeStrategy, TradeType.BUY, PositionSizer.fixed());
        TradingRecord numberRecord = localManager.run(oneTradeStrategy, TradeType.BUY, PositionSizer.fixed(3));
        TradingRecord numRecord = localManager.run(oneTradeStrategy, TradeType.BUY,
                PositionSizer.fixed(numFactory.two()));

        assertEquals(numFactory.one(), unitRecord.getPositions().getFirst().getEntry().getAmount());
        assertEquals(numFactory.numOf(3), numberRecord.getPositions().getFirst().getEntry().getAmount());
        assertEquals(numFactory.two(), numRecord.getPositions().getFirst().getEntry().getAmount());
    }

    @Test
    public void positionSizerFixedFactoriesRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.fixed(0));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.fixed(-1));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.fixed(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.fixed(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.fixed(numFactory.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> PositionSizer.fixed(DoubleNumFactory.getInstance().numOf(Double.POSITIVE_INFINITY)));
    }

    @Test
    public void positionSizerBalanceUsesMaxAffordableAmountWithEntryFees() {
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));

        assertEntryAmount(10.0, managerWithCosts(10, new ZeroCostModel()).run(oneTradeStrategy, TradeType.BUY,
                PositionSizer.balance(100)));
        assertEntryAmount(9.5, managerWithCosts(10, new FixedTransactionCostModel(5)).run(oneTradeStrategy,
                TradeType.BUY, PositionSizer.balance(100)));
        assertEntryAmount(100.0 / 11.0, managerWithCosts(10, new LinearTransactionCostModel(0.1)).run(oneTradeStrategy,
                TradeType.BUY, PositionSizer.balance(100)));
    }

    @Test
    public void positionSizerBalanceUsesRealizedBalance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 40, 20).build();
        BarSeriesManager localManager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy twoTradeStrategy = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));

        TradingRecord tradingRecord = localManager.run(twoTradeStrategy, TradeType.BUY, PositionSizer.balance(100));
        List<Position> positions = tradingRecord.getPositions();

        assertEquals(numFactory.numOf(10), positions.get(0).getEntry().getAmount());
        assertEquals(numFactory.numOf(5), positions.get(1).getEntry().getAmount());
    }

    @Test
    public void positionSizerBalanceRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.balance(0));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.balance(-1));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.balance(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.balance(Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> PositionSizer.balance(100, null));
    }

    @Test
    public void positionSizerBalanceSupportsCustomRule() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20).build();
        BarSeriesManager localManager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        PositionSizer positionSizer = PositionSizer.balance(100,
                (context, balance) -> context.maxAffordableAmount(balance.dividedBy(numFactory.two())));

        TradingRecord tradingRecord = localManager.run(oneTradeStrategy, TradeType.BUY, positionSizer);

        assertEquals(numFactory.numOf(5), tradingRecord.getPositions().getFirst().getEntry().getAmount());
    }

    @Test
    public void positionSizerKellyUsesCoefficient() {
        Strategy oneTradeStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));

        assertEntryAmount(40.0, managerWithCosts(10, new ZeroCostModel()).run(oneTradeStrategy, TradeType.BUY,
                PositionSizer.kelly(1000, 0.6, 2)));
        assertEntryAmount(20.0, managerWithCosts(10, new ZeroCostModel()).run(oneTradeStrategy, TradeType.BUY,
                PositionSizer.kelly(1000, 0.6, 2, 0.5)));
        assertEntryAmount(48.0, managerWithCosts(10, new ZeroCostModel()).run(oneTradeStrategy, TradeType.BUY,
                PositionSizer.kelly(1000, 0.6, 2, 1.2)));
    }

    @Test
    public void positionSizerKellyRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.kelly(1000, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.kelly(1000, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.kelly(1000, 0.6, 0));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.kelly(1000, 0.6, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.kelly(1000, Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> PositionSizer.kelly(1000, 0.4, 1));
    }

    @Test
    public void runOnSeries() {
        List<Position> positions = manager.run(strategy).getPositions();
        assertEquals(2, positions.size());

        assertEquals(buyAt(2, seriesForRun.getBar(2).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(sellAt(4, seriesForRun.getBar(4).getClosePrice(), numOf(1)), positions.get(0).getExit());

        assertEquals(buyAt(6, seriesForRun.getBar(6).getClosePrice(), numOf(1)), positions.get(1).getEntry());
        assertEquals(sellAt(7, seriesForRun.getBar(7).getClosePrice(), numOf(1)), positions.get(1).getExit());
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Position> positions = manager.run(aStrategy, 0, 3).getPositions();
        assertEquals(1, positions.size());

        assertEquals(buyAt(1, seriesForRun.getBar(1).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(sellAt(3, seriesForRun.getBar(3).getClosePrice(), numOf(1)), positions.get(0).getExit());
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Position> positions = manager.run(aStrategy, TradeType.SELL, 0, 3).getPositions();
        assertEquals(1, positions.size());

        assertEquals(sellAt(1, seriesForRun.getBar(1).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(buyAt(3, seriesForRun.getBar(3).getClosePrice(), numOf(1)), positions.get(0).getExit());
    }

    @Test
    public void runUsesStrategyStartingTypeByDefault() {
        Strategy shortStrategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8), TradeType.SELL);
        shortStrategy.setUnstableBars(2);

        List<Position> positions = manager.run(shortStrategy).getPositions();
        assertEquals(2, positions.size());
        assertEquals(sellAt(2, seriesForRun.getBar(2).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(buyAt(4, seriesForRun.getBar(4).getClosePrice(), numOf(1)), positions.get(0).getExit());
    }

    @Test
    public void runWithIndexesUsesStrategyStartingTypeByDefault() {
        Strategy shortStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3), TradeType.SELL);
        List<Position> positions = manager.run(shortStrategy, 0, 3).getPositions();
        assertEquals(1, positions.size());

        assertEquals(sellAt(1, seriesForRun.getBar(1).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(buyAt(3, seriesForRun.getBar(3).getClosePrice(), numOf(1)), positions.get(0).getExit());
    }

    @Test
    public void runBetweenIndexes() {

        // only 1 entry happened within [0-3]
        TradingRecord tradingRecord = manager.run(strategy, 0, 3);
        List<Position> positions = tradingRecord.getPositions();
        assertEquals(0, tradingRecord.getPositions().size());
        assertEquals(2, tradingRecord.getCurrentPosition().getEntry().getIndex());

        // 1 entry and 1 exit happened within [0-4]
        tradingRecord = manager.run(strategy, 0, 4);
        positions = tradingRecord.getPositions();
        assertEquals(1, positions.size());
        assertEquals(buyAt(2, seriesForRun.getBar(2).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(sellAt(4, seriesForRun.getBar(4).getClosePrice(), numOf(1)), positions.get(0).getExit());

        // no trades happened within [4-4]
        tradingRecord = manager.run(strategy, 4, 4);
        positions = tradingRecord.getPositions();
        assertTrue(positions.isEmpty());

        // 1 entry and 1 exit happened within [5-8]
        tradingRecord = manager.run(strategy, 5, 8);
        positions = tradingRecord.getPositions();
        assertEquals(1, positions.size());
        assertEquals(buyAt(6, seriesForRun.getBar(6).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(sellAt(7, seriesForRun.getBar(7).getClosePrice(), numOf(1)), positions.get(0).getExit());
    }

    @Test
    public void runOnSeriesSlices() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().closePrice(1d).add();
        series.barBuilder().closePrice(2d).add();
        series.barBuilder().closePrice(3d).add();
        series.barBuilder().closePrice(4d).add();
        series.barBuilder().closePrice(5d).add();
        series.barBuilder().closePrice(6d).add();
        series.barBuilder().closePrice(7d).add();
        series.barBuilder().closePrice(8d).add();
        series.barBuilder().closePrice(9d).add();
        series.barBuilder().closePrice(10d).add();

        manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());

        Strategy aStrategy = new BaseStrategy(new FixedRule(0, 3, 5, 7), new FixedRule(2, 4, 6, 9));

        // only 1 entry happened within [0-1]
        TradingRecord tradingRecord = manager.run(aStrategy, 0, 1);
        List<Position> positions = tradingRecord.getPositions();
        assertEquals(0, positions.size());
        assertEquals(0, tradingRecord.getCurrentPosition().getEntry().getIndex());

        // only 1 entry happened within [2-3]
        tradingRecord = manager.run(aStrategy, 2, 3);
        positions = tradingRecord.getPositions();
        assertEquals(0, positions.size());
        assertEquals(3, tradingRecord.getCurrentPosition().getEntry().getIndex());

        // 1 entry and 1 exit happened within [4-6]
        positions = manager.run(aStrategy, 4, 6).getPositions();
        assertEquals(1, positions.size());
        assertEquals(buyAt(5, series.getBar(5).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(sellAt(6, series.getBar(6).getClosePrice(), numOf(1)), positions.get(0).getExit());

        // 1 entry happened within [7-7]
        tradingRecord = manager.run(aStrategy, 7, 7);
        positions = tradingRecord.getPositions();
        assertEquals(0, positions.size());
        assertEquals(7, tradingRecord.getCurrentPosition().getEntry().getIndex());

        // no trade happened within [8-8]
        positions = manager.run(aStrategy, 8, 8).getPositions();
        assertTrue(positions.isEmpty());

        // no trade happened within [9-9]
        positions = manager.run(aStrategy, 9, 9).getPositions();
        assertTrue(positions.isEmpty());
    }

    @Test
    public void invokesExecutionModelOnBarForEachVisitedIndex() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d, 40d).build();
        List<Integer> visitedIndices = new ArrayList<>();
        TradeExecutionModel model = new TradeExecutionModel() {
            @Override
            public void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
                visitedIndices.add(index);
            }

            @Override
            public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
                // no-op
            }
        };
        BarSeriesManager localManager = new BarSeriesManager(series, model);
        Strategy noSignalStrategy = new BaseStrategy(new FixedRule(), new FixedRule());

        localManager.run(noSignalStrategy, 1, 3);

        assertEquals(List.of(1, 2, 3), visitedIndices);
    }

    @Test
    public void onBarCanOperateWithoutAnyStrategySignal() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        TradeExecutionModel model = new TradeExecutionModel() {
            @Override
            public void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
                if (index == 1 && tradingRecord.isClosed()) {
                    tradingRecord.operate(index, barSeries.getBar(index).getClosePrice(), barSeries.numFactory().one());
                } else if (index == 2 && !tradingRecord.isClosed()) {
                    tradingRecord.operate(index, barSeries.getBar(index).getClosePrice(), barSeries.numFactory().one());
                }
            }

            @Override
            public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
                // no-op
            }
        };
        BarSeriesManager localManager = new BarSeriesManager(series, model);
        Strategy noSignalStrategy = new BaseStrategy(new FixedRule(), new FixedRule());

        TradingRecord tradingRecord = localManager.run(noSignalStrategy);

        assertEquals(1, tradingRecord.getPositionCount());
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(1, position.getEntry().getIndex());
        assertEquals(2, position.getExit().getIndex());
    }

    @Test
    public void runWithProvidedTradingRecordReturnsSameInstance() {
        TradingRecord providedRecord = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        TradingRecord returnedRecord = manager.run(strategy, providedRecord, numOf(1), 0, 8);

        assertSame(providedRecord, returnedRecord);
        assertEquals(2, returnedRecord.getPositionCount());
    }

    @Test
    public void runWithProvidedBaseTradingRecordSupportsLiveBacktestStack() {
        BaseTradingRecord liveRecord = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), 0, 8);

        TradingRecord returnedRecord = manager.run(strategy, liveRecord, numOf(1), 0, 8);

        assertSame(liveRecord, returnedRecord);
        assertEquals(2, returnedRecord.getPositionCount());
        Position firstPosition = returnedRecord.getPositions().getFirst();
        assertEquals(2, firstPosition.getEntry().getIndex());
        assertEquals(4, firstPosition.getExit().getIndex());
        assertEquals(TradeType.BUY, firstPosition.getEntry().getType());
        assertEquals(TradeType.SELL, firstPosition.getExit().getType());
    }

    @Test
    public void defaultRunCanUseConfiguredTradingRecordFactory() {
        final int[] capturedBounds = new int[2];
        BarSeriesManager.TradingRecordFactory recordFactory = (tradeType, startIndex, endIndex, txCost, holdCost) -> {
            capturedBounds[0] = startIndex;
            capturedBounds[1] = endIndex;
            return new BaseTradingRecord(tradeType, ExecutionMatchPolicy.FIFO, txCost, holdCost, startIndex, endIndex);
        };
        BarSeriesManager localManager = new BarSeriesManager(seriesForRun, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnCurrentCloseModel(), recordFactory);

        TradingRecord record = localManager.run(strategy, TradeType.BUY, numOf(1), -10, 99);

        assertTrue(record instanceof BaseTradingRecord);
        assertEquals(seriesForRun.getBeginIndex(), capturedBounds[0]);
        assertEquals(seriesForRun.getEndIndex(), capturedBounds[1]);
    }

    @Test
    public void runWalkForwardReturnsAllConfiguredSplits() {
        WalkForwardConfig config = new WalkForwardConfig(3, 2, 2, 0, 0, 2, 1, List.of(), 1, List.of(1), 7L);
        StrategyWalkForwardExecutionResult result = manager.runWalkForward(strategy, config);
        List<WalkForwardSplit> expectedSplits = new AnchoredExpandingWalkForwardSplitter().split(seriesForRun, config);

        assertEquals(expectedSplits.size(), result.folds().size());
        assertEquals(expectedSplits.size(), result.runtimeReport().foldRuntimes().size());
        assertTrue(result.holdoutFold().isPresent());
    }

    @Test
    public void runWalkForwardRespectsExplicitTradeTypeAndAmount() {
        WalkForwardConfig config = new WalkForwardConfig(3, 2, 2, 0, 0, 2, 1, List.of(), 1, List.of(1), 7L);
        List<WalkForwardSplit> splits = new AnchoredExpandingWalkForwardSplitter().split(seriesForRun, config);
        WalkForwardSplit firstSplit = splits.getFirst();
        Strategy foldStrategy = new BaseStrategy(new FixedRule(firstSplit.testStart()),
                new FixedRule(firstSplit.testEnd()), TradeType.SELL);

        StrategyWalkForwardExecutionResult result = manager.runWalkForward(foldStrategy, TradeType.SELL, HUNDRED,
                config);
        Trade entry = result.folds()
                .getFirst()
                .tradingStatement()
                .getTradingRecord()
                .getPositions()
                .getFirst()
                .getEntry();

        assertEquals(TradeType.SELL, entry.getType());
        assertEquals(HUNDRED, entry.getAmount());
    }

    @Test
    public void runWalkForwardWithPositionSizerUsesDynamicAmount() {
        WalkForwardConfig config = new WalkForwardConfig(3, 2, 2, 0, 0, 2, 1, List.of(), 1, List.of(1), 7L);
        List<WalkForwardSplit> splits = new AnchoredExpandingWalkForwardSplitter().split(seriesForRun, config);
        StrategyWalkForwardExecutionResult result = manager.runWalkForward(strategy, TradeType.BUY,
                context -> numFactory.numOf(context.signalIndex()), config);

        assertEquals(splits.size(), result.folds().size());
        for (StrategyWalkForwardExecutionResult.FoldResult fold : result.folds()) {
            if (!fold.tradingRecord().getPositions().isEmpty()) {
                Position firstPosition = fold.tradingRecord().getPositions().getFirst();
                assertEquals(numFactory.numOf(firstPosition.getEntry().getIndex()),
                        firstPosition.getEntry().getAmount());
                assertEquals(firstPosition.getEntry().getAmount(), firstPosition.getExit().getAmount());
            }
        }
    }

    private Trade buyAt(int index, Num price, Num amount) {
        return Trade.buyAt(index, price, amount);
    }

    private Trade sellAt(int index, Num price, Num amount) {
        return Trade.sellAt(index, price, amount);
    }

    private BarSeriesManager managerWithCosts(Number entryPrice, CostModel transactionCostModel) {
        double firstPrice = entryPrice.doubleValue();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(firstPrice, numFactory.numOf(entryPrice).plus(numFactory.one()).doubleValue())
                .build();
        return new BarSeriesManager(series, transactionCostModel, new ZeroCostModel(), new TradeOnCurrentCloseModel());
    }

    private void assertEntryAmount(double expected, TradingRecord tradingRecord) {
        double actual = tradingRecord.getPositions().getFirst().getEntry().getAmount().doubleValue();
        assertEquals(expected, actual, 1e-9);
    }
}
