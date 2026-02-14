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
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Regression tests for {@link KlingerVolumeOscillatorIndicator}.
 */
public class KlingerVolumeOscillatorIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] BULLISH_EXPECTED = { -9139.65225161, 2505.12759431, 9624.87726624, 12684.19073606,
            16564.67757807, 17382.50889971 };

    private static final double[] BEARISH_EXPECTED = { 9345.65237103, -2472.19724983, -9674.66125703, -12734.79507168,
            -16657.62635653, -17455.73721387 };

    private static final double[] SIDEWAYS_EXPECTED = { 58541.66666667, 18215.27777778, 35153.93518519, 1574.49845679,
            23452.26980453, -6480.51804698 };

    public KlingerVolumeOscillatorIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesAcrossBullishBearishAndSidewaysScenarios() {
        // Spreadsheet references were generated with Klinger VF, EMA(3), EMA(5),
        // and KVO = EMA(3) - EMA(5).
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
        assertThat(Num.isNaNOrNull(indicator.getValue(5))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(6))).isFalse();
    }

    @Test
    public void throwsForInvalidPeriods() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 6, 5));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final KlingerVolumeOscillatorIndicator original = new KlingerVolumeOscillatorIndicator(series, 3, 5);

        final String json = original.toJson();
        final Indicator<Num> restoredBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restoredBase).isInstanceOf(KlingerVolumeOscillatorIndicator.class);
        assertThat(restoredBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restoredBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), restoredBase.getValue(i));
        }
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues) {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        final int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(6);
        assertThat(expectedStableValues.length).isEqualTo(series.getBarCount() - unstableBars);

        for (int i = series.getBeginIndex(); i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i <= series.getEndIndex(); i++) {
            assertNumEquals(expectedStableValues[i - unstableBars], indicator.getValue(i));
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
