/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PercentRankIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private PercentRankIndicator percentRank;
    private BarSeries barSeries;

    public PercentRankIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
    }

    @Test
    public void constructorThrowsExceptionForInvalidPeriod() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> new PercentRankIndicator(closePrice, 0));
        assertThrows(IllegalArgumentException.class, () -> new PercentRankIndicator(closePrice, -1));
    }

    @Test
    public void returnsNaNForUnstableBars() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 (underlying) + 5 (period) - 1 = 4
        assertThat(unstableBars).isEqualTo(4);

        // All indices before unstable period should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(percentRank.getValue(i).isNaN()).isTrue();
        }
    }

    @Test
    public void calculatesPercentRankForAscendingValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // During unstable period, should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(percentRank.getValue(i).isNaN()).isTrue();
        }

        // At index 5, value is 6
        // Window: [1, 2, 3, 4, 5] (indices 0-4)
        // All 5 values are less than 6, so percent rank = 100%
        Num value = percentRank.getValue(5);
        assertNumEquals(100, value);
    }

    @Test
    public void calculatesPercentRankForDescendingValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // At index 5, value is 5
        // Window: [10, 9, 8, 7, 6] (indices 0-4)
        // All 5 values are greater than 5, so percent rank = 0%
        Num value = percentRank.getValue(5);
        assertNumEquals(0, value);
    }

    @Test
    public void calculatesPercentRankForMixedValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 9, 12, 11, 10, 13, 12, 11, 10)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // At index 5, value is 10
        // Window: [10, 11, 9, 12, 11] (indices 0-4)
        // Values less than 10: [9] (1 value)
        // Percent rank = 1/5 * 100 = 20%
        Num value = percentRank.getValue(5);
        assertNumEquals(20, value);
    }

    @Test
    public void calculatesPercentRankWhenAllValuesEqual() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 5, 5, 5, 5, 5, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // At index 5, value is 5
        // Window: [5, 5, 5, 5, 5] (indices 0-4)
        // No values are less than 5, so percent rank = 0%
        Num value = percentRank.getValue(5);
        assertNumEquals(0, value);
    }

    @Test
    public void calculatesPercentRankForMiddleValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 3, 7, 8, 9, 10)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // At index 5, value is 3
        // Window: [1, 2, 3, 4, 5] (indices 0-4)
        // Values less than 3: [1, 2] (2 values)
        // Percent rank = 2/5 * 100 = 40%
        Num value = percentRank.getValue(5);
        assertNumEquals(40, value);
    }

    @Test
    public void returnsValidValuesForNormalData() {
        // Test that normal data produces valid (non-NaN) results
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 3, 7).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // After unstable period, values should be valid (not NaN)
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = percentRank.getValue(i);
            assertThat(value.isNaN()).isFalse();
        }

        // At index 5, value is 3
        // Window: [1, 2, 3, 4, 5] (indices 0-4)
        // Values less than 3: [1, 2] (2 values)
        // Percent rank = 2/5 * 100 = 40%
        Num value = percentRank.getValue(5);
        assertNumEquals(40, value);
    }

    @Test
    public void returnsValidValuesWithVaryingData() {
        // Test that varying data produces valid (non-NaN) results
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 8, 12, 9, 11, 5, 7)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4 (indices 0-3)
        assertThat(unstableBars).isEqualTo(4);

        // At index 5, value is 5
        // Window: [10, 8, 12, 9, 11] (indices 0-4)
        // Values less than 5: [] (0 values)
        // Percent rank = 0/5 * 100 = 0%
        Num value = percentRank.getValue(5);
        assertThat(value.isNaN()).isFalse();
        assertNumEquals(0, value);
    }

    @Test
    public void handlesWindowAtSeriesBeginning() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 10);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 10 - 1 = 9 (indices 0-8)
        assertThat(unstableBars).isEqualTo(9);

        // During unstable period, should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(percentRank.getValue(i).isNaN()).isTrue();
        }

        // Period is 10, but at index 9, we have enough data
        // At index 9, value is 10
        // Window: [1, 2, 3, 4, 5, 6, 7, 8, 9] (indices 0-8)
        // Values less than 10: [1, 2, 3, 4, 5, 6, 7, 8, 9] (9 values)
        // Percent rank = 9/9 * 100 = 100%
        Num value = percentRank.getValue(9);
        assertNumEquals(100, value);
    }

    @Test
    public void returnsCorrectUnstableBarsCount() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        // ClosePriceIndicator has unstable period 0, so formula is: 0 + period - 1
        percentRank = new PercentRankIndicator(closePrice, 5);
        assertThat(percentRank.getCountOfUnstableBars()).isEqualTo(4); // 0 + 5 - 1

        percentRank = new PercentRankIndicator(closePrice, 10);
        assertThat(percentRank.getCountOfUnstableBars()).isEqualTo(9); // 0 + 10 - 1
    }

    @Test
    public void returnsCorrectUnstableBarsCountWithUnderlyingIndicatorUnstablePeriod() {
        // Test that PercentRankIndicator correctly accounts for underlying indicator's
        // unstable period
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        // DifferenceIndicator has unstable period 1
        DifferenceIndicator diffIndicator = new DifferenceIndicator(closePrice);
        percentRank = new PercentRankIndicator(diffIndicator, 5);
        // Should be: 1 (underlying) + 5 (period) - 1 = 5
        assertThat(percentRank.getCountOfUnstableBars()).isEqualTo(5);

        percentRank = new PercentRankIndicator(diffIndicator, 10);
        // Should be: 1 (underlying) + 10 (period) - 1 = 10
        assertThat(percentRank.getCountOfUnstableBars()).isEqualTo(10);
    }

    @Test
    public void calculatesPercentRankWithPeriodOne() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 1);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 1 - 1 = 0 (no unstable period)
        assertThat(unstableBars).isEqualTo(0);

        // At index 1, value is 2
        // Window: [1] (index 0)
        // Values less than 2: [1] (1 value)
        // Percent rank = 1/1 * 100 = 100%
        Num value = percentRank.getValue(1);
        assertNumEquals(100, value);

        // At index 2, value is 3
        // Window: [2] (index 1)
        // Values less than 3: [2] (1 value)
        // Percent rank = 1/1 * 100 = 100%
        value = percentRank.getValue(2);
        assertNumEquals(100, value);
    }

    @Test
    public void calculatesPercentRankForDifferenceIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 9, 12, 11, 10, 13)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        DifferenceIndicator priceChange = new DifferenceIndicator(closePrice);
        percentRank = new PercentRankIndicator(priceChange, 3);

        // At index 4, price change is 11 - 12 = -1
        // Window: [11-10=1, 9-11=-2, 12-9=3] (indices 1-3)
        // Values less than -1: [-2] (1 value)
        // Percent rank = 1/3 * 100 = 33.33...%
        Num value = percentRank.getValue(4);
        Num expected = numFactory.numOf(100).dividedBy(numFactory.numOf(3));
        assertNumEquals(expected, value);
    }

    @Test
    public void excludesUnstablePeriodValuesFromUnderlyingIndicator() {
        // This test verifies that PercentRankIndicator correctly excludes values
        // from the underlying indicator's unstable period when calculating the window
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 9, 12, 11, 10, 13)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        DifferenceIndicator priceChange = new DifferenceIndicator(closePrice);
        // DifferenceIndicator has unstable period of 1, so index 0 returns NaN
        percentRank = new PercentRankIndicator(priceChange, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 1 (underlying) + 5 (period) - 1 = 5 (indices 0-4)
        assertThat(unstableBars).isEqualTo(5);

        // During unstable period, should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(percentRank.getValue(i).isNaN()).isTrue();
        }

        // After unstable period, at index 5, price change is 10 - 11 = -1
        // With fix: startIndex = max(0+1, 5-5) = max(1, 0) = 1
        // Window: [11-10=1, 9-11=-2, 12-9=3, 11-12=-1] (indices 1-4)
        // Values less than -1: [-2] (1 value)
        // Percent rank = 1/4 * 100 = 25%
        Num value = percentRank.getValue(5);
        Num expected = numFactory.numOf(100).dividedBy(numFactory.numOf(4));
        assertNumEquals(expected, value);
    }

    @Test
    public void returnsNaNDuringUnstablePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        int unstableBars = percentRank.getCountOfUnstableBars();
        // Unstable period: 0 + 5 - 1 = 4
        assertThat(unstableBars).isEqualTo(4);

        // All indices before unstable period should return NaN
        for (int i = 0; i < unstableBars; i++) {
            assertThat(percentRank.getValue(i).isNaN()).isTrue();
        }
    }
}
