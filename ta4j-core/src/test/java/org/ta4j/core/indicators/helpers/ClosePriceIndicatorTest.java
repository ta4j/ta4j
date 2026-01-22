/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ClosePriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private ClosePriceIndicator closePrice;

    BarSeries barSeries;

    public ClosePriceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        closePrice = new ClosePriceIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(closePrice.getValue(i), barSeries.getBar(i).getClosePrice());
        }
    }
}
