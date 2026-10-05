/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.serialization.IndicatorSerialization;

import org.ta4j.core.serialization.ComponentDescriptor;

import org.ta4j.core.mocks.MockBarBuilderFactory;

import org.ta4j.core.BaseBarSeriesBuilder;

import static org.assertj.core.api.Assertions.assertThat;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CorrelationCoefficientIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;

    public CorrelationCoefficientIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        int i = 20;
        var now = Instant.now();
        BarSeries data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // close, volume
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(6).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(7).volume(105).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(10).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(13).volume(120).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(15).volume(180).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(200).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(85).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(70).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(5).volume(90).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(110).add();
        data.barBuilder().endTime(now.minusSeconds(i)).closePrice(10).volume(95).add();

        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void usingBarCount5UsingClosePriceAndVolume() {
        var coef = new CorrelationCoefficientIndicator(close, volume, 5);

        assertTrue(coef.getValue(0).isNaN());

        assertNumEquals(1, coef.getValue(1));
        assertNumEquals(0.8773, coef.getValue(2));
        assertNumEquals(0.9073, coef.getValue(3));
        assertNumEquals(0.9219, coef.getValue(4));
        assertNumEquals(0.9205, coef.getValue(5));
        assertNumEquals(0.4565, coef.getValue(6));
        assertNumEquals(-0.4622, coef.getValue(7));
        assertNumEquals(0.05747, coef.getValue(8));
        assertNumEquals(0.1442, coef.getValue(9));
        assertNumEquals(-0.1263, coef.getValue(10));
        assertNumEquals(-0.5345, coef.getValue(11));
        assertNumEquals(-0.7275, coef.getValue(12));
        assertNumEquals(0.1676, coef.getValue(13));
        assertNumEquals(0.2506, coef.getValue(14));
        assertNumEquals(-0.2938, coef.getValue(15));
        assertNumEquals(-0.3586, coef.getValue(16));
        assertNumEquals(0.1713, coef.getValue(17));
        assertNumEquals(0.9841, coef.getValue(18));
        assertNumEquals(0.9799, coef.getValue(19));
    }

    @Test
    public void sampleAndPopulationCorrelationMatchWhenCovarianceIsScaledConsistently() {
        var population = CorrelationCoefficientIndicator.ofPopulation(close, volume, 5);
        var sample = CorrelationCoefficientIndicator.ofSample(close, volume, 5);

        assertTrue(population.getValue(0).isNaN());
        assertTrue(sample.getValue(0).isNaN());
        for (int i = 1; i <= 19; i++) {
            assertNumEquals(population.getValue(i), sample.getValue(i), 1.0e-12);
        }
    }

    @Test
    public void nonPositiveBarCountFallsBackToOne() {
        var populationWithOne = CorrelationCoefficientIndicator.ofPopulation(close, volume, 1);
        var populationWithZero = CorrelationCoefficientIndicator.ofPopulation(close, volume, 0);
        var sampleWithOne = CorrelationCoefficientIndicator.ofSample(close, volume, 1);
        var sampleWithNegative = CorrelationCoefficientIndicator.ofSample(close, volume, -5);

        for (int i = 0; i <= 19; i++) {
            assertNumEquals(populationWithOne.getValue(i), populationWithZero.getValue(i), 1.0e-12);
            assertNumEquals(sampleWithOne.getValue(i), sampleWithNegative.getValue(i), 1.0e-12);
        }
    }

    @Test
    public void anchorsWindowAtBeginIndexAfterRemoval() {
        // Evict the first four bars so beginIndex = 4; the retained (close, volume)
        // pairs sit at absolute indices 4..9: (5,10) (6,5) (7,14) (8,7) (9,18) (10,9).
        int i = 10;
        var now = Instant.now();
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] closes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        double[] volumes = { 4, 1, 6, 3, 10, 5, 14, 7, 18, 9 };
        for (int j = 0; j < closes.length; j++) {
            pruned.barBuilder().endTime(now.minusSeconds(i--)).closePrice(closes[j]).volume(volumes[j]).add();
        }
        pruned.setMaximumBarCount(6);

        ClosePriceIndicator close = new ClosePriceIndicator(pruned);
        VolumeIndicator volume = new VolumeIndicator(pruned, 1);
        // Window [4..8] has only five retained observations; a six-bar window is
        // unavailable until index 9, so population and sample correlation are NaN.
        assertThat(new CorrelationCoefficientIndicator(close, volume, 6).getValue(8).isNaN()).isTrue();
        // Sample correlation uses the same full-window availability boundary
        assertThat(CorrelationCoefficientIndicator.ofSample(close, volume, 6).getValue(8).isNaN()).isTrue();
        // Window [4..9]: covariance = 2.25, variances = 17.5/6 and 113.5/6 -> 0.3029
        assertNumEquals(0.3029, new CorrelationCoefficientIndicator(close, volume, 6).getValue(9));
    }

    @Test
    public void acceptsAlignedInputsFromSeparateSeries() {
        BarSeries xSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries ySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var now = Instant.now();
        for (int j = 0; j < 5; j++) {
            xSeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(2 * (j + 1)).add();
            ySeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(5 * (j + 1)).add();
        }

        var coef = new CorrelationCoefficientIndicator(new ClosePriceIndicator(xSeries),
                new ClosePriceIndicator(ySeries), 5);

        // Perfectly linear pairs (2,5), (4,10), (6,15), (8,20), (10,25)
        assertNumEquals(1, coef.getValue(4));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        return List.of(serializationFixture(series,
                new CorrelationCoefficientIndicator(close, volume, 8, SampleType.SAMPLE), stableIndexes(series)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void roundTripSerializationPreservesSampleTypeSelection() {
        BarSeries series = close.getBarSeries();
        CorrelationCoefficientIndicator sample = CorrelationCoefficientIndicator.ofSample(close, volume, 5);
        ComponentDescriptor sampleDescriptor = sample.toDescriptor();

        assertThat(sampleDescriptor.getParameters()).containsEntry("sampleType", "SAMPLE");

        Indicator<Num> fromDescriptor = (Indicator<Num>) IndicatorSerialization.fromDescriptor(series,
                sampleDescriptor);
        assertThat(fromDescriptor).isInstanceOf(CorrelationCoefficientIndicator.class);
        ComponentDescriptor fromDescriptorValue = fromDescriptor.toDescriptor();
        assertThat(fromDescriptorValue.getParameters()).containsEntry("sampleType", "SAMPLE");

        Indicator<Num> fromJson = (Indicator<Num>) IndicatorSerialization.fromJson(series, sample.toJson());
        assertThat(fromJson).isInstanceOf(CorrelationCoefficientIndicator.class);
        ComponentDescriptor fromJsonDescriptor = fromJson.toDescriptor();
        assertThat(fromJsonDescriptor.getParameters()).containsEntry("sampleType", "SAMPLE");
        for (int index = 0; index <= series.getEndIndex(); index++) {
            assertThat(fromJson.getValue(index)).isEqualTo(sample.getValue(index));
        }
    }

    @Test
    public void rollingSeriesCorrelationMatchesVisibleWindowOnly() {
        double[][] values = { { 6, 100 }, { 7, 105 }, { 9, 130 }, { 12, 160 }, { 11, 150 }, { 10, 90 }, { 14, 180 },
                { 9, 95 } };
        BarSeries rollingSeries = seriesWithCloseAndVolume(values);
        rollingSeries.setMaximumBarCount(7);
        BarSeries visibleSeries = seriesWithCloseAndVolume(new double[][] { { 7, 105 }, { 9, 130 }, { 12, 160 },
                { 11, 150 }, { 10, 90 }, { 14, 180 }, { 9, 95 } });
        CorrelationCoefficientIndicator rollingCorrelation = CorrelationCoefficientIndicator
                .ofPopulation(new ClosePriceIndicator(rollingSeries), new VolumeIndicator(rollingSeries), 5);
        CorrelationCoefficientIndicator visibleCorrelation = CorrelationCoefficientIndicator
                .ofPopulation(new ClosePriceIndicator(visibleSeries), new VolumeIndicator(visibleSeries), 5);

        int stableIndex = rollingSeries.getBeginIndex() + rollingCorrelation.getCountOfUnstableBars();
        assertThat(rollingCorrelation.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(rollingCorrelation.getValue(stableIndex - 1).isNaN()).isTrue();
        for (int index = stableIndex; index <= rollingSeries.getEndIndex(); index++) {
            assertNumEquals(visibleCorrelation.getValue(index - rollingSeries.getBeginIndex()),
                    rollingCorrelation.getValue(index), 1.0e-12);
        }
    }

    private BarSeries seriesWithCloseAndVolume(double[][] values) {
        BarSeries barSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        for (double[] value : values) {
            barSeries.barBuilder().closePrice(value[0]).volume(value[1]).add();
        }
        return barSeries;
    }
}
