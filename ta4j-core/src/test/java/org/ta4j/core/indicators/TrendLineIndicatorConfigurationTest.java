/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings.Mode;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TrendLineIndicatorConfigurationTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineIndicatorConfigurationTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldExposeDefaultCapsAndToleranceInDescriptor() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13, 8);
        final var indicator = new TrendLineSupportIndicator(series, 1, 15);

        indicator.getValue(series.getEndIndex());

        final ToleranceSettings tolerance = indicator.getToleranceSettings();
        assertThat(tolerance.mode).isEqualTo(Mode.PERCENTAGE);
        assertThat(tolerance.value).isEqualTo(0.02d);
        assertThat(tolerance.minimumAbsolute).isEqualTo(1e-9d);
        assertThat(indicator.getMaxSwingPointsForTrendline())
                .isEqualTo(AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE);
        assertThat(indicator.getMaxCandidatePairs()).isEqualTo(AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS);

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("maxSwingPointsForTrendline",
                AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE);
        assertThat(descriptor.getParameters()).containsEntry("maxCandidatePairs",
                AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS);

        assertThat(descriptor.getParameters()).containsEntry("toleranceMode", tolerance.mode.name());
        try {
            assertThat(Double.parseDouble(descriptor.getParameters().get("toleranceValue").toString()))
                    .isEqualTo(tolerance.value);
            assertThat(Double.parseDouble(descriptor.getParameters().get("toleranceMinimum").toString()))
                    .isEqualTo(tolerance.minimumAbsolute);
        } catch (NumberFormatException e) {
            fail("Could not parse tolerance value", e);
        }
    }

    @Test
    public void shouldReturnSegmentMetadata() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] lows = { 10d, 15d, 12d };
        for (double low : lows) {
            series.barBuilder().openPrice(low).closePrice(low).highPrice(low + 1d).lowPrice(low).add();
        }
        final var swingIndicator = new StaticSwingIndicator(new LowPriceIndicator(series), List.of(0, 1, 2));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 10, 0.30d, 0.20d, 0.15d, 0.20d, 0.15d);

        indicator.getValue(series.getEndIndex());
        final AbstractTrendLineIndicator.TrendLineSegment segment = indicator.getCurrentSegment();

        assertThat(segment).isNotNull();
        assertThat(segment.firstIndex).isEqualTo(0);
        assertThat(segment.secondIndex).isEqualTo(2);
        assertThat(segment.touchCount).isEqualTo(2);
        assertThat(segment.outsideCount).isEqualTo(1);
        assertThat(segment.touchesExtreme).isTrue();
        assertThat(segment.windowStart).isEqualTo(series.getBeginIndex());
        assertThat(segment.windowEnd).isEqualTo(series.getEndIndex());
    }

    @Test
    public void shouldReturnSegmentWhenQueriedBeforeGetValue() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] lows = { 9d, 7d, 11d, 6d };
        for (double low : lows) {
            final double high = low + 1d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        final var swingIndicator = new StaticSwingIndicator(new LowPriceIndicator(series), List.of(1, 3));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 10, 0.40d, 0.15d, 0.15d, 0.15d, 0.15d);

        final AbstractTrendLineIndicator.TrendLineSegment segment = indicator.getCurrentSegment();

        assertThat(segment).isNotNull();
        assertThat(segment.firstIndex).isEqualTo(1);
        assertThat(segment.secondIndex).isEqualTo(3);
    }

    @Test
    public void shouldClampNegativeToleranceMinimum() {
        final ToleranceSettings tolerance = ToleranceSettings.from(Mode.ABSOLUTE, 1.5d, -10d);
        assertThat(tolerance.minimumAbsolute).isZero();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInfiniteScoringWeight() {
        AbstractTrendLineIndicator.ScoringWeights.of(Double.POSITIVE_INFINITY, 0.2d, 0.2d, 0.1d, 0.1d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeToleranceValue() {
        ToleranceSettings.percentage(-0.01d, 0.0d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInfiniteToleranceValue() {
        ToleranceSettings.absolute(Double.POSITIVE_INFINITY);
    }

    @Test
    public void shouldHaveDefaultWeightsSummingToOne() {
        final AbstractTrendLineIndicator.ScoringWeights weights = AbstractTrendLineIndicator.ScoringWeights
                .defaultWeights();
        final double sum = weights.touchCountWeight + weights.touchesExtremeWeight + weights.outsideCountWeight
                + weights.averageDeviationWeight + weights.anchorRecencyWeight;

        assertThat(sum).isEqualTo(1.0d);
    }

    @Test
    public void shouldHaveTouchCountBiasPresetSummingToOne() {
        final AbstractTrendLineIndicator.ScoringWeights weights = AbstractTrendLineIndicator.ScoringWeights
                .touchCountBiasPreset();
        final double sum = weights.touchCountWeight + weights.touchesExtremeWeight + weights.outsideCountWeight
                + weights.averageDeviationWeight + weights.anchorRecencyWeight;

        assertThat(sum).isEqualTo(1.0d);
    }

    @Test
    public void shouldHaveExtremeSwingBiasPresetSummingToOne() {
        final AbstractTrendLineIndicator.ScoringWeights weights = AbstractTrendLineIndicator.ScoringWeights
                .extremeSwingBiasPreset();
        final double sum = weights.touchCountWeight + weights.touchesExtremeWeight + weights.outsideCountWeight
                + weights.averageDeviationWeight + weights.anchorRecencyWeight;

        assertThat(sum).isEqualTo(1.0d);

    }

    @Test
    public void shouldHaveDefaultScoringWeightsEqualToDefaultWeights() {
        final AbstractTrendLineIndicator.ScoringWeights defaultWeights = AbstractTrendLineIndicator.ScoringWeights
                .defaultWeights();
        final AbstractTrendLineIndicator.ScoringWeights defaultScoringWeights = AbstractTrendLineIndicator.ScoringWeights
                .defaultScoringWeights();

        assertThat(defaultScoringWeights.touchCountWeight).isEqualTo(defaultWeights.touchCountWeight);
        assertThat(defaultScoringWeights.touchesExtremeWeight).isEqualTo(defaultWeights.touchesExtremeWeight);
        assertThat(defaultScoringWeights.outsideCountWeight).isEqualTo(defaultWeights.outsideCountWeight);
        assertThat(defaultScoringWeights.averageDeviationWeight).isEqualTo(defaultWeights.averageDeviationWeight);
        assertThat(defaultScoringWeights.anchorRecencyWeight).isEqualTo(defaultWeights.anchorRecencyWeight);
    }

    @Test
    public void shouldUseDefaultWeightsPresetWithIndicator() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.defaultWeights();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        assertThat(indicator.getScoringWeights()).isNotNull();
    }

    @Test
    public void shouldUseTouchCountBiasPresetWithIndicator() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.touchCountBiasPreset();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        assertThat(indicator.getScoringWeights()).isNotNull();
    }

    @Test
    public void shouldUseExtremeSwingBiasPresetWithIndicator() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.extremeSwingBiasPreset();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        assertThat(indicator.getScoringWeights()).isNotNull();
    }

    @Test
    public void shouldHaveBuilderDefaultsSummingToOne() {
        final AbstractTrendLineIndicator.ScoringWeights weights = AbstractTrendLineIndicator.ScoringWeights.builder()
                .build();
        final double sum = weights.touchCountWeight + weights.touchesExtremeWeight + weights.outsideCountWeight
                + weights.averageDeviationWeight + weights.anchorRecencyWeight;

        assertThat(sum).isEqualTo(1.0d);
    }

    @Test
    public void shouldInstantiateSupportIndicatorWithDefaultWeightsPreset() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.defaultWeights();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
    }

    @Test
    public void shouldInstantiateSupportIndicatorWithTouchCountBiasPreset() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.touchCountBiasPreset();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
    }

    @Test
    public void shouldInstantiateSupportIndicatorWithExtremeSwingBiasPreset() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.extremeSwingBiasPreset();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
    }

    @Test
    public void shouldInstantiateResistanceIndicatorWithDefaultWeightsPreset() {
        final var series = seriesFromHighs(10, 12, 11, 12, 11, 12, 9);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.defaultWeights();
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
    }

    @Test
    public void shouldInstantiateResistanceIndicatorWithTouchCountBiasPreset() {
        final var series = seriesFromHighs(10, 12, 11, 12, 11, 12, 9);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.touchCountBiasPreset();
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
    }

    @Test
    public void shouldInstantiateResistanceIndicatorWithExtremeSwingBiasPreset() {
        final var series = seriesFromHighs(10, 12, 11, 12, 11, 12, 9);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.extremeSwingBiasPreset();
        final var indicator = new TrendLineResistanceIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
    }

    @Test
    public void shouldInstantiateSupportIndicatorWithDefaultScoringWeightsPreset() {
        final var series = seriesFromLows(10, 8, 9, 8, 9, 8, 11);
        final var weights = AbstractTrendLineIndicator.ScoringWeights.defaultScoringWeights();
        final var indicator = new TrendLineSupportIndicator(series, 1, Integer.MAX_VALUE, weights);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num value = indicator.getValue(i);
            assertThat(value).isNotNull();
        }

        assertThat(indicator.getScoringWeights()).isNotNull();
        final var segment = indicator.getCurrentSegment();
        assertThat(segment).isNotNull();
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

    private static final class StaticSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

        private final List<Integer> swingIndexes;
        private final Indicator<Num> priceIndicator;

        private StaticSwingIndicator(Indicator<Num> priceIndicator, List<Integer> swingIndexes) {
            super(priceIndicator);
            this.priceIndicator = priceIndicator;
            this.swingIndexes = new ArrayList<>(swingIndexes);
        }

        @Override
        protected Num calculate(int index) {
            final int latest = getLatestSwingIndex(index);
            if (latest < 0) {
                return getBarSeries().numFactory().numOf(0);
            }
            return priceIndicator.getValue(latest);
        }

        @Override
        public int getLatestSwingIndex(int index) {
            int latest = -1;
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    latest = swingIndex;
                } else {
                    break;
                }
            }
            return latest;
        }

        @Override
        public List<Integer> getSwingPointIndexesUpTo(int index) {
            final List<Integer> result = new ArrayList<>();
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    result.add(swingIndex);
                }
            }
            return result;
        }

        @Override
        public Indicator<Num> getPriceIndicator() {
            return priceIndicator;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private BarSeries seriesFromLows(double... lows) {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        for (double low : lows) {
            final double high = low + 1d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        return series;
    }
}
