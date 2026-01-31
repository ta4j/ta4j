/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ScoringWeights;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;

public class TrendLineResistanceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineResistanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldSelectLineTouchingMostSwingHighs() {
        final var series = seriesFromHighs(10, 12, 11, 12, 11, 12, 9);
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 3, 5);
        assertThat(indicator.getValue(series.getEndIndex())).isEqualByComparingTo(series.numFactory().numOf(12));
    }

    @Test
    public void shouldSelectHighestScoringLineWhenTouchesTie() {
        final var series = seriesFromHighs(12, 14, 13, 13, 16, 14, 12, 15, 13, 18);
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var expected = expectedProjection(series, 4, 7, 9);

        assertThat(indicator.getValue(9).minus(expected).abs().doubleValue()).isLessThan(1e-9);
    }

    @Test
    public void shouldLimitSwingHighsToLookbackWindow() {
        final var series = seriesFromHighs(12, 14, 13, 13, 16, 14, 12, 15, 13, 18);
        final var indicator = new TrendLineResistanceIndicator(series, 1, 6);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 4, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(4);
        final var x2 = numFactory.numOf(7);
        final var y1 = numFactory.numOf(16);
        final var y2 = numFactory.numOf(15);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y1.minus(slope.multipliedBy(x1));
        final var expected = slope.multipliedBy(numFactory.numOf(9)).plus(intercept);

        assertThat(indicator.getValue(9)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldFavorExtremeTouchOnEqualTouches() {
        final var series = seriesFromHighs(5, 15, 8, 20, 10, 18, 9, 24, 11);
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        assertThat(indicator.getSwingPointIndexes()).containsExactly(1, 3, 5, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(3);
        final var x2 = numFactory.numOf(7);
        final var y1 = numFactory.numOf(20);
        final var y2 = numFactory.numOf(24);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y1.minus(slope.multipliedBy(x1));
        final var expected = slope.multipliedBy(numFactory.numOf(8)).plus(intercept);

        assertThat(indicator.getValue(8)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldSerializeIncludingBarCount() {
        final var series = seriesFromHighs(11, 14, 13, 16, 12, 15, 11, 14, 13);
        final var highIndicator = new HighPriceIndicator(series);
        final var indicator = new TrendLineResistanceIndicator(highIndicator, 1, 1, 0, 20);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("TrendLineResistanceIndicator");
        assertThat(descriptor.getParameters()).doesNotContainKey("unstableBars");
        assertThat(descriptor.getParameters()).containsEntry("barCount", 20);
        assertThat(descriptor.getComponents()).hasSize(1);
        final ComponentDescriptor swingDescriptor = descriptor.getComponents().getFirst();
        assertThat(swingDescriptor.getType()).isEqualTo("RecentFractalSwingHighIndicator");
        assertThat(swingDescriptor.getParameters()).containsKey("unstableBars");
        assertThat(swingDescriptor.getComponents())
                .anySatisfy(component -> assertThat(component.getType()).isEqualTo("HighPriceIndicator"));

        final String json = indicator.toJson();
        assertThat(json).contains("TrendLineResistanceIndicator");
        assertThat(json).contains("\"barCount\":20");
    }

    @Test
    public void shouldWorkWithZigZagSwingHighIndicator() {
        final var series = seriesFromHighs(12, 13, 15, 14, 16, 17, 15, 14, 18, 16, 15);
        final var highIndicator = new HighPriceIndicator(series);
        final var reversalThreshold = new ConstantIndicator<>(series, series.numFactory().numOf(2.0));
        final var stateIndicator = new ZigZagStateIndicator(highIndicator, reversalThreshold);
        final var swingHighIndicator = new RecentZigZagSwingHighIndicator(stateIndicator, highIndicator);
        final var indicator = new TrendLineResistanceIndicator(swingHighIndicator, 0, 0, 10);

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
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE);

        final double[] highs = { 12, 13, 15, 14, 16, 17, 15, 14, 18, 16, 15, 17, 19, 16, 18, 17, 19 };
        for (double high : highs) {
            final double low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
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
        final var series = seriesFromHighs(11, 14, 13, 16, 12, 15, 11, 14, 13);
        final var weights = ScoringWeights.extremeSwingBiasPreset();
        final var indicator = new TrendLineResistanceIndicator(series, 1, 12, weights);

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        try {
            assertThat(Double.parseDouble(descriptor.getParameters().get("touchesExtremeWeight").toString()))
                    .isEqualTo(weights.touchesExtremeWeight);
        } catch (NumberFormatException e) {
            fail("Failed to parse 'touchesExtremeWeight' parameter as double", e);
        }
        try {
            assertThat(Double.parseDouble(descriptor.getParameters().get("touchCountWeight").toString()))
                    .isEqualTo(weights.touchCountWeight);
        } catch (NumberFormatException e) {
            fail("Failed to parse 'touchCountWeight' parameter as double", e);
        }

        final String json = indicator.toJson();
        final Indicator<?> restored = IndicatorSerialization.fromJson(series, json);
        assertThat(restored).isInstanceOf(TrendLineResistanceIndicator.class);
        final ScoringWeights restoredWeights = ((TrendLineResistanceIndicator) restored).getScoringWeights();
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
        for (int i = 0; i < 6; i++) {
            final double high = 20 + i;
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(high - 1d).add();
        }
        final var swingIndicator = new StaticSwingIndicator(new HighPriceIndicator(series), List.of(0, 1, 2, 3, 4));
        final var indicator = new TrendLineResistanceIndicator(swingIndicator, 10, 0.30d, 0.20d, 0.15d, 0.20d, 0.15d,
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
        final var series = seriesFromHighs(10, 13, 11, 14, 12);
        final var indicator = new TrendLineResistanceIndicator(series, 1, 4);

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
        final double[] highs = { 10, 13, 11, 14, 11, 15, 12 };
        for (double high : highs) {
            final double low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
        }
        final var indicator = new TrendLineResistanceIndicator(series, 1, 4);

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
        final double[] highs = { 9, 12, 10, 13, 9 };
        for (double high : highs) {
            final double low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
        }
        final var indicator = new TrendLineResistanceIndicator(series, 1, 4);

        final Num initialValue = indicator.getValue(1);
        assertThat(initialValue.isNaN()).isFalse();

        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(9).add();

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

        addBar(series, "2025-01-01T00:00:00Z", 12);
        addBar(series, "2025-01-02T00:00:00Z", 15);
        addBar(series, "2025-01-03T00:00:00Z", 13);
        addBar(series, "2025-01-04T00:00:00Z", 17);
        addBar(series, "2025-01-05T00:00:00Z", 14);

        final var priceIndicator = new HighPriceIndicator(series);
        final var swingIndicator = new StaticSwingIndicator(priceIndicator, List.of(1, 3));
        final var indicator = new TrendLineResistanceIndicator(swingIndicator, 0, 0, Integer.MAX_VALUE,
                ScoringWeights.defaultWeights());

        final int initialEnd = series.getEndIndex();
        final Num initialValue = indicator.getValue(initialEnd);
        final Num expectedInitial = expectedProjection(series, 1, 3, initialEnd);
        assertThat(initialValue).isEqualByComparingTo(expectedInitial);

        addBar(series, "2025-01-06T00:00:00Z", 16);

        final int newEnd = series.getEndIndex();
        final Num updatedValue = indicator.getValue(newEnd);
        final Num expectedUpdated = expectedProjection(series, 1, 3, newEnd);

        assertThat(updatedValue).isEqualByComparingTo(expectedUpdated);
    }

    @Test
    public void shouldFallbackWhenPriceIndicatorHasWarmupNaNs() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] highs = { 12, 13, 15, 17, 16, 19, 17, 21 };
        for (double high : highs) {
            final double low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
        }
        final var highIndicator = new HighPriceIndicator(series);
        final var warmupIndicator = new WarmupIndicator(highIndicator, 2);
        final var swingIndicator = new RecentFractalSwingHighIndicator(warmupIndicator, 1, 1, 0);
        final var indicator = new TrendLineResistanceIndicator(swingIndicator, 1, 1, 10);

        final int endIndex = series.getEndIndex();
        final Num expected = expectedProjection(series, 3, 5, endIndex);
        assertThat(indicator.getValue(endIndex)).isEqualByComparingTo(expected);
    }

    @Test
    public void shouldRecomputeTrendLineWhenNewSwingIsConfirmed() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();

        addBar(series, "2025-01-01T00:00:00Z", 18d); // 0
        addBar(series, "2025-01-02T00:00:00Z", 20d); // 1 swing
        addBar(series, "2025-01-03T00:00:00Z", 19d); // 2
        addBar(series, "2025-01-04T00:00:00Z", 21d); // 3 swing
        addBar(series, "2025-01-05T00:00:00Z", 19d); // 4

        final var highIndicator = new HighPriceIndicator(series);
        final var swingIndicator = new MutableSwingIndicator(highIndicator, List.of(1, 3));
        final var weights = ScoringWeights.defaultWeights();
        final var indicator = new TrendLineResistanceIndicator(swingIndicator, Integer.MAX_VALUE,
                weights.touchCountWeight, weights.touchesExtremeWeight, weights.outsideCountWeight,
                weights.averageDeviationWeight, weights.anchorRecencyWeight, ToleranceSettings.defaultSettings(), 2,
                10);

        final int initialEnd = series.getEndIndex();
        final Num initialValue = indicator.getValue(initialEnd);
        final var initialSegment = indicator.getCurrentSegment();
        final Num expectedInitial = expectedProjection(series, 1, 3, initialEnd);

        assertThat(initialSegment).isNotNull();
        assertThat(initialSegment.firstIndex).isEqualTo(1);
        assertThat(initialSegment.secondIndex).isEqualTo(3);
        assertThat(initialValue).isEqualByComparingTo(expectedInitial);

        addBar(series, "2025-01-06T00:00:00Z", 22d); // 5 new swing
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

    private BarSeries seriesFromHighs(double... highs) {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        for (double high : highs) {
            final var low = Math.max(0d, high - 2d);
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
        }
        return series;
    }

    private void addBar(BarSeries series, String isoInstant, double highPrice) {
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse(isoInstant))
                .openPrice(highPrice)
                .closePrice(highPrice)
                .highPrice(highPrice)
                .lowPrice(Math.max(0d, highPrice - 1d))
                .volume(1d)
                .add();
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

    private Num expectedProjection(BarSeries series, int startIndex, int endIndex, int targetIndex) {
        final var factory = series.numFactory();
        final Num startPrice = series.getBar(startIndex).getHighPrice();
        final Num endPrice = series.getBar(endIndex).getHighPrice();
        final long startMillis = series.getBar(startIndex).getEndTime().toEpochMilli();
        final long endMillis = series.getBar(endIndex).getEndTime().toEpochMilli();
        final long targetMillis = series.getBar(targetIndex).getEndTime().toEpochMilli();

        final Num numerator = endPrice.minus(startPrice);
        final Num denominator = factory.numOf(endMillis - startMillis);
        final Num slope = numerator.dividedBy(denominator);
        final Num delta = factory.numOf(targetMillis - startMillis);
        return slope.multipliedBy(delta).plus(startPrice);
    }

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

    private static final class StaticSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

        private final Indicator<Num> priceIndicator;
        private final List<Integer> swingIndexes;

        private StaticSwingIndicator(Indicator<Num> priceIndicator, List<Integer> swingIndexes) {
            super(priceIndicator);
            this.priceIndicator = priceIndicator;
            this.swingIndexes = swingIndexes;
        }

        @Override
        public int getLatestSwingIndex(int index) {
            int latest = -1;
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    latest = swingIndex;
                }
            }
            return latest;
        }

        @Override
        public List<Integer> getSwingPointIndexesUpTo(int index) {
            final List<Integer> filtered = new java.util.ArrayList<>();
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    filtered.add(swingIndex);
                }
            }
            return filtered;
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
            return priceIndicator.getValue(getLatestSwingIndex(index));
        }
    }
}
