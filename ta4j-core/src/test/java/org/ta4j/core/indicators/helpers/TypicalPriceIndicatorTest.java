/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TypicalPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TypicalPriceIndicator typicalPriceIndicator;

    private BarSeries barSeries;

    public TypicalPriceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        typicalPriceIndicator = new TypicalPriceIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarHighPrice() {
        for (int i = 0; i < 10; i++) {
            Bar bar = barSeries.getBar(i);
            Num typicalPrice = bar.getHighPrice().plus(bar.getLowPrice()).plus(bar.getClosePrice()).dividedBy(numOf(3));
            assertEquals(typicalPrice, typicalPriceIndicator.getValue(i));
        }
    }
}
