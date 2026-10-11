/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.walkforward.WalkForwardConfig;

public class ExitOnRunEndModelTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();

    @Test
    public void closesOpenPositionAtTheLastCloseAndChargesTheTransactionCost() {
        BarSeries series = seriesWithOpensAndCloses(4);
        BarSeriesManager manager = new BarSeriesManager(series, new LinearTransactionCostModel(0.01d),
                new ZeroCostModel(), new ExitOnRunEndModel(new TradeOnCurrentCloseModel()));
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule());

        TradingRecord record = manager.run(strategy, TradeType.BUY, numFactory.two());

        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
        Trade exit = record.getPositions().getFirst().getExit();
        assertEquals(3, exit.getIndex());
        assertEquals(series.getBar(3).getClosePrice(), exit.getPricePerAsset());
        assertEquals(numFactory.two(), exit.getAmount());
        // 1% of the 2 x close(3) exit value.
        Num expectedCost = series.getBar(3)
                .getClosePrice()
                .multipliedBy(numFactory.two())
                .multipliedBy(numFactory.numOf(0.01d));
        assertEquals(0, expectedCost.compareTo(exit.getCost()));
    }

    @Test
    public void leavesClosedAndEmptyRecordsUntouched() {
        BarSeries series = seriesWithOpensAndCloses(3);
        ExitOnRunEndModel model = new ExitOnRunEndModel(new TradeOnCurrentCloseModel());
        TradingRecord closed = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));
        List<Trade> tradesBefore = closed.getTrades();
        TradingRecord empty = new BaseTradingRecord();

        model.onRunEnd(2, closed, series);
        model.onRunEnd(2, empty, series);

        assertEquals(tradesBefore, closed.getTrades());
        assertTrue(closed.isClosed());
        assertTrue(empty.getTrades().isEmpty());
        assertTrue(empty.isClosed());
    }

    @Test
    public void delegatesEveryCallToTheWrappedModel() {
        BarSeries series = seriesWithOpensAndCloses(3);
        RecordingModel delegate = new RecordingModel(new TradeExecutionModel.ExecutionTarget(2, numFactory.hundred()));
        ExitOnRunEndModel model = new ExitOnRunEndModel(delegate);
        TradingRecord record = new BaseTradingRecord();

        model.onBar(0, record, series);
        model.execute(1, record, series, numFactory.one());
        TradeExecutionModel.ExecutionTarget target = model.estimateEntryTarget(1, series, TradeType.SELL);
        model.onRunEnd(2, record, series);
        model.onRunEnd(2, record);

        assertSame(delegate.target, target);
        assertEquals(List.of("onBar 0", "execute 1 1", "estimate 1 SELL", "onRunEnd 2 with series",
                "onRunEnd 2 without series"), delegate.calls);
        assertThrows(NullPointerException.class, () -> new ExitOnRunEndModel(null));
    }

    @Test
    public void runEndWithoutTheSeriesDoesNotExit() {
        BarSeries series = seriesWithOpensAndCloses(3);
        ExitOnRunEndModel model = new ExitOnRunEndModel(new TradeOnCurrentCloseModel());
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series));

        model.onRunEnd(2, record);

        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(1, record.getTrades().size());
    }

    @Test
    public void nextOpenSignalOnTheRunEndIsExitedAtTheRunEndCloseNeverAfterTheRun() {
        BarSeries series = seriesWithOpensAndCloses(6);
        BarSeriesManager manager = new BarSeriesManager(series, new ExitOnRunEndModel(new TradeOnNextOpenModel()));
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));

        TradingRecord record = manager.run(strategy, TradeType.BUY, 1, 3);

        // The entry signal on bar 1 fills at bar 2's open. The exit signal on the
        // run's last bar would fill at bar 4's open, after the run: it does not
        // fill, and the wrapper exits at bar 3's close instead.
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
        Position position = record.getPositions().getFirst();
        assertEquals(2, position.getEntry().getIndex());
        assertEquals(series.getBar(2).getOpenPrice(), position.getEntry().getPricePerAsset());
        assertEquals(3, position.getExit().getIndex());
        assertEquals(series.getBar(3).getClosePrice(), position.getExit().getPricePerAsset());
        assertNotEquals(series.getBar(4).getOpenPrice(), position.getExit().getPricePerAsset());

        // An entry signal on the run's last bar never fills, so there is nothing
        // to exit.
        TradingRecord lateEntry = manager.run(new BaseStrategy(new FixedRule(3), new FixedRule()), TradeType.BUY, 1, 3);
        assertTrue(lateEntry.getTrades().isEmpty());
        assertTrue(lateEntry.isClosed());
    }

    @Test
    public void stopLimitPendingEntryIsExpiredByTheDelegateBeforeTheExit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(100d).lowPrice(100d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(102d).lowPrice(99d).closePrice(101d).volume(2d).add();
        StopLimitExecutionModel stopLimit = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.numOf(0.5d), 3);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());

        TradingRecord record = new BarSeriesManager(series, new ExitOnRunEndModel(stopLimit)).run(strategy,
                TradeType.BUY, numFactory.numOf(3));

        // Bar 1's volume of 2 at 50% participation fills 1 of the 3 requested.
        // The delegate expires the order at run end and commits that fill; only
        // then is the committed unit exited at bar 1's close.
        assertTrue(stopLimit.getPendingOrder(record).isEmpty());
        assertEquals(1, stopLimit.getRejectedOrders(record).size());
        assertEquals(numFactory.one(), stopLimit.getRejectedOrders(record).getFirst().filledAmount());
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
        Trade exit = record.getPositions().getFirst().getExit();
        assertEquals(1, exit.getIndex());
        assertEquals(numFactory.one(), exit.getAmount());
        assertEquals(series.getBar(1).getClosePrice(), exit.getPricePerAsset());
    }

    @Test
    public void walkForwardFoldsEndFlatAtTheirTestEndByDefault() {
        BarSeries series = seriesWithOpensAndCloses(48);
        WalkForwardConfig config = new WalkForwardConfig(12, 6, 6, 0, 0, 6, 3, List.of(2), 1, List.of(1), 42L);
        Strategy enterAndHold = new BaseStrategy(BooleanRule.TRUE, BooleanRule.FALSE);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series, new ZeroCostModel(),
                new ZeroCostModel(), new TradeOnNextOpenModel());
        StrategyWalkForwardExecutor alreadyWrapped = new StrategyWalkForwardExecutor(series, new ZeroCostModel(),
                new ZeroCostModel(), new ExitOnRunEndModel(new TradeOnNextOpenModel()));
        BarSeriesManager manager = new BarSeriesManager(series, new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnNextOpenModel());

        assertFoldsEndFlat(series, executor.execute(enterAndHold, TradeType.BUY, numFactory.one(), config));
        assertFoldsEndFlat(series, alreadyWrapped.execute(enterAndHold, TradeType.BUY, numFactory.one(), config));
        assertFoldsEndFlat(series, manager.runWalkForward(enterAndHold, TradeType.BUY, numFactory.one(), config));
        // A plain run keeps the position open for criteria to mark or ignore.
        assertTrue(manager.run(enterAndHold, TradeType.BUY, 12, 17).getCurrentPosition().isOpened());
    }

    private static void assertFoldsEndFlat(BarSeries series, StrategyWalkForwardExecutionResult result) {
        assertFalse(result.folds().isEmpty());
        for (StrategyWalkForwardExecutionResult.FoldResult fold : result.folds()) {
            TradingRecord record = fold.tradingRecord();
            int testEnd = fold.split().testEnd();
            assertTrue(fold.split().foldId(), record.isClosed());
            assertEquals(1, record.getPositionCount());
            Trade exit = record.getPositions().getFirst().getExit();
            assertEquals(testEnd, exit.getIndex());
            assertEquals(series.getBar(testEnd).getClosePrice(), exit.getPricePerAsset());
        }
    }

    @Test
    public void lotAwareRecordEndsFullyFlat() {
        BarSeries series = seriesWithOpensAndCloses(3);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(Trade.buyAt(0, numFactory.hundred(), numFactory.one()));
        record.operate(Trade.buyAt(1, numFactory.numOf(110d), numFactory.two()));
        assertEquals(2, record.getOpenPositions().size());

        new ExitOnRunEndModel(new TradeOnCurrentCloseModel()).onRunEnd(2, record, series);

        // One exit of the 3 open units at bar 2's close closes both lots.
        assertTrue(record.getOpenPositions().isEmpty());
        assertTrue(record.isClosed());
        assertEquals(2, record.getPositionCount());
        Num exitedAmount = numFactory.zero();
        for (Position position : record.getPositions()) {
            assertEquals(2, position.getExit().getIndex());
            assertEquals(series.getBar(2).getClosePrice(), position.getExit().getPricePerAsset());
            exitedAmount = exitedAmount.plus(position.getExit().getAmount());
        }
        assertEquals(numFactory.numOf(3), exitedAmount);
    }

    /**
     * Builds bars whose open (100 + 10i) differs from their close (105 + 10i), so a
     * fill price identifies the bar and price source it came from.
     */
    private BarSeries seriesWithOpensAndCloses(int barCount) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < barCount; i++) {
            double open = 100d + 10d * i;
            series.barBuilder()
                    .openPrice(open)
                    .highPrice(open + 6d)
                    .lowPrice(open - 1d)
                    .closePrice(open + 5d)
                    .volume(10d)
                    .add();
        }
        return series;
    }

    private static final class RecordingModel implements TradeExecutionModel {

        private final List<String> calls = new ArrayList<>();
        private final ExecutionTarget target;

        private RecordingModel(ExecutionTarget target) {
            this.target = target;
        }

        @Override
        public void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
            calls.add("onBar " + index);
        }

        @Override
        public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
            calls.add("execute " + index + " " + amount);
        }

        @Override
        public ExecutionTarget estimateEntryTarget(int signalIndex, BarSeries barSeries, TradeType tradeType) {
            calls.add("estimate " + signalIndex + " " + tradeType);
            return target;
        }

        @Override
        public void onRunEnd(int lastProcessedIndex, TradingRecord tradingRecord) {
            calls.add("onRunEnd " + lastProcessedIndex + " without series");
        }

        @Override
        public void onRunEnd(int lastProcessedIndex, TradingRecord tradingRecord, BarSeries barSeries) {
            calls.add("onRunEnd " + lastProcessedIndex + " with series");
        }
    }

    @Test
    public void leavesOpenRecordUntouchedWhenRunProcessesNoBars() {
        BarSeries series = seriesWithOpensAndCloses(3);
        BarSeriesManager manager = new BarSeriesManager(series, new ExitOnRunEndModel(new TradeOnCurrentCloseModel()));
        Strategy noSignals = new BaseStrategy(new FixedRule(), new FixedRule());
        TradingRecord outOfRange = new BaseTradingRecord(Trade.buyAt(0, series));
        TradingRecord inverted = new BaseTradingRecord(Trade.buyAt(0, series));

        manager.run(noSignals, outOfRange, numFactory.one(), 3, 4);
        manager.run(noSignals, inverted, numFactory.one(), 2, 1);

        assertTrue(outOfRange.getCurrentPosition().isOpened());
        assertEquals(1, outOfRange.getTrades().size());
        assertTrue(inverted.getCurrentPosition().isOpened());
        assertEquals(1, inverted.getTrades().size());
    }

    @Test
    public void closesAtEachLogicalWindowEndAcrossRetentionShapes() {
        int[][] windows = { { 2, 4, 0 }, { 4, 6, 4 }, { 4, 6, 2 } };
        double[][] rawCloses = { { 10, 11, 12, 13, 14, 15 }, { 12, 13, 14 }, { 10, 11, 12, 13, 14, 15 } };
        String[] scenarios = { "constrained", "pruned", "constrained and pruned" };

        for (int scenario = 0; scenario < windows.length; scenario++) {
            int begin = windows[scenario][0];
            int end = windows[scenario][1];
            BarSeries series = ConstrainedSeriesSupport.offsetSeries(scenarios[scenario], numFactory, begin, end,
                    windows[scenario][2], rawCloses[scenario]);
            BarSeriesManager manager = new BarSeriesManager(series,
                    new ExitOnRunEndModel(new TradeOnCurrentCloseModel()));
            Strategy enterAtLogicalBegin = new BaseStrategy(new FixedRule(begin), BooleanRule.FALSE);

            TradingRecord record = manager.run(enterAtLogicalBegin, TradeType.BUY, numFactory.one());

            assertTrue(scenarios[scenario], record.isClosed());
            Position position = record.getPositions().getFirst();
            assertEquals(scenarios[scenario], begin, position.getEntry().getIndex());
            assertEquals(scenarios[scenario], end, position.getExit().getIndex());
            assertEquals(scenarios[scenario], series.getBar(end).getClosePrice(),
                    position.getExit().getPricePerAsset());
        }
    }
}
