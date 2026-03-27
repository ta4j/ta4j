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
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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

    public ForceIndexIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesAcrossBullishBearishAndSidewaysScenarios() {
        // Spreadsheet references were generated with:
        // rawForce(i) = (close(i) - close(i-1)) * volume(i)
        // FI(i) = EMA(rawForce, 3)
        assertScenarioMatches(bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = bullish(numFactory);
        final ForceIndexIndicator indicator = new ForceIndexIndicator(series, 3);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(Num.isNaNOrNull(indicator.getValue(3))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(4))).isFalse();
    }

    @Test
    public void throwsForInvalidBarCount() {
        final BarSeries series = bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new ForceIndexIndicator(series, 0));
        assertThrows(IllegalArgumentException.class, () -> new ForceIndexIndicator(series, -2));
    }

    @Test
    public void sourceIndicatorConstructorMatchesSeriesConstructor() {
        final BarSeries series = bullish(numFactory);

        final ForceIndexIndicator bySeries = new ForceIndexIndicator(series, 7);
        final ForceIndexIndicator byIndicators = new ForceIndexIndicator(new ClosePriceIndicator(series),
                new VolumeIndicator(series), 7);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(bySeries.getValue(i), byIndicators.getValue(i));
        }
    }

    @Test
    public void toStringIncludesConfiguredParameters() {
        final ForceIndexIndicator indicator = new ForceIndexIndicator(bullish(numFactory), 7);

        assertThat(indicator.toString()).contains("barCount: 7");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = bullish(numFactory);
        final ForceIndexIndicator original = new ForceIndexIndicator(series, 7);

        final String json = original.toJson();
        final Indicator<Num> restoredBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restoredBase).isInstanceOf(ForceIndexIndicator.class);
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
