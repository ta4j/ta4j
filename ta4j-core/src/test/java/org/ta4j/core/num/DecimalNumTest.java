/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

public class DecimalNumTest {

    private static final int NUMBARS = 10000;

    // 120 digit precision
    private static final String SUPER_PRECISION_STRING = "1.234567890" + // 10
            "1234567890" + // 20
            "1234567890" + // 30
            "1234567890" + // 40
            "1234567890" + // 50
            "1234567890" + // 60
            "1234567890" + // 70
            "1234567890" + // 80
            "1234567890" + // 90
            "1234567890" + // 100
            "1234567890" + // 110
            "1234567890"; // 120
    // 120 digit precision
    private static final String SUPER_PRECISION_LARGE_STRING = "1234567890" + // 10
            "1234567890" + // 20
            "1234567890" + // 30
            "1234567890" + // 40
            "1234567890" + // 50
            "1234567890" + // 60
            "1234567890" + // 70
            "1234567890" + // 80
            "1234567890" + // 90
            "1234567890" + // 100
            "1234567890" + // 110
            "1234567890"; // 120
    private static final Num FIRST_SUPER_PRECISION_NUM = DecimalNum.valueOf(SUPER_PRECISION_STRING);

    // override the auto-precision based on length of SUPER_PRECISION_STRING by
    // passing a precision to valueOf()
    private final NumFactory superPrecisionFunc = DecimalNumFactory.getInstance(256);
    // auto-set precision based on length of SUPER_PRECISION_STRING (120)
    private final NumFactory precisionFunc = DecimalNumFactory.getInstance(120);
    private final NumFactory precision32Func = DecimalNumFactory.getInstance(32);
    private final NumFactory doubleFunc = DoubleNumFactory.getInstance();
    private final NumFactory lowPrecisionFunc = DecimalNumFactory.getInstance(3);

    private BarSeries superPrecisionSeries;
    private BarSeries precisionSeries;
    private BarSeries precision32Series;
    private BarSeries doubleSeries;
    private BarSeries lowPrecisionSeries;

    private Indicator<Num> superPrecisionIndicator;
    private Indicator<Num> precisionIndicator;
    private Indicator<Num> precision32Indicator;
    private Indicator<Num> doubleIndicator;
    private Indicator<Num> lowPrecisionIndicator;

    @Test(expected = ArithmeticException.class)
    public void testPowOverflowExponent() {
        final Num x = DecimalNum.valueOf("2");
        final Num n = DecimalNum.valueOf(SUPER_PRECISION_LARGE_STRING);
        assertNumEquals("1", x.pow(n));
    }

    @Test
    public void testPowLargeBase() {
        final Num x = DecimalNum.valueOf(SUPER_PRECISION_STRING);
        final Num n = DecimalNum.valueOf("512");
        final Num result = x.pow(n);
        assertNumEquals(DecimalNum.valueOf(
                "71724632698264595311439425390811606219342673848.4006139819755840062853530122432564321605895163413448768150879157699962725"),
                result);
        assertEquals(120, ((BigDecimal) result.getDelegate()).precision());
        assertEquals(120, ((DecimalNum) result).getMathContext().getPrecision());
    }

    @Test
    public void decimalNumTest() {
        init();
        test();
    }

