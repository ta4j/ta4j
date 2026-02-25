/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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

public class SlippageExecutionModelTest extends AbstractIndicatorTest<BarSeries, Num> {

    public SlippageExecutionModelTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void appliesConfigurableSlippageOnNextOpen() {
        BarSeries series = buildSeries();
        Num slippage = numOf(0.10);

        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        TradingRecord tradingRecord = new BarSeriesManager(series,
                new SlippageExecutionModel(slippage, SlippageExecutionModel.ExecutionPrice.NEXT_OPEN)).run(strategy);

        assertEquals(1, tradingRecord.getPositions().size());
        Position position = tradingRecord.getPositions().getFirst();

        Num one = series.numFactory().one();
        Num expectedEntryPrice = series.getBar(1).getOpenPrice().multipliedBy(one.plus(slippage));
        Num expectedExitPrice = series.getBar(2).getOpenPrice().multipliedBy(one.minus(slippage));

        assertEquals(expectedEntryPrice, position.getEntry().getPricePerAsset());
        assertEquals(expectedExitPrice, position.getExit().getPricePerAsset());
    }

    @Test
    public void appliesConfigurableSlippageOnCurrentClose() {
        BarSeries series = buildSeries();
        Num slippage = numOf(0.05);

        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        TradingRecord tradingRecord = new BarSeriesManager(series,
                new SlippageExecutionModel(slippage, SlippageExecutionModel.ExecutionPrice.CURRENT_CLOSE))
                .run(strategy);

        assertEquals(1, tradingRecord.getPositions().size());
        Position position = tradingRecord.getPositions().getFirst();

        Num one = series.numFactory().one();
        Num expectedEntryPrice = series.getBar(0).getClosePrice().multipliedBy(one.plus(slippage));
        Num expectedExitPrice = series.getBar(1).getClosePrice().multipliedBy(one.minus(slippage));

        assertEquals(expectedEntryPrice, position.getEntry().getPricePerAsset());
        assertEquals(expectedExitPrice, position.getExit().getPricePerAsset());
    }

    @Test
    public void skipsSignalWhenNoNextOpenBarExists() {
        BarSeries series = buildSeries();
        Strategy strategy = new BaseStrategy(new FixedRule(2), new FixedRule(2));

        TradingRecord tradingRecord = new BarSeriesManager(series,
                new SlippageExecutionModel(numOf(0.01), SlippageExecutionModel.ExecutionPrice.NEXT_OPEN)).run(strategy);

        assertTrue(tradingRecord.getTrades().isEmpty());
    }

    @Test
    public void rejectsInvalidSlippageRatios() {
        assertThrows(IllegalArgumentException.class, () -> new SlippageExecutionModel(numFactory.minusOne(),
                SlippageExecutionModel.ExecutionPrice.NEXT_OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> new SlippageExecutionModel(numFactory.one(), SlippageExecutionModel.ExecutionPrice.NEXT_OPEN));
    }

    @Test
    public void exposesConfiguredParameters() {
        Num slippage = numFactory.numOf(0.02);
        SlippageExecutionModel model = new SlippageExecutionModel(slippage,
                SlippageExecutionModel.ExecutionPrice.CURRENT_CLOSE);

        assertEquals(slippage, model.getSlippageRatio());
        assertEquals(SlippageExecutionModel.ExecutionPrice.CURRENT_CLOSE, model.getExecutionPrice());
    }

    @Test
    public void rejectsNullExecutionPrice() {
        assertThrows(NullPointerException.class, () -> new SlippageExecutionModel(numFactory.zero(), null));
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(110d).highPrice(112d).lowPrice(108d).closePrice(109d).volume(10d).add();
        series.barBuilder().openPrice(120d).highPrice(122d).lowPrice(118d).closePrice(121d).volume(10d).add();

        return series;
    }
}
