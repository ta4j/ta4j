/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ROCIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final double[] closePriceValues = new double[] { 11045.27, 11167.32, 11008.61, 11151.83, 10926.77, 10868.12,
            10520.32, 10380.43, 10785.14, 10748.26, 10896.91, 10782.95, 10620.16, 10625.83, 10510.95, 10444.37,
            10068.01, 10193.39, 10066.57, 10043.75 };

    private ClosePriceIndicator closePrice;

    public ROCIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closePriceValues).build());
    }

    @Test
    public void getValueWhenBarCountIs12() {
        var roc = new ROCIndicator(closePrice, 12);
        assertEquals(12, roc.getCountOfUnstableBars());

        // Incomplete time frame
        assertNumEquals(0, roc.getValue(0));
        assertNumEquals(1.105, roc.getValue(1));
        assertNumEquals(-0.3319, roc.getValue(2));
        assertNumEquals(0.9648, roc.getValue(3));

        // Complete time frame
        double[] results13to20 = { -3.8488, -4.8489, -4.5206, -6.3439, -7.8592, -6.2083, -4.3131, -3.2434 };
        for (int i = 0; i < results13to20.length; i++) {
            assertNumEquals(results13to20[i], roc.getValue(i + 12));
        }
    }
}
