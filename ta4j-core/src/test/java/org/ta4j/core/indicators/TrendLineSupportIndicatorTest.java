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
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;

public class TrendLineSupportIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineSupportIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldSelectLineTouchingMostSwingLows() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 3, 5);
        assertThat(indicator.getValue(series.getEndIndex())).isEqualByComparingTo(series.numFactory().numOf(8));
    }

    @Test
    public void shouldPreferLineContainingCurrentPriceWhenTouchesTie() {
        final var series = seriesFromLows(14, 11, 13, 12, 9, 11, 13, 10, 12, 8);
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(1);
        final var x2 = numFactory.numOf(4);
        final var y1 = numFactory.numOf(11);
        final var y2 = numFactory.numOf(9);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y1.minus(slope.multipliedBy(x1));
        final var expected = slope.multipliedBy(numFactory.numOf(9)).plus(intercept);
        final var priceAtIndex = series.getBar(9).getLowPrice();

        assertThat(indicator.getValue(9)).isEqualByComparingTo(expected);
        assertThat(priceAtIndex.isNaN()).isFalse();
        assertThat(expected).isLessThan(priceAtIndex);
    }

    @Test
    public void shouldLimitSwingPointsToLookbackWindow() {
        final var series = seriesFromLows(14, 11, 13, 12, 9, 11, 13, 10, 12, 8);
        final var indicator = new TrendLineSupportIndicator(series, 1, 6);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(4);
        final var x2 = numFactory.numOf(7);
        final var y1 = numFactory.numOf(9);
        final var y2 = numFactory.numOf(10);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y1.minus(slope.multipliedBy(x1));
        final var expected = slope.multipliedBy(numFactory.numOf(9)).plus(intercept);

        assertThat(indicator.getValue(9)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldFavorLinesWithFewerOutsideSwings() {
        final var series = seriesFromLows(9, 5, 8, 11, 10, 15, 16, 6, 20);
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(1);
        final var x2 = numFactory.numOf(7);
        final var y1 = numFactory.numOf(5);
        final var y2 = numFactory.numOf(6);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y1.minus(slope.multipliedBy(x1));
        final var expected = slope.multipliedBy(numFactory.numOf(8)).plus(intercept);

        assertThat(indicator.getValue(8)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldSerializeIncludingBarCount() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13, 8);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0, 15);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineSupportIndicator");
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 2);
        assertThat(descriptor.getParameters()).containsEntry("barCount", 15);
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentFractalSwingLowIndicator"));
        assertThat(descriptor.getComponents()).anySatisfy(component -> component.getType().equals("LowPriceIndicator"));

        final String json = indicator.toJson();
        assertThat(json).contains("TrendLineSupportIndicator");
        assertThat(json).contains("\"unstableBars\":2");
        assertThat(json).contains("\"barCount\":15");
    }

    @Test
    public void shouldWorkWithZigZagSwingLowIndicator() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12);
        final var lowIndicator = new LowPriceIndicator(series);
        final var reversalThreshold = new ConstantIndicator<>(series, series.numFactory().numOf(2.0));
        final var stateIndicator = new ZigZagStateIndicator(lowIndicator, reversalThreshold);
        final var swingLowIndicator = new RecentZigZagSwingLowIndicator(stateIndicator, lowIndicator);
        final var indicator = new TrendLineSupportIndicator(swingLowIndicator, 0, 0, 10);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).isNotEmpty();
        assertThat(indicator.getValue(series.getEndIndex())).isNotNull();
    }

    @Test
    public void shouldHandleBarRemovalWithoutThrowingException() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        final double[] lows = { 12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12, 9, 13, 8, 12, 10, 11 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
            indicator.getValue(series.getEndIndex());
        }

        series.setMaximumBarCount(10);
        indicator.getValue(series.getEndIndex());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isNotNull();
        }
    }

    private BarSeries seriesFromLows(double... lows) {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        for (double low : lows) {
            final var high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        return series;
    }
}
