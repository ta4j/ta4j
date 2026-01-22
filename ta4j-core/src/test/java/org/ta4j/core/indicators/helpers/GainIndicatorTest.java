/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class GainIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public GainIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 7, 4, 3, 3, 5, 3, 2)
                .build();
    }

    @Test
    public void gainUsingClosePrice() {
        var gain = new GainIndicator(new ClosePriceIndicator(data));
        assertNumEquals(0, gain.getValue(0));
        assertNumEquals(1, gain.getValue(1));
        assertNumEquals(1, gain.getValue(2));
        assertNumEquals(1, gain.getValue(3));
        assertNumEquals(0, gain.getValue(4));
        assertNumEquals(1, gain.getValue(5));
        assertNumEquals(3, gain.getValue(6));
        assertNumEquals(0, gain.getValue(7));
        assertNumEquals(0, gain.getValue(8));
        assertNumEquals(0, gain.getValue(9));
        assertNumEquals(2, gain.getValue(10));
        assertNumEquals(0, gain.getValue(11));
        assertNumEquals(0, gain.getValue(12));
    }
}