    private void init() {
        final Duration timePeriod = Duration.ofDays(1);
        Instant endTime = Instant.now();
        final double[] deltas = { 20.8, 30.1, -15.3, 10.2, -16.7, -9.8 };
        Num superPrecisionNum = FIRST_SUPER_PRECISION_NUM;
        this.superPrecisionSeries = new BaseBarSeriesBuilder().withName("superPrecision")
                .withNumFactory(this.superPrecisionFunc)
                .build();
        this.precisionSeries = new BaseBarSeriesBuilder().withName("precision")
                .withNumFactory(this.precisionFunc)
                .build();
        this.precision32Series = new BaseBarSeriesBuilder().withName("precision32")
                .withNumFactory(this.precision32Func)
                .build();
        this.doubleSeries = new BaseBarSeriesBuilder().withName("double").withNumFactory(this.doubleFunc).build();
        this.lowPrecisionSeries = new BaseBarSeriesBuilder().withName("lowPrecision")
                .withNumFactory(this.lowPrecisionFunc)
                .build();

        for (int i = 0; i < NUMBARS; i++) {
            this.superPrecisionSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(superPrecisionNum.toString())
                    .closePrice(superPrecisionNum.toString())
                    .highPrice(superPrecisionNum.toString())
                    .lowPrice(superPrecisionNum.toString())
                    .volume("0")
                    .amount("0")
                    .trades("0")
                    .add();
            this.precisionSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(superPrecisionNum.toString())
                    .closePrice(superPrecisionNum.toString())
                    .highPrice(superPrecisionNum.toString())
                    .lowPrice(superPrecisionNum.toString())
                    .amount("0")
                    .volume("0")
                    .trades("0")
                    .add();
            this.precision32Series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(superPrecisionNum.toString())
                    .closePrice(superPrecisionNum.toString())
                    .highPrice(superPrecisionNum.toString())
                    .lowPrice(superPrecisionNum.toString())
                    .amount("0")
                    .volume("0")
                    .trades("0")
                    .add();
            this.doubleSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(superPrecisionNum.toString())
                    .closePrice(superPrecisionNum.toString())
                    .highPrice(superPrecisionNum.toString())
                    .lowPrice(superPrecisionNum.toString())
                    .amount("0")
                    .volume("0")
                    .trades("0")
                    .add();
            this.lowPrecisionSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(superPrecisionNum.toString())
                    .closePrice(superPrecisionNum.toString())
                    .highPrice(superPrecisionNum.toString())
                    .lowPrice(superPrecisionNum.toString())
                    .amount("0")
                    .volume("0")
                    .trades("0")
                    .add();
            endTime = endTime.plus(timePeriod);
            superPrecisionNum = superPrecisionNum.plus(DecimalNum.valueOf(deltas[i % 6]));
        }

    }

