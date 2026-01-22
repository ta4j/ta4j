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

public class LossIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public LossIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 7, 4, 3, 3, 5, 3, 2)
                .build();
    }

    @Test
    public void lossUsingClosePrice() {
        var loss = new LossIndicator(new ClosePriceIndicator(data));
        assertNumEquals(0, loss.getValue(0));
        assertNumEquals(0, loss.getValue(1));
        assertNumEquals(0, loss.getValue(2));
        assertNumEquals(0, loss.getValue(3));
        assertNumEquals(1, loss.getValue(4));
        assertNumEquals(0, loss.getValue(5));
        assertNumEquals(0, loss.getValue(6));
        assertNumEquals(3, loss.getValue(7));
        assertNumEquals(1, loss.getValue(8));
        assertNumEquals(0, loss.getValue(9));
        assertNumEquals(0, loss.getValue(10));
        assertNumEquals(2, loss.getValue(11));
        assertNumEquals(1, loss.getValue(12));
    }
}
