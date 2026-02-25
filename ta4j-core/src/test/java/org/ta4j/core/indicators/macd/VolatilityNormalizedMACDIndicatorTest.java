/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.macd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

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
    public void exposesConfiguredParametersAndSubIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(closePrice, 8, 21, 34, 5,
                200);

        assertThat(indicator.getPriceIndicator()).isSameAs(closePrice);
        assertThat(indicator.getFastBarCount()).isEqualTo(8);
        assertThat(indicator.getSlowBarCount()).isEqualTo(21);
        assertThat(indicator.getAtrBarCount()).isEqualTo(34);
        assertThat(indicator.getDefaultSignalBarCount()).isEqualTo(5);
        assertThat(indicator.getScaleFactor()).isEqualByComparingTo(numFactory.numOf(200));
        assertThat(indicator.getFastEma().getBarCount()).isEqualTo(8);
        assertThat(indicator.getSlowEma().getBarCount()).isEqualTo(21);
        assertThat(indicator.getAtrIndicator().getBarCount()).isEqualTo(34);
    }

    @Test
    public void supportsHistogramModesAndLineValues() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        BiFunction<Indicator<Num>, Integer, Indicator<Num>> customSignalFactory = SMAIndicator::new;
        Indicator<Num> customSignal = indicator.getSignalLine(9, customSignalFactory);

        int stableIndex = Math.max(indicator.getCountOfUnstableBars(), customSignal.getCountOfUnstableBars());
        Num defaultHistogram = indicator.getHistogram(9, MACDHistogramMode.MACD_MINUS_SIGNAL).getValue(stableIndex);
        Num invertedHistogram = indicator.getHistogram(9, MACDHistogramMode.SIGNAL_MINUS_MACD).getValue(stableIndex);
        assertThat(invertedHistogram).isEqualByComparingTo(defaultHistogram.multipliedBy(numFactory.minusOne()));

        MACDLineValues lineValues = indicator.getLineValues(stableIndex, 9, customSignalFactory,
                MACDHistogramMode.SIGNAL_MINUS_MACD);
        Num expectedMacd = indicator.getValue(stableIndex);
        Num expectedSignal = customSignal.getValue(stableIndex);
        Num expectedHistogram = expectedSignal.minus(expectedMacd);
        assertThat(lineValues.macd()).isEqualByComparingTo(expectedMacd);
        assertThat(lineValues.signal()).isEqualByComparingTo(expectedSignal);
        assertThat(lineValues.histogram()).isEqualByComparingTo(expectedHistogram);
    }

    @Test
    public void lineValuesWithCustomSignalFactoryUseSingleSignalLineInstance() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        AtomicInteger factoryInvocations = new AtomicInteger();
        BiFunction<Indicator<Num>, Integer, Indicator<Num>> statefulFactory = (source, bars) -> {
            int invocation = factoryInvocations.incrementAndGet();
            return NumericIndicator.of(source).plus(invocation);
        };

        int stableIndex = indicator.getCountOfUnstableBars();
        MACDLineValues lineValues = indicator.getLineValues(stableIndex, 9, statefulFactory,
                MACDHistogramMode.SIGNAL_MINUS_MACD);

        assertThat(factoryInvocations.get()).isEqualTo(1);
        assertThat(lineValues.signal()).isEqualByComparingTo(lineValues.macd().plus(numFactory.one()));
        assertThat(lineValues.histogram()).isEqualByComparingTo(numFactory.one());
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
    public void supportsInjectingCustomSignalLineIndicatorFactory() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        BiFunction<Indicator<Num>, Integer, Indicator<Num>> customSignalFactory = SMAIndicator::new;

        Indicator<Num> customSignalLine = indicator.getSignalLine(9, customSignalFactory);
        Indicator<Num> customHistogram = indicator.getHistogram(9, customSignalFactory);

        assertThat(customSignalLine).isInstanceOf(SMAIndicator.class);

        int firstStableIndex = Math.max(indicator.getCountOfUnstableBars(), customSignalLine.getCountOfUnstableBars());
        Num expected = indicator.getValue(firstStableIndex).minus(customSignalLine.getValue(firstStableIndex));
        assertThat(customHistogram.getValue(firstStableIndex)).isEqualByComparingTo(expected);
    }

    @Test
    public void rejectsInvalidCustomSignalLineFactories() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        BarSeries otherSeries = buildOscillatingSeries(40);

        assertThrows(IllegalArgumentException.class, () -> indicator.getSignalLine(9, null));
        assertThrows(IllegalArgumentException.class, () -> indicator.getSignalLine(9, (src, bars) -> null));
        assertThrows(IllegalArgumentException.class, () -> indicator.getSignalLine(9,
                (src, bars) -> new EMAIndicator(new ClosePriceIndicator(otherSeries), bars)));
        assertThrows(IllegalArgumentException.class, () -> indicator.getHistogram(9, (MACDHistogramMode) null));
        assertThrows(IllegalArgumentException.class, () -> indicator.getHistogram(9, SMAIndicator::new, null));
    }

    @Test
    public void rejectsSignalLineFactoriesThatReturnDifferentSeriesEvenWhenEqual() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        BarSeries equalityCompatibleSeries = new EqualityCompatibleBarSeries(series);

        assertThrows(IllegalArgumentException.class, () -> indicator.getSignalLine(9,
                (src, bars) -> new EMAIndicator(new ClosePriceIndicator(equalityCompatibleSeries), bars)));
    }

    @Test
    public void classifiesMomentumStateUsingDefaultThresholds() {
        assertThat(MACDVMomentumState.fromMacdV(numFactory.zero(), (MACDVMomentumProfile) null))
                .isEqualTo(MACDVMomentumState.UNDEFINED);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.zero(), (NumFactory) null))
                .isEqualTo(MACDVMomentumState.UNDEFINED);
        assertThat(MACDVMomentumState.fromMacdV(NaN.NaN, numFactory)).isEqualTo(MACDVMomentumState.UNDEFINED);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(151), numFactory))
                .isEqualTo(MACDVMomentumState.HIGH_RISK);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(150), numFactory))
                .isEqualTo(MACDVMomentumState.RALLYING_OR_RETRACING);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(50), numFactory))
                .isEqualTo(MACDVMomentumState.RALLYING_OR_RETRACING);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.zero(), numFactory)).isEqualTo(MACDVMomentumState.RANGING);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(-50), numFactory))
                .isEqualTo(MACDVMomentumState.RANGING);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(-51), numFactory))
                .isEqualTo(MACDVMomentumState.REBOUNDING_OR_REVERSING);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(-150), numFactory))
                .isEqualTo(MACDVMomentumState.REBOUNDING_OR_REVERSING);
        assertThat(MACDVMomentumState.fromMacdV(numFactory.numOf(-151), numFactory))
                .isEqualTo(MACDVMomentumState.LOW_RISK);
    }

    @Test
    public void histogramModeComputeMatchesExpectedPolarity() {
        Num macd = numFactory.numOf(12);
        Num signal = numFactory.numOf(5);

        assertThat(MACDHistogramMode.MACD_MINUS_SIGNAL.compute(macd, signal)).isEqualByComparingTo(numFactory.numOf(7));
        assertThat(MACDHistogramMode.SIGNAL_MINUS_MACD.compute(macd, signal))
                .isEqualByComparingTo(numFactory.numOf(-7));
        assertThrows(NullPointerException.class, () -> MACDHistogramMode.MACD_MINUS_SIGNAL.compute(null, signal));
        assertThrows(NullPointerException.class, () -> MACDHistogramMode.MACD_MINUS_SIGNAL.compute(macd, null));
    }

    @Test
    public void getMomentumStateReturnsUndefinedDuringUnstableWindow() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(indicator.getMomentumState(unstableBars - 1)).isEqualTo(MACDVMomentumState.UNDEFINED);
        assertThat(indicator.getMomentumState(unstableBars)).isNotEqualTo(MACDVMomentumState.UNDEFINED);
    }

    @Test
    public void momentumAndRuleHelpersMatchExplicitComposition() {
        VolatilityNormalizedMACDIndicator indicator = new VolatilityNormalizedMACDIndicator(series, 12, 26, 9);
        MACDVMomentumProfile profile = new MACDVMomentumProfile(25, 55, -25, -55);
        int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(indicator.getMomentumState(unstableBars - 1, profile)).isEqualTo(MACDVMomentumState.UNDEFINED);
        Num value = indicator.getValue(unstableBars);
        MACDVMomentumState expectedState = MACDVMomentumState.fromMacdV(value, profile);
        assertThat(indicator.getMomentumState(unstableBars, profile)).isEqualTo(expectedState);
        assertThat(indicator.getMomentumStateIndicator(profile).getValue(unstableBars)).isEqualTo(expectedState);

        Rule crossedUpRule = indicator.crossedUpSignal();
        Rule explicitCrossedUpRule = new CrossedUpIndicatorRule(indicator, indicator.getSignalLine());
        Rule crossedDownRule = indicator.crossedDownSignal();
        Rule explicitCrossedDownRule = new CrossedDownIndicatorRule(indicator, indicator.getSignalLine());
        Rule customCrossedDownRule = indicator.crossedDownSignal(9, SMAIndicator::new);
        Rule explicitCustomCrossedDownRule = new CrossedDownIndicatorRule(indicator,
                indicator.getSignalLine(9, SMAIndicator::new));
        Rule inStateRule = indicator.inMomentumState(profile, MACDVMomentumState.RALLYING_OR_RETRACING);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(crossedUpRule.isSatisfied(i)).isEqualTo(explicitCrossedUpRule.isSatisfied(i));
            assertThat(crossedDownRule.isSatisfied(i)).isEqualTo(explicitCrossedDownRule.isSatisfied(i));
            assertThat(customCrossedDownRule.isSatisfied(i)).isEqualTo(explicitCustomCrossedDownRule.isSatisfied(i));
            assertThat(inStateRule.isSatisfied(i))
                    .isEqualTo(indicator.getMomentumState(i, profile) == MACDVMomentumState.RALLYING_OR_RETRACING);
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

    private static final class EqualityCompatibleBarSeries extends BaseBarSeries {

        EqualityCompatibleBarSeries(BarSeries delegate) {
            super(delegate.getName() + "-equal-copy", new ArrayList<>(delegate.getBarData()));
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BarSeries;
        }

        @Override
        public int hashCode() {
            return 31;
        }
    }
}
