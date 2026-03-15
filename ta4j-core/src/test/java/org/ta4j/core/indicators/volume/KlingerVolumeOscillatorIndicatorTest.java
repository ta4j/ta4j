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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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

    private static final double[] BULLISH_250X_EXPECTED = { -22849.130629025, 6262.818985775, 24062.1931656,
            31710.47684015, 41411.693945175, 43456.272249275 };

    private static final double[] BEARISH_250X_EXPECTED = { 23364.130927575, -6180.493124575, -24186.653142575,
            -31836.9876792, -41644.065891325, -43639.343034675 };

    private static final double[] SIDEWAYS_250X_EXPECTED = { 146354.166666675, 45538.19444445, 87884.837962975,
            3936.246141975, 58630.674511325, -16201.29511745 };

    private static final double[][] BULLISH_OHLCV = { { 100, 102, 103, 99, 1000 }, { 102, 104, 105, 101, 1100 },
            { 104, 107, 108, 103, 1200 }, { 107, 109, 110, 106, 1300 }, { 109, 112, 113, 108, 1400 },
            { 112, 114, 115, 111, 1500 }, { 114, 117, 118, 113, 1600 }, { 117, 119, 120, 116, 1700 },
            { 119, 121, 122, 118, 1800 }, { 121, 124, 125, 120, 1900 }, { 124, 126, 127, 123, 2000 },
            { 126, 129, 130, 125, 2100 } };

    private static final double[][] BEARISH_OHLCV = { { 130, 128, 131, 127, 1050 }, { 128, 126, 129, 125, 1150 },
            { 126, 123, 127, 122, 1250 }, { 123, 121, 124, 120, 1350 }, { 121, 118, 122, 117, 1450 },
            { 118, 116, 119, 115, 1550 }, { 116, 113, 117, 112, 1650 }, { 113, 111, 114, 110, 1750 },
            { 111, 109, 112, 108, 1850 }, { 109, 106, 110, 105, 1950 }, { 106, 104, 107, 103, 2050 },
            { 104, 101, 105, 100, 2150 } };

    private static final double[][] SIDEWAYS_OHLCV = { { 100, 101, 102, 99, 1000 }, { 101, 100, 102, 99, 980 },
            { 100, 101, 102, 99, 1020 }, { 101, 100, 102, 99, 1000 }, { 100, 101, 102, 99, 990 },
            { 101, 100, 102, 99, 1010 }, { 100, 101, 102, 99, 995 }, { 101, 100, 102, 99, 1005 },
            { 100, 101, 102, 99, 1000 }, { 101, 100, 102, 99, 1002 }, { 100, 101, 102, 99, 998 },
            { 101, 100, 102, 99, 1001 } };

    public KlingerVolumeOscillatorIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesWithDefaultScaleAcrossScenarios() {
        // Spreadsheet references were generated with Klinger VF, EMA(3), EMA(5),
        // and KVO = EMA(3) - EMA(5).
        assertScenarioMatches(bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesWithCustomScaleAcrossScenarios() {
        // Spreadsheet references were generated using the same input bars and periods,
        // with Klinger VF scale multiplier set to 250.
        assertScenarioMatches(bullish(numFactory), BULLISH_250X_EXPECTED, 250);
        assertScenarioMatches(bearish(numFactory), BEARISH_250X_EXPECTED, 250);
        assertScenarioMatches(sideways(numFactory), SIDEWAYS_250X_EXPECTED, 250);
    }

    @Test
    public void supportsSourceIndicatorConstructorWithCustomScale() {
        final BarSeries series = bullish(numFactory);
        final KlingerVolumeOscillatorIndicator bySeries = new KlingerVolumeOscillatorIndicator(series, 3, 5, 250);
        final KlingerVolumeOscillatorIndicator byIndicators = new KlingerVolumeOscillatorIndicator(
                new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series),
                new VolumeIndicator(series), 3, 5, 250);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(bySeries.getValue(i), byIndicators.getValue(i));
        }
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = bullish(numFactory);
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
        assertThat(Num.isNaNOrNull(indicator.getValue(5))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(6))).isFalse();
    }

    @Test
    public void throwsForInvalidPeriods() {
        final BarSeries series = bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 6, 5));
    }

    @Test
    public void throwsForInvalidScaleMultiplier() {
        final BarSeries series = bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, -100));
        assertThrows(IllegalArgumentException.class,
                () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new KlingerVolumeOscillatorIndicator(series, 3, 5, Double.POSITIVE_INFINITY));
    }

    @Test
    public void toStringIncludesConfiguredPeriodsAndScale() {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(bullish(numFactory), 3,
                5, 250);

        assertThat(indicator.toString()).contains("shortPeriod: 3")
                .contains("longPeriod: 5")
                .contains("scaleMultiplier: 250");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = bullish(numFactory);
        final KlingerVolumeOscillatorIndicator original = new KlingerVolumeOscillatorIndicator(series, 3, 5, 250);

        final String json = original.toJson();
        final Indicator<Num> restoredBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restoredBase).isInstanceOf(KlingerVolumeOscillatorIndicator.class);
        assertThat(restoredBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restoredBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), restoredBase.getValue(i));
        }
    }

    private static BarSeries bullish(final NumFactory numFactory) {
        return buildSeries(numFactory, BULLISH_OHLCV);
    }

    private static BarSeries bearish(final NumFactory numFactory) {
        return buildSeries(numFactory, BEARISH_OHLCV);
    }

    private static BarSeries sideways(final NumFactory numFactory) {
        return buildSeries(numFactory, SIDEWAYS_OHLCV);
    }

    private static BarSeries buildSeries(final NumFactory numFactory, final double[][] ohlcvData) {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (double[] row : ohlcvData) {
            series.barBuilder()
                    .openPrice(row[0])
                    .closePrice(row[1])
                    .highPrice(row[2])
                    .lowPrice(row[3])
                    .volume(row[4])
                    .add();
        }

        return series;
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues) {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5);
        assertScenarioMatchesWithIndicator(series, expectedStableValues, indicator);
    }

    private void assertScenarioMatches(final BarSeries series, final double[] expectedStableValues,
            final double scaleMultiplier) {
        final KlingerVolumeOscillatorIndicator indicator = new KlingerVolumeOscillatorIndicator(series, 3, 5,
                scaleMultiplier);
        assertScenarioMatchesWithIndicator(series, expectedStableValues, indicator);
    }

    private void assertScenarioMatchesWithIndicator(final BarSeries series, final double[] expectedStableValues,
            final KlingerVolumeOscillatorIndicator indicator) {
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
