/*
 * SPDX-License-Identifier: MIT
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
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings;
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
    public void shouldSelectHighestScoringLineWhenTouchesTie() {
        final var series = seriesFromLows(14, 11, 13, 12, 9, 11, 13, 10, 12, 8);
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var expected = expectedProjection(series, 4, 7, 9);

        assertThat(indicator.getValue(9).minus(expected).abs().doubleValue()).isLessThan(1e-9);
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
        assertThat(descriptor.getParameters()).doesNotContainKey("unstableBars");
        assertThat(descriptor.getParameters()).containsEntry("barCount", 15);
        assertThat(descriptor.getComponents()).hasSize(1);
        final ComponentDescriptor swingDescriptor = descriptor.getComponents().getFirst();
        assertThat(swingDescriptor.getType()).isEqualTo("RecentFractalSwingLowIndicator");
        assertThat(swingDescriptor.getParameters()).containsKey("unstableBars");
        assertThat(swingDescriptor.getComponents())
                .anySatisfy(component -> assertThat(component.getType()).isEqualTo("LowPriceIndicator"));

        final String json = indicator.toJson();
        assertThat(json).contains("TrendLineSupportIndicator");
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
        final var weights = ScoringWeights.of(0.40d, 0.20d, 0.15d, 0.15d, 0.10d);
        final var indicator = new TrendLineSupportIndicator(series, 1, 15, weights);

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(parseDoubleParameter(descriptor, "touchCountWeight")).isEqualTo(weights.touchCountWeight);

        final String json = indicator.toJson();
        final Indicator<?> restored = IndicatorSerialization.fromJson(series, json);
        assertThat(restored).isInstanceOf(TrendLineSupportIndicator.class);
        final ScoringWeights restoredWeights = ((TrendLineSupportIndicator) restored).getScoringWeights();
        assertThat(restoredWeights.touchCountWeight).isEqualTo(weights.touchCountWeight);
        assertThat(restoredWeights.touchesExtremeWeight).isEqualTo(weights.touchesExtremeWeight);
        assertThat(restoredWeights.outsideCountWeight).isEqualTo(weights.outsideCountWeight);
        assertThat(restoredWeights.averageDeviationWeight).isEqualTo(weights.averageDeviationWeight);
        assertThat(restoredWeights.anchorRecencyWeight).isEqualTo(weights.anchorRecencyWeight);
    }

    @Test
    public void shouldRespectSwingAndPairCaps() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        // Simple ascending lows so the last two swings define the line
        for (int i = 0; i < 6; i++) {
            final double low = 10 + i;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(low + 1d).lowPrice(low).add();
        }
        final var swingIndicator = new StaticSwingIndicator(new LowPriceIndicator(series), List.of(0, 1, 2, 3, 4));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 10, 0.30d, 0.20d, 0.15d, 0.20d, 0.15d,
                ToleranceSettings.defaultSettings(), 2, 3);

        indicator.getValue(series.getEndIndex());

        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
        assertThat(segment.firstIndex).isEqualTo(3);
        assertThat(segment.secondIndex).isEqualTo(4);
        assertThat(indicator.getMaxSwingPointsForTrendline()).isEqualTo(2);
        assertThat(indicator.getMaxCandidatePairs()).isEqualTo(3);
    }

    @Test
    public void shouldReturnValueAtWindowStartWhenLineExists() {
        final var series = seriesFromLows(10, 7, 9, 6, 8);
        final var indicator = new TrendLineSupportIndicator(series, 1, 4);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        final int endIndex = series.getEndIndex();
        final int windowStart = endIndex - 4 + 1;

        assertThat(windowStart).isEqualTo(1);
        assertThat(indicator.getValue(windowStart).isNaN()).isFalse();
        assertThat(indicator.getValue(windowStart - 1).isNaN()).isTrue();
    }

    @Test
    public void shouldReturnValueAtNewWindowStartAfterAdvance() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] lows = { 10, 7, 9, 6, 9, 5, 9 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        final var indicator = new TrendLineSupportIndicator(series, 1, 4);

        indicator.getValue(series.getEndIndex());

        final int endIndex = series.getEndIndex();
        final int windowStart = endIndex - 4 + 1;

        assertThat(windowStart).isEqualTo(3);
        assertThat(indicator.getValue(windowStart).isNaN()).isFalse();
        assertThat(indicator.getValue(windowStart - 1).isNaN()).isTrue();
    }

    @Test
    public void shouldInvalidateCachedValuesWhenWindowAdvances() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] lows = { 9, 6, 8, 5, 9 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        final var indicator = new TrendLineSupportIndicator(series, 1, 4);

        final Num initialValue = indicator.getValue(1);
        assertThat(initialValue.isNaN()).isFalse();

        series.barBuilder().openPrice(8).closePrice(8).highPrice(10).lowPrice(8).add();
        series.barBuilder().openPrice(9).closePrice(9).highPrice(11).lowPrice(9).add();

        final int endIndex = series.getEndIndex();
        final int windowStart = endIndex - 4 + 1;

        assertThat(windowStart).isEqualTo(3);
        assertThat(indicator.getValue(1).isNaN()).isTrue();
        assertThat(indicator.getValue(windowStart).isNaN()).isFalse();
    }

    @Test
    public void shouldRescoreUsingCachedGeometryWhenSwingsUnchanged() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();

        addBar(series, "2025-01-01T00:00:00Z", 10);
        addBar(series, "2025-01-02T00:00:00Z", 8);
        addBar(series, "2025-01-03T00:00:00Z", 12);
        addBar(series, "2025-01-04T00:00:00Z", 7);
        addBar(series, "2025-01-05T00:00:00Z", 11);

        final var priceIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new StaticSwingIndicator(priceIndicator, List.of(1, 3));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, Integer.MAX_VALUE,
                ScoringWeights.defaultWeights());

        final int initialEnd = series.getEndIndex();
        final Num initialValue = indicator.getValue(initialEnd);
        final Num expectedInitial = expectedProjection(series, 1, 3, initialEnd);
        assertThat(initialValue).isEqualByComparingTo(expectedInitial);

        addBar(series, "2025-01-06T00:00:00Z", 15);

        final int newEnd = series.getEndIndex();
        final Num updatedValue = indicator.getValue(newEnd);
        final Num expectedUpdated = expectedProjection(series, 1, 3, newEnd);

        assertThat(updatedValue).isEqualByComparingTo(expectedUpdated);
    }

    @Test
    public void shouldFallbackWhenPriceIndicatorHasWarmupNaNs() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] lows = { 12, 11, 10, 8, 11, 9, 12 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        final var lowIndicator = new LowPriceIndicator(series);
        final var warmupIndicator = new WarmupIndicator(lowIndicator, 2);
        final var swingIndicator = new RecentFractalSwingLowIndicator(warmupIndicator, 1, 1, 0);
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 1, 1, 10);

        final int endIndex = series.getEndIndex();
        final Num expected = expectedProjection(series, 3, 5, endIndex);
        assertThat(indicator.getValue(endIndex)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldReturnNaNForOutOfBoundsIndices() {
        final var series = seriesFromLows(10, 8, 9, 8, 9);
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();

        assertThat(indicator.getValue(beginIndex - 1).isNaN()).isTrue();
        assertThat(indicator.getValue(endIndex + 1).isNaN()).isTrue();
    }

    @Test
    public void shouldUseEpochMillisecondOffsetsNotBarIndices() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();

        addBar(series, "2024-01-01T00:00:00Z", 100d);
        addBar(series, "2024-01-02T00:00:00Z", 98d);
        addBar(series, "2024-01-05T00:00:00Z", 96d);
        addBar(series, "2024-01-10T00:00:00Z", 94d);

        final var priceIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new StaticSwingIndicator(priceIndicator, List.of(0, 2));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, Integer.MAX_VALUE);

        final Num valueAtIndex1 = indicator.getValue(1);
        final Num valueAtIndex3 = indicator.getValue(3);

        final Num expectedAtIndex1 = expectedProjection(series, 0, 2, 1);
        final Num expectedAtIndex3 = expectedProjection(series, 0, 2, 3);

        assertThat(valueAtIndex1).isEqualByComparingTo(expectedAtIndex1);
        assertThat(valueAtIndex3).isEqualByComparingTo(expectedAtIndex3);

        final long millisBetween0And2 = series.getBar(2).getEndTime().toEpochMilli()
                - series.getBar(0).getEndTime().toEpochMilli();
        final long millisBetween0And1 = series.getBar(1).getEndTime().toEpochMilli()
                - series.getBar(0).getEndTime().toEpochMilli();

        assertThat(millisBetween0And2).isEqualTo(4 * 24 * 60 * 60 * 1000L);
        assertThat(millisBetween0And1).isEqualTo(1 * 24 * 60 * 60 * 1000L);

        final Num priceDiff = series.getBar(2).getLowPrice().minus(series.getBar(0).getLowPrice());
        final Num timeDiff = series.numFactory().numOf(millisBetween0And2);
        final Num slope = priceDiff.dividedBy(timeDiff);

        final Num expectedValue1 = series.getBar(0)
                .getLowPrice()
                .plus(slope.multipliedBy(series.numFactory().numOf(millisBetween0And1)));

        assertThat(valueAtIndex1).isEqualByComparingTo(expectedValue1);
    }

    @Test
    public void shouldHandleEpochTimeZeroAsValidBase() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();

        final Instant epochZero = Instant.ofEpochMilli(0L);
        addBarAtInstant(series, epochZero, 100d);
        addBarAtInstant(series, epochZero.plus(Duration.ofDays(1)), 98d);
        addBarAtInstant(series, epochZero.plus(Duration.ofDays(2)), 96d);
        addBarAtInstant(series, epochZero.plus(Duration.ofDays(3)), 94d);

        final var priceIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new StaticSwingIndicator(priceIndicator, List.of(0, 2));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, Integer.MAX_VALUE);

        final Num valueAtIndex1 = indicator.getValue(1);
        final Num valueAtIndex3 = indicator.getValue(3);

        final Num expectedAtIndex1 = expectedProjection(series, 0, 2, 1);
        final Num expectedAtIndex3 = expectedProjection(series, 0, 2, 3);

        assertThat(valueAtIndex1).isEqualByComparingTo(expectedAtIndex1);
        assertThat(valueAtIndex3).isEqualByComparingTo(expectedAtIndex3);
        assertThat(valueAtIndex1.isNaN()).isFalse();
        assertThat(valueAtIndex3.isNaN()).isFalse();
    }

    @Test
    public void shouldUseConsistentCoordinateScaleForSlopeCalculation() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();

        addBar(series, "2024-01-01T00:00:00Z", 100d);
        addBar(series, "2024-01-02T00:00:00Z", 102d);
        addBar(series, "2024-01-10T00:00:00Z", 118d);
        addBar(series, "2024-01-20T00:00:00Z", 138d);

        final var priceIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new StaticSwingIndicator(priceIndicator, List.of(0, 2));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, Integer.MAX_VALUE);

        final Num valueAtIndex1 = indicator.getValue(1);
        final Num valueAtIndex3 = indicator.getValue(3);

        final Num expectedAtIndex1 = expectedProjection(series, 0, 2, 1);
        final Num expectedAtIndex3 = expectedProjection(series, 0, 2, 3);

        assertThat(valueAtIndex1).isEqualByComparingTo(expectedAtIndex1);
        assertThat(valueAtIndex3).isEqualByComparingTo(expectedAtIndex3);

        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();

        final long startMillis = series.getBar(segment.firstIndex).getEndTime().toEpochMilli();
        final long endMillis = series.getBar(segment.secondIndex).getEndTime().toEpochMilli();
        final long target1Millis = series.getBar(1).getEndTime().toEpochMilli();
        final long target3Millis = series.getBar(3).getEndTime().toEpochMilli();

        final Num startPrice = series.getBar(segment.firstIndex).getLowPrice();
        final Num endPrice = series.getBar(segment.secondIndex).getLowPrice();

        final Num priceDiff = endPrice.minus(startPrice);
        final Num timeDiff = series.numFactory().numOf(endMillis - startMillis);
        final Num slope = priceDiff.dividedBy(timeDiff);

        final Num expected1 = startPrice
                .plus(slope.multipliedBy(series.numFactory().numOf(target1Millis - startMillis)));
        final Num expected3 = startPrice
                .plus(slope.multipliedBy(series.numFactory().numOf(target3Millis - startMillis)));

        assertThat(valueAtIndex1).isEqualByComparingTo(expected1);
        assertThat(valueAtIndex3).isEqualByComparingTo(expected3);
    }

    @Test
    public void shouldInvalidateCacheWhenSwingsChange() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        final double[] lows = { 10d, 9d, 0d };
        for (double low : lows) {
            series.barBuilder().openPrice(low).closePrice(low).highPrice(low + 1d).lowPrice(low).add();
        }
        final var lowIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new MutableSwingIndicator(lowIndicator, List.of(0, 1));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, 3, ScoringWeights.defaultWeights());

        final Num initial = indicator.getValue(series.getEndIndex());
        assertThat(initial.isNaN()).isFalse();

        swingIndicator.addSwing(2);

        final Num updated = indicator.getValue(series.getEndIndex());
        assertThat(updated.isNaN()).isFalse();
        assertThat(updated).isNotEqualByComparingTo(initial);
    }

    @Test
    public void shouldInvalidateCacheWhenWindowMovesForward() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        final double[] lows = { 10d, 8d, 6d };
        for (double low : lows) {
            series.barBuilder().openPrice(low).closePrice(low).highPrice(low + 1d).lowPrice(low).add();
        }
        final var lowIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new MutableSwingIndicator(lowIndicator, List.of(1, 2));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 0, 0, 2, ScoringWeights.defaultWeights());

        final Num initial = indicator.getValue(2);
        assertThat(initial.isNaN()).isFalse();

        series.barBuilder().openPrice(1d).closePrice(1d).highPrice(2d).lowPrice(1d).add();
        swingIndicator.addSwing(3);

        assertThat(indicator.getValue(1).isNaN()).isTrue();
    }

    @Test
    public void shouldRecomputeTrendLineWhenNewSwingIsConfirmed() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();

        addBar(series, "2025-01-01T00:00:00Z", 12d); // 0
        addBar(series, "2025-01-02T00:00:00Z", 10d); // 1 swing
        addBar(series, "2025-01-03T00:00:00Z", 11d); // 2
        addBar(series, "2025-01-04T00:00:00Z", 9d); // 3 swing
        addBar(series, "2025-01-05T00:00:00Z", 12d); // 4

        final var lowIndicator = new LowPriceIndicator(series);
        final var swingIndicator = new MutableSwingIndicator(lowIndicator, List.of(1, 3));
        final var weights = ScoringWeights.defaultWeights();
        final var indicator = new TrendLineSupportIndicator(swingIndicator, Integer.MAX_VALUE, weights.touchCountWeight,
                weights.touchesExtremeWeight, weights.outsideCountWeight, weights.averageDeviationWeight,
                weights.anchorRecencyWeight, ToleranceSettings.defaultSettings(), 2, 10);

        final int initialEnd = series.getEndIndex();
        final Num initialValue = indicator.getValue(initialEnd);
        final var initialSegment = indicator.getCurrentSegment();
        final Num expectedInitial = expectedProjection(series, 1, 3, initialEnd);

        assertThat(initialSegment).isNotNull();
        assertThat(initialSegment.firstIndex).isEqualTo(1);
        assertThat(initialSegment.secondIndex).isEqualTo(3);
        assertThat(initialValue).isEqualByComparingTo(expectedInitial);

        addBar(series, "2025-01-06T00:00:00Z", 8d); // 5 new swing
        swingIndicator.addSwing(5);

        final int updatedEnd = series.getEndIndex();
        final Num updatedValue = indicator.getValue(updatedEnd);
        final var updatedSegment = indicator.getCurrentSegment();
        final Num expectedUpdated = expectedProjection(series, 3, 5, updatedEnd);

        assertThat(updatedSegment).isNotNull();
        assertThat(updatedSegment.firstIndex).isEqualTo(3);
        assertThat(updatedSegment.secondIndex).isEqualTo(5);
        assertThat(updatedValue).isEqualByComparingTo(expectedUpdated);
        assertThat(updatedValue).isNotEqualByComparingTo(initialValue);
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

    private void addBarAtInstant(BarSeries series, Instant instant, double lowPrice) {
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(instant)
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

    /**
     * Safely extracts a double value from a ComponentDescriptor parameter map.
     * Handles NumberFormatException gracefully with a meaningful error message.
     *
     * @param descriptor    the component descriptor
     * @param parameterName the parameter name
     * @return the double value
     * @throws AssertionError if the parameter is missing, null, or cannot be parsed
     *                        as a double
     */
    private double parseDoubleParameter(ComponentDescriptor descriptor, String parameterName) {
        final Object value = descriptor.getParameters().get(parameterName);
        if (value == null) {
            throw new AssertionError(String.format("Parameter '%s' is missing or null in descriptor", parameterName));
        }

        // If already a Number, extract double value directly
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        // Try parsing from string representation
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new AssertionError(
                    String.format("Failed to parse parameter '%s' as double. Value: '%s' (type: %s). Error: %s",
                            parameterName, value, value.getClass().getName(), e.getMessage()),
                    e);
        }
    }

    /**
     * Safely extracts an int value from a ComponentDescriptor parameter map.
     * Handles NumberFormatException gracefully with a meaningful error message.
     *
     * @param descriptor    the component descriptor
     * @param parameterName the parameter name
     * @return the int value
     * @throws AssertionError if the parameter is missing, null, or cannot be parsed
     *                        as an int
     */

    private static final class WarmupIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> delegate;
        private final int warmupBars;

        private WarmupIndicator(Indicator<Num> delegate, int warmupBars) {
            super(delegate);
            this.delegate = delegate;
            this.warmupBars = warmupBars;
        }

        @Override
        public int getCountOfUnstableBars() {
            return warmupBars;
        }

        @Override
        protected Num calculate(int index) {
            if (index < getBarSeries().getBeginIndex() + warmupBars) {
                return NaN;
            }
            return delegate.getValue(index);
        }
    }

    private static final class MutableSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

        private final Indicator<Num> priceIndicator;
        private final List<Integer> swingIndexes;

        private MutableSwingIndicator(Indicator<Num> priceIndicator, List<Integer> swingIndexes) {
            super(priceIndicator);
            this.priceIndicator = priceIndicator;
            this.swingIndexes = new ArrayList<>(swingIndexes);
        }

        void addSwing(int index) {
            swingIndexes.add(index);
            swingIndexes.sort(Integer::compare);
            invalidateCache();
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