    public void test() {
        final Num num = this.superPrecisionFunc.numOf(new BigDecimal(SUPER_PRECISION_STRING));
        // get the max precision from the MathContext
        assertEquals(256, ((DecimalNum) num).getMathContext().getPrecision());
        // get the auto precision from the delegate
        assertEquals(120, ((BigDecimal) num.getDelegate()).precision());

        assertEquals(120, ((BigDecimal) FIRST_SUPER_PRECISION_NUM.getDelegate()).precision());
        assertEquals(120, (((DecimalNum) FIRST_SUPER_PRECISION_NUM).getMathContext().getPrecision()));
        assertEquals(120, ((BigDecimal) this.superPrecisionSeries.getBar(0).getClosePrice().getDelegate()).precision());
        assertEquals(120, ((BigDecimal) this.precisionSeries.getBar(0).getClosePrice().getDelegate()).precision());
        assertEquals(17, (new BigDecimal(this.doubleSeries.getBar(0).getClosePrice().toString())).precision());
        assertEquals(3, (new BigDecimal(this.lowPrecisionSeries.getBar(0).getClosePrice().toString())).precision());

        assertNumEquals(DecimalNum.valueOf(
                "1.23456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"),
                this.superPrecisionSeries.getBar(0).getClosePrice());
        assertNumEquals(DecimalNum.valueOf(
                "1.23456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"),
                this.precisionSeries.getBar(0).getClosePrice());
        assertNumEquals(DecimalNum.valueOf("1.2345678901234567890123456789012"),
                this.precision32Series.getBar(0).getClosePrice());
        assertNumEquals(DecimalNum.valueOf("1.2345678901234567"),
                DecimalNum.valueOf(this.doubleSeries.getBar(0).getClosePrice().toString()));
        assertNumEquals(DecimalNum.valueOf("1.23"), this.lowPrecisionSeries.getBar(0).getClosePrice());

        final Indicator<Num> superPrecisionClose = new ClosePriceIndicator(this.superPrecisionSeries);
        final Indicator<Num> precisionClose = new ClosePriceIndicator(this.precisionSeries);
        final Indicator<Num> precision32Close = new ClosePriceIndicator(this.precision32Series);
        final Indicator<Num> doubleClose = new ClosePriceIndicator(this.doubleSeries);
        final Indicator<Num> lowPrecisionClose = new ClosePriceIndicator(this.lowPrecisionSeries);

        this.superPrecisionIndicator = new RSIIndicator(superPrecisionClose, 200);
        this.precisionIndicator = new RSIIndicator(precisionClose, 200);
        this.precision32Indicator = new RSIIndicator(precision32Close, 200);
        this.doubleIndicator = new RSIIndicator(doubleClose, 200);
        this.lowPrecisionIndicator = new RSIIndicator(lowPrecisionClose, 200);

        calculateSuperPrecision();
        calculatePrecision();
        calculatePrecision32();
        calculateDouble();
        calculateLowPrecision();

        // accuracies relative to SuperPrecision
        assertIndicatorEqualsAfterUnstablePeriod(this.superPrecisionIndicator, this.precisionIndicator,
                DecimalNum.valueOf(
                        "0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"));
        assertIndicatorNotEquals(this.superPrecisionIndicator, this.precisionIndicator, DecimalNum.valueOf(
                "0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"));
        assertIndicatorEqualsAfterUnstablePeriod(this.superPrecisionIndicator, this.precision32Indicator,
                DecimalNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorNotEquals(this.superPrecisionIndicator, this.precision32Indicator,
                DecimalNum.valueOf("0.00000000000000000000000000001"));
        assertIndicatorEqualsAfterUnstablePeriod(this.superPrecisionIndicator, this.doubleIndicator,
                DecimalNum.valueOf("0.000000000001"));
        assertIndicatorNotEquals(this.superPrecisionIndicator, this.doubleIndicator,
                DecimalNum.valueOf("0.0000000000001"));
        assertIndicatorEqualsAfterUnstablePeriod(this.superPrecisionIndicator, this.lowPrecisionIndicator,
                DecimalNum.valueOf("4"));
        assertIndicatorNotEquals(this.superPrecisionIndicator, this.lowPrecisionIndicator, DecimalNum.valueOf("3.6"));
        // accuracies relative to Precision
        assertIndicatorEqualsAfterUnstablePeriod(this.precisionIndicator, this.precision32Indicator,
                DecimalNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorNotEquals(this.precisionIndicator, this.precision32Indicator,
                DecimalNum.valueOf("0.00000000000000000000000000001"));
        assertIndicatorEqualsAfterUnstablePeriod(this.precisionIndicator, this.doubleIndicator,
                DecimalNum.valueOf("0.000000000001"));
        assertIndicatorNotEquals(this.precisionIndicator, this.doubleIndicator, DecimalNum.valueOf("0.0000000000001"));
        assertIndicatorEqualsAfterUnstablePeriod(this.precisionIndicator, this.lowPrecisionIndicator,
                DecimalNum.valueOf("4"));
        assertIndicatorNotEquals(this.precisionIndicator, this.lowPrecisionIndicator, DecimalNum.valueOf("3.6"));
        // accuracies relative to Precision32
        assertIndicatorEqualsAfterUnstablePeriod(this.precision32Indicator, this.doubleIndicator,
                DecimalNum.valueOf("0.000000000001"));
        assertIndicatorNotEquals(this.precision32Indicator, this.doubleIndicator,
                DecimalNum.valueOf("0.0000000000001"));
        assertIndicatorEqualsAfterUnstablePeriod(this.precision32Indicator, this.lowPrecisionIndicator,
                DecimalNum.valueOf("4"));
        assertIndicatorNotEquals(this.precision32Indicator, this.lowPrecisionIndicator, DecimalNum.valueOf("3.6"));
        // accuracies relative to Double
        assertIndicatorEqualsAfterUnstablePeriod(this.doubleIndicator, this.lowPrecisionIndicator,
                DecimalNum.valueOf("4"));
        assertIndicatorNotEquals(this.doubleIndicator, this.lowPrecisionIndicator, DecimalNum.valueOf("3.6"));

        // This helps for doing a memory snapshot
        // Thread.sleep(1000000);
    }

    // use separate methods for each of these for memory/CPU profiling

    private void calculateSuperPrecision() {
        final Indicator<Num> indicator = this.superPrecisionIndicator;
        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    private void calculatePrecision() {
        final Indicator<Num> indicator = this.precisionIndicator;
        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    private void calculatePrecision32() {
        final Indicator<Num> indicator = this.precision32Indicator;
        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    private void calculateDouble() {
        final Indicator<Num> indicator = this.doubleIndicator;
        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    private void calculateLowPrecision() {
        final Indicator<Num> indicator = this.lowPrecisionIndicator;
        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    private void assertIndicatorEqualsAfterUnstablePeriod(Indicator<Num> expected, Indicator<Num> actual, Num delta) {
        int startIndex = Math.max(expected.getBarSeries().getBeginIndex(),
                Math.max(actual.getBarSeries().getBeginIndex(),
                        Math.max(expected.getCountOfUnstableBars(), actual.getCountOfUnstableBars())));
        int endIndex = Math.min(expected.getBarSeries().getEndIndex(), actual.getBarSeries().getEndIndex());

        for (int i = startIndex; i <= endIndex; i++) {
            Num expectedValue = expected.getValue(i);
            Num actualValue = actual.getValue(i);

            if (expectedValue.isNaN() || actualValue.isNaN()) {
                if (!(expectedValue.isNaN() && actualValue.isNaN())) {
                    throw new AssertionError(String.format("Failed at index %s: expected %s but actual was %s", i,
                            expectedValue, actualValue));
                }
                continue;
            }

            Num exp = DecimalNum.valueOf(expectedValue.toString());
            Num act = DecimalNum.valueOf(actualValue.toString());
            Num difference = exp.minus(act).abs();
            if (difference.isGreaterThan(delta)) {
                throw new AssertionError(
                        String.format("Failed at index %s: expected %s but actual was %s", i, exp, act));
            }
        }
    }

    @Test(expected = NumberFormatException.class)
    public void testValueOfForFloatNaNShouldThrowNumberFormatException() {
        DecimalNum.valueOf(Float.NaN);
    }

    @Test(expected = NumberFormatException.class)
    public void testValueOfForDoubleNaNShouldThrowNumberFormatException() {
        DecimalNum.valueOf(Double.NaN);
    }

    @Test
    public void testEqualsDecimalNumWithDoubleNum() {
        final DoubleNum doubleNum = DoubleNum.valueOf(3.0);
        final DecimalNum decimalNum = DecimalNum.valueOf(3.0);

        assertNotEquals(decimalNum, doubleNum);
    }

    @Test
    public void testZeroEquals() {
        final Num num1 = DecimalNum.valueOf(-0.0);
        final Num num2 = DecimalNum.valueOf(0.0);

        assertTrue(num1.isEqual(num2));
    }

    @Test
    public void testExpZero() {
        final Num zero = DecimalNum.valueOf(0);
        final Num result = zero.exp();
        assertNumEquals(1, result);
    }

    @Test
    public void testExpOne() {
        final Num one = DecimalNum.valueOf(1);
        final Num result = one.exp();
        // e ≈ 2.718281828459046 (with default precision rounding)
        assertNumEquals("2.718281828459046", result);
    }

    @Test
    public void testExpNegativeOne() {
        final Num negOne = DecimalNum.valueOf(-1);
        final Num result = negOne.exp();
        // e^-1 ≈ 0.3678794411714424 (with default precision rounding)
        assertNumEquals("0.3678794411714424", result);
    }

    @Test
    public void testExpTwo() {
        final Num two = DecimalNum.valueOf(2);
        final Num result = two.exp();
        // e^2 ≈ 7.389056098930649 (with default precision rounding)
        assertNumEquals("7.389056098930649", result);
    }

    @Test
    public void testExpHighPrecision() {
        final NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(40);
        final Num one = highPrecisionFactory.one();
        final Num result = one.exp();
        // e with 40 digits precision
        assertNumEquals("2.718281828459045235360287471352662497761", result);
    }

    @Test
    public void testExpSmallValue() {
        final Num small = DecimalNum.valueOf("0.1");
        final Num result = small.exp();
        // e^0.1 ≈ 1.1051709180756477
        assertNumEquals("1.1051709180756477", result);
    }

    @Test
    public void testExpLargeValue() {
        final Num large = DecimalNum.valueOf(10);
        final Num result = large.exp();
        // e^10 ≈ 22026.46579480673 (with default precision rounding)
        assertNumEquals("22026.46579480673", result);
    }

}
