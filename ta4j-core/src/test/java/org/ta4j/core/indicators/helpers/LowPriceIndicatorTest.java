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

public class LowPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private LowPriceIndicator lowPriceIndicator;

    private BarSeries barSeries;

    public LowPriceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        lowPriceIndicator = new LowPriceIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarLowPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(lowPriceIndicator.getValue(i), barSeries.getBar(i).getLowPrice());
        }
    }
}
