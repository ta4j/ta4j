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
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Assume;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.DecimalNumFactory;

public class SchaffTrendCycleIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SchaffTrendCycleIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculatesSchaffTrendCycle() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61,
                        46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice);

        String[] expected;
        if (numFactory instanceof DecimalNumFactory) {
            expected = new String[] { "0", "0.0", "0.00", "0.000", "50.0000", "75.00000", "87.500000", "93.7500000",
                    "96.87500000", "98.437500000", "99.2187500000", "99.60937500000", "99.804687500000",
                    "99.9023437500000", "99.95117187500000", "99.97558593750000", "99.98779296875000",
                    "99.99389648437500", "99.99694824218750", "49.99847412109375" };
        } else {
            expected = new String[] { "0.0", "0.0", "0.0", "0.0", "50.0", "75.0", "87.5", "93.75", "96.875", "98.4375",
                    "99.21875", "99.609375", "99.8046875", "99.90234375", "99.951171875", "99.9755859375",
                    "99.98779296875", "99.993896484375", "99.9969482421875", "49.99847412109375" };
        }

        for (int i = 0; i < expected.length; i++) {
            Num value = indicator.getValue(i);
            assertThat(value).isEqualByComparingTo(numFactory.numOf(expected[i]));
        }
    }

    @Test
    public void returnsPreviousValueWhenRangeIsZero() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 5, 5, 5, 5, 5).build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 3, 5, 3, 2);

        for (int i = 1; i < series.getBarCount(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(indicator.getValue(i - 1));
        }
    }

    @Test
    public void propagatesNaNValues() {
        Assume.assumeFalse(numFactory instanceof DecimalNumFactory);
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, Double.NaN, 11, 12, 13)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 5, 10, 3, 3);

        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(2).isNaN()).isTrue();
    }
}
