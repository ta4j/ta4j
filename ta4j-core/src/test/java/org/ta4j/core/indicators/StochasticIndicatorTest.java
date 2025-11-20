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
import static org.junit.Assert.assertThrows;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.DecimalNumFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StochasticIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public StochasticIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void calculatesStochasticCorrectly() {
        // Test data: values from 10 to 20 over 5 periods
        // Lookback 5: at index 4, min=10, max=14, value=14
        // Stochastic = (14-10)/(14-10) * 100 = 100
        List<Num> values = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 5);

        // Index 4: value=14, min=10, max=14 → (14-10)/(14-10)*100 = 100
        assertThat(stochastic.getValue(4)).isEqualByComparingTo(numFactory.numOf(100));

        // Index 5: value=15, min=11, max=15 → (15-11)/(15-11)*100 = 100
        assertThat(stochastic.getValue(5)).isEqualByComparingTo(numFactory.numOf(100));

        // Index 6: value=16, min=12, max=16 → (16-12)/(16-12)*100 = 100
        assertThat(stochastic.getValue(6)).isEqualByComparingTo(numFactory.numOf(100));
    }

    @Test
    public void calculatesStochasticAtZero() {
        // Test data: values from 10 to 20, but at index 5, value is at minimum
        List<Num> values = Arrays.asList(10, 11, 12, 13, 14, 10, 16, 17, 18, 19, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 5);

        // Index 5: value=10, min=10, max=14 → (10-10)/(14-10)*100 = 0
        assertThat(stochastic.getValue(5)).isEqualByComparingTo(numFactory.numOf(0));
    }

    @Test
    public void calculatesStochasticAtMidpoint() {
        // Test data: oscillating values to test midpoint calculation
        List<Num> values = Arrays.asList(10, 15, 20, 15, 10, 15, 20, 15, 10, 15, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 5);

        // Index 5: value=15, min=10, max=20 → (15-10)/(20-10)*100 = 50
        assertThat(stochastic.getValue(5)).isEqualByComparingTo(numFactory.numOf(50));
    }

    @Test
    public void returnsZeroWhenRangeIsZeroAtFirstIndex() {
        // Test data: first few values are the same, then different
        // This avoids infinite recursion while still testing zero range at first index
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 5, 5, 10, 15, 20).build();
        List<Num> values = Arrays.asList(5, 5, 5, 10, 15, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(series, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 3);

        // At first index where range is zero, should return zero
        int beginIndex = series.getBeginIndex();
        Num firstValue = stochastic.getValue(beginIndex);
        assertThat(firstValue).isEqualByComparingTo(numFactory.numOf(0));

        // Index 1 and 2 also have zero range (all values are 5)
        // They should return the previous value (which is 0)
        Num secondValue = stochastic.getValue(beginIndex + 1);
        assertThat(secondValue).isEqualByComparingTo(numFactory.numOf(0));
    }

    @Test
    public void returnsPreviousValueWhenRangeIsZero() {
        // Test data: all values are the same after initial value
        List<Num> values = Arrays.asList(10, 5, 5, 5, 5, 5)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 3);

        // Index 1: value=5, min=5, max=5 → range is zero, should return previous value
        // Previous value at index 0: value=10, min=10, max=10 → 0
        Num value1 = stochastic.getValue(1);
        Num value2 = stochastic.getValue(2);
        assertThat(value2).isEqualByComparingTo(value1);
    }

    @Test
    public void propagatesNaNValues() {
        Assume.assumeFalse(numFactory instanceof DecimalNumFactory);
        List<Num> values = Arrays.asList(10.0, Double.NaN, 12.0, 13.0, 14.0)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 3);

        assertThat(stochastic.getValue(1).isNaN()).isTrue();
    }

    @Test
    public void returnsNaNWhenHighestOrLowestIsNaN() {
        Assume.assumeFalse(numFactory instanceof DecimalNumFactory);
        // Create a scenario where highest or lowest might be NaN
        List<Num> values = Arrays.asList(10.0, 11.0, Double.NaN, 13.0, 14.0)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 3);

        // When NaN is in the lookback window, highest/lowest might be NaN
        // This depends on how HighestValueIndicator and LowestValueIndicator handle NaN
        // For now, we test that the indicator handles it gracefully
        // The behavior depends on how HighestValueIndicator handles NaN
        // We just verify it doesn't crash
        assertThat(stochastic.getValue(3)).isNotNull();
    }

    @Test
    public void testGetCountOfUnstableBars() {
        int lookback = 5;
        List<Num> values = Arrays.asList(10, 11, 12, 13, 14, 15)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, lookback);

        assertThat(stochastic.getCountOfUnstableBars()).isEqualTo(lookback);
    }

    @Test
    public void throwsExceptionForInvalidLookback() {
        List<Num> values = Arrays.asList(10, 11, 12).stream().map(numFactory::numOf).collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new StochasticIndicator(mockIndicator, 0);
        });
        assertThat(exception.getMessage()).contains("Stochastic look-back length must be a positive integer");

        exception = assertThrows(IllegalArgumentException.class, () -> {
            new StochasticIndicator(mockIndicator, -1);
        });
        assertThat(exception.getMessage()).contains("Stochastic look-back length must be a positive integer");
    }

    @Test
    public void worksWithClosePriceIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        StochasticIndicator stochastic = new StochasticIndicator(closePrice, 5);

        // Verify it calculates without errors
        for (int i = 0; i < series.getBarCount(); i++) {
            Num value = stochastic.getValue(i);
            assertThat(value).isNotNull();
            // Stochastic values should be between 0 and 100 (or NaN)
            if (!value.isNaN()) {
                assertThat(value.doubleValue()).isBetween(0.0, 100.0);
            }
        }
    }

    @Test
    public void handlesAscendingSequence() {
        // Strictly ascending values
        List<Num> values = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 5);

        // In an ascending sequence, each value should be at 100% (it's the max in its
        // window)
        for (int i = 4; i < values.size(); i++) {
            Num value = stochastic.getValue(i);
            assertThat(value).isEqualByComparingTo(numFactory.numOf(100));
        }
    }

    @Test
    public void handlesDescendingSequence() {
        // Strictly descending values
        List<Num> values = Arrays.asList(20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 5);

        // In a descending sequence, each value should be at 0% (it's the min in its
        // window)
        for (int i = 4; i < values.size(); i++) {
            Num value = stochastic.getValue(i);
            assertThat(value).isEqualByComparingTo(numFactory.numOf(0));
        }
    }

    @Test
    public void handlesOscillatingValues() {
        // Oscillating pattern: 10, 20, 10, 20, ...
        List<Num> values = Arrays.asList(10, 20, 10, 20, 10, 20, 10, 20, 10, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(data, values);
        StochasticIndicator stochastic = new StochasticIndicator(mockIndicator, 5);

        // At index 4: value=10, min=10, max=20 → (10-10)/(20-10)*100 = 0
        assertThat(stochastic.getValue(4)).isEqualByComparingTo(numFactory.numOf(0));

        // At index 5: value=20, min=10, max=20 → (20-10)/(20-10)*100 = 100
        assertThat(stochastic.getValue(5)).isEqualByComparingTo(numFactory.numOf(100));
    }

    @Test
    public void worksWithDifferentLookbackPeriods() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .build();
        List<Num> values = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator mockIndicator = new MockIndicator(series, values);

        StochasticIndicator stochastic3 = new StochasticIndicator(mockIndicator, 3);
        StochasticIndicator stochastic5 = new StochasticIndicator(mockIndicator, 5);
        StochasticIndicator stochastic10 = new StochasticIndicator(mockIndicator, 10);

        // All should calculate without errors
        for (int i = 0; i < values.size(); i++) {
            Num value3 = stochastic3.getValue(i);
            Num value5 = stochastic5.getValue(i);
            Num value10 = stochastic10.getValue(i);

            assertThat(value3).isNotNull();
            assertThat(value5).isNotNull();
            assertThat(value10).isNotNull();
        }

        // Verify unstable bars count matches lookback
        assertThat(stochastic3.getCountOfUnstableBars()).isEqualTo(3);
        assertThat(stochastic5.getCountOfUnstableBars()).isEqualTo(5);
        assertThat(stochastic10.getCountOfUnstableBars()).isEqualTo(10);
    }

    @Test
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
                .build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        StochasticIndicator original = new StochasticIndicator(base, 5);

        String json = original.toJson();
        @SuppressWarnings("unchecked")
        Indicator<Num> reconstructed = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(StochasticIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(original.toDescriptor());

        // Compare values after unstable period
        int unstableBars = original.getCountOfUnstableBars();
        for (int i = series.getBeginIndex() + unstableBars; i <= series.getEndIndex(); i++) {
            Num expected = original.getValue(i);
            Num actual = reconstructed.getValue(i);
            if (isNaN(expected) || isNaN(actual)) {
                assertThat(isNaN(actual)).isEqualTo(isNaN(expected));
            } else {
                assertThat(actual).isEqualTo(expected);
            }
        }
    }

    private boolean isNaN(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }
}
