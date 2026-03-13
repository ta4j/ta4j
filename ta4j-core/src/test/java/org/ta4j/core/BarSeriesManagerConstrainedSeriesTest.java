/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.rules.FixedRule;

public class BarSeriesManagerConstrainedSeriesTest {

    @Test
    public void currentCloseModelClosesOpenPositionUsingRawBarsBeyondConstrainedEndIndex() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        BarSeries sourceSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BaseBarSeries constrainedSeries = new BaseBarSeries("constrained-series",
                List.copyOf(sourceSeries.getBarData()), 0, 1, true, numFactory, new TimeBarBuilderFactory());
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(2));

        TradingRecord tradingRecord = new BarSeriesManager(constrainedSeries, new TradeOnCurrentCloseModel())
                .run(strategy);

        assertEquals(1, tradingRecord.getPositionCount());
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(0, position.getEntry().getIndex());
        assertEquals(2, position.getExit().getIndex());
        assertEquals(constrainedSeries.getBar(2).getClosePrice(), position.getExit().getPricePerAsset());
    }
}
