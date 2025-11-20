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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;

public class TrendLineResistanceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineResistanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldReturnNaNUntilTwoSwingHighsConfirmed() {
        final var series = seriesFromHighs(12, 13, 15, 14, 16, 17, 15, 14);
        final var highIndicator = new HighPriceIndicator(series);
        final var indicator = new TrendLineResistanceIndicator(highIndicator, 1, 1, 0);

        for (int i = 0; i <= 5; i++) {
            assertThat(indicator.getValue(i).isNaN()).isTrue();
        }

        assertThat(indicator.getValue(6).isNaN()).isFalse();
        assertThat(indicator.getPivotIndexes()).containsExactly(2, 5);
    }

    @Test
    public void shouldProjectExpectedResistanceLine() {
        final var series = seriesFromHighs(11, 14, 13, 16, 12, 15, 11);
        final var highIndicator = new HighPriceIndicator(series);
        final var indicator = new TrendLineResistanceIndicator(highIndicator, 1, 1, 0);

        for (int i = 0; i <= 4; i++) {
            indicator.getValue(i);
        }

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(1);
        final var x2 = numFactory.numOf(3);
        final var y1 = highIndicator.getValue(1);
        final var y2 = highIndicator.getValue(3);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));
        final var expected = slope.multipliedBy(numFactory.numOf(4)).plus(intercept);

        assertThat(indicator.getValue(4)).isEqualByComparingTo(expected);
        assertThat(indicator.getPivotIndexes()).containsExactly(1, 3);
    }

    @Test
    public void shouldRemainStableAcrossEqualHighPlateau() {
        final var series = seriesFromHighs(10, 12, 14, 13, 15, 15, 15, 13, 11);
        final var highIndicator = new HighPriceIndicator(series);
        final var indicator = new TrendLineResistanceIndicator(highIndicator, 1, 1, 2);

        for (int i = 0; i <= 6; i++) {
            assertThat(indicator.getValue(i).isNaN()).isTrue();
        }

        final var valueAtSeven = indicator.getValue(7);
        final var valueAtEight = indicator.getValue(8);

        assertThat(valueAtSeven.isNaN()).isFalse();
        assertThat(valueAtEight.isNaN()).isFalse();
        assertThat(indicator.getPivotIndexes()).containsExactly(2, 6);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(2);
        final var x2 = numFactory.numOf(6);
        final var y1 = highIndicator.getValue(2);
        final var y2 = highIndicator.getValue(6);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));

        assertThat(valueAtSeven).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(7)).plus(intercept));
        assertThat(valueAtEight).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(8)).plus(intercept));
    }

    @Test
    public void shouldPurgeCachedPivotsWhenSeriesLosesLeadingBars() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        series.setMaximumBarCount(5);
        final var highIndicator = new HighPriceIndicator(series);
        final var indicator = new TrendLineResistanceIndicator(highIndicator, 1, 1, 0);

        final double[] initialHighs = { 12, 13, 15, 14, 16, 17, 15, 14, 18, 16, 15 };
        for (double high : initialHighs) {
            final double low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
            indicator.getValue(series.getEndIndex());
        }

        final int beginAfterPurging = series.getBeginIndex();
        assertThat(beginAfterPurging).isGreaterThan(0);
        assertThat(indicator.getPivotIndexes()).allMatch(pivotIndex -> pivotIndex >= beginAfterPurging);
        assertThat(indicator.getValue(series.getEndIndex()).isNaN())
                .as("only one pivot remains so projection should be NaN")
                .isTrue();

        final double[] additionalHighs = { 17, 15, 18, 16 };
        for (double high : additionalHighs) {
            final double low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
            indicator.getValue(series.getEndIndex());
        }

        final int finalBeginIndex = series.getBeginIndex();
        assertThat(finalBeginIndex).isGreaterThan(beginAfterPurging);
        assertThat(indicator.getPivotIndexes()).allMatch(pivotIndex -> pivotIndex >= finalBeginIndex);
        assertThat(indicator.getPivotIndexes()).containsExactly(11, 13);
        assertThat(indicator.getValue(series.getEndIndex()).isNaN()).isFalse();
    }

    @Test
    public void shouldSerializeAndDeserializeIndicator() {
        final var series = seriesFromHighs(11, 14, 13, 16, 12, 15, 11, 14, 13);
        final var highIndicator = new HighPriceIndicator(series);
        final var original = new TrendLineResistanceIndicator(highIndicator, 1, 1, 0);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        // Test serialization to descriptor
        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineResistanceIndicator");
        // unstableBars is serialized from the superclass field (1 + 1 = 2)
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 2);
        // Both swingHighIndicator and priceIndicator are serialized as components
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentSwingHighIndicator"));
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("HighPriceIndicator"));

        // Verify transient fields are not serialized
        assertThat(descriptor.getParameters()).doesNotContainKey("lastScannedIndex");
        assertThat(descriptor.getParameters()).doesNotContainKey("pivots");

        // Test JSON serialization
        final String json = original.toJson();
        assertThat(json).contains("TrendLineResistanceIndicator");
        assertThat(json).contains("\"unstableBars\":2");
        assertThat(json).contains("RecentSwingHighIndicator");
        assertThat(json).contains("HighPriceIndicator");
    }

    @Test
    public void shouldSerializeAndDeserializeIndicatorWithBarSeriesConstructor() {
        final var series = seriesFromHighs(10, 12, 14, 13, 15, 15, 15, 13, 11);
        final var original = new TrendLineResistanceIndicator(series, 2);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        // Test serialization
        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineResistanceIndicator");
        // unstableBars = 2 + 2 = 4 (precedingLowerBars + followingLowerBars)
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 4);
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentSwingHighIndicator"));
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("HighPriceIndicator"));

        // Test JSON serialization
        final String json = original.toJson();
        assertThat(json).contains("TrendLineResistanceIndicator");
        assertThat(json).contains("\"unstableBars\":4");
    }

    @Test
    public void shouldSerializeAndDeserializeIndicatorWithDefaultConstructor() {
        final var series = seriesFromHighs(11, 14, 13, 16, 12, 15, 11, 14, 13);
        final var original = new TrendLineResistanceIndicator(series);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        // Test serialization
        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineResistanceIndicator");
        // unstableBars = 3 + 3 = 6 (default surroundingLowerBars is 3)
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 6);
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentSwingHighIndicator"));
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("HighPriceIndicator"));

        // Test JSON serialization
        final String json = original.toJson();
        assertThat(json).contains("TrendLineResistanceIndicator");
        assertThat(json).contains("\"unstableBars\":6");
    }

    private BarSeries seriesFromHighs(double... highs) {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        for (double high : highs) {
            final var low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
        }
        return series;
    }
}
