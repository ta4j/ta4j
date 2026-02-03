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
import static org.assertj.core.api.Assertions.within;
import static org.ta4j.core.num.NaN.NaN;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PriceClusterSupportIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public PriceClusterSupportIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = buildSeries(new double[] { 20, 20, 20, 12, 12, 12, 15, 15, 15 },
                new double[] { 5, 4, 6, 10, 11, 12, 7, 8, 9 });
    }

    @Test
    public void shouldRespectLookbackWindow() {
        var indicator = new PriceClusterSupportIndicator(series, 3, numOf(0.25));

        assertThat(indicator.getValue(6).doubleValue()).as("lookback excludes early clusters")
                .isCloseTo(12.0, within(1e-6));
        assertThat(indicator.getClusterIndex(6)).isEqualTo(5);

        assertThat(indicator.getValue(5).doubleValue()).as("tie resolved toward lower price")
                .isCloseTo(12.0, within(1e-6));
        assertThat(indicator.getClusterIndex(5)).isEqualTo(5);
    }

    @Test
    public void shouldPropagateNaNWhenWindowContainsNoValidValues() {
        var baseIndicator = new ClosePriceIndicator(series);
        Indicator<Num> withNaN = new Indicator<>() {
            private final Set<Integer> invalidIndices = Set.of(2, 3, 4, 5, 6, 7, 8);

            @Override
            public Num getValue(int index) {
                if (invalidIndices.contains(index)) {
                    return NaN;
                }
                return baseIndicator.getValue(index);
            }

            @Override
            public BarSeries getBarSeries() {
                return baseIndicator.getBarSeries();
            }

            @Override
            public int getCountOfUnstableBars() {
                return baseIndicator.getCountOfUnstableBars();
            }
        };

        var indicator = new PriceClusterSupportIndicator(withNaN, 2, numOf(0.1));

        assertThat(indicator.getValue(2)).as("valid values should survive mixed window")
                .isEqualByComparingTo(numOf(20));
        assertThat(indicator.getClusterIndex(2)).isEqualTo(1);

        assertThat(indicator.getValue(7).isNaN()).as("NaN when entire lookback is invalid").isTrue();
        assertThat(indicator.getClusterIndex(7)).isEqualTo(-1);
    }

    @Test
    public void shouldFavorVolumeHeavierClusterBeforeTieBreakers() {
        BarSeries customSeries = buildSeries(new double[] { 10, 15, 15 }, new double[] { 100, 30, 30 });

        var indicator = new PriceClusterSupportIndicator(customSeries, 0, numOf(0.5));

        assertThat(indicator.getValue(2)).as("support favours heavier low cluster despite smaller count")
                .isEqualByComparingTo(numOf(10));
        assertThat(indicator.getClusterIndex(2)).isEqualTo(0);
    }

    private BarSeries buildSeries(double[] closes, double[] volumes) {
        var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries barSeries = builder.build();
        for (int i = 0; i < closes.length; i++) {
            barSeries.barBuilder()
                    .closePrice(closes[i])
                    .openPrice(closes[i])
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
                    .volume(volumes[i])
                    .add();
        }
        return barSeries;
    }
}
