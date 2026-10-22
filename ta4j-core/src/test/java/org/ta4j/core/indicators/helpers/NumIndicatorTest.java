/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NumIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private NumIndicator closePrice;
    private BarSeries barSeries;

    public NumIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        closePrice = new NumIndicator(barSeries, Bar::getClosePrice);
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        for (int i = 0; i < 10; i++) {
            assertEquals(closePrice.getValue(i), barSeries.getBar(i).getClosePrice());
        }
    }
}
