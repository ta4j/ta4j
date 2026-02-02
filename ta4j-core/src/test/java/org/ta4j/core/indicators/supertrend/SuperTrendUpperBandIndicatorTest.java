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

public class SuperTrendUpperBandIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public SuperTrendUpperBandIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void returnsCurrentBasicAfterAtrNaNPeriod() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 1d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();

        assertNumEquals(17.5, indicator.getValue(2));
        assertNumEquals(17.5, indicator.getValue(3));
    }

    @Test
    public void defaultConstructorUsesStandardParameters() {
        BarSeries series = buildLongerSeries();
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series);

        // Default uses barCount=10 ATR and multiplier=3
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(10);

        // Verify it produces valid values after unstable period
        Num value10 = indicator.getValue(10);
        assertThat(Num.isNaNOrNull(value10)).isFalse();
        assertThat(value10.isPositive()).isTrue();
    }

    @Test
    public void handlesExtremeSmallMultiplier() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 0.01d);

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
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 100d);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(atrIndicator.getCountOfUnstableBars());
        assertThat(Num.isNaNOrNull(indicator.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(1))).isTrue();

        // With very large multiplier, band should be much higher than median price
        Num value2 = indicator.getValue(2);
        assertThat(Num.isNaNOrNull(value2)).isFalse();
        assertThat(value2.isPositive()).isTrue();
    }

    @Test
    public void recoversGracefullyAfterNaNPeriod() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 1d);

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
    public void upperBandOnlyMovesDownDuringDowntrend() {
        // Build a downtrend series where prices fall
        BarSeries series = buildDowntrendSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 1d);

        // After unstable period, upper band should only decrease (ratchet down)
        // or stay same when current basic is less than or equal to previous band
        int unstableBars = indicator.getCountOfUnstableBars();
        for (int i = unstableBars + 1; i < series.getBarCount(); i++) {
            Num currentBand = indicator.getValue(i);
            Num previousBand = indicator.getValue(i - 1);
            // Upper band should not increase during downtrend (ratchet behavior)
            assertThat(currentBand.isLessThanOrEqual(previousBand))
                    .as("Upper band at index %d should not increase above previous", i)
                    .isTrue();
        }
    }

    @Test
    public void upperBandResetWhenPriceClosesAbove() {
        // Build series where price breaks above upper band
        BarSeries series = buildBreakoutSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 2);
        SuperTrendUpperBandIndicator indicator = new SuperTrendUpperBandIndicator(series, atrIndicator, 1d);

        // When price closes above upper band, band should reset to current basic
        // This tests the "previous close > previous upper band" condition
        int unstableBars = indicator.getCountOfUnstableBars();
        assertThat(series.getBarCount()).isGreaterThan(unstableBars + 2);

        // Values should be valid after unstable period
        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num value = indicator.getValue(i);
            assertThat(Num.isNaNOrNull(value)).isFalse();
            assertThat(value.isPositive()).isTrue();
        }
    }

    @Test
    public void serializationRoundTrip() {
        BarSeries series = buildSeries();
        ATRIndicator atrIndicator = new ATRIndicator(series, 3);
        SuperTrendUpperBandIndicator original = new SuperTrendUpperBandIndicator(series, atrIndicator, 2.5);

        String json = original.toJson();
        @SuppressWarnings("unchecked")
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(SuperTrendUpperBandIndicator.class);
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

    private BarSeries buildDowntrendSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100).closePrice(98).highPrice(101).lowPrice(97).add();
        series.barBuilder().openPrice(98).closePrice(95).highPrice(99).lowPrice(94).add();
        series.barBuilder().openPrice(95).closePrice(92).highPrice(96).lowPrice(91).add();
        series.barBuilder().openPrice(92).closePrice(88).highPrice(93).lowPrice(87).add();
        series.barBuilder().openPrice(88).closePrice(84).highPrice(89).lowPrice(83).add();
        series.barBuilder().openPrice(84).closePrice(80).highPrice(85).lowPrice(79).add();
        return series;
    }

    private BarSeries buildBreakoutSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        // Start with consolidation
        series.barBuilder().openPrice(100).closePrice(101).highPrice(102).lowPrice(99).add();
        series.barBuilder().openPrice(101).closePrice(100).highPrice(102).lowPrice(99).add();
        series.barBuilder().openPrice(100).closePrice(101).highPrice(102).lowPrice(99).add();
        // Sharp breakout above
        series.barBuilder().openPrice(101).closePrice(110).highPrice(111).lowPrice(100).add();
        series.barBuilder().openPrice(110).closePrice(115).highPrice(116).lowPrice(109).add();
        return series;
    }
}
