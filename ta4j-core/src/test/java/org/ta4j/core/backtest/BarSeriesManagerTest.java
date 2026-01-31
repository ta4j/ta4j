/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

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
    public void runOnSeries() {
        List<Position> positions = manager.run(strategy).getPositions();
        assertEquals(2, positions.size());

        assertEquals(Trade.buyAt(2, seriesForRun.getBar(2).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(Trade.sellAt(4, seriesForRun.getBar(4).getClosePrice(), numOf(1)), positions.get(0).getExit());

        assertEquals(Trade.buyAt(6, seriesForRun.getBar(6).getClosePrice(), numOf(1)), positions.get(1).getEntry());
        assertEquals(Trade.sellAt(7, seriesForRun.getBar(7).getClosePrice(), numOf(1)), positions.get(1).getExit());
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Position> positions = manager.run(aStrategy, 0, 3).getPositions();
        assertEquals(1, positions.size());

        assertEquals(Trade.buyAt(1, seriesForRun.getBar(1).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(Trade.sellAt(3, seriesForRun.getBar(3).getClosePrice(), numOf(1)), positions.get(0).getExit());
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Position> positions = manager.run(aStrategy, TradeType.SELL, 0, 3).getPositions();
        assertEquals(1, positions.size());

        assertEquals(Trade.sellAt(1, seriesForRun.getBar(1).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(Trade.buyAt(3, seriesForRun.getBar(3).getClosePrice(), numOf(1)), positions.get(0).getExit());
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
        assertEquals(Trade.buyAt(2, seriesForRun.getBar(2).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(Trade.sellAt(4, seriesForRun.getBar(4).getClosePrice(), numOf(1)), positions.get(0).getExit());

        // no trades happened within [4-4]
        tradingRecord = manager.run(strategy, 4, 4);
        positions = tradingRecord.getPositions();
        assertTrue(positions.isEmpty());

        // 1 entry and 1 exit happened within [5-8]
        tradingRecord = manager.run(strategy, 5, 8);
        positions = tradingRecord.getPositions();
        assertEquals(1, positions.size());
        assertEquals(Trade.buyAt(6, seriesForRun.getBar(6).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(Trade.sellAt(7, seriesForRun.getBar(7).getClosePrice(), numOf(1)), positions.get(0).getExit());
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
        assertEquals(Trade.buyAt(5, series.getBar(5).getClosePrice(), numOf(1)), positions.get(0).getEntry());
        assertEquals(Trade.sellAt(6, series.getBar(6).getClosePrice(), numOf(1)), positions.get(0).getExit());

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
}
