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
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public VarianceIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9)
                .build();
    }

    @Test
    public void varianceUsingBarCount4UsingClosePrice() {
        final var variance = VarianceIndicator.ofSample(new ClosePriceIndicator(this.data), 4);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.5, variance.getValue(1));
        assertNumEquals(2.0 / 2, variance.getValue(2));
        assertNumEquals(1.66666, variance.getValue(3));
        assertNumEquals(0.66666, variance.getValue(4));
        assertNumEquals(0.33333, variance.getValue(5));
        assertNumEquals(0.66666, variance.getValue(6));
        assertNumEquals(0.66666, variance.getValue(7));
        assertNumEquals(0.66666, variance.getValue(8));
        assertNumEquals(4.66666, variance.getValue(9));
        assertNumEquals(14.0000, variance.getValue(10));
    }

    @Test
    // @DisplayName("Random access that uses slow path")
    public void randomAccess() {
        final var variance = VarianceIndicator.ofSample(new ClosePriceIndicator(this.data), 4);

        assertNumEquals(14.0000, variance.getValue(10));
        assertNumEquals(0.66666, variance.getValue(6));
        assertNumEquals(14.0000, variance.getValue(10));
    }

    @Test
    public void firstValueShouldBeZero() {
        final var variance = VarianceIndicator.ofSample(new ClosePriceIndicator(this.data), 4);
        assertNumEquals(0, variance.getValue(0));
    }

    @Test
    public void varianceShouldBeZeroWhenBarCountIs1() {
        final var variance = VarianceIndicator.ofSample(new ClosePriceIndicator(this.data), 1);
        assertNumEquals(0, variance.getValue(3));
        assertNumEquals(0, variance.getValue(8));
    }

    @Test
    public void varianceUsingBarCount2UsingClosePrice() {
        final var variance = VarianceIndicator.ofSample(new ClosePriceIndicator(this.data), 2);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.5, variance.getValue(1));
        assertNumEquals(0.5, variance.getValue(2));
        assertNumEquals(0.5, variance.getValue(3));
        assertNumEquals(4.5, variance.getValue(9));
        assertNumEquals(40.5, variance.getValue(10));
    }
}
