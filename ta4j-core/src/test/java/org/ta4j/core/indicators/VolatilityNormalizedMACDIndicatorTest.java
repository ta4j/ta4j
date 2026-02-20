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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VolatilityNormalizedMACDIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final Instant START = Instant.parse("2024-03-01T00:00:00Z");
    private static final Duration PERIOD = Duration.ofDays(1);

    private BarSeries series;

    public VolatilityNormalizedMACDIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = buildOscillatingSeries(120);
    }

    @Test
    public void validatesConstructorArguments() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityNormalizedMACDIndicator(closePrice, 13, 12, 9));
        assertThrows(IllegalArgumentException.class, () -> new VolatilityNormalizedMACDIndicator(closePrice, 0, 12, 9));
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityNormalizedMACDIndicator(closePrice, 12, 26, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityNormalizedMACDIndicator(closePrice, 12, 26, 26, 9, 0));
    }

    @Test
    public void defaultConfigurationHonorsUnstableBoundary() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series);
        int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(26);
        assertThat(Num.isNaNOrNull(indicator.getValue(unstableBars - 1))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(unstableBars))).isFalse();
    }

    @Test
    public void matchesReferenceFormula() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(closePrice, 12, 26, 9);

        EMAIndicator fastEma = new EMAIndicator(closePrice, 12);
        EMAIndicator slowEma = new EMAIndicator(closePrice, 26);
        ATRIndicator atr = new ATRIndicator(series, 26);

        int expectedUnstableBars = Math.max(
                closePrice.getCountOfUnstableBars()
                        + Math.max(fastEma.getCountOfUnstableBars(), slowEma.getCountOfUnstableBars()),
                atr.getCountOfUnstableBars());
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(expectedUnstableBars);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num actual = indicator.getValue(i);

            if (i < expectedUnstableBars) {
                assertThat(Num.isNaNOrNull(actual)).isTrue();
                continue;
            }

            Num spread = fastEma.getValue(i).minus(slowEma.getValue(i));
            Num atrValue = atr.getValue(i);
            Num expected;
            if (atrValue.isZero()) {
                expected = spread.isZero() ? numFactory.zero() : org.ta4j.core.num.NaN.NaN;
            } else {
                expected = spread.dividedBy(atrValue).multipliedBy(numFactory.hundred());
            }

            if (Num.isNaNOrNull(expected)) {
                assertThat(Num.isNaNOrNull(actual)).isTrue();
            } else {
                assertThat(actual).isEqualByComparingTo(expected);
            }
        }
    }

    @Test
    public void signalAndHistogramConvenienceMethodsMatchExplicitCalculations() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        EMAIndicator signalLine = indicator.getSignalLine();
        EMAIndicator explicitSignalLine = indicator.getSignalLine(9);

        assertThat(indicator.getMacdV()).isSameAs(indicator);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num signal = signalLine.getValue(i);
            Num explicitSignal = explicitSignalLine.getValue(i);
            Num histogram = indicator.getHistogram().getValue(i);
            Num expectedHistogram = indicator.getHistogram(9).getValue(i);

            if (Num.isNaNOrNull(signal) || Num.isNaNOrNull(explicitSignal)) {
                assertThat(Num.isNaNOrNull(signal)).isTrue();
                assertThat(Num.isNaNOrNull(explicitSignal)).isTrue();
            } else {
                assertThat(signal).isEqualByComparingTo(explicitSignal);
            }

            if (Num.isNaNOrNull(histogram) || Num.isNaNOrNull(expectedHistogram)) {
                assertThat(Num.isNaNOrNull(histogram)).isTrue();
                assertThat(Num.isNaNOrNull(expectedHistogram)).isTrue();
            } else {
                assertThat(histogram).isEqualByComparingTo(expectedHistogram);
            }
        }
    }

    @Test
    public void returnsZeroWhenAtrAndSpreadAreBothZero() {
        BarSeries flatSeries = buildFlatSeries(10, 10.0);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(flatSeries);
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(closePrice, 2, 5, 1, 2,
                100);

        int unstableBars = indicator.getCountOfUnstableBars();
        Num value = indicator.getValue(unstableBars);

        assertThat(value).isEqualByComparingTo(numFactory.zero());
    }

    @Test
    public void returnsNaNWhenAtrIsZeroButSpreadIsNonZero() {
        double[] stepCloses = { 10.0, 10.0, 10.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0 };
        BarSeries stepSeries = buildFlatSeries(stepCloses);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(stepSeries);
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(closePrice, 2, 5, 1, 2,
                100);

        int unstableBars = indicator.getCountOfUnstableBars();
        Num value = indicator.getValue(unstableBars);

        assertThat(Num.isNaNOrNull(value)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        VolatilityNormalizedMACDIndicator original = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);

        String json = original.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(VolatilityNormalizedMACDIndicator.class);
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

    private BarSeries buildOscillatingSeries(int barCount) {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("volatility-normalized-macd-series")
                .withNumFactory(numFactory);
        BarSeries result = builder.build();

        for (int i = 0; i < barCount; i++) {
            double baseline = 200.0 + (i * 0.65);
            double close = baseline + Math.sin(i / 4.0) * 1.1;
            double open = close - 0.4;
            double high = close + 1.3 + ((i % 4) * 0.1);
            double low = close - 1.1 - ((i % 3) * 0.08);

            result.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1L)))
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(1_000 + i * 10L)
                    .add();
        }

        return result;
    }

    private BarSeries buildFlatSeries(int barCount, double closePrice) {
        double[] closes = new double[barCount];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = closePrice;
        }
        return buildFlatSeries(closes);
    }

    private BarSeries buildFlatSeries(double[] closes) {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("flat-series").withNumFactory(numFactory);
        BarSeries result = builder.build();

        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            result.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1L)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1_000)
                    .add();
        }
        return result;
    }
}
