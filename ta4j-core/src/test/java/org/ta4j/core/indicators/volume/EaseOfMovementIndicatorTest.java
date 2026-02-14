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
 * Regression tests for {@link EaseOfMovementIndicator}.
 */
public class EaseOfMovementIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] BULLISH_EXPECTED = { 8.46056721, 9.01251526, 7.76251526, 7.80257937, 6.78717320,
            6.04643246, 5.63524825, 5.34113060, 5.84377611 };

    private static final double[] BEARISH_EXPECTED = { -8.12130972, -8.67603235, -7.49323666, -7.54935338, -6.58055206,
            -5.87145587, -5.48295548, -5.20420984, -5.70075289 };

    private static final double[] SIDEWAYS_EXPECTED = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };

    public EaseOfMovementIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesAcrossBullishBearishAndSidewaysScenarios() {
        // Spreadsheet references were generated with:
        // emv1(i) = distanceMoved(i) / boxRatio(i), EMV(i) = SMA(emv1, 3)
        // using volumeDivisor = 1000.
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final EaseOfMovementIndicator indicator = new EaseOfMovementIndicator(series, 3, 1000);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(3);
        assertThat(Num.isNaNOrNull(indicator.getValue(2))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(3))).isFalse();
    }

    @Test
    public void throwsForInvalidParameters() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, -4, 1000));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 3, -10));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final EaseOfMovementIndicator original = new EaseOfMovementIndicator(series, 3, 1000);

        final String json = original.toJson();
        final Indicator<Num> restoredBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restoredBase).isInstanceOf(EaseOfMovementIndicator.class);
        assertThat(restoredBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restoredBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), restoredBase.getValue(i));
        }
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues) {
        final EaseOfMovementIndicator indicator = new EaseOfMovementIndicator(series, 3, 1000);
        final int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(3);
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
