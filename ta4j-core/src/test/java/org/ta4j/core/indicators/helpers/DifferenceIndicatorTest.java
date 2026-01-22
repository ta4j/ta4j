/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DifferenceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private DifferenceIndicator priceChange;

    private BarSeries barSeries;

    public DifferenceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        priceChange = new DifferenceIndicator(new ClosePriceIndicator(barSeries));
    }

    @Test
    public void indicatorShouldRetrieveBarDifference() {
        assertThat(priceChange.getValue(0).isNaN()).isTrue();
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = barSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = barSeries.getBar(i).getClosePrice();
            assertEquals(priceChange.getValue(i), currentBarClosePrice.minus(previousBarClosePrice));
        }
    }
}
