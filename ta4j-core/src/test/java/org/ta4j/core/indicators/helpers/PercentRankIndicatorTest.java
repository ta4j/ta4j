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
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
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

        // Should return NaN only when there's no valid data in the window
        // Index 0: no previous values in window, so no valid data -> NaN
        assertThat(percentRank.getValue(0).isNaN()).isTrue();
        // For indices > 0, we should be able to calculate as long as there's at least
        // one valid value
        // The indicator calculates with whatever data is available in the window
    }

    @Test
    public void calculatesPercentRankForAscendingValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

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

        // At index 5, value is 3
        // Window: [1, 2, 3, 4, 5] (indices 0-4)
        // Values less than 3: [1, 2] (2 values)
        // Percent rank = 2/5 * 100 = 40%
        Num value = percentRank.getValue(5);
        assertNumEquals(40, value);
    }

    @Test
    public void handlesNaNValuesInWindow() {
        Assume.assumeFalse(numFactory instanceof DecimalNumFactory);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, Double.NaN, 4, 5, 3, 7)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        // At index 5, value is 3
        // Window: [1, 2, NaN, 4, 5] (indices 0-4)
        // NaN is ignored, valid values: [1, 2, 4, 5] (4 values)
        // Values less than 3: [1, 2] (2 values)
        // Percent rank = 2/4 * 100 = 50%
        Num value = percentRank.getValue(5);
        assertNumEquals(50, value);
    }

    @Test
    public void returnsNaNWhenAllWindowValuesAreNaN() {
        Assume.assumeFalse(numFactory instanceof DecimalNumFactory);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 5)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 5);

        // At index 5, value is 5
        // Window: [NaN, NaN, NaN, NaN, NaN] (indices 0-4)
        // All values are NaN, so no valid values
        // Should return NaN
        Num value = percentRank.getValue(5);
        assertThat(value.isNaN()).isTrue();
    }

    @Test
    public void handlesWindowAtSeriesBeginning() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 10);

        // Period is 10, but series only has 5 bars
        // At index 4, value is 5
        // Window: [1, 2, 3, 4] (indices 0-3, limited by beginIndex)
        // Values less than 5: [1, 2, 3, 4] (4 values)
        // Percent rank = 4/4 * 100 = 100%
        Num value = percentRank.getValue(4);
        assertNumEquals(100, value);
    }

    @Test
    public void returnsCorrectUnstableBarsCount() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        percentRank = new PercentRankIndicator(closePrice, 5);
        assertThat(percentRank.getCountOfUnstableBars()).isEqualTo(5);

        percentRank = new PercentRankIndicator(closePrice, 10);
        assertThat(percentRank.getCountOfUnstableBars()).isEqualTo(10);
    }

    @Test
    public void calculatesPercentRankWithPeriodOne() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        percentRank = new PercentRankIndicator(closePrice, 1);

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
    public void calculatesPercentRankForPriceChangeIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 9, 12, 11, 10, 13)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        PriceChangeIndicator priceChange = new PriceChangeIndicator(closePrice);
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
        PriceChangeIndicator priceChange = new PriceChangeIndicator(closePrice);
        // PriceChangeIndicator has unstable period of 1, so index 0 returns NaN
        percentRank = new PercentRankIndicator(priceChange, 5);

        // At index 2, price change is 9 - 11 = -2
        // Without the fix: startIndex = max(0, 2-5) = max(0, -3) = 0
        // Window would include index 0 (NaN from unstable period), which gets skipped
        // Window: [NaN, 11-10=1] (indices 0-1, but 0 is NaN so only 1 is valid)
        // With the fix: startIndex = max(0+1, 2-5) = max(1, -3) = 1
        // Window: [11-10=1] (index 1 only)
        // Values less than -2: [] (0 values)
        // Percent rank = 0/1 * 100 = 0%
        Num value = percentRank.getValue(2);
        assertNumEquals(0, value);

        // At index 3, price change is 12 - 9 = 3
        // With fix: startIndex = max(1, 3-5) = max(1, -2) = 1
        // Window: [11-10=1, 9-11=-2] (indices 1-2)
        // Values less than 3: [1, -2] (2 values)
        // Percent rank = 2/2 * 100 = 100%
        value = percentRank.getValue(3);
        assertNumEquals(100, value);

        // At index 4, price change is 11 - 12 = -1
        // With fix: startIndex = max(1, 4-5) = max(1, -1) = 1
        // Window: [11-10=1, 9-11=-2, 12-9=3] (indices 1-3)
        // Values less than -1: [-2] (1 value)
        // Percent rank = 1/3 * 100 = 33.33...%
        value = percentRank.getValue(4);
        Num expected = numFactory.numOf(100).dividedBy(numFactory.numOf(3));
        assertNumEquals(expected, value);
    }
}
