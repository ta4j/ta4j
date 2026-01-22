/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ClosePriceRatioIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceRatioIndicator variationIndicator;

    private BarSeries barSeries;

    public ClosePriceRatioIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        variationIndicator = new ClosePriceRatioIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarVariation() {
        assertNumEquals(1, variationIndicator.getValue(0));
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = barSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = barSeries.getBar(i).getClosePrice();
            assertEquals(variationIndicator.getValue(i), currentBarClosePrice.dividedBy(previousBarClosePrice));
        }
    }
}
