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

public class HighPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private HighPriceIndicator highPriceIndicator;

    private BarSeries barSeries;

    public HighPriceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        highPriceIndicator = new HighPriceIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarHighPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(highPriceIndicator.getValue(i), barSeries.getBar(i).getHighPrice());
        }
    }
}
