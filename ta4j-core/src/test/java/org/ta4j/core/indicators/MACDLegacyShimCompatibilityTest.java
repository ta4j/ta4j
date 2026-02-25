/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.rules.MomentumStateRule;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;

public class MACDLegacyShimCompatibilityTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final Instant START = Instant.parse("2024-04-01T00:00:00Z");
    private static final Duration PERIOD = Duration.ofDays(1);

    private BarSeries series;

    public MACDLegacyShimCompatibilityTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("macd-legacy-shim-series")
                .withNumFactory(numFactory);
        series = builder.build();
        for (int i = 0; i < 90; i++) {
            double close = 100 + (i * 0.35) + Math.sin(i / 6.0);
            double open = close - 0.25;
            double high = close + 1.2;
            double low = close - 1.0;
            double volume = 1000 + (i * 20);

            series.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1L)))
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(volume)
                    .add();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void legacyVolatilityNormalizedMacdShimSupportsLegacyHelperTypes() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        int stableIndex = indicator.getCountOfUnstableBars();

        MACDLineValues lineValues = indicator.getLineValues(stableIndex, 9, MACDHistogramMode.MACD_MINUS_SIGNAL);
        assertThat(lineValues.macd()).isEqualByComparingTo(indicator.getValue(stableIndex));
        assertThat(lineValues.histogram()).isEqualByComparingTo(
                indicator.getHistogram(9, MACDHistogramMode.MACD_MINUS_SIGNAL).getValue(stableIndex));

        MACDVMomentumProfile profile = new MACDVMomentumProfile(20, 40, -20, -40);
        MACDVMomentumState state = indicator.getMomentumState(stableIndex, profile);
        MACDVMomentumStateIndicator stateIndicator = indicator.getMomentumStateIndicator(profile);
        MomentumStateRule rule = new MomentumStateRule(stateIndicator, state);
        Indicator<org.ta4j.core.indicators.macd.MACDVMomentumState> normalizedIndicator = rule
                .getMomentumStateIndicator();
        org.ta4j.core.indicators.macd.MACDVMomentumState normalizedExpected = rule.getExpectedState();

        assertThat(normalizedIndicator.getValue(stableIndex).name()).isEqualTo(state.name());
        assertThat(normalizedExpected.name()).isEqualTo(state.name());
        assertThat(rule.isSatisfied(stableIndex)).isTrue();
    }
}
