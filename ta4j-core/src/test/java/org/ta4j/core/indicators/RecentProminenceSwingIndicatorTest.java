/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.swing.ProminenceSwingConfig;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RecentProminenceSwingIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public RecentProminenceSwingIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldConfirmProminentHighAfterRightSideEvidence() {
        final BarSeries series = seriesFromCloses(10, 12, 14, 20, 15, 13);
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 0, 14, 1.0);
        final RecentProminenceSwingHighIndicator indicator = new RecentProminenceSwingHighIndicator(
                new ClosePriceIndicator(series), constant(series, 5), config);

        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(-1);
        assertThat(indicator.getLatestSwingIndex(5)).isEqualTo(3);
        assertThat(indicator.getLatestSwingConfirmationIndex(5)).isEqualTo(5);
        assertThat(indicator.getValue(5)).isEqualByComparingTo(numOf(20));
    }

    @Test
    public void shouldConfirmProminentLowAfterRightSideEvidence() {
        final BarSeries series = seriesFromCloses(20, 18, 16, 10, 15, 17);
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 0, 14, 1.0);
        final RecentProminenceSwingLowIndicator indicator = new RecentProminenceSwingLowIndicator(
                new ClosePriceIndicator(series), constant(series, 5), config);

        assertThat(indicator.getLatestSwingIndex(5)).isEqualTo(3);
        assertThat(indicator.getLatestSwingConfirmationIndex(5)).isEqualTo(5);
        assertThat(indicator.getValue(5)).isEqualByComparingTo(numOf(10));
    }

    @Test
    public void shouldReportPartialBaselineUnstableBarsForHighsAndLows() {
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 0, 14, 1.0);
        final BarSeries highSeries = seriesFromCloses(10, 20, 15, 14);
        final RecentProminenceSwingHighIndicator high = new RecentProminenceSwingHighIndicator(
                new ClosePriceIndicator(highSeries), constant(highSeries, 5), config);
        final BarSeries lowSeries = seriesFromCloses(20, 10, 15, 16);
        final RecentProminenceSwingLowIndicator low = new RecentProminenceSwingLowIndicator(
                new ClosePriceIndicator(lowSeries), constant(lowSeries, 5), config);

        assertThat(high.getCountOfUnstableBars()).isEqualTo(3);
        assertThat(low.getCountOfUnstableBars()).isEqualTo(3);
        assertThat(high.getValue(2).isNaN()).isTrue();
        assertThat(low.getValue(2).isNaN()).isTrue();
        assertThat(high.getValue(3)).isEqualByComparingTo(numOf(20));
        assertThat(low.getValue(3)).isEqualByComparingTo(numOf(10));
    }

    @Test
    public void shouldNotAddExtraWarmupBarWhenProminenceThresholdIsUnstable() {
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 3, 0, 14, 1.0);
        final BarSeries series = seriesFromCloses(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 20, 15, 14,
                13);
        final Indicator<Num> minimumProminence = new MockIndicator(series, 14, numOf(5), numOf(5), numOf(5), numOf(5),
                numOf(5), numOf(5), numOf(5), numOf(5), numOf(5), numOf(5), numOf(5), numOf(5), numOf(5), numOf(5),
                numOf(5), numOf(5), numOf(5), numOf(5));
        final RecentProminenceSwingHighIndicator indicator = new RecentProminenceSwingHighIndicator(
                new ClosePriceIndicator(series), minimumProminence, config);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(17);
        assertThat(indicator.getValue(16).isNaN()).isTrue();
        assertThat(indicator.getValue(17)).isEqualByComparingTo(numOf(20));
    }

    @Test
    public void shouldRejectLocallyDominantNoiseBelowProminenceThreshold() {
        final BarSeries series = seriesFromCloses(10, 12, 14, 20, 15, 13);
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 0, 14, 1.0);
        final RecentProminenceSwingHighIndicator indicator = new RecentProminenceSwingHighIndicator(
                new ClosePriceIndicator(series), constant(series, 8), config);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).isEmpty();
    }

    @Test
    public void shouldResolveAllowedPlateauToDeterministicMidpoint() {
        final BarSeries series = seriesFromCloses(10, 12, 20, 20, 15, 13);
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 1, 14, 1.0);
        final RecentProminenceSwingHighIndicator indicator = new RecentProminenceSwingHighIndicator(
                new ClosePriceIndicator(series), constant(series, 5), config);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).containsExactly(2);
    }

    @Test
    public void shouldExposeBalancedDefaultConfiguration() {
        assertThat(ProminenceSwingConfig.defaults()).isEqualTo(new ProminenceSwingConfig(20, 3, 0, 14, 1.0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        final BarSeries series = seriesFromCloses(10, 12, 14, 20, 15, 13);
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 0, 14, 1.0);
        return List.of(
                serializationFixture(series,
                        new RecentProminenceSwingHighIndicator(new ClosePriceIndicator(series), constant(series, 5),
                                config)),
                serializationFixture(series, new RecentProminenceSwingLowIndicator(new ClosePriceIndicator(series),
                        constant(series, 5), config)));
    }

    private Indicator<Num> constant(final BarSeries series, final Number value) {
        return new ConstantIndicator<>(series, numOf(value));
    }

    private BarSeries seriesFromCloses(final double... closes) {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).add();
        }
        return series;
    }
}
