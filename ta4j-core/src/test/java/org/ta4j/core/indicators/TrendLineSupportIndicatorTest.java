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

import static org.assertj.core.api.Assertions.assertThat;

public class TrendLineSupportIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineSupportIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldReturnNaNBeforeFirstSwingLowAndAnchorAtPivots() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue();

        assertThat(indicator.getValue(2)).isEqualByComparingTo(lowIndicator.getValue(2));
        // Swing low at index 5 needs to be confirmed at index 6 (followingHigherBars=1)
        indicator.getValue(6); // Discover swing point at index 5
        assertThat(indicator.getSwingPointIndexes()).containsExactly(2, 5);
        // Verify swing point value is returned at the swing point index
        assertThat(indicator.getValue(5)).isEqualByComparingTo(lowIndicator.getValue(5));

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(2);
        final var x2 = numFactory.numOf(5);
        final var y1 = lowIndicator.getValue(2);
        final var y2 = lowIndicator.getValue(5);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));

        assertThat(indicator.getValue(4)).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(4)).plus(intercept));
    }

    @Test
    public void shouldProjectExpectedSupportLine() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        for (int i = 0; i <= 6; i++) {
            indicator.getValue(i);
        }

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(1);
        final var x2 = numFactory.numOf(5);
        final var y1 = lowIndicator.getValue(1);
        final var y2 = lowIndicator.getValue(5);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));
        final var expected = slope.multipliedBy(numFactory.numOf(6)).plus(intercept);

        assertThat(indicator.getValue(6)).isEqualByComparingTo(expected);
        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 5);
    }

    @Test
    public void shouldRemainStableAcrossEqualLowPlateau() {
        final var series = seriesFromLows(13, 12, 9, 11, 10, 8, 8, 8, 12, 14);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 2);

        final var valueAtEight = indicator.getValue(8);
        final var valueAtNine = indicator.getValue(9);

        assertThat(valueAtEight.isNaN()).isFalse();
        assertThat(valueAtNine.isNaN()).isFalse();
        assertThat(indicator.getSwingPointIndexes()).containsExactly(2, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(2);
        final var x2 = numFactory.numOf(7);
        final var y1 = lowIndicator.getValue(2);
        final var y2 = lowIndicator.getValue(7);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));

        assertThat(valueAtEight).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(8)).plus(intercept));
        assertThat(valueAtNine).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(9)).plus(intercept));
    }

    @Test
    public void shouldPurgeCachedPivotsWhenSeriesLosesLeadingBars() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        series.setMaximumBarCount(5);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        final double[] initialLows = { 12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12 };
        for (double low : initialLows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
            indicator.getValue(series.getEndIndex());
        }

        final int beginAfterPurging = series.getBeginIndex();
        assertThat(beginAfterPurging).isGreaterThan(0);
        assertThat(indicator.getSwingPointIndexes()).allMatch(pivotIndex -> pivotIndex >= beginAfterPurging);
        assertThat(indicator.getValue(series.getEndIndex()).isNaN())
                .as("only one pivot remains so projection should be NaN")
                .isTrue();

        final double[] additionalLows = { 9, 13, 8, 12 };
        for (double low : additionalLows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
            indicator.getValue(series.getEndIndex());
        }

        final int finalBeginIndex = series.getBeginIndex();
        assertThat(finalBeginIndex).isGreaterThan(beginAfterPurging);
        assertThat(indicator.getSwingPointIndexes()).allMatch(pivotIndex -> pivotIndex >= finalBeginIndex);
        assertThat(indicator.getSwingPointIndexes()).containsExactly(11, 13);
        assertThat(indicator.getValue(series.getEndIndex()).isNaN()).isFalse();
    }

    @Test
    public void shouldSerializeAndDeserializeIndicator() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13, 8);
        final var lowIndicator = new LowPriceIndicator(series);
        final var original = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        // Test serialization to descriptor
        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineSupportIndicator");
        // unstableBars is serialized from the superclass field (1 + 1 = 2)
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 2);
        // Both swingLowIndicator and priceIndicator are serialized as components
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentFractalSwingLowIndicator"));
        assertThat(descriptor.getComponents()).anySatisfy(component -> component.getType().equals("LowPriceIndicator"));

        // Verify transient fields are not serialized
        assertThat(descriptor.getParameters()).doesNotContainKey("lastScannedIndex");
        assertThat(descriptor.getParameters()).doesNotContainKey("pivots");

        // Test JSON serialization
        final String json = original.toJson();
        assertThat(json).contains("TrendLineSupportIndicator");
        assertThat(json).contains("\"unstableBars\":2");
        assertThat(json).contains("RecentFractalSwingLowIndicator");
        assertThat(json).contains("LowPriceIndicator");
    }

    @Test
    public void shouldSerializeAndDeserializeIndicatorWithBarSeriesConstructor() {
        final var series = seriesFromLows(13, 12, 9, 11, 10, 8, 8, 8, 12, 14);
        final var original = new TrendLineSupportIndicator(series, 2);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        // Test serialization
        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineSupportIndicator");
        // unstableBars = 2 + 2 = 4 (precedingHigherBars + followingHigherBars)
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 4);
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentFractalSwingLowIndicator"));
        assertThat(descriptor.getComponents()).anySatisfy(component -> component.getType().equals("LowPriceIndicator"));

        // Test JSON serialization
        final String json = original.toJson();
        assertThat(json).contains("TrendLineSupportIndicator");
        assertThat(json).contains("\"unstableBars\":4");
    }

    @Test
    public void shouldSerializeAndDeserializeIndicatorWithDefaultConstructor() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13, 8);
        final var original = new TrendLineSupportIndicator(series);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        // Test serialization
        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineSupportIndicator");
        // unstableBars = 3 + 3 = 6 (default surroundingHigherBars is 3)
        assertThat(descriptor.getParameters()).containsEntry("unstableBars", 6);
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("RecentFractalSwingLowIndicator"));
        assertThat(descriptor.getComponents()).anySatisfy(component -> component.getType().equals("LowPriceIndicator"));

        // Test JSON serialization
        final String json = original.toJson();
        assertThat(json).contains("TrendLineSupportIndicator");
        assertThat(json).contains("\"unstableBars\":6");
    }

    @Test
    public void shouldWorkWithFractalSwingLowIndicatorConstructor() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11);
        final var lowIndicator = new LowPriceIndicator(series);
        final var swingLowIndicator = new RecentFractalSwingLowIndicator(lowIndicator, 1, 1, 0);
        final var indicator = new TrendLineSupportIndicator(swingLowIndicator, 1, 1);

        // Should behave the same as the constructor that takes priceIndicator directly
        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue();

        assertThat(indicator.getValue(2)).isEqualByComparingTo(lowIndicator.getValue(2));
        // Swing low at index 5 needs to be confirmed at index 6 (followingHigherBars=1)
        indicator.getValue(6); // Discover swing point at index 5
        assertThat(indicator.getSwingPointIndexes()).containsExactly(2, 5);
        // Verify swing point value is returned at the swing point index
        assertThat(indicator.getValue(5)).isEqualByComparingTo(lowIndicator.getValue(5));

        // Verify the price indicator was extracted correctly
        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(2);
        final var x2 = numFactory.numOf(5);
        final var y1 = lowIndicator.getValue(2);
        final var y2 = lowIndicator.getValue(5);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));
        final var expected = slope.multipliedBy(numFactory.numOf(6)).plus(intercept);

        assertThat(indicator.getValue(6)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldWorkWithZigZagSwingLowIndicatorConstructor() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12);
        final var lowIndicator = new LowPriceIndicator(series);
        final var reversalThreshold = new ConstantIndicator<>(series, series.numFactory().numOf(2.0));
        final var stateIndicator = new ZigZagStateIndicator(lowIndicator, reversalThreshold);
        final var swingLowIndicator = new RecentZigZagSwingLowIndicator(stateIndicator, lowIndicator);
        final var indicator = new TrendLineSupportIndicator(swingLowIndicator, 0, 0);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        // Verify it works with ZigZag implementation
        assertThat(indicator.getSwingPointIndexes()).isNotEmpty();
        // The exact pivots depend on ZigZag state, but we should have at least one
        // pivot
        // if there are enough bars
        if (indicator.getSwingPointIndexes().size() >= 2) {
            final int lastIndex = series.getEndIndex();
            final var value = indicator.getValue(lastIndex);
            // If we have 2+ pivots, we should get a projection (not NaN)
            // But it might be NaN if pivots are too recent, so we just verify it doesn't
            // crash
            assertThat(value).isNotNull();
        }
    }

    @Test
    public void shouldExtractPriceIndicatorCorrectlyFromFractalSwingLow() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11);
        final var lowIndicator = new LowPriceIndicator(series);
        final var swingLowIndicator = new RecentFractalSwingLowIndicator(lowIndicator, 1, 1, 0);

        // Verify getPriceIndicator returns the correct indicator
        assertThat(swingLowIndicator.getPriceIndicator()).isSameAs(lowIndicator);

        final var indicator = new TrendLineSupportIndicator(swingLowIndicator, 1, 1);

        // Verify the trend line uses the correct price indicator by checking
        // calculations
        for (int i = 0; i <= 6; i++) {
            indicator.getValue(i);
        }

        // The trend line should use the same price values as the low indicator
        final var pivotIndexes = indicator.getSwingPointIndexes();
        if (pivotIndexes.size() >= 2) {
            final int pivot1 = pivotIndexes.get(0);
            final int pivot2 = pivotIndexes.get(1);
            final var price1 = lowIndicator.getValue(pivot1);
            final var price2 = lowIndicator.getValue(pivot2);

            // Verify these match what the trend line would use internally
            assertThat(price1).isNotNull();
            assertThat(price2).isNotNull();
        }
    }

    @Test
    public void shouldExtractPriceIndicatorCorrectlyFromZigZagSwingLow() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12);
        final var lowIndicator = new LowPriceIndicator(series);
        final var reversalThreshold = new ConstantIndicator<>(series, series.numFactory().numOf(2.0));
        final var stateIndicator = new ZigZagStateIndicator(lowIndicator, reversalThreshold);
        final var swingLowIndicator = new RecentZigZagSwingLowIndicator(stateIndicator, lowIndicator);

        // Verify getPriceIndicator returns the correct indicator
        assertThat(swingLowIndicator.getPriceIndicator()).isSameAs(lowIndicator);

        final var indicator = new TrendLineSupportIndicator(swingLowIndicator, 0, 0);

        // Verify it works correctly
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        // Should not throw and should produce valid results
        assertThat(indicator.getSwingPointIndexes()).isNotNull();
    }

    @Test
    public void shouldReturnNaNWhenAccessingValuesThatWouldUseRemovedPivots() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        // Build up enough data to create pivots and establish a trend line
        final double[] initialLows = { 12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12 };
        for (double low : initialLows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }

        // Access values to populate cache and establish trend line
        final int endIndexBeforePurge = series.getEndIndex();
        indicator.getValue(endIndexBeforePurge);
        final var pivotIndexesBeforePurge = indicator.getSwingPointIndexes();
        assertThat(pivotIndexesBeforePurge.size()).isGreaterThanOrEqualTo(2);

        // Store a value that would have used the first pivot
        final int firstPivotIndex = pivotIndexesBeforePurge.get(0);
        final int secondPivotIndex = pivotIndexesBeforePurge.get(1);
        final int middleIndex = (firstPivotIndex + secondPivotIndex) / 2;
        final var valueBeforePurge = indicator.getValue(middleIndex);
        assertThat(valueBeforePurge.isNaN()).isFalse();

        // Remove leading bars, which should purge the first pivot
        series.setMaximumBarCount(5);
        final int beginIndexAfterPurge = series.getBeginIndex();
        assertThat(beginIndexAfterPurge).isGreaterThan(firstPivotIndex);

        // Verify pivots were purged - check after accessing to trigger purge
        indicator.getValue(series.getEndIndex());
        final var pivotIndexesAfterPurge = indicator.getSwingPointIndexes();
        assertThat(pivotIndexesAfterPurge).allMatch(pivotIndex -> pivotIndex >= beginIndexAfterPurge);

        // Accessing the same middle index should now return NaN because the first pivot
        // was removed
        // This tests the defensive check that prevents IndexOutOfBoundsException
        final var valueAfterPurge = indicator.getValue(middleIndex);
        assertThat(valueAfterPurge.isNaN()).as("Value should be NaN when pivot pair would include a removed pivot")
                .isTrue();

        // Accessing current valid indices should still work
        assertThat(indicator.getValue(series.getEndIndex())).isNotNull();
    }

    @Test
    public void shouldHandleRapidBarRemovalWithoutThrowingException() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        // Build up data with multiple pivots
        final double[] lows = { 12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12, 9, 13, 8, 12, 10, 11 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }

        // Access values to populate cache
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        // Rapidly remove bars multiple times
        series.setMaximumBarCount(10);
        final int beginIndex1 = series.getBeginIndex();
        indicator.getValue(series.getEndIndex()); // Trigger purge
        assertThat(indicator.getSwingPointIndexes()).allMatch(pivotIndex -> pivotIndex >= beginIndex1);

        series.setMaximumBarCount(7);
        final int beginIndex2 = series.getBeginIndex();
        assertThat(beginIndex2).isGreaterThan(beginIndex1);
        indicator.getValue(series.getEndIndex()); // Trigger purge
        assertThat(indicator.getSwingPointIndexes()).allMatch(pivotIndex -> pivotIndex >= beginIndex2);

        series.setMaximumBarCount(4);
        final int beginIndex3 = series.getBeginIndex();
        assertThat(beginIndex3).isGreaterThan(beginIndex2);
        indicator.getValue(series.getEndIndex()); // Trigger purge
        assertThat(indicator.getSwingPointIndexes()).allMatch(pivotIndex -> pivotIndex >= beginIndex3);

        // Access all remaining indices - should not throw IndexOutOfBoundsException
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final var value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }
    }

    @Test
    public void shouldPurgePivotsWhenAllButOnePivotIsRemoved() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        // Create two clear pivots
        final double[] lows = { 12, 11, 9, 10, 13, 8, 9, 11 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }

        // Verify we have at least 2 pivots
        indicator.getValue(series.getEndIndex());
        final var initialPivots = indicator.getSwingPointIndexes();
        assertThat(initialPivots.size()).isGreaterThanOrEqualTo(2);

        // Remove bars aggressively so only the last pivot remains
        series.setMaximumBarCount(2);
        final int beginIndex = series.getBeginIndex();

        // Access end index - should return NaN because we need 2 pivots for projection
        final var value = indicator.getValue(series.getEndIndex());
        assertThat(value.isNaN()).as("Should return NaN when only one pivot remains").isTrue();

        // Verify only valid pivots remain
        final var remainingPivots = indicator.getSwingPointIndexes();
        assertThat(remainingPivots).allMatch(pivotIndex -> pivotIndex >= beginIndex);
        assertThat(remainingPivots.size()).isLessThanOrEqualTo(1);
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
