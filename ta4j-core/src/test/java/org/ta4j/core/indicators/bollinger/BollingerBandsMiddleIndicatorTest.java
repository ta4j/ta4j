/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.bollinger;

import static junit.framework.TestCase.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BollingerBandsMiddleIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public BollingerBandsMiddleIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
    }

    @Test
    public void bollingerBandsMiddleUsingSMA() {
        var sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
        var bbmSMA = new BollingerBandsMiddleIndicator(sma);

        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(sma.getValue(i), bbmSMA.getValue(i));
        }
    }
}
