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
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class BarSeriesManagerConstrainedSeriesTest {

    @Test
    public void currentCloseModelClosesOpenPositionUsingRawBarsBeyondConstrainedEndIndex() {
        BarSeries sourceSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(10d, 20d, 30d)
                .build();
        NumFactory numFactory = sourceSeries.numFactory();
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

    @Test
    public void snapshotPreservesLeadingOrphanRawBars() {
        // beginIndex=1, endIndex=2, removedBarsCount=0: the raw bar at index 0
        // is an orphan preceding the logical range. The snapshot must keep the
        // raw-to-logical offset so getBar(1) returns the raw bar at index 1
        // (price 20), not the raw bar at index 0 (price 10).
        BarSeries sourceSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(10d, 20d, 30d)
                .build();
        NumFactory numFactory = sourceSeries.numFactory();
        BaseBarSeries leadingOrphanSeries = new BaseBarSeries("leading-orphan-series",
                List.copyOf(sourceSeries.getBarData()), 1, 2, 0, false, numFactory, new TimeBarBuilderFactory());
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));

        TradingRecord tradingRecord = new BarSeriesManager(leadingOrphanSeries, new TradeOnCurrentCloseModel())
                .run(strategy);

        assertEquals(1, tradingRecord.getPositionCount());
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(1, position.getEntry().getIndex());
        assertEquals(2, position.getExit().getIndex());
        assertEquals(leadingOrphanSeries.getBar(1).getClosePrice(), position.getEntry().getPricePerAsset());
        assertEquals(leadingOrphanSeries.getBar(2).getClosePrice(), position.getExit().getPricePerAsset());
    }

    @Test
    public void closeScanReachesOffsetBarsBeyondEndIndex() {
        // removedBarsCount=10 (leading orphans), raw bars at logical 10, 11, 12,
        // endIndex=11 (one trailing raw bar at logical 12). The close scan must
        // reach logical index 12, not stop at getBarData().size() (3).
        BarSeries sourceSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(10d, 20d, 30d)
                .build();
        NumFactory numFactory = sourceSeries.numFactory();
        BaseBarSeries offsetSeries = new BaseBarSeries("offset-series", List.copyOf(sourceSeries.getBarData()), 10, 11,
                10, false, numFactory, new TimeBarBuilderFactory());
        Strategy strategy = new BaseStrategy(new FixedRule(10), new FixedRule(12));

        TradingRecord tradingRecord = new BarSeriesManager(offsetSeries, new TradeOnCurrentCloseModel()).run(strategy);

        assertEquals(1, tradingRecord.getPositionCount());
        Position position = tradingRecord.getPositions().getFirst();
        assertEquals(10, position.getEntry().getIndex());
        assertEquals(12, position.getExit().getIndex());
        assertEquals(offsetSeries.getBar(12).getClosePrice(), position.getExit().getPricePerAsset());
    }
}
