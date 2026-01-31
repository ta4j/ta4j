/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.BarSeries;
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
                assertThat(Num.isNaNOrNull(value)).isTrue();
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
            assertThat(Num.isNaNOrNull(value)).isFalse();
        }
    }

    @Test
    public void unstableBarsMatchSmoothingPeriods() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 7, 5);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(12);
    }

    @Test
    public void returnsNaNDuringUnstablePeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 4, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        assertThat(unstableBars).isEqualTo(6); // 4 + 2

        // All indices before unstable period should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void usesDefaultConstructor() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
                        33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice);

        // Default constructor uses (25, 13), so unstable period is 38
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(38);

        // Values after unstable period should be valid
        for (int i = 38; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(Num.isNaNOrNull(value)).isFalse();
        }
    }

    @Test
    public void throwsExceptionForZeroFirstSmoothingPeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        final var closePrice = new ClosePriceIndicator(series);
        assertThrows(IllegalArgumentException.class, () -> new TrueStrengthIndexIndicator(closePrice, 0, 13));
    }

    @Test
    public void throwsExceptionForNegativeFirstSmoothingPeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        final var closePrice = new ClosePriceIndicator(series);
        assertThrows(IllegalArgumentException.class, () -> new TrueStrengthIndexIndicator(closePrice, -1, 13));
    }

    @Test
    public void throwsExceptionForZeroSecondSmoothingPeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        final var closePrice = new ClosePriceIndicator(series);
        assertThrows(IllegalArgumentException.class, () -> new TrueStrengthIndexIndicator(closePrice, 25, 0));
    }

    @Test
    public void throwsExceptionForNegativeSecondSmoothingPeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        final var closePrice = new ClosePriceIndicator(series);
        assertThrows(IllegalArgumentException.class, () -> new TrueStrengthIndexIndicator(closePrice, 25, -1));
    }

    @Test
    public void returnsNaNForZeroDenominator() {
        // When all price changes are zero (constant prices), denominator will be zero
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 10, 10, 10, 10, 10, 10, 10, 10, 10)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 2, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        // After unstable period, all values should be NaN because denominator is zero
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void valuesAreInExpectedRange() {
        // TSI should be in range [-100, 100]
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
                        33, 34, 35, 36, 37, 38, 39, 40)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 4, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        Num hundred = numFactory.numOf(100);
        Num negativeHundred = numFactory.numOf(-100);

        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            if (!Num.isNaNOrNull(value)) {
                assertThat(value.isLessThanOrEqual(hundred)).isTrue();
                assertThat(value.isGreaterThanOrEqual(negativeHundred)).isTrue();
            }
        }
    }

    @Test
    public void handlesUpwardTrend() {
        // Upward trend should produce positive TSI values
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 3, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        // After unstable period, values should be positive for upward trend
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            if (!Num.isNaNOrNull(value)) {
                assertThat(value.isGreaterThan(numFactory.zero())).isTrue();
            }
        }
    }

    @Test
    public void handlesDownwardTrend() {
        // Downward trend should produce negative TSI values
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 3, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        // After unstable period, values should be negative for downward trend
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            if (!Num.isNaNOrNull(value)) {
                assertThat(value.isLessThan(numFactory.zero())).isTrue();
            }
        }
    }

    @Test
    public void handlesAlternatingPrices() {
        // Alternating prices should produce values around zero
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10,
                        11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new TrueStrengthIndexIndicator(closePrice, 3, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        // After unstable period, values should be valid (may be close to zero)
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(Num.isNaNOrNull(value)).isFalse();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                        27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40)
                .build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        TrueStrengthIndexIndicator original = new TrueStrengthIndexIndicator(base, 4, 2);

        String json = original.toJson();
        Indicator<Num> reconstructed = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(TrueStrengthIndexIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(original.toDescriptor());

        // Compare values after unstable period
        int unstableBars = original.getCountOfUnstableBars();
        for (int i = series.getBeginIndex() + unstableBars; i <= series.getEndIndex(); i++) {
            Num expected = original.getValue(i);
            Num actual = reconstructed.getValue(i);
            if (Num.isNaNOrNull(expected) || Num.isNaNOrNull(actual)) {
                assertThat(Num.isNaNOrNull(actual)).isEqualTo(Num.isNaNOrNull(expected));
            } else {
                assertThat(actual).isEqualTo(expected);
            }
        }
    }

}
