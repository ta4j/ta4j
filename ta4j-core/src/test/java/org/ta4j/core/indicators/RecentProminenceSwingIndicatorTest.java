/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.elliott.swing.ProminenceSwingConfig;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RecentProminenceSwingIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public RecentProminenceSwingIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldResetCachedMinimumProminenceForRevisionlessHigh() {
        assertRevisionlessThresholdRevalidation(true, false);
    }

    @Test
    public void shouldResetCachedMinimumProminenceForRevisionlessLow() {
        assertRevisionlessThresholdRevalidation(false, false);
    }

    @Test
    public void shouldResetAtrProminenceForRevisionlessHigh() {
        assertRevisionlessThresholdRevalidation(true, true);
    }

    @Test
    public void shouldResetAtrProminenceForRevisionlessLow() {
        assertRevisionlessThresholdRevalidation(false, true);
    }

    private void assertRevisionlessThresholdRevalidation(boolean high, boolean atr) {
        final BarSeries series = revisionlessSeries();
        final double[] closes = high ? new double[] { 10, 12, 14, 20, 15, 13 }
                : new double[] { 20, 18, 16, 10, 15, 17 };
        for (int index = 0; index < closes.length; index++) {
            series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .endTime(Instant.EPOCH.plus(Duration.ofMinutes(index + 1)))
                    .openPrice(closes[index])
                    .highPrice(closes[index])
                    .lowPrice(closes[index])
                    .closePrice(closes[index])
                    .add();
        }
        final ProminenceSwingConfig config = new ProminenceSwingConfig(5, 2, 0, 1, 1.0);
        final AbstractRecentSwingIndicator warmed = prominenceIndicator(series, config, high, atr);
        assertThat(warmed.getLatestSwingIndex(5)).isEqualTo(3);
        series.getBar(2).addPrice(series.numFactory().numOf(high ? -10 : 40));
        final AbstractRecentSwingIndicator fresh = prominenceIndicator(series, config, high, atr);
        assertThat(fresh.getLatestSwingIndex(5)).isEqualTo(-1);
        assertThat(warmed.getLatestSwingIndex(5)).isEqualTo(fresh.getLatestSwingIndex(5));
        assertThat(warmed.getLatestSwingConfirmationIndex(5)).isEqualTo(fresh.getLatestSwingConfirmationIndex(5));
        assertThat(warmed.getSwingPointIndexes()).isEqualTo(fresh.getSwingPointIndexes());
        assertThat(warmed.getValue(5).isNaN()).isTrue();
        series.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(Instant.EPOCH.plus(Duration.ofMinutes(7)))
                .openPrice(closes[5])
                .highPrice(closes[5])
                .lowPrice(closes[5])
                .closePrice(closes[5])
                .add();
        assertThat(warmed.getSwingPointIndexes()).isEqualTo(fresh.getSwingPointIndexes());
    }

    private AbstractRecentSwingIndicator prominenceIndicator(BarSeries series, ProminenceSwingConfig config,
            boolean high, boolean atr) {
        if (atr) {
            return high ? new RecentProminenceSwingHighIndicator(series, config)
                    : new RecentProminenceSwingLowIndicator(series, config);
        }
        final TRIndicator threshold = new TRIndicator(series);
        return high ? new RecentProminenceSwingHighIndicator(new HighPriceIndicator(series), threshold, config)
                : new RecentProminenceSwingLowIndicator(new LowPriceIndicator(series), threshold, config);
    }

    private BarSeries revisionlessSeries() {
        final BarSeries delegate = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        return (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(), new Class<?>[] { BarSeries.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getBarHistoryRevision")) {
                        return -1L;
                    }
                    if (method.getName().equals("getBarSeriesChangeSnapshot")) {
                        synchronized (delegate) {
                            return new BarSeries.BarSeriesChangeSnapshot(-1L, -1, delegate.getRemovedBarsCount() - 1,
                                    delegate.getMaximumBarCount(), delegate.getEndIndex());
                        }
                    }
                    return method.invoke(delegate, args);
                });
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
