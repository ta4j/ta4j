/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link DailyMeasurementIndicator}.
 */
public class DailyMeasurementIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] EXPECTED = { 4.0, 4.0, 5.0, 4.0, 5.0, 4.0, 5.0, 4.0, 4.0, 5.0, 4.0, 5.0 };

    public DailyMeasurementIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesHighMinusLowReferenceValues() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final DailyMeasurementIndicator indicator = new DailyMeasurementIndicator(series);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(numFactory.numOf(EXPECTED[i]));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValues() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final DailyMeasurementIndicator original = new DailyMeasurementIndicator(series);

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(DailyMeasurementIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restored.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualByComparingTo(original.getValue(i));
        }
    }
}
