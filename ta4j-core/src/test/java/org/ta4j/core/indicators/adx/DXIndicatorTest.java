/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DXIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public DXIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new DXIndicator(data, (int) params[0]), numFactory);
    }

    @Test
    public void cachedHistoricalValuesReanchorAfterPruning() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] prices = { 10, 12, 11, 15, 13, 16, 12, 17, 14, 18 };
        for (int i = 0; i < prices.length; i++) {
            double price = prices[i];
            series.barBuilder()
                    .openPrice(price)
                    .highPrice(price + i % 3 + 1)
                    .lowPrice(price - i % 2 - 1)
                    .closePrice(price)
                    .volume(100 + i)
                    .add();
        }
        series.setMaximumBarCount(10);
        Indicator<Num> reused = new DXIndicator(series, 2);
        for (int i = 0; i < series.getEndIndex(); i++) {
            reused.getValue(i);
        }
        series.barBuilder().openPrice(9).highPrice(11).lowPrice(8).closePrice(9).volume(110).add();
        Indicator<Num> fresh = new DXIndicator(series, 2);
        for (int i = series.getBeginIndex(); i < series.getEndIndex(); i++) {
            assertNumEquals(fresh.getValue(i), reused.getValue(i));
        }
    }

    @Test
    public void unstableBarsFollowDirectionalIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();

        Indicator<Num> oneBar = getIndicator(series, 1);
        assertEquals(2, oneBar.getCountOfUnstableBars());

        Indicator<Num> threeBars = getIndicator(series, 3);
        assertEquals(4, threeBars.getCountOfUnstableBars());

        Indicator<Num> thirteenBars = getIndicator(series, 13);
        assertEquals(14, thirteenBars.getCountOfUnstableBars());
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new DXIndicator(series, 7), stableIndexes(series)));
    }

}
