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
 * Regression tests for {@link ForceIndexIndicator}.
 */
public class ForceIndexIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] BULLISH_EXPECTED = { 3400.0, 3200.0, 4000.0, 3700.0, 3650.0, 4675.0, 4337.5,
            5318.75 };

    private static final double[] BEARISH_EXPECTED = { -3525.0, -3312.5, -4131.25, -3815.625, -3757.8125, -4803.90625,
            -4451.953125, -5450.9765625 };

    private static final double[] SIDEWAYS_EXPECTED = { -5.0, -507.5, 243.75, -380.625, 309.6875, -346.15625,
            325.921875, -337.5390625 };

    public ForceIndexIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesAcrossBullishBearishAndSidewaysScenarios() {
        // Spreadsheet references were generated with:
        // rawForce(i) = (close(i) - close(i-1)) * volume(i)
        // FI(i) = EMA(rawForce, 3)
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(VolumeSpreadsheetReferenceScenarios.sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final ForceIndexIndicator indicator = new ForceIndexIndicator(series, 3);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(Num.isNaNOrNull(indicator.getValue(3))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(4))).isFalse();
    }

    @Test
    public void throwsForInvalidBarCount() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new ForceIndexIndicator(series, 0));
        assertThrows(IllegalArgumentException.class, () -> new ForceIndexIndicator(series, -2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final ForceIndexIndicator original = new ForceIndexIndicator(series, 3);

        final String json = original.toJson();
        final Indicator<Num> restoredBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restoredBase).isInstanceOf(ForceIndexIndicator.class);
        assertThat(restoredBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restoredBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), restoredBase.getValue(i));
        }
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues) {
        final ForceIndexIndicator indicator = new ForceIndexIndicator(series, 3);
        final int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(4);
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
