/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.StochasticIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ZLEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public ZLEMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 15, 20, 18, 17, 18, 15, 12, 10, 8, 5, 2)
                .build();
    }

    @Test
    public void ZLEMAUsingBarCount10UsingClosePrice() {
        var zlema = new ZLEMAIndicator(new ClosePriceIndicator(data), 10);

        assertNumEquals(11.9091, zlema.getValue(9));
        assertNumEquals(8.8347, zlema.getValue(10));
        assertNumEquals(5.7739, zlema.getValue(11));
    }

    @Test
    public void ZLEMAFirstValueShouldBeEqualsToFirstDataValue() {
        var zlema = new ZLEMAIndicator(new ClosePriceIndicator(data), 10);
        assertNumEquals(10, zlema.getValue(0));
    }

    @Test
    public void valuesLessThanBarCountMustBeEqualsToSMAValues() {
        var zlema = new ZLEMAIndicator(new ClosePriceIndicator(data), 10);
        var sma = new SMAIndicator(new ClosePriceIndicator(data), 10);

        for (int i = 0; i < 9; i++) {
            assertEquals(sma.getValue(i), zlema.getValue(i));
        }
    }

    @Test
    public void smallBarCount() {
        var zlema = new ZLEMAIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(10, zlema.getValue(0));
    }

    @Test
    public void retainedWindowReanchorsAfterStochasticInvalidation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : new double[] { 10, 20, 30, 40, 40, 40, 40, 40 }) {
            series.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        ZLEMAIndicator zlema = new ZLEMAIndicator(stochastic, 2);
        zlema.getValue(7); // NaN chain before the advance

        series.setMaximumBarCount(5);

        assertEquals(3, series.getBeginIndex());
        assertNumEquals(0, zlema.getValue(3));
    }
}
