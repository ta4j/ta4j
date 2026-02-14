/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link CumulativeMeasurementIndicator}.
 */
public class CumulativeMeasurementIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] EXPECTED = { 2.0, 5.0, 7.0, 12.0, 11.0 };

    public CumulativeMeasurementIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void appliesTrendResetRecurrence() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 1d, 1d, 1d, 1d)
                .build();
        final FixedNumIndicator measurement = new FixedNumIndicator(series, 2, 3, 4, 5, 6);
        final FixedNumIndicator trend = new FixedNumIndicator(series, 1, 1, -1, -1, 1);

        final CumulativeMeasurementIndicator indicator = new CumulativeMeasurementIndicator(measurement, trend);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(numFactory.numOf(EXPECTED[i]));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValues() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final DailyMeasurementIndicator measurement = new DailyMeasurementIndicator(series);
        final TrendDirectionIndicator trend = new TrendDirectionIndicator(series);
        final CumulativeMeasurementIndicator original = new CumulativeMeasurementIndicator(measurement, trend);

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(CumulativeMeasurementIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restored.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualByComparingTo(original.getValue(i));
        }
    }
}
