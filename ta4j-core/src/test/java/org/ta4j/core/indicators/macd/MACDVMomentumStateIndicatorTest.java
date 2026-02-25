/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.macd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.NumFactory;

public class MACDVMomentumStateIndicatorTest
        extends AbstractIndicatorTest<Indicator<MACDVMomentumState>, MACDVMomentumState> {

    private static final Instant START = Instant.parse("2024-02-01T00:00:00Z");
    private static final Duration PERIOD = Duration.ofDays(1);

    private BarSeries series;

    public MACDVMomentumStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = buildSeries(120);
    }

    @Test
    public void returnsUndefinedInsideUnstableWindowAndClassifiesAfterward() {
        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        MACDVMomentumStateIndicator stateIndicator = new MACDVMomentumStateIndicator(macdV);
        int unstableBars = macdV.getCountOfUnstableBars();

        assertThat(stateIndicator.getCountOfUnstableBars()).isEqualTo(unstableBars);
        assertThat(stateIndicator.getValue(unstableBars - 1)).isEqualTo(MACDVMomentumState.UNDEFINED);
        assertThat(stateIndicator.getValue(unstableBars))
                .isEqualTo(MACDVMomentumState.fromMacdV(macdV.getValue(unstableBars), series.numFactory()));
    }

    @Test
    public void supportsCustomThresholdsAndExposesMomentumProfile() {
        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        MACDVMomentumStateIndicator stateIndicator = new MACDVMomentumStateIndicator(macdV, 20, 40, -20, -40);
        int unstableBars = stateIndicator.getCountOfUnstableBars();

        assertThat(stateIndicator.getPositiveRangeThreshold().toString()).isEqualTo("20");
        assertThat(stateIndicator.getPositiveRiskThreshold().toString()).isEqualTo("40");
        assertThat(stateIndicator.getNegativeRangeThreshold().toString()).isEqualTo("-20");
        assertThat(stateIndicator.getNegativeRiskThreshold().toString()).isEqualTo("-40");
        MACDVMomentumProfile momentumProfile = stateIndicator.getMomentumProfile();
        assertThat(momentumProfile.positiveRangeThreshold().toString()).isEqualTo("20");
        assertThat(momentumProfile.positiveRiskThreshold().toString()).isEqualTo("40");
        assertThat(momentumProfile.negativeRangeThreshold().toString()).isEqualTo("-20");
        assertThat(momentumProfile.negativeRiskThreshold().toString()).isEqualTo("-40");
        assertThat(stateIndicator.getValue(unstableBars)).isEqualTo(
                MACDVMomentumState.fromMacdV(macdV.getValue(unstableBars), stateIndicator.getMomentumProfile()));
    }

    @Test
    public void validatesConstructorArguments() {
        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);

        assertThrows(NullPointerException.class, () -> new MACDVMomentumStateIndicator(null));
        assertThrows(NullPointerException.class, () -> new MACDVMomentumStateIndicator(macdV, null));
        assertThrows(IllegalArgumentException.class, () -> new MACDVMomentumStateIndicator(macdV, 20, 20, -20, -40));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        MACDVMomentumStateIndicator original = new MACDVMomentumStateIndicator(macdV, 20, 40, -20, -40);

        String json = original.toJson();
        Indicator<MACDVMomentumState> restored = (Indicator<MACDVMomentumState>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(MACDVMomentumStateIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restored.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    private BarSeries buildSeries(int barCount) {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("macdv-momentum-state-series")
                .withNumFactory(numFactory);
        BarSeries result = builder.build();
        for (int i = 0; i < barCount; i++) {
            double baseline = 140.0 + (i * 0.3);
            double close = baseline + Math.sin(i / 3.0) * 0.8;
            double open = close - 0.2;
            double high = close + 0.9 + ((i % 3) * 0.08);
            double low = close - 0.8 - ((i % 4) * 0.06);

            result.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1L)))
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(900 + (i * 17L))
                    .add();
        }
        return result;
    }
}
