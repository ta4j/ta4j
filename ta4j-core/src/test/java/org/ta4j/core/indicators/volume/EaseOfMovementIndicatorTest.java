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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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

    public EaseOfMovementIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesSpreadsheetReferenceValuesAcrossBullishBearishAndSidewaysScenarios() {
        // Spreadsheet references were generated with:
        // emv1(i) = distanceMoved(i) / boxRatio(i), EMV(i) = SMA(emv1, 3)
        // using volumeDivisor = 1000.
        assertScenarioMatches(bullish(numFactory), BULLISH_EXPECTED);
        assertScenarioMatches(bearish(numFactory), BEARISH_EXPECTED);
        assertScenarioMatches(sideways(numFactory), SIDEWAYS_EXPECTED);
    }

    @Test
    public void reportsExpectedUnstableBoundary() {
        final BarSeries series = bullish(numFactory);
        final EaseOfMovementIndicator indicator = new EaseOfMovementIndicator(series, 3, 1000);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(3);
        assertThat(Num.isNaNOrNull(indicator.getValue(2))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(3))).isFalse();
    }

    @Test
    public void throwsForInvalidParameters() {
        final BarSeries series = bullish(numFactory);

        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, -4, 1000));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 3, -10));
        assertThrows(IllegalArgumentException.class, () -> new EaseOfMovementIndicator(series, 3, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new EaseOfMovementIndicator(series, 3, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new EaseOfMovementIndicator(series, 3, Double.NEGATIVE_INFINITY));
    }

    @Test
    public void sourceIndicatorConstructorMatchesSeriesConstructor() {
        final BarSeries series = bullish(numFactory);
        final double volumeDivisor = 1000;

        final EaseOfMovementIndicator bySeries = new EaseOfMovementIndicator(series, 3, volumeDivisor);
        final EaseOfMovementIndicator byIndicators = new EaseOfMovementIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), new VolumeIndicator(series), 3, volumeDivisor);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(bySeries.getValue(i), byIndicators.getValue(i));
        }
    }

    @Test
    public void toStringIncludesConfiguredParameters() {
        final EaseOfMovementIndicator indicator = new EaseOfMovementIndicator(bullish(numFactory), 3, 1000);

        assertThat(indicator.toString()).contains("barCount: 3").contains("volumeDivisor: 1000");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        final BarSeries series = bullish(numFactory);
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

    @Test
    public void handlesEmptySeriesWithoutNegativeIndexAccess() {
        final BarSeries emptySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        final EaseOfMovementIndicator indicator = new EaseOfMovementIndicator(emptySeries, 1, 1000);

        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertNumEquals(0, indicator.getValue(1));
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
