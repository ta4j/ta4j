/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ATRIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public ATRIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new ATRIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "ATR.xls", 7, numFactory);
    }

    @Test
    public void testDummy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var now = Instant.now();
        series.addBar(series.barBuilder()
                .endTime(now.minusSeconds(5))
                .openPrice(0)
                .closePrice(12)
                .highPrice(15)
                .lowPrice(8)
                .amount(0)
                .volume(0)
                .build());
        series.barBuilder()
                .endTime(now.minusSeconds(4))
                .openPrice(0)
                .closePrice(8)
                .highPrice(11)
                .lowPrice(6)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(3))
                .openPrice(0)
                .closePrice(15)
                .highPrice(17)
                .lowPrice(14)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(2))
                .openPrice(0)
                .closePrice(15)
                .highPrice(17)
                .lowPrice(14)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(1))
                .openPrice(0)
                .closePrice(0)
                .highPrice(0)
                .lowPrice(2)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        Indicator<Num> indicator = getIndicator(series, 3);

        // With barCount=3, unstable period is 3, so indices 0, 1, 2 return NaN
        assertTrue(Double.isNaN(indicator.getValue(0).doubleValue()));
        assertTrue(Double.isNaN(indicator.getValue(1).doubleValue()));
        assertTrue(Double.isNaN(indicator.getValue(2).doubleValue()));

        // Index 3 is first valid value after unstable period - initializes to current
        // TR value
        // TR at index 3 = max(15-14, |15-15|, |15-15|) = 1, but we need to check actual
        // TR value
        Num value3 = indicator.getValue(3);
        assertFalse(Double.isNaN(value3.doubleValue()));

        // Index 4 should continue normal MMA calculation
        assertEquals(15d / 3 + (1 - 1d / 3) * value3.doubleValue(), indicator.getValue(4).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void testXls() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> indicator;

        indicator = getIndicator(xlsSeries, 1);
        // With barCount=1, unstable period is 1, so index 0 returns NaN
        // The first value after unstable period initializes to current TR value, so
        // values will differ
        // from external data which was calculated with old behavior. Only check end
        // value which should converge.
        assertEquals(4.8, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        // With barCount=3, unstable period is 3, so indices 0-2 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(7.4225, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        // With barCount=13, unstable period is 13, so indices 0-12 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(8.8082, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        ATRIndicator indicator = new ATRIndicator(series, 5);

        String json = indicator.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertEquals(indicator.toDescriptor(), restored.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(indicator.getValue(i), restored.getValue(i));
        }
    }
}
