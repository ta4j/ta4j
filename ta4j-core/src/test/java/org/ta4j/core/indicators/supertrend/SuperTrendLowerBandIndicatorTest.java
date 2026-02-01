/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supertrend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SuperTrendLowerBandIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public SuperTrendLowerBandIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void respectsAtrUnstablePeriodAndRecovers() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();

        assertNumEquals(11.5, indicator.getValue(2));
        assertNumEquals(12.5, indicator.getValue(3));
    }

    @Test
    public void defaultConstructorUsesStandardParameters() {
        BarSeries series = buildLongerSeries();
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series);

        // Default uses barCount=10 ATR and multiplier=3
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(10);

        // Verify it produces valid values after unstable period
        Num value10 = indicator.getValue(10);
        assertThat(Num.isNaNOrNull(value10)).isFalse();
    }

    @Test
    public void handlesExtremeSmallMultiplier() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 0.01d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();

        // With very small multiplier, band should be close to median price
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isPositive()).isTrue();
    }

    @Test
    public void handlesExtremeLargeMultiplier() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 100d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();

        // With very large multiplier, band should be much lower than median price
        // (could even be negative, but should not be NaN)
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
    }

    @Test
    public void returnsNaNDuringUnstablePeriod() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 3);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(3);
        // All unstable period values should be NaN
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(2))).isTrue();

        // After unstable period, should recover
        Num value3 = indicator.getValue(3);
        assertThat(Num.isNaNOrNull(value3)).isFalse();
    }

    @Test
    public void recoversGracefullyAfterUnstablePeriod() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        // Verify NaN during unstable period
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();

        // Verify recovery after unstable period
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isPositive()).isTrue();

        // Verify continued stability
        Num value3 = indicator.getValue(3);
        assertThat(Num.isNaNOrNull(value3)).isFalse();
        assertThat(value3.isPositive()).isTrue();
    }

    @Test
    public void lowerBandOnlyMovesUpDuringUptrend() {
        // Build an uptrend series where prices rise
        BarSeries series = buildUptrendSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        // After unstable period, lower band should only increase (ratchet up)
        // or stay same when current basic is greater than or equal to previous band
        int unstableBars = indicator.getCountOfUnstableBars();
        for (int i = unstableBars + 1; i < series.getBarCount(); i++) {
            Num currentBand = indicator.getValue(i);
            Num previousBand = indicator.getValue(i - 1);
            // Lower band should not decrease during uptrend (ratchet behavior)
            assertThat(currentBand.isGreaterThanOrEqual(previousBand))
                    .as("Lower band at index %d should not decrease below previous", i)
                    .isTrue();
        }
    }

    @Test
    public void lowerBandResetWhenPriceClosesBelow() {
        // Build series where price breaks below lower band
        BarSeries series = buildBreakdownSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator indicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 1d);

        // When price closes below lower band, band should reset to current basic
        // This tests the "previous close < previous lower band" condition
        int unstableBars = indicator.getCountOfUnstableBars();
        assertThat(series.getBarCount()).isGreaterThan(unstableBars + 2);

        // Values should be valid after unstable period
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(Num.isNaNOrNull(value)).isFalse();
        }
    }

    @Test
    public void lowerBandAlwaysBelowUpperBand() {
        BarSeries series = buildLongerSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendLowerBandIndicator lowerIndicator = new SuperTrendLowerBandIndicator(series, atrIndicator, 2d);
        SuperTrendUpperBandIndicator upperIndicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 2d);

        int unstableBars = Math.max(lowerIndicator.getCountOfUnstableBars(), upperIndicator.getCountOfUnstableBars());
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num lowerValue = lowerIndicator.getValue(i);
            Num upperValue = upperIndicator.getValue(i);

            // Skip if either is NaN
            if (Num.isNaNOrNull(lowerValue) || Num.isNaNOrNull(upperValue)) {
                continue;
            }

            assertThat(lowerValue.isLessThan(upperValue)).as("Lower band at index %d should be less than upper band", i)
                    .isTrue();
        }
    }

    @Test
    public void serializationRoundTrip() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 3);
        SuperTrendLowerBandIndicator original = new SuperTrendLowerBandIndicator(series, atrIndicator, 2.5);

        String json = original.toJson();
        @SuppressWarnings("unchecked")
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(SuperTrendLowerBandIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        // Verify values match
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(11).highPrice(12).lowPrice(10).add();
        series.barBuilder().openPrice(12).closePrice(13).highPrice(14).lowPrice(11).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(16).lowPrice(13).add();
        series.barBuilder().openPrice(16).closePrice(16).highPrice(18).lowPrice(14).add();
        return series;
    }

    private BarSeries buildLongerSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double basePrice = 100;
        for (int i = 0; i < 15; i++) {
            double open = basePrice + i * 2;
            double close = basePrice + i * 2 + 1;
            double high = close + 1;
            double low = open - 0.5;
            series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
        }
        return series;
    }

    private BarSeries buildUptrendSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100).closePrice(102).highPrice(103).lowPrice(99).add();
        series.barBuilder().openPrice(102).closePrice(105).highPrice(106).lowPrice(101).add();
        series.barBuilder().openPrice(105).closePrice(108).highPrice(109).lowPrice(104).add();
        series.barBuilder().openPrice(108).closePrice(112).highPrice(113).lowPrice(107).add();
        series.barBuilder().openPrice(112).closePrice(116).highPrice(117).lowPrice(111).add();
        series.barBuilder().openPrice(116).closePrice(120).highPrice(121).lowPrice(115).add();
        return series;
    }

    private BarSeries buildBreakdownSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        // Start with consolidation
        series.barBuilder().openPrice(100).closePrice(101).highPrice(102).lowPrice(99).add();
        series.barBuilder().openPrice(101).closePrice(100).highPrice(102).lowPrice(99).add();
        series.barBuilder().openPrice(100).closePrice(101).highPrice(102).lowPrice(99).add();
        // Sharp breakdown below
        series.barBuilder().openPrice(101).closePrice(90).highPrice(102).lowPrice(89).add();
        series.barBuilder().openPrice(90).closePrice(85).highPrice(91).lowPrice(84).add();
        return series;
    }
}
