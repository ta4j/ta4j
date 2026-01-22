/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TradeCountIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private TradeCountIndicator tradeIndicator;

    BarSeries barSeries;

    public TradeCountIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        tradeIndicator = new TradeCountIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarTrade() {
        for (int i = 0; i < 10; i++) {
            assertEquals((long) tradeIndicator.getValue(i), barSeries.getBar(i).getTrades());
        }
    }
}
