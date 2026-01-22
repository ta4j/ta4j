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

public class OpenPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private OpenPriceIndicator openPriceIndicator;

    BarSeries barSeries;

    public OpenPriceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        openPriceIndicator = new OpenPriceIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarOpenPrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(openPriceIndicator.getValue(i), barSeries.getBar(i).getOpenPrice());
        }
    }
}
