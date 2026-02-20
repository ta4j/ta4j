/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MACDVIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Duration PERIOD = Duration.ofDays(1);

    private BarSeries series;

    public MACDVIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = buildSeries(90);
    }

    @Test
    public void throwsErrorOnIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> new MACDVIndicator(new ClosePriceIndicator(series), 10, 5));
        assertThrows(IllegalArgumentException.class, () -> new MACDVIndicator(new ClosePriceIndicator(series), 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new MACDVIndicator(new ClosePriceIndicator(series), 5, 10, 0));
    }

    @Test
    public void signalAndHistogramUseConfiguredDefaultSignalPeriod() {
        MACDVIndicator indicator = new MACDVIndicator(series, 5, 10, 4);

        EMAIndicator defaultSignalLine = indicator.getSignalLine();
        EMAIndicator explicitSignalLine = indicator.getSignalLine(4);
        NumericIndicator defaultHistogram = indicator.getHistogram();
        NumericIndicator explicitHistogram = indicator.getHistogram(4);

        assertThat(indicator.getMacd()).isSameAs(indicator);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num defaultSignal = defaultSignalLine.getValue(i);
            Num explicitSignal = explicitSignalLine.getValue(i);
            Num defaultHist = defaultHistogram.getValue(i);
            Num explicitHist = explicitHistogram.getValue(i);

            if (Num.isNaNOrNull(defaultSignal) || Num.isNaNOrNull(explicitSignal)) {
                assertThat(Num.isNaNOrNull(defaultSignal)).isTrue();
                assertThat(Num.isNaNOrNull(explicitSignal)).isTrue();
            } else {
                assertThat(defaultSignal).isEqualByComparingTo(explicitSignal);
            }

            if (Num.isNaNOrNull(defaultHist) || Num.isNaNOrNull(explicitHist)) {
                assertThat(Num.isNaNOrNull(defaultHist)).isTrue();
                assertThat(Num.isNaNOrNull(explicitHist)).isTrue();
            } else {
                assertThat(defaultHist).isEqualByComparingTo(explicitHist);
            }
        }
    }

    @Test
    public void matchesComposedVolumeAtrWeightedMacd() {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        MACDVIndicator indicator = new MACDVIndicator(closePrice, 5, 10);

        Indicator<Num> volume = new VolumeIndicator(series);
        Indicator<Num> shortWeights = NumericIndicator.of(volume).dividedBy(new ATRIndicator(series, 5));
        Indicator<Num> longWeights = NumericIndicator.of(volume).dividedBy(new ATRIndicator(series, 10));
        VWMAIndicator shortVwema = new VWMAIndicator(closePrice, shortWeights, 5, EMAIndicator::new);
        VWMAIndicator longVwema = new VWMAIndicator(closePrice, longWeights, 10, EMAIndicator::new);

        int expectedUnstableBars = Math.max(shortVwema.getCountOfUnstableBars(), longVwema.getCountOfUnstableBars());
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(expectedUnstableBars);

        assertThat(Num.isNaNOrNull(indicator.getValue(expectedUnstableBars - 1))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(expectedUnstableBars))).isFalse();

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num actual = indicator.getValue(i);
            Num expected = shortVwema.getValue(i).minus(longVwema.getValue(i));

            if (Num.isNaNOrNull(expected)) {
                assertThat(Num.isNaNOrNull(actual)).isTrue();
            } else {
                assertThat(actual).isEqualByComparingTo(expected);
            }
        }
    }

    @Test
    public void defaultConfigurationRespectsUnstableBoundary() {
        MACDVIndicator indicator = new MACDVIndicator(series);
        int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(26);
        assertThat(Num.isNaNOrNull(indicator.getValue(unstableBars - 1))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(unstableBars))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        MACDVIndicator original = new MACDVIndicator(series, 5, 10, 4);

        String json = original.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(MACDVIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restored.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num expected = original.getValue(i);
            Num actual = restored.getValue(i);
            if (Num.isNaNOrNull(expected)) {
                assertThat(Num.isNaNOrNull(actual)).isTrue();
            } else {
                assertThat(actual).isEqualByComparingTo(expected);
            }
        }
    }

    private BarSeries buildSeries(int barCount) {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("macdv-test-series")
                .withNumFactory(numFactory);
        BarSeries result = builder.build();
        for (int i = 0; i < barCount; i++) {
            double baseline = 100.0 + (i * 0.45);
            double close = baseline + Math.sin(i / 5.0);
            double open = close - 0.35;
            double high = close + 1.4 + ((i % 4) * 0.05);
            double low = close - 1.2 - ((i % 3) * 0.04);
            double volume = 1_000 + (i * 22) + ((i % 5) * 40);

            result.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1L)))
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
