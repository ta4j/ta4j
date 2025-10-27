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
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class RecentSwingLowIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public RecentSwingLowIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = createSeriesFromLows(12, 10, 7, 8, 9, 6, 7, 8, 5, 7, 8, 4, 6, 7);
    }

    @Test
    public void shouldReturnMostRecentSwingLow() {
        final var indicator = new RecentSwingLowIndicator(new LowPriceIndicator(series), 2, 2, 0);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(3).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingIndex(3)).isEqualTo(-1);

        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(7));
        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(2);

        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(7));
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(2);

        assertThat(indicator.getValue(7)).isEqualByComparingTo(numOf(6));
        assertThat(indicator.getLatestSwingIndex(7)).isEqualTo(5);

        assertThat(indicator.getValue(10)).isEqualByComparingTo(numOf(5));
        assertThat(indicator.getLatestSwingIndex(10)).isEqualTo(8);

        assertThat(indicator.getValue(13)).isEqualByComparingTo(numOf(4));
        assertThat(indicator.getLatestSwingIndex(13)).isEqualTo(11);
    }

    @Test
    public void shouldDelayConfirmationUntilFollowingBarsAvailable() {
        final var indicator = new RecentSwingLowIndicator(new LowPriceIndicator(series), 2, 2, 0);

        assertThat(indicator.getLatestSwingIndex(5)).isEqualTo(2);
        assertThat(indicator.getValue(5)).isEqualByComparingTo(numOf(7));

        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(2);
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(7));

        assertThat(indicator.getLatestSwingIndex(7)).isEqualTo(5);
        assertThat(indicator.getValue(7)).isEqualByComparingTo(numOf(6));
    }

    @Test
    public void shouldAllowRoundedBottomsWhenEqualBarsPermitted() {
        final var roundedSeries = createSeriesFromLows(9, 7, 8, 6, 6, 9, 10, 11);
        final var noEquals = new RecentSwingLowIndicator(new LowPriceIndicator(roundedSeries), 2, 2, 0);
        final var withEquals = new RecentSwingLowIndicator(new LowPriceIndicator(roundedSeries), 2, 2, 1);

        assertThat(noEquals.getValue(6).isNaN()).isTrue();
        assertThat(noEquals.getLatestSwingIndex(6)).isEqualTo(-1);

        assertThat(withEquals.getValue(6)).isEqualByComparingTo(numOf(6));
        assertThat(withEquals.getLatestSwingIndex(6)).isEqualTo(4);
    }

    @Test
    public void shouldRejectRoundedBottomsThatExceedEqualAllowance() {
        final var roundedSeries = createSeriesFromLows(10, 6, 4, 4, 4, 4, 6, 7, 8);
        final var indicator = new RecentSwingLowIndicator(new LowPriceIndicator(roundedSeries), 1, 2, 1);

        assertThat(indicator.getValue(7).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingIndex(7)).isEqualTo(-1);
    }

    @Test
    public void shouldPropagateNaNFromUnderlyingIndicator() {
        final var baseSeries = createSeriesFromLows(12, 10, 7, 8, 9, 6, 7, 8, 5, 7, 8);
        final var lowIndicator = new LowPriceIndicator(baseSeries);
        final var indicatorWithNaNFollowing = new RecentSwingLowIndicator(indicatorWithNaN(lowIndicator, 7), 2, 2, 0);

        assertThat(indicatorWithNaNFollowing.getValue(7)).isEqualByComparingTo(numOf(7));
        assertThat(indicatorWithNaNFollowing.getLatestSwingIndex(7)).isEqualTo(2);
        assertThat(indicatorWithNaNFollowing.getValue(10)).isEqualByComparingTo(numOf(7));

        final var shortSeries = createSeriesFromLows(12, 10, 7, 8, 9, 6, 7);
        final var shortIndicator = new LowPriceIndicator(shortSeries);
        final var indicator = new RecentSwingLowIndicator(indicatorWithNaN(shortIndicator, 2), 2, 2, 0);

        assertThat(indicator.getValue(6).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(-1);
    }

    private BarSeries createSeriesFromLows(double... lows) {
        final var barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double low : lows) {
            final double high = low + 2d;
            barSeries.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        return barSeries;
    }

    private Indicator<Num> indicatorWithNaN(Indicator<Num> delegate, int... indices) {
        final Set<Integer> invalidIndices = Arrays.stream(indices).boxed().collect(Collectors.toSet());
        return new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return invalidIndices.contains(index) ? NaN : delegate.getValue(index);
            }

            @Override
            public BarSeries getBarSeries() {
                return delegate.getBarSeries();
            }

            @Override
            public int getCountOfUnstableBars() {
                return delegate.getCountOfUnstableBars();
            }
        };
    }
}
