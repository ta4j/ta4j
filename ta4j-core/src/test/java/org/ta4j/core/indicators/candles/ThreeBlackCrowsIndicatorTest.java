/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ThreeBlackCrowsIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public ThreeBlackCrowsIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(19).closePrice(19).highPrice(22).lowPrice(15.0).add();
        series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(8.0).add();
        series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17.0).add();
        series.barBuilder().openPrice(19).closePrice(17).highPrice(20).lowPrice(16.9).add();
        series.barBuilder().openPrice(17.5).closePrice(14).highPrice(18).lowPrice(13.9).add();
        series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(11.0).add();
        series.barBuilder().openPrice(12).closePrice(14).highPrice(15).lowPrice(8.0).add();
        series.barBuilder().openPrice(13).closePrice(16).highPrice(16).lowPrice(11.0).add();
    }

    @Test
    public void getValue() {
        var tbc = new ThreeBlackCrowsIndicator(series, 3, 0.1);
        assertFalse(tbc.getValue(0));
        assertFalse(tbc.getValue(1));
        assertFalse(tbc.getValue(2));
        assertFalse(tbc.getValue(3));
        assertFalse(tbc.getValue(4));
        assertTrue(tbc.getValue(5));
        assertFalse(tbc.getValue(6));
        assertFalse(tbc.getValue(7));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List
                .of(serializationFixture(series, new ThreeBlackCrowsIndicator(series, 5, 1.0), stableIndexes(series)));
    }

    @Test
    public void unstableBarsCoverSmaBaselineWindow() {
        for (int barCount : new int[] { 2, 4, 8 }) {
            BarSeries boundarySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            // Bullish pre-bars with a large lower shadow (shadow 2, average 2)
            for (int i = 0; i < barCount - 1; ++i) {
                boundarySeries.barBuilder()
                        .openPrice(20 + i)
                        .closePrice(21 + i)
                        .highPrice(22 + i)
                        .lowPrice(18 + i)
                        .add();
            }
            // White candle at index barCount - 1
            boundarySeries.barBuilder()
                    .openPrice(20 + barCount)
                    .closePrice(22 + barCount)
                    .highPrice(23.5 + barCount)
                    .lowPrice(18 + barCount)
                    .add();
            // Three black crows with very short lower shadows (0.1 < 2 * 0.1)
            boundarySeries.barBuilder()
                    .openPrice(23 + barCount)
                    .closePrice(20 + barCount)
                    .highPrice(23 + barCount)
                    .lowPrice(19.9 + barCount)
                    .add();
            boundarySeries.barBuilder()
                    .openPrice(22.5 + barCount)
                    .closePrice(19 + barCount)
                    .highPrice(22.5 + barCount)
                    .lowPrice(18.9 + barCount)
                    .add();
            boundarySeries.barBuilder()
                    .openPrice(22 + barCount)
                    .closePrice(18 + barCount)
                    .highPrice(22 + barCount)
                    .lowPrice(17.9 + barCount)
                    .add();

            var tbc = new ThreeBlackCrowsIndicator(boundarySeries, barCount, 0.1);
            int boundary = barCount + 2;
            assertEquals(Math.max(4, boundary), tbc.getCountOfUnstableBars());
            for (int i = 0; i < boundary; ++i) {
                assertFalse(tbc.getValue(i));
            }
            assertTrue(tbc.getValue(boundary));
        }
    }

}
