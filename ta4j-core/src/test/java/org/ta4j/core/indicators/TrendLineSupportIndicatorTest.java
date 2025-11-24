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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ScoringWeights;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;

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

        final var expected = expectedProjection(series, 1, 4, 9);
        final var priceAtIndex = series.getBar(9).getLowPrice();

        assertThat(indicator.getValue(9).minus(expected).abs().doubleValue()).isLessThan(1e-9);
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

        final var expected = expectedProjection(series, 4, 7, 9);

        assertThat(indicator.getValue(9).minus(expected).abs().doubleValue()).isLessThan(1e-9);
    }

    @Test
    public void shouldFavorLinesWithFewerOutsideSwings() {
        final var series = seriesFromLows(9, 5, 8, 11, 10, 15, 16, 6, 20);
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var expected = expectedProjection(series, 1, 7, 8);

        assertThat(indicator.getValue(8).minus(expected).abs().doubleValue()).isLessThan(1e-9);
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
        assertThat(descriptor.getComponents()).hasSize(1);
        final ComponentDescriptor swingDescriptor = descriptor.getComponents().getFirst();
        assertThat(swingDescriptor.getType()).isEqualTo("RecentFractalSwingLowIndicator");
        assertThat(swingDescriptor.getComponents())
                .anySatisfy(component -> assertThat(component.getType()).isEqualTo("LowPriceIndicator"));

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
    public void shouldProjectUsingBarTimestampsWhenSpacingIsIrregular() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, "2024-01-01T00:00:00Z", 100d);
        addBar(series, "2024-01-02T00:00:00Z", 102d);
        addBar(series, "2024-01-03T00:00:00Z", 104d);
        addBar(series, "2024-01-06T00:00:00Z", 110d);

        final var priceIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new StaticSwingIndicator(priceIndicator, List.of(0, 3));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, Integer.MAX_VALUE);

        final Num expectedAtIndex1 = expectedProjection(series, 0, 3, 1);
        final Num expectedAtIndex2 = expectedProjection(series, 0, 3, 2);

        assertThat(indicator.getValue(1)).isEqualByComparingTo(expectedAtIndex1);
        assertThat(indicator.getValue(2)).isEqualByComparingTo(expectedAtIndex2);
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

    @Test
    public void shouldRoundTripCustomScoringWeightsThroughSerialization() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13, 8);
        final var weights = ScoringWeights.of(0.40d, 0.20d, 0.15d, 0.15d, 0.05d, 0.05d);
        final var indicator = new TrendLineSupportIndicator(series, 1, 15, weights);

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(Double.parseDouble(descriptor.getParameters().get("touchWeight").toString()))
                .isEqualTo(weights.touchWeight);
        assertThat(Double.parseDouble(descriptor.getParameters().get("containBonus").toString()))
                .isEqualTo(weights.containBonus);

        final String json = indicator.toJson();
        final Indicator<?> restored = IndicatorSerialization.fromJson(series, json);
        assertThat(restored).isInstanceOf(TrendLineSupportIndicator.class);
        final ScoringWeights restoredWeights = ((TrendLineSupportIndicator) restored).getScoringWeights();
        assertThat(restoredWeights.touchWeight).isEqualTo(weights.touchWeight);
        assertThat(restoredWeights.extremeWeight).isEqualTo(weights.extremeWeight);
        assertThat(restoredWeights.outsideWeight).isEqualTo(weights.outsideWeight);
        assertThat(restoredWeights.proximityWeight).isEqualTo(weights.proximityWeight);
        assertThat(restoredWeights.recencyWeight).isEqualTo(weights.recencyWeight);
        assertThat(restoredWeights.containBonus).isEqualTo(weights.containBonus);
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

    private void addBar(BarSeries series, String isoInstant, double lowPrice) {
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse(isoInstant))
                .openPrice(lowPrice)
                .closePrice(lowPrice)
                .highPrice(lowPrice + 1d)
                .lowPrice(lowPrice)
                .volume(1d)
                .add();
    }

    private Num expectedProjection(BarSeries series, int startIndex, int endIndex, int targetIndex) {
        final var factory = series.numFactory();
        final Num startPrice = series.getBar(startIndex).getLowPrice();
        final Num endPrice = series.getBar(endIndex).getLowPrice();
        final long startMillis = series.getBar(startIndex).getEndTime().toEpochMilli();
        final long endMillis = series.getBar(endIndex).getEndTime().toEpochMilli();
        final long targetMillis = series.getBar(targetIndex).getEndTime().toEpochMilli();

        final Num numerator = endPrice.minus(startPrice);
        final Num denominator = factory.numOf(endMillis - startMillis);
        final Num slope = numerator.dividedBy(denominator);
        final Num delta = factory.numOf(targetMillis - startMillis);
        return slope.multipliedBy(delta).plus(startPrice);
    }

    private static final class StaticSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

        private final Indicator<Num> priceIndicator;
        private final List<Integer> swingIndexes;

        private StaticSwingIndicator(Indicator<Num> priceIndicator, List<Integer> swingIndexes) {
            super(priceIndicator);
            this.priceIndicator = priceIndicator;
            this.swingIndexes = List.copyOf(swingIndexes);
        }

        @Override
        public int getLatestSwingIndex(int index) {
            for (int i = swingIndexes.size() - 1; i >= 0; i--) {
                final int swingIndex = swingIndexes.get(i);
                if (swingIndex <= index) {
                    return swingIndex;
                }
            }
            return -1;
        }

        @Override
        public List<Integer> getSwingPointIndexesUpTo(int index) {
            final List<Integer> result = new ArrayList<>();
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    result.add(swingIndex);
                }
            }
            return List.copyOf(result);
        }

        @Override
        public Indicator<Num> getPriceIndicator() {
            return priceIndicator;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            final int swingIndex = getLatestSwingIndex(index);
            return swingIndex >= 0 ? priceIndicator.getValue(swingIndex) : NaN;
        }
    }
}
