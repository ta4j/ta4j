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

public class ConnorsRSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ConnorsRSIIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculatesConnorsRsi() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61,
                        46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64, 45.21, 45.21, 45.19)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new ConnorsRSIIndicator(closePrice);

        String[] expected;
        if (numFactory instanceof DecimalNumFactory) {
            expected = new String[] { null, null, null, "12.18567070657102", "69.06755217121677", "72.5059147004284",
                    "73.73677101725837", "79.5876838805176", "83.86597359199957", "75.29421051103307",
                    "35.28572816944667", "52.19165504295467", "24.98574645545766", "71.81989521691763",
                    "47.16058202103873", "28.91898638400797", "52.93858460896397", "75.7062756340410",
                    "34.03076412591793", "13.89468230877114", "11.90368970832415", "42.13166112940083",
                    "33.61725512301057" };
        } else {
            expected = new String[] { null, null, null, "12.185670706570884", "69.06755217121675", "72.50591470042842",
                    "73.73677101725839", "79.58768388051763", "83.86597359199958", "75.29421051103309",
                    "35.28572816944672", "52.1916550429547", "24.98574645545763", "71.81989521691764",
                    "47.16058202103873", "28.918986384007955", "52.93858460896397", "75.70627563404095",
                    "34.03076412591793", "13.894682308771143", "11.903689708324118", "42.131661129400804",
                    "33.61725512301053" };
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
    public void propagatesNaNValues() {
        Assume.assumeFalse(numFactory instanceof DecimalNumFactory);
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, Double.NaN, 11, 12)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new ConnorsRSIIndicator(closePrice, 3, 2, 3);

        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(2).isNaN()).isTrue();
    }

    @Test
    public void unstableBarsAccountForPercentRank() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new ConnorsRSIIndicator(closePrice, 2, 2, 4);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(5);
    }
}
