/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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
