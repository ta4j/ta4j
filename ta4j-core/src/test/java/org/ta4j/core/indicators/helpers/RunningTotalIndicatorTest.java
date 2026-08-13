/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RunningTotalIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public RunningTotalIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new RunningTotalIndicator(data, (int) params[0]), numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
    }

    @Test
    public void calculate() {
        Indicator<Num> runningTotal = getIndicator(new ClosePriceIndicator(data), 3);
        double[] expected = new double[] { 1.0, 3.0, 6.0, 9.0, 12.0 };
        for (int i = 0; i < expected.length; i++) {
            assertNumEquals(expected[i], runningTotal.getValue(i));
        }
    }

    @Test
    public void recomputesAfterMidSeriesReplace() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5)
                .build();
        RunningTotalIndicator runningTotal = new RunningTotalIndicator(new ClosePriceIndicator(series), 3);

        assertNumEquals(12, runningTotal.getValue(4));

        series.replaceBar(2, series.barBuilder().openPrice(2).highPrice(2).lowPrice(2).closePrice(10).build());
        series.addBar(series.barBuilder().openPrice(6).highPrice(6).lowPrice(6).closePrice(6).build());

        // window [3..5] = closes 4 + 5 + 6, not a stale partial sum
        assertNumEquals(15, runningTotal.getValue(5));
    }

    @Test
    public void fastPathSurvivesAppendOnlyGrowth() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        RunningTotalIndicator runningTotal = new RunningTotalIndicator(new ClosePriceIndicator(series), 3);

        assertNumEquals(9, runningTotal.getValue(3));

        series.addBar(series.barBuilder().openPrice(6).highPrice(6).lowPrice(6).closePrice(6).build());
        assertNumEquals(12, runningTotal.getValue(4));

        series.addBar(series.barBuilder().openPrice(7).highPrice(7).lowPrice(7).closePrice(7).build());
        assertNumEquals(15, runningTotal.getValue(5));
    }

    @Test
    public void anchorsAtBeginIndexAfterRemoval() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4)
                .withMaxBarCount(3).build();
        RunningTotalIndicator runningTotal = new RunningTotalIndicator(new ClosePriceIndicator(series), 2);

        // remaining closes are [2, 3, 4] at indices 1..3
        assertNumEquals(2, runningTotal.getValue(1));
        assertNumEquals(5, runningTotal.getValue(2));
        assertNumEquals(7, runningTotal.getValue(3));
    }

    @Test
    public void recoversWhenInvalidValueLeavesWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        FixedIndicator<Num> base = new FixedIndicator<>(series, numOf(1), NaN.NaN, numOf(3), numOf(4));
        RunningTotalIndicator runningTotal = new RunningTotalIndicator(base, 2);

        assertTrue(runningTotal.getValue(1).isNaN());
        assertTrue(runningTotal.getValue(2).isNaN());
        assertNumEquals(7, runningTotal.getValue(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        Indicator<Num> base = new ClosePriceIndicator(data);
        RunningTotalIndicator indicator = new RunningTotalIndicator(base, 3);

        String json = indicator.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(data, json);

        assertEquals(indicator.toDescriptor(), restored.toDescriptor());
        for (int i = data.getBeginIndex(); i <= data.getEndIndex(); i++) {
            assertNumEquals(indicator.getValue(i), restored.getValue(i));
        }
    }
}
