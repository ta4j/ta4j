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

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TrueStrengthIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrueStrengthIndexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculatesTrueStrengthIndex() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 4, 3, 2, 1, 2)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 4, 2);

        String[] expected;
        if (numFactory instanceof DecimalNumFactory) {
            expected = new String[] { null, "100", "100", "100", "100", "46.66666666666667", "-3.111111111111110",
                    "-38.90370370370370", "-62.35456790123456", "-23.75018930041152" };
        } else {
            expected = new String[] { null, "100.0", "100.0", "100.0", "100.0", "46.666666666666664",
                    "-3.111111111111109", "-38.903703703703705", "-62.35456790123457", "-23.750189300411524" };
        }

        for (int i = 0; i < expected.length; i++) {
            Num value = indicator.getValue(i);
            if (expected[i] == null) {
                assertThat(value.isNaN()).isTrue();
            } else {
                assertThat(value).isEqualByComparingTo(numFactory.numOf(expected[i]));
            }
        }
    }

    @Test
    public void returnsValidValuesForNormalData() {
        // Test that normal data produces valid (non-NaN) results after unstable period
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 4, 2);

        // After unstable period, values should be valid (not NaN)
        int unstableBars = indicator.getCountOfUnstableBars();
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(value.isNaN()).isFalse();
        }
    }

    @Test
    public void unstableBarsMatchSmoothingPeriods() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 7, 5);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(12);
    }
}
