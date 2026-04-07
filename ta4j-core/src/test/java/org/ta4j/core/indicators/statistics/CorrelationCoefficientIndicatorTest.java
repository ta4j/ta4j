/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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
        int i = 20;
        var now = Instant.now();
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // close, volume
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(6).volume(100).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(7).volume(105).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(130).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(150).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(10).volume(130).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(95).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(13).volume(120).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(15).volume(180).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(150).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(200).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(150).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(85).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(70).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(5).volume(90).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(100).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(95).add();
        series.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(110).add();
        series.barBuilder().endTime(now.minusSeconds(i)).closePrice(10).volume(95).add();

        close = new ClosePriceIndicator(series);
        volume = new VolumeIndicator(series, 2);
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
}
