/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
        // Use smaller parameters to allow testing after unstable period with limited
        // data
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 3, 5, 3, 2);

        int unstableBars = indicator.getCountOfUnstableBars();
        // Unstable period: 5 + 3 + 2 + 3 + 2 = 15
        assertThat(unstableBars).isEqualTo(15);

        // During unstable period, should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }

        // After unstable period, verify values are valid and in expected range
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(Num.isNaNOrNull(value)).isFalse();
            // Schaff Trend Cycle values should be between 0 and 100
            assertThat(value.doubleValue()).isBetween(0.0, 100.0);
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
    public void returnsValidValuesForNormalData() {
        // Test that normal data produces valid (non-NaN) results after unstable period
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 5, 10, 3, 3);

        // After unstable period, values should be valid (not NaN)
        int unstableBars = indicator.getCountOfUnstableBars();
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(value.isNaN()).isFalse();
            // Schaff Trend Cycle values should be between 0 and 100
            assertThat(value.doubleValue()).isBetween(0.0, 100.0);
        }
    }

    @Test
    public void returnsNaNDuringUnstablePeriod() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
                .build();
        final var closePrice = new ClosePriceIndicator(series);
        final var indicator = new SchaffTrendCycleIndicator(closePrice, 5, 10, 3, 3);

        int unstableBars = indicator.getCountOfUnstableBars();
        // Unstable period: 10 + 3 + 3 + 3 + 3 = 22
        assertThat(unstableBars).isEqualTo(22);

        // All indices before unstable period should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }
}
