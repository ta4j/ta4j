/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link VolumeForceIndicator}.
 */
public class VolumeForceIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] EXPECTED = { 97777.77777777777, -109090.9090909091, -158888.8888888889,
            130666.66666666667 };

    public VolumeForceIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesReferenceComputation() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 1d, 1d, 1d, 1d)
                .build();
        final FixedNumIndicator volume = new FixedNumIndicator(series, 1000, 1100, 1200, 1300, 1400);
        final FixedNumIndicator measurement = new FixedNumIndicator(series, 4, 5, 6, 7, 8);
        final FixedNumIndicator trend = new FixedNumIndicator(series, 1, 1, -1, -1, 1);
        final FixedNumIndicator cumulative = new FixedNumIndicator(series, 4, 9, 11, 18, 15);

        final VolumeForceIndicator indicator = new VolumeForceIndicator(volume, measurement, trend, cumulative, 100);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(1);
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isFalse();
        for (int i = 1; i <= series.getEndIndex(); i++) {
            assertNumEquals(EXPECTED[i - 1], indicator.getValue(i));
        }
    }

    @Test
    public void throwsForInvalidScaleMultiplier() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 1d).build();
        final FixedNumIndicator volume = new FixedNumIndicator(series, 1, 1);
        final FixedNumIndicator measurement = new FixedNumIndicator(series, 1, 1);
        final FixedNumIndicator trend = new FixedNumIndicator(series, 1, 1);
        final FixedNumIndicator cumulative = new FixedNumIndicator(series, 1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new VolumeForceIndicator(volume, measurement, trend, cumulative, 0));
    }

    @Test
    public void throwsForMismatchedSourceIndicators() {
        final BarSeries firstSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 1d).build();
        final BarSeries secondSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 1d).build();
        final FixedNumIndicator volume = new FixedNumIndicator(firstSeries, 1, 1);
        final FixedNumIndicator measurement = new FixedNumIndicator(firstSeries, 1, 1);
        final FixedNumIndicator trend = new FixedNumIndicator(firstSeries, 1, 1);
        final FixedNumIndicator cumulative = new FixedNumIndicator(secondSeries, 1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new VolumeForceIndicator(volume, measurement, trend, cumulative));
    }

    @Test
    public void throwsForNullScaleMultiplier() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 1d).build();
        final FixedNumIndicator volume = new FixedNumIndicator(series, 1, 1);
        final FixedNumIndicator measurement = new FixedNumIndicator(series, 1, 1);
        final FixedNumIndicator trend = new FixedNumIndicator(series, 1, 1);
        final FixedNumIndicator cumulative = new FixedNumIndicator(series, 1, 1);

        assertThrows(NullPointerException.class,
                () -> new VolumeForceIndicator(volume, measurement, trend, cumulative, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValues() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final DailyMeasurementIndicator dailyMeasurement = new DailyMeasurementIndicator(series);
        final TrendDirectionIndicator trendDirection = new TrendDirectionIndicator(series);
        final CumulativeMeasurementIndicator cumulativeMeasurement = new CumulativeMeasurementIndicator(
                dailyMeasurement, trendDirection);
        final VolumeForceIndicator original = new VolumeForceIndicator(new VolumeIndicator(series), dailyMeasurement,
                trendDirection, cumulativeMeasurement);

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(VolumeForceIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restored.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), restored.getValue(i));
        }
    }

    private void assertSameNumOrNaN(final Num expected, final Num actual) {
        if (Num.isNaNOrNull(expected) || Num.isNaNOrNull(actual)) {
            assertThat(Num.isNaNOrNull(actual)).isEqualTo(Num.isNaNOrNull(expected));
            return;
        }
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
