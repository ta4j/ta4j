/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers;

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

public class PriceChangePercentageIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private PriceChangePercentageIndicator priceChangePercentage;

    private BarSeries barSeries;

    public PriceChangePercentageIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        priceChangePercentage = new PriceChangePercentageIndicator(new ClosePriceIndicator(barSeries));
    }

    @Test
    public void indicatorShouldRetrieveBarPercentageChange() {
        assertThat(priceChangePercentage.getValue(0).isNaN()).isTrue();
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = barSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = barSeries.getBar(i).getClosePrice();
            Num expectedPercentage = currentBarClosePrice.minus(previousBarClosePrice)
                    .dividedBy(previousBarClosePrice)
                    .multipliedBy(numFactory.hundred());
            assertNumEquals(expectedPercentage, priceChangePercentage.getValue(i));
        }
    }

    @Test
    public void indicatorShouldReturnNaNForZeroPreviousValue() {
        BarSeries seriesWithZero = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 10, 20).build();
        PriceChangePercentageIndicator indicator = new PriceChangePercentageIndicator(
                new ClosePriceIndicator(seriesWithZero));

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue(); // Previous value is 0, division by zero
        assertNumEquals(numFactory.hundred(), indicator.getValue(2)); // (20 - 10) / 10 * 100 = 100%
    }
}
