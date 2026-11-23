/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.CachedIndicator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

public class PercentageChangeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private PercentageChangeIndicator priceChangePercentage;

    private BarSeries barSeries;

    public PercentageChangeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        priceChangePercentage = new PercentageChangeIndicator(new ClosePriceIndicator(barSeries));
    }

    @Test
    public void startsCurrentAndLaggedRecursiveSourcesBeforeWarmUpEnds() {
        TraceTestLogger capture = new TraceTestLogger();
        capture.open();
        try {
            for (boolean laggedSource : new boolean[] { false, true }) {
                BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
                series.setMaximumBarCount(2);
                ClosePriceIndicator close = new ClosePriceIndicator(series);
                EMAIndicator average = new EMAIndicator(close, 3);
                PercentageChangeIndicator change = laggedSource ? new PercentageChangeIndicator(close, average)
                        : new PercentageChangeIndicator(average);
                for (int index = 0; index < 12; index++) {
                    series.barBuilder().closePrice(10).add();
                    Num value = change.getValue(index);
                    if (index < 4) {
                        assertThat(value.isNaN()).isTrue();
                    } else {
                        assertNumEquals(0, value);
                    }
                }
            }
        } finally {
            capture.close();
        }
    }

    @Test
    public void preservesPreviousBarAliasAtRetainedHead() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(1);
        PercentageChangeIndicator change = new PercentageChangeIndicator(new ClosePriceIndicator(series));
        for (int index = 0; index < 3; index++) {
            series.barBuilder().closePrice(10 * (index + 1)).add();
            Num value = change.getValue(index);
            if (index == 0) {
                assertThat(value.isNaN()).isTrue();
            } else {
                // A removed previous bar keeps the existing retained-head alias.
                assertNumEquals(0, value);
            }
        }
    }

    @Test
    public void indicatorShouldRetrieveBarPercentageChange() {
        assertThat(priceChangePercentage.getValue(0).isNaN()).isTrue();
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = barSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = barSeries.getBar(i).getClosePrice();
            Num expectedPercentage = currentBarClosePrice.minus(previousBarClosePrice)
                    .dividedBy(previousBarClosePrice)
                    .multipliedBy(numFactory.hundred());
            assertNumEquals(expectedPercentage, priceChangePercentage.getValue(i));
        }
    }

    @Test
    public void supportsEachReturnRepresentationAndPreservesLegacyPercentage() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 99).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        assertNumEquals(10, new PercentageChangeIndicator(close).getValue(1));
        for (ReturnRepresentation representation : ReturnRepresentation.values()) {
            PercentageChangeIndicator change = new PercentageChangeIndicator(close, representation);
            double expectedGain = switch (representation) {
            case PERCENTAGE -> 10;
            case DECIMAL -> 0.1;
            case MULTIPLICATIVE -> 1.1;
            case LOG -> Math.log(1.1);
            };
            double expectedLoss = switch (representation) {
            case PERCENTAGE -> -10;
            case DECIMAL -> -0.1;
            case MULTIPLICATIVE -> 0.9;
            case LOG -> Math.log(0.9);
            };
            assertThat(change.getValue(0).isNaN()).isTrue();
            assertThat(change.getValue(1).doubleValue()).isCloseTo(expectedGain, within(1e-12));
            assertThat(change.getValue(2).doubleValue()).isCloseTo(expectedLoss, within(1e-12));
        }
    }

    @Test
    public void logReturnUsesThePriceRatioAndRejectsNonPositiveRatios() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1e18, 1, 1e18, -1).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        PercentageChangeIndicator change = new PercentageChangeIndicator(close, ReturnRepresentation.LOG);
        PercentageChangeIndicator threshold = new PercentageChangeIndicator(close, 5, ReturnRepresentation.LOG);
        for (PercentageChangeIndicator indicator : new PercentageChangeIndicator[] { change, threshold }) {
            assertThat(indicator.getValue(1).doubleValue()).isCloseTo(-41.44653167389282, within(1e-12));
            assertThat(indicator.getValue(2).doubleValue()).isCloseTo(41.44653167389282, within(1e-12));
            assertThat(indicator.getValue(3).isNaN()).isTrue();
        }
    }

    @Test
    public void explicitRepresentationWorksWithPreviousSourceAndPercentageThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        int[] closes = { 100, 101, 103, 110, 111 };
        for (int i = 0; i < closes.length; i++) {
            series.barBuilder().openPrice(100).closePrice(closes[i]).add();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        OpenPriceIndicator open = new OpenPriceIndicator(series);
        for (ReturnRepresentation representation : ReturnRepresentation.values()) {
            PercentageChangeIndicator previous = new PercentageChangeIndicator(close, open, 0, representation);
            PercentageChangeIndicator previousNum = new PercentageChangeIndicator(close, open, numOf(0),
                    representation);
            PercentageChangeIndicator threshold = new PercentageChangeIndicator(close, 5, representation);
            double expected = switch (representation) {
            case PERCENTAGE -> 10;
            case DECIMAL -> 0.1;
            case MULTIPLICATIVE -> 1.1;
            case LOG -> Math.log(1.1);
            };
            assertThat(previous.getValue(3).doubleValue()).isCloseTo(expected, within(1e-12));
            assertThat(previousNum.getValue(3).doubleValue()).isCloseTo(expected, within(1e-12));
            assertThat(threshold.getValue(1).doubleValue()).isCloseTo(switch (representation) {
            case PERCENTAGE -> 1;
            case DECIMAL -> 0.01;
            case MULTIPLICATIVE -> 1.01;
            case LOG -> Math.log(1.01);
            }, within(1e-12));
            assertThat(threshold.getValue(2).doubleValue()).isCloseTo(switch (representation) {
            case PERCENTAGE -> 3;
            case DECIMAL -> 0.03;
            case MULTIPLICATIVE -> 1.03;
            case LOG -> Math.log(1.03);
            }, within(1e-12));
            assertThat(threshold.getValue(3).doubleValue()).isCloseTo(expected, within(1e-12));
            assertThat(new PercentageChangeIndicator(close, numOf(5), representation).getValue(3).doubleValue())
                    .isCloseTo(expected, within(1e-12));
            double afterThreshold = switch (representation) {
            case PERCENTAGE -> 100.0 / 110;
            case DECIMAL -> 1.0 / 110;
            case MULTIPLICATIVE -> 111.0 / 110;
            case LOG -> Math.log(111.0 / 110);
            };
            assertThat(threshold.getValue(4).doubleValue()).isCloseTo(afterThreshold, within(1e-12));
        }
    }

    @Test
    public void unlimitedPrecisionReturnsNaNWhenAnExactReturnIsNotRepresentable() {
        BarSeries series = new MockBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance(MathContext.UNLIMITED))
                .withData(3, 4, 6)
                .build();
        var close = new ClosePriceIndicator(series);
        for (ReturnRepresentation representation : ReturnRepresentation.values()) {
            assertThat(new PercentageChangeIndicator(close, representation).getValue(1).isNaN()).isTrue();
            assertThat(new PercentageChangeIndicator(close, 10, representation).getValue(1).isNaN()).isTrue();
        }
        assertNumEquals(100, new PercentageChangeIndicator(close, 10).getValue(2));
    }

    @Test
    public void unlimitedPrecisionUsesDefaultPrecisionForLogarithms() {
        BarSeries series = new MockBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance(MathContext.UNLIMITED))
                .withData(100, 110, 121)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        PercentageChangeIndicator change = new PercentageChangeIndicator(close, ReturnRepresentation.LOG);
        PercentageChangeIndicator threshold = new PercentageChangeIndicator(close, 5, ReturnRepresentation.LOG);
        for (PercentageChangeIndicator indicator : new PercentageChangeIndicator[] { change, threshold }) {
            for (int index = 1; index <= 2; index++) {
                Num result = indicator.getValue(index);
                assertThat(result.bigDecimalValue()).isEqualByComparingTo("0.09531017980432486");
                assertThat(((DecimalNum) result).getMathContext()).isEqualTo(MathContext.UNLIMITED);
            }
        }
        assertThat(new PercentageChangeIndicator(close, ReturnRepresentation.DECIMAL).getValue(1).bigDecimalValue())
                .isEqualByComparingTo("0.1");
        assertThat(new PercentageChangeIndicator(close, 5, ReturnRepresentation.DECIMAL).getValue(1).bigDecimalValue())
                .isEqualByComparingTo("0.1");
    }

    @Test
    public void strictRoundingOnlyAllowsTheExactLogarithmOfOne() {
        for (MathContext context : new MathContext[] { new MathContext(0, RoundingMode.UNNECESSARY),
                new MathContext(20, RoundingMode.UNNECESSARY) }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance(context))
                    .withData(100, 100, 110)
                    .build();
            ClosePriceIndicator close = new ClosePriceIndicator(series);
            PercentageChangeIndicator change = new PercentageChangeIndicator(close, ReturnRepresentation.LOG);
            PercentageChangeIndicator threshold = new PercentageChangeIndicator(close, 5, ReturnRepresentation.LOG);
            for (PercentageChangeIndicator indicator : new PercentageChangeIndicator[] { change, threshold }) {
                assertThat(indicator.getValue(1).bigDecimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(indicator.getValue(2).isNaN()).isTrue();
            }
        }
    }

    @Test
    public void decimalLogarithmsKeepPrecisionAcrossExtremeAndNearUnitRatios() {
        MathContext context = new MathContext(50, RoundingMode.HALF_EVEN);
        NumFactory factory = DecimalNumFactory.getInstance(context);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 1, 1).build();
        FixedIndicator<Num> extremes = new FixedIndicator<>(series, factory.numOf("1E400"), factory.numOf("3"),
                factory.numOf("1E400"));
        PercentageChangeIndicator change = new PercentageChangeIndicator(extremes, ReturnRepresentation.LOG);
        PercentageChangeIndicator threshold = new PercentageChangeIndicator(extremes, 5, ReturnRepresentation.LOG);
        BigDecimal expected = new BigDecimal("919.93542490895016391580133663682315733579310489369");
        for (PercentageChangeIndicator indicator : new PercentageChangeIndicator[] { change, threshold }) {
            Num loss = indicator.getValue(1);
            assertThat(loss.bigDecimalValue()).isEqualByComparingTo(expected.negate());
            assertThat(((DecimalNum) loss).getMathContext()).isEqualTo(context);
            assertThat(indicator.getValue(2).bigDecimalValue()).isEqualByComparingTo(expected);
        }

        FixedIndicator<Num> nearOne = new FixedIndicator<>(series, factory.one(),
                factory.numOf("0.99999999999999999999999999999999999999999999999999"));
        Num smallChange = new PercentageChangeIndicator(nearOne, ReturnRepresentation.LOG).getValue(1);
        assertThat(smallChange.bigDecimalValue()).isEqualByComparingTo("-1E-50");
    }

    @Test
    public void decimalLogarithmsHonorDirectedRounding() {
        for (RoundingMode mode : new RoundingMode[] { RoundingMode.DOWN, RoundingMode.UP }) {
            NumFactory factory = DecimalNumFactory.getInstance(new MathContext(10, mode));
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110).build();
            Num result = new PercentageChangeIndicator(new ClosePriceIndicator(series), ReturnRepresentation.LOG)
                    .getValue(1);
            assertThat(result.bigDecimalValue())
                    .isEqualByComparingTo(mode == RoundingMode.DOWN ? "0.09531017980" : "0.09531017981");
        }
    }

    @Test
    public void decimalLogarithmsConvergeBeyondTheOriginalFractionDepth() {
        NumFactory factory = DecimalNumFactory.getInstance(new MathContext(350, RoundingMode.HALF_EVEN));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 1).build();
        FixedIndicator<Num> prices = new FixedIndicator<>(series, factory.numOf("1E400"), factory.one());
        Num result = new PercentageChangeIndicator(prices, ReturnRepresentation.LOG).getValue(1);
        assertThat(result.bigDecimalValue()).isEqualByComparingTo(new BigDecimal(
                "-921.034037197618273607196581873745683040440595451509190413331160387029043870940992094398882035839319"
                        + "336787113616914499453363810186033122702666514947639512675793162883330221872337519957930493279"
                        + "411357402123586151093051538465346488915079287954698617466989761697309746062019573725975756591"
                        + "84776176008884204068566992014752336050588322742270972864913420880"));
    }

    @Test
    public void nonFinitePricesProduceNaNAndCannotBecomeThresholdBaselines() {
        DoubleNumFactory factory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 1, 1).build();
        for (double infinity : new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            var prices = new FixedIndicator<Num>(series, factory.hundred(), DoubleNum.valueOf(infinity),
                    factory.numOf(110));
            var adjacent = new PercentageChangeIndicator(prices, ReturnRepresentation.MULTIPLICATIVE);
            var threshold = new PercentageChangeIndicator(prices, 5, ReturnRepresentation.MULTIPLICATIVE);
            assertThat(adjacent.getValue(1).isNaN()).isTrue();
            assertThat(adjacent.getValue(2).isNaN()).isTrue();
            assertThat(threshold.getValue(1).isNaN()).isTrue();
            assertNumEquals(1.1, threshold.getValue(2));
        }
    }

    @Test
    public void multiplicativeReturnIsUndefinedWhenFactorUnderflows() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(1e200, 1e-200, 0, 1e-199, 1.1e200)
                .build();
        var change = new PercentageChangeIndicator(new ClosePriceIndicator(series),
                ReturnRepresentation.MULTIPLICATIVE);
        var threshold = new PercentageChangeIndicator(new ClosePriceIndicator(series), 5,
                ReturnRepresentation.MULTIPLICATIVE);

        assertThat(change.getValue(1).isNaN()).isTrue();
        assertThat(threshold.getValue(1).isNaN()).isTrue();
        assertThat(threshold.getValue(2).isNaN()).isTrue();
        assertThat(threshold.getValue(3).isNaN()).isTrue();
        assertNumEquals(1.1, threshold.getValue(4));
    }

    @Test
    public void rejectsNullReturnRepresentation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        assertThatNullPointerException()
                .isThrownBy(() -> new PercentageChangeIndicator(close, (ReturnRepresentation) null));
    }

    @Test
    public void decimalThresholdReturnKeepsSmallChangesBeforeDivision() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100.000000000001)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num expected = close.getValue(1).minus(close.getValue(0)).dividedBy(close.getValue(0));
        PercentageChangeIndicator change = new PercentageChangeIndicator(close, 10, ReturnRepresentation.DECIMAL);
        assertNumEquals(expected, change.getValue(1));
    }

    @Test
    public void defaultPercentageStillUsesTheProtectedCalculationHook() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        PercentageChangeIndicator overridden = new PercentageChangeIndicator(new ClosePriceIndicator(series)) {
            @Override
            protected Num calculateRateOfReturnPercentage(Num currentValue, Num previousValue) {
                return numFactory.numOf(42);
            }
        };
        assertNumEquals(42, overridden.getValue(1));
    }

    @Test
    public void representationsAndReplayStateRoundTripWithoutBreakingLegacyDescriptors() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int value : new int[] { 100, 101, 102, 103 }) {
            series.barBuilder().openPrice(100).closePrice(value).add();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        OpenPriceIndicator open = new OpenPriceIndicator(series);
        for (ReturnRepresentation representation : ReturnRepresentation.values()) {
            PercentageChangeIndicator threshold = new PercentageChangeIndicator(close, 10, representation);
            threshold.getValue(1);
            assertThat(threshold.toDescriptor().getParameters()).doesNotContainKey("lastNotification");
            IndicatorSerializationRoundTripTestSupport.assertIndicatorRoundTrips(series, threshold, 2, 3);
            PercentageChangeIndicator previous = new PercentageChangeIndicator(close, open, 0, representation);
            IndicatorSerializationRoundTripTestSupport.assertIndicatorRoundTrips(series, previous, 1, 2, 3);
        }

        ComponentDescriptor descriptor = new PercentageChangeIndicator(close).toDescriptor();
        var legacyParameters = new LinkedHashMap<>(descriptor.getParameters());
        legacyParameters.remove("returnRepresentation");
        var legacy = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withLabel(descriptor.getLabel())
                .withParameters(legacyParameters);
        descriptor.getComponents().forEach(legacy::addComponent);
        Indicator<?> restored = Indicator.fromJson(series, ComponentSerialization.toJson(legacy.build()));
        assertThat(((Num) restored.getValue(1)).doubleValue()).isCloseTo(1, within(1e-12));
    }

    @Test
    public void indicatorShouldReturnNaNForZeroPreviousValue() {
        BarSeries seriesWithZero = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 10, 20).build();
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(new ClosePriceIndicator(seriesWithZero));

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue(); // Previous value is 0, division by zero
        assertNumEquals(numFactory.hundred(), indicator.getValue(2)); // (20 - 10) / 10 * 100 = 100%
    }

    @Test
    public void indicatorShouldWorkWithPreviousIndicator() {
        Instant endTime = Instant.now();
        Duration duration = Duration.ofSeconds(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(2.8)
                .highPrice(2.9)
                .lowPrice(1)
                .closePrice(2.9)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(1))
                .openPrice(2.5)
                .highPrice(2.5)
                .lowPrice(2)
                .closePrice(2.4)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(2))
                .openPrice(2.0)
                .highPrice(3.0)
                .lowPrice(1)
                .closePrice(3.0)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(3))
                .openPrice(3)
                .highPrice(3.2)
                .lowPrice(3)
                .closePrice(3.2)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(4))
                .openPrice(4)
                .highPrice(4)
                .lowPrice(3.5)
                .closePrice(3.5)
                .add();

        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        PercentageChangeIndicator diff = new PercentageChangeIndicator(openPrice, closePrice);

        // index: 0: beginIndex <= index
        assertNumEquals(NaN.NaN, diff.getValue(0));

        // index: 1: currentOpenPrice = 2.5, previousClosePrice = 2.9
        // (2.5 - 2.9) / 2.9 * 100 = -13.79310344827586
        assertNumEquals(numOf(-13.79310344827586), diff.getValue(1));

        // index: 2: currentOpenPrice = 2.0, previousClosePrice = 2.4
        // (2.0 - 2.4) / 2.4 * 100 = -16.666666666666664 (precision difference expected)
        assertNumEquals(numOf(-16.66666666666667), diff.getValue(2), 1e-12);

        // index: 3: currentOpenPrice = 3.0, previousClosePrice = 3.0
        // (3.0 - 3.0) / 3.0 * 100 = 0.0
        assertNumEquals(numOf(0.0), diff.getValue(3));

        // index: 4: currentOpenPrice = 4.0, previousClosePrice = 3.2
        // (4.0 - 3.2) / 3.2 * 100 = 25.0
        assertNumEquals(numOf(25.0), diff.getValue(4), 1e-12);
    }

    @Test
    public void indicatorShouldWorkWithPercentageThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<>(series, numOf(1000), numOf(1010), numOf(1020),
                numOf(1050), numOf(1060.5), numOf(1081.5), numOf(1102.5), numOf(1091.475), numOf(1113.525),
                numOf(1036.35), numOf(1067.4405));

        PercentageChangeIndicator indicator = new PercentageChangeIndicator(mockIndicator, null, numOf(5));

        assertNumEquals(NaN.NaN, indicator.getValue(0));
        assertNumEquals(numOf(1), indicator.getValue(1));
        assertNumEquals(numOf(2), indicator.getValue(2));
        assertNumEquals(numOf(5), indicator.getValue(3));
        assertNumEquals(numOf(1), indicator.getValue(4));
        assertNumEquals(numOf(3), indicator.getValue(5));
        assertNumEquals(numOf(5), indicator.getValue(6));
        assertNumEquals(numOf(-1), indicator.getValue(7), 1e-12);
        assertNumEquals(numOf(1), indicator.getValue(8));
        assertNumEquals(numOf(-6), indicator.getValue(9));
        assertNumEquals(numOf(3), indicator.getValue(10));
    }

    @Test
    public void indicatorShouldWorkWithPercentageThresholdAndPreviousIndicator() {
        // When threshold > 0, previousIndicator is ignored - threshold tracking uses
        // indicator itself
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<>(series, numOf(1000), numOf(1010), numOf(1020),
                numOf(1050), numOf(1060.5), numOf(1081.5), numOf(1102.5), numOf(1091.475), numOf(1113.525),
                numOf(1036.35), numOf(1067.4405));

        // Even though we pass a previousIndicator, it should be ignored when threshold
        // > 0
        FixedIndicator<Num> dummyPrevious = new FixedIndicator<>(series, numOf(999), numOf(998), numOf(997));
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(mockIndicator, dummyPrevious, numOf(5));

        // Should behave the same as without previousIndicator when threshold is set
        assertNumEquals(NaN.NaN, indicator.getValue(0));
        assertNumEquals(numOf(1), indicator.getValue(1));
        assertNumEquals(numOf(2), indicator.getValue(2));
        assertNumEquals(numOf(5), indicator.getValue(3));
        assertNumEquals(numOf(1), indicator.getValue(4));
        assertNumEquals(numOf(3), indicator.getValue(5));
        assertNumEquals(numOf(5), indicator.getValue(6));
        assertNumEquals(numOf(-1), indicator.getValue(7), 1e-12);
        assertNumEquals(numOf(1), indicator.getValue(8));
        assertNumEquals(numOf(-6), indicator.getValue(9));
        assertNumEquals(numOf(3), indicator.getValue(10));
    }

    @Test
    public void indicatorShouldUsePreviousValueWhenThresholdIsZero() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<>(series, numOf(100), numOf(101), numOf(98.98),
                numOf(102.186952), numOf(91.9682568), numOf(100.5213046824), numOf(101.526517729224));

        PercentageChangeIndicator indicator = new PercentageChangeIndicator(mockIndicator);

        assertNumEquals(NaN.NaN, indicator.getValue(0));
        assertNumEquals(numOf(1), indicator.getValue(1));
        assertNumEquals(numOf(-2), indicator.getValue(2), 1e-12);
        assertNumEquals(numOf(3.24), indicator.getValue(3), 1e-12);
        assertNumEquals(numOf(-10), indicator.getValue(4));
        assertNumEquals(numOf(9.3), indicator.getValue(5), 1e-12);
        assertNumEquals(numOf(1), indicator.getValue(6), 1e-12);
    }

    @Test
    public void trimDecimalParameters() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 1.5, 2, 3, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        // Use constructor with threshold only (no previousIndicator) for proper
        // deserialization matching
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(base, 1.5);

        ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("percentageThreshold", "1.5");

        String json = indicator.toJson();
        ComponentDescriptor parsed = ComponentSerialization.parse(json);
        assertThat(parsed.getParameters()).containsEntry("percentageThreshold", "1.5");

        Indicator<?> reconstructed = Indicator.fromJson(series, json);
        assertThat(reconstructed).isInstanceOf(PercentageChangeIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(descriptor);
    }

    @Test
    public void transientHistoricalReadDoesNotAdvanceTheNotificationState() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 115, 120, 105, 110)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        AtomicBoolean sourceFailing = new AtomicBoolean(true);
        CachedIndicator<Num> flaky = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                if (index == 1 && sourceFailing.get()) {
                    markResultTransient();
                    return NaN.NaN;
                }
                return close.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(flaky, numOf(10));

        assertThat(indicator.getValue(4).isNaN()).isTrue();
        sourceFailing.set(false);

        assertThat(indicator.getValue(4).doubleValue()).isCloseTo(-4.3478, within(1e-3));
    }
}
