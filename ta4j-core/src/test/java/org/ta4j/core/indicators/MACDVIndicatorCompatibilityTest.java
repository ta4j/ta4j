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
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.macd.MACDHistogramMode;
import org.ta4j.core.indicators.macd.MACDLineValues;
import org.ta4j.core.indicators.macd.MACDVMomentumProfile;
import org.ta4j.core.indicators.macd.MACDVMomentumStateIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@SuppressWarnings("removal")
public class MACDVIndicatorCompatibilityTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Duration PERIOD = Duration.ofDays(1);

    private BarSeries series;

    public MACDVIndicatorCompatibilityTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = buildSeries(60);
    }

    @Test
    public void deprecatedShimMatchesMovedMacdvValues() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDVIndicator shim = new MACDVIndicator(closePrice, 5, 10, 4);
        org.ta4j.core.indicators.macd.MACDVIndicator moved = new org.ta4j.core.indicators.macd.MACDVIndicator(
                closePrice, 5, 10, 4);

        assertThat(shim.getPriceIndicator()).isSameAs(closePrice);
        assertThat(shim.getShortBarCount()).isEqualTo(moved.getShortBarCount());
        assertThat(shim.getLongBarCount()).isEqualTo(moved.getLongBarCount());
        assertThat(shim.getDefaultSignalBarCount()).isEqualTo(moved.getDefaultSignalBarCount());
        assertThat(shim.getCountOfUnstableBars()).isEqualTo(moved.getCountOfUnstableBars());

        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            Num shimValue = shim.getValue(index);
            Num movedValue = moved.getValue(index);
            if (Num.isNaNOrNull(movedValue)) {
                assertThat(Num.isNaNOrNull(shimValue)).isTrue();
            } else {
                assertThat(shimValue).isEqualByComparingTo(movedValue);
            }
        }
    }

    @Test
    public void deprecatedShimComposesHelpersFromTheShimSource() {
        MACDVIndicator shim = new MACDVIndicator(series, 5, 10, 4);
        int stableIndex = shim.getCountOfUnstableBars();

        MACDLineValues lineValues = shim.getLineValues(stableIndex, 4, SMAIndicator::new,
                MACDHistogramMode.SIGNAL_MINUS_MACD);
        Num signal = shim.getSignalLine(4, SMAIndicator::new).getValue(stableIndex);

        assertThat(shim.getMacd().getValue(stableIndex)).isEqualByComparingTo(shim.getValue(stableIndex));
        assertThat(lineValues.macd()).isEqualByComparingTo(shim.getValue(stableIndex));
        assertThat(lineValues.signal()).isEqualByComparingTo(signal);
        assertThat(lineValues.histogram()).isEqualByComparingTo(signal.minus(shim.getValue(stableIndex)));
        assertThat(shim.getHistogram(4, MACDHistogramMode.MACD_MINUS_SIGNAL).getValue(stableIndex))
                .isEqualByComparingTo(shim.getValue(stableIndex).minus(shim.getSignalLine(4).getValue(stableIndex)));

        MACDVMomentumStateIndicator momentum = shim.getMomentumStateIndicator(MACDVMomentumProfile.defaultProfile());
        assertThat(IndicatorUtils.isSameSeries(momentum.getMacdVIndicator().getBarSeries(), shim.getBarSeries()))
                .isTrue();
        assertThat(momentum.getMacdVIndicator().getValue(stableIndex)).isEqualByComparingTo(shim.getValue(stableIndex));
        assertThat(shim.crossedUpSignal()).isNotNull();
        assertThat(shim.crossedDownSignal()).isNotNull();
        assertThat(shim.inMomentumState(momentum.getValue(stableIndex))).isNotNull();
    }

    private BarSeries buildSeries(int barCount) {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("macdv-compatibility-test-series")
                .withNumFactory(numFactory);
        BarSeries result = builder.build();
        for (int index = 0; index < barCount; index++) {
            double baseline = 100.0 + (index * 0.45);
            double close = baseline + Math.sin(index / 5.0);
            double open = close - 0.35;
            double high = close + 1.4 + ((index % 4) * 0.05);
            double low = close - 1.2 - ((index % 3) * 0.04);
            double volume = 1_000 + (index * 22) + ((index % 5) * 40);

            result.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(index + 1L)))
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(volume)
                    .add();
        }
        return result;
    }
}
