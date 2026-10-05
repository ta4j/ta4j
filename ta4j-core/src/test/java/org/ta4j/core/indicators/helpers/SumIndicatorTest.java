/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class SumIndicatorTest {

    private Indicator<Num> constantIndicator;
    private Indicator<Num> mockIndicator;
    private Indicator<Num> mockIndicator2;
    private SumIndicator sumIndicator;

    @Before
    public void setUp() {
        BarSeries series = new MockBarSeriesBuilder().build();
        constantIndicator = new ConstantIndicator<>(series, series.numFactory().numOf(6));
        mockIndicator = new FixedIndicator<>(series, series.numFactory().numOf(-2.0), series.numFactory().numOf(0.00),
                series.numFactory().numOf(1.00), series.numFactory().numOf(2.53), series.numFactory().numOf(5.87),
                series.numFactory().numOf(6.00), series.numFactory().numOf(10.0));
        mockIndicator2 = new FixedIndicator<>(series, series.numFactory().numOf(0), series.numFactory().numOf(1),
                series.numFactory().numOf(2), series.numFactory().numOf(3), series.numFactory().numOf(10),
                series.numFactory().numOf(-42), series.numFactory().numOf(-1337));
        sumIndicator = new SumIndicator(constantIndicator, mockIndicator, mockIndicator2);
    }

    @Test
    public void getValue() {
        assertNumEquals("4.0", sumIndicator.getValue(0));
        assertNumEquals("7.0", sumIndicator.getValue(1));
        assertNumEquals("9.0", sumIndicator.getValue(2));
        assertNumEquals("11.53", sumIndicator.getValue(3));
        assertNumEquals("21.87", sumIndicator.getValue(4));
        assertNumEquals("-30.0", sumIndicator.getValue(5));
        assertNumEquals("-1321.0", sumIndicator.getValue(6));
    }

    @Test
    public void getDependencies() {
        assertEquals(List.of(constantIndicator, mockIndicator, mockIndicator2), sumIndicator.getDependencies());
    }
}
