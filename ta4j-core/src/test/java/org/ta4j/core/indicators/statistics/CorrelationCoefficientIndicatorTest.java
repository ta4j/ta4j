/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CorrelationCoefficientIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;
    private BarSeries series;

    public CorrelationCoefficientIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = seriesWithCloseAndVolume(new double[][] { { 6, 100 }, { 7, 105 }, { 9, 130 }, { 12, 160 }, { 11, 150 },
                { 10, 130 }, { 11, 95 }, { 13, 120 }, { 15, 180 }, { 12, 160 }, { 8, 150 }, { 4, 200 }, { 3, 150 },
                { 4, 85 }, { 3, 70 }, { 5, 90 }, { 8, 100 }, { 9, 95 }, { 11, 110 }, { 10, 95 } });

        close = new ClosePriceIndicator(series);
        volume = new VolumeIndicator(series, 2);
    }

    @Test
    public void usingBarCount5UsingClosePriceAndVolume() {
        CorrelationCoefficientIndicator coef = new CorrelationCoefficientIndicator(close, volume, 5);

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
        CorrelationCoefficientIndicator population = CorrelationCoefficientIndicator.ofPopulation(close, volume, 5);
        CorrelationCoefficientIndicator sample = CorrelationCoefficientIndicator.ofSample(close, volume, 5);

        assertTrue(population.getValue(0).isNaN());
        assertTrue(sample.getValue(0).isNaN());
        for (int i = 1; i <= 19; i++) {
            assertNumEquals(population.getValue(i), sample.getValue(i), 1.0e-12);
        }
    }

    @Test
    public void nonPositiveBarCountFallsBackToOne() {
        CorrelationCoefficientIndicator populationWithOne = CorrelationCoefficientIndicator.ofPopulation(close, volume,
                1);
        CorrelationCoefficientIndicator populationWithZero = CorrelationCoefficientIndicator.ofPopulation(close, volume,
                0);
        CorrelationCoefficientIndicator sampleWithOne = CorrelationCoefficientIndicator.ofSample(close, volume, 1);
        CorrelationCoefficientIndicator sampleWithNegative = CorrelationCoefficientIndicator.ofSample(close, volume,
                -5);

        for (int i = 0; i <= 19; i++) {
            assertNumEquals(populationWithOne.getValue(i), populationWithZero.getValue(i), 1.0e-12);
            assertNumEquals(sampleWithOne.getValue(i), sampleWithNegative.getValue(i), 1.0e-12);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void roundTripSerializationPreservesSampleTypeSelection() {
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

        for (int index = rollingSeries.getBeginIndex(); index <= rollingSeries.getEndIndex(); index++) {
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
