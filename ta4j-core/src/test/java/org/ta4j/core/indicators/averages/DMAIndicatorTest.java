/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.*;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new DMAIndicator(data, (int) params[0], 5), numFactory);
    }

    private BarSeries data;
    private double[] results = { 1, 1.5, 2, 2.5, 2.6, 3.2, 3.8, 4, 3.8, 3.8, 3.8, 3.4, 3, 3.6, 3.8, 4.4, 5.4, 6.8, 7.6,
            9, 10, 10.6, 10.8, 10.6, 10, 9, 8, 7, 6, 5, 4 };

    private double[] results3 = { 1, 1.5, 2, 3, 3.33333333333333, 3.66666666666667, 4, 4.33333333333333, 4,
            3.33333333333333, 3.33333333333333, 3.33333333333333, 3, 3.66666666666667, 4, 5.66666666666667,
            6.33333333333333, 8, 9, 10, 11, 11.3333333333333, 11, 10, 9, 8, 7, 6, 5, 4, 3 };

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2, 6, 4, 7, 8, 9, 10, 11, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3,
                        2)
                .build();
    }

    @Test
    public void usingBarCount3UsingClosePrice() {
        int displacement = 5;
        DMAIndicator dmaIndicator = new DMAIndicator(new ClosePriceIndicator(data), 3, displacement);

        int unstableBars = dmaIndicator.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertNumEquals(NaN.NaN, dmaIndicator.getValue(i));
        }

        for (int i = unstableBars; i < dmaIndicator.getBarSeries().getBarCount(); i++) {
            assertNumEquals(results3[i - displacement], dmaIndicator.getValue(i));
        }
    }

    @Test
    public void usingBarCount5UsingClosePriceNegativeDisplacement() {
        int displacement = -5;
        DMAIndicator dmaIndicator = new DMAIndicator(new ClosePriceIndicator(data), 5, displacement);

        for (int i = 5; i < dmaIndicator.getBarSeries().getBarCount() - 6; i++) {

            assertNumEquals(results[i - displacement], dmaIndicator.getValue(i));
        }
    }

    @Test
    public void usingBarCount5UsingClosePrice() {
        int displacement = 5;
        DMAIndicator dmaIndicator = new DMAIndicator(new ClosePriceIndicator(data), 5, displacement);

        int unstableBars = dmaIndicator.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertNumEquals(NaN.NaN, dmaIndicator.getValue(i));
        }

        for (int i = unstableBars; i < dmaIndicator.getBarSeries().getBarCount(); i++) {
            assertNumEquals(results[i - displacement], dmaIndicator.getValue(i));
        }
    }

    @Test
    public void whenBarCountIs1ResultShouldBeIndicatorValue() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 5; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i - 5).getClosePrice(), indicator.getValue(i));
        }
    }

}
