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

import java.lang.reflect.Field;

import org.junit.Assume;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;

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
            expected = new String[] { null, null, null, "12.18567070657102", "77.4008855045501", "77.5059147004284",
                    "77.0701043505917", "82.7622870551208", "86.84216406819003", "76.68309939992197",
                    "36.0264689101874", "53.40377625507587", "25.23827170798291", "74.1703225673450",
                    "47.89318275363947", "29.23644670146829", "53.7719179422973", "77.1768638693351",
                    "34.46649397341247", "13.89468230877114", "12.07912830481538", "42.76658176432147",
                    "34.19445570021117" };
        } else {
            expected = new String[] { null, null, null, "12.185670706570884", "77.40088550455009", "77.50591470042842",
                    "77.07010435059173", "82.76228705512081", "86.84216406819006", "76.68309939992197",
                    "36.026468910187454", "53.40377625507591", "25.238271707982886", "74.170322567345",
                    "47.89318275363947", "29.23644670146827", "53.77191794229731", "77.17686386933507",
                    "34.46649397341249", "13.894682308771143", "12.079128304815347", "42.76658176432144",
                    "34.19445570021111" };
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

    @Test
    public void percentRankUsesFullLookbackWhenExcludingCurrentBar() throws Exception {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 9, 12, 11)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        int percentRankPeriod = 3;
        final var indicator = new ConnorsRSIIndicator(closePrice, 2, 2, percentRankPeriod);
        int index = 4;

        Field percentRankField = ConnorsRSIIndicator.class.getDeclaredField("percentRankIndicator");
        percentRankField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Indicator<Num> percentRankIndicator = (Indicator<Num>) percentRankField.get(indicator);

        Num expectedPercentRank = computePercentRank(closePrice, index, percentRankPeriod);
        Num actualPercentRank = percentRankIndicator.getValue(index);

        assertThat(actualPercentRank).isEqualByComparingTo(expectedPercentRank);
        Num one = numFactory.one();
        Num three = numFactory.numOf(3);
        Num hundred = numFactory.numOf(100);
        Num expectedRatio = one.dividedBy(three).multipliedBy(hundred);
        assertThat(actualPercentRank).isEqualByComparingTo(expectedRatio);
    }

    private Num computePercentRank(Indicator<Num> closePrice, int index, int period) {
        if (index <= closePrice.getBarSeries().getBeginIndex()) {
            return NaN.NaN;
        }
        Num current = closePrice.getValue(index);
        Num previous = closePrice.getValue(index - 1);
        if (isNaN(current) || isNaN(previous)) {
            return NaN.NaN;
        }
        Num currentChange = current.minus(previous);
        int beginIndex = closePrice.getBarSeries().getBeginIndex();
        int startIndex = Math.max(beginIndex + 1, index - period);
        int valid = 0;
        int lessThanCount = 0;
        for (int i = startIndex; i < index; i++) {
            Num candidate = closePrice.getValue(i);
            Num prior = closePrice.getValue(i - 1);
            if (isNaN(candidate) || isNaN(prior)) {
                continue;
            }
            Num candidateChange = candidate.minus(prior);
            valid++;
            if (candidateChange.isLessThan(currentChange)) {
                lessThanCount++;
            }
        }
        if (valid == 0) {
            return NaN.NaN;
        }
        Num ratio = numFactory.numOf(lessThanCount).dividedBy(numFactory.numOf(valid));
        return ratio.multipliedBy(numFactory.numOf(100));
    }

    private static boolean isNaN(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }
}
