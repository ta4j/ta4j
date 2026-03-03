/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class TradeOnPriceExecutionModelTest extends AbstractIndicatorTest<BarSeries, Num> {

    public TradeOnPriceExecutionModelTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void tradeOnCurrentCloseExecutesAtCurrentBarClose() {
        BarSeries series = buildSeries();
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));

        TradingRecord tradingRecord = new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy);

        assertEquals(1, tradingRecord.getPositionCount());
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(0, position.getEntry().getIndex());
        assertEquals(1, position.getExit().getIndex());
        assertEquals(series.getBar(0).getClosePrice(), position.getEntry().getPricePerAsset());
        assertEquals(series.getBar(1).getClosePrice(), position.getExit().getPricePerAsset());
    }

    @Test
    public void tradeOnNextOpenExecutesAtNextBarOpen() {
        BarSeries series = buildSeries();
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));

        TradingRecord tradingRecord = new BarSeriesManager(series, new TradeOnNextOpenModel()).run(strategy);

        assertEquals(1, tradingRecord.getPositionCount());
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(1, position.getEntry().getIndex());
        assertEquals(2, position.getExit().getIndex());
        assertEquals(series.getBar(1).getOpenPrice(), position.getEntry().getPricePerAsset());
        assertEquals(series.getBar(2).getOpenPrice(), position.getExit().getPricePerAsset());
    }

    @Test
    public void tradeOnNextOpenSkipsSignalWhenNoNextBarExists() {
        BarSeries series = buildSeries();
        Strategy strategy = new BaseStrategy(new FixedRule(2), new FixedRule(2));

        TradingRecord tradingRecord = new BarSeriesManager(series, new TradeOnNextOpenModel()).run(strategy);

        assertTrue(tradingRecord.getTrades().isEmpty());
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(101d).volume(10d).add();
        series.barBuilder().openPrice(110d).highPrice(112d).lowPrice(108d).closePrice(109d).volume(10d).add();
        series.barBuilder().openPrice(120d).highPrice(122d).lowPrice(118d).closePrice(121d).volume(10d).add();

        return series;
    }
}
