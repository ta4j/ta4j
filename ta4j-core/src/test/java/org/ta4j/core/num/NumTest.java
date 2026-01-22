/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static java.math.RoundingMode.HALF_UP;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumNotEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;

public class NumTest extends AbstractIndicatorTest<Object, Num> {

    public static final int HIGH_PRECISION = 128;
    private static final MathContext HIGH_PRECISION_CONTEXT = new MathContext(HIGH_PRECISION, HALF_UP);

    public NumTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testZero() {
        final Num anyNaNNum = NaN;
        final Num anyDecimalNum = DecimalNum.valueOf(3);
        final Num anyDoubleNum = DoubleNum.valueOf(3);
        assertNumEquals(NaN, anyNaNNum.getNumFactory().zero());
        assertNumEquals(0, numOf(3).getNumFactory().zero());
        assertNumEquals(0, anyDecimalNum.getNumFactory().zero());
        assertNumEquals(0, anyDoubleNum.getNumFactory().zero());
    }

    @Test
    public void testOne() {
        final Num anyNaNNum = NaN;
        final Num anyDecimalNum = DecimalNum.valueOf(3);
        final Num anyDoubleNum = DoubleNum.valueOf(3);
        assertNumEquals(NaN, anyNaNNum.getNumFactory().one());
        assertNumEquals(1, numOf(3).getNumFactory().one());
        assertNumEquals(1, anyDecimalNum.getNumFactory().one());
        assertNumEquals(1, anyDoubleNum.getNumFactory().one());
    }

    @Test
    public void testHundred() {
        final Num anyNaNNum = NaN;
        final Num anyDecimalNum = DecimalNum.valueOf(3);
        final Num anyDoubleNum = DoubleNum.valueOf(3);
        assertNumEquals(NaN, anyNaNNum.getNumFactory().hundred());
        assertNumEquals(100, numOf(3).getNumFactory().hundred());
        assertNumEquals(100, anyDecimalNum.getNumFactory().hundred());
        assertNumEquals(100, anyDoubleNum.getNumFactory().hundred());
    }

    @Test(expected = AssertionError.class)
    public void testStringNumFail() {
        assertNumEquals("1.234", numOf(4.321));
    }

    @Test
    public void testStringNumPass() {
        assertNumEquals("1.234", numOf(1.234));
    }

    @Test
    public void testDecimalNumPrecision() {
        final String highPrecisionString = "1.928749238479283749238472398472936872364823749823749238749238749283749238472983749238749832749274";
        final Num num = numOf(highPrecisionString, HIGH_PRECISION);
        final Num highPrecisionNum = DecimalNum.valueOf(highPrecisionString, HIGH_PRECISION_CONTEXT);
        assertTrue(((DecimalNum) highPrecisionNum).matches(num, 17));
        final BigDecimal fromNum = new BigDecimal(num.toString());
        if (num.getClass().equals(DoubleNum.class)) {
            assertEquals(17, fromNum.precision());
            assertTrue(((DecimalNum) highPrecisionNum).matches(num, 17));
            assertFalse(((DecimalNum) highPrecisionNum).matches(num, 18));
        }
        if (num.getClass().equals(DecimalNum.class)) {
            assertEquals(97, fromNum.precision());
            // since precisions are the same, will match to any precision
            assertTrue(((DecimalNum) highPrecisionNum).matches(num, 10000));
        }
    }

    private Num numOf(final String string, final int precision) {
        return DecimalNumFactory.getInstance(precision).numOf(string);
    }

    @Test
    public void testValueOf() {
        assertNumEquals(0.33333333333333333332, numOf(0.33333333333333333332));
        assertNumEquals(1, numOf(1d));
        assertNumEquals(2.54, numOf(new BigDecimal("2.54")));

        assertNumEquals(0.33, numOf(0.33));
        assertNumEquals(1, numOf(1));
        assertNumEquals(2.54, numOf(new BigDecimal("2.54")));
    }

    @Test
    public void testMultiplicationSymmetrically() {
        final Num decimalFromString = numOf(new BigDecimal("0.33"));
        final Num decimalFromDouble = numOf(45.33);
        assertEquals(decimalFromString.multipliedBy(decimalFromDouble),
                decimalFromDouble.multipliedBy(decimalFromString));

        final Num doubleNumFromString = numOf(new BigDecimal("0.33"));
        final Num doubleNumFromDouble = numOf(10.33);
        assertNumEquals(doubleNumFromString.multipliedBy(doubleNumFromDouble),
                doubleNumFromDouble.multipliedBy(doubleNumFromString));
    }

    @Test(expected = java.lang.ClassCastException.class)
    public void testFailDifferentNumsAdd() {
        final Num a = DecimalNum.valueOf(12);
        final Num b = DoubleNum.valueOf(12);
        a.plus(b);
    }

    @Test(expected = java.lang.ClassCastException.class)
    public void testFailDifferentNumsCompare() {
        final Num a = DecimalNum.valueOf(12);
        final Num b = DoubleNum.valueOf(13);
        a.isEqual(b);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFailNaNtoInt() {
        NaN.intValue();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFailNaNtoLong() {
        NaN.longValue();
    }

    @Test
    public void testNaN() {
        final Num a = NaN;
        final Num eleven = DecimalNum.valueOf(11);

        Num mustBeNaN = a.plus(eleven);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = a.minus(eleven);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = a.dividedBy(a);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = a.multipliedBy(NaN);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = a.max(eleven);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = eleven.min(a);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = a.pow(12);
        assertNumEquals(mustBeNaN, NaN);

        mustBeNaN = a.pow(a);
        assertNumEquals(mustBeNaN, NaN);

        final Double nanDouble = a.doubleValue();
        assertEquals(Double.NaN, nanDouble);

        final Float nanFloat = a.floatValue();
        assertEquals(Float.NaN, nanFloat);

        Assert.assertEquals(NaN, a); // NaN == NaN -> true

    }

    @Test
    public void testArithmetic() {
        final Num ten = numOf(10);
        final Num million = numOf(1000000);
        assertNumEquals(10, ten);
        assertNumEquals("1000000.0", million);

        final Num zero = ten.minus(ten);
        assertNumEquals(0, zero);

        final Num hundred = ten.multipliedBy(ten);
        assertNumEquals(100, hundred);

        final Num hundredMillion = hundred.multipliedBy(million);
        assertNumEquals(100000000, hundredMillion);

        assertNumEquals(hundredMillion.dividedBy(hundred), million);
        assertNumEquals(0, hundredMillion.remainder(hundred));

        final Num five = ten.getNumFactory().numOf(5); // generate new value with NumFunction
        final Num zeroDotTwo = ten.getNumFactory().numOf(0.2); // generate new value with NumFunction
        final Num fiveHundred54 = ten.getNumFactory().numOf(554); // generate new value with NumFunction
        assertNumEquals(0, hundredMillion.remainder(five));

        assertNumEquals(0.00032, zeroDotTwo.pow(5));
        assertNumEquals(0.7247796636776955, zeroDotTwo.pow(zeroDotTwo));
        assertNumEquals(1.37972966146, zeroDotTwo.pow(numOf(-0.2)));
        assertNumEquals(554, fiveHundred54.max(five));
        assertNumEquals(5, fiveHundred54.min(five));
        assertTrue(fiveHundred54.isGreaterThan(five));
        assertFalse(five.isGreaterThan(five.getNumFactory().numOf(5)));
        assertFalse(five.isGreaterThanOrEqual(fiveHundred54));
        assertFalse(five.isGreaterThanOrEqual(five.getNumFactory().numOf(6)));
        assertTrue(five.isGreaterThanOrEqual(five.getNumFactory().numOf(5)));

        assertEquals(five, five.getNumFactory().numOf(5));
        assertEquals(five, five.getNumFactory().numOf(5.0));
        assertEquals(five, five.getNumFactory().numOf((float) 5));
        assertEquals(five, five.getNumFactory().numOf((short) 5));

        assertNotEquals(five, five.getNumFactory().numOf(4.9));
        assertNotEquals(five, five.getNumFactory().numOf(6));
        assertNotEquals(five, five.getNumFactory().numOf((float) 15));
        assertNotEquals(five, five.getNumFactory().numOf((short) 45));
    }

    @Test
    public void sqrtOfBigInteger() {
        final String sqrtOfTwo = "1.4142135623730950488016887242096980785696718753769480731"
                + "766797379907324784621070388503875343276415727350138462309122970249248360"
                + "558507372126441214970999358314132226659275055927557999505011527820605715";

        assertNumEquals(sqrtOfTwo, this.numFactory.numOf(2).sqrt(new MathContext(200)));
    }

    @Test
    public void sqrtOfBigDouble() {
        final String sqrtOfOnePointTwo = "1.095445115010332226913939565601604267905489389995966508453788899464986554245445467601716872327741252";

        assertNumEquals(sqrtOfOnePointTwo, this.numFactory.numOf(1.2).sqrt(new MathContext(100)));
    }

    @Test
    public void sqrtOfNegativeDouble() {
        assertTrue(numOf(-1.2).sqrt(new MathContext(12)).isNaN());
        assertTrue(numOf(-1.2).sqrt().isNaN());
    }

    @Test
    public void sqrtOfZero() {
        assertNumEquals(0, numOf(0).sqrt(new MathContext(12)));
        assertNumEquals(0, numOf(0).sqrt());
    }

    @Test
    public void sqrtLudicrousPrecision() {
        final BigDecimal numBD = BigDecimal.valueOf(Double.MAX_VALUE)
                .multiply(BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.ONE));

        if (this.numFactory instanceof DoubleNumFactory) {
            final var sqrt = DoubleNum.valueOf(numBD).sqrt(new MathContext(100000));
            assertThat(sqrt.toString()).isEqualTo("Infinity");
        } else if (this.numFactory instanceof DecimalNumFactory) {
            final var sqrt = DecimalNum.valueOf(numBD, new MathContext(100000)).sqrt(new MathContext(100000));
            final var props = new Properties();

            try (final var is = getClass().getResourceAsStream("numTest.properties")) {
                props.load(is);
                assertNumEquals(props.getProperty("sqrtCorrect100000"), sqrt);
                assertNumNotEquals(props.getProperty("sqrtCorrect99999"), sqrt);
                assertNumEquals(Double.MAX_VALUE, sqrt);
                assertNumNotEquals(numOf(Double.MAX_VALUE), sqrt);
                final BigDecimal sqrtBD = new BigDecimal(sqrt.toString());
                assertNumEquals(numOf(numBD), numOf(sqrtBD.multiply(sqrtBD, new MathContext(99999, HALF_UP))));
                assertNumNotEquals(numOf(numBD), sqrt.multipliedBy(sqrt));
            } catch (final IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    @Test
    public void sqrtOddExponent() {
        final BigDecimal numBD = BigDecimal.valueOf(Double.valueOf("3E11"));
        final Num sqrt = numOf(numBD).sqrt();
        assertNumEquals("547722.55750516611345696978280080", sqrt);
    }

    @Test
    public void testSerialization() throws Exception {
        final Num numVal = this.numFactory.numOf(1.3);
        serializeDeserialize(numVal);
    }

    private static void serializeDeserialize(final Num o) throws IOException, ClassNotFoundException {
        final byte[] array;
        try (final var baos = new ByteArrayOutputStream()) {
            try (final var out = new ObjectOutputStream(baos)) {
                out.writeObject(o);
                array = baos.toByteArray();
            }

        }
        try (final var baos = new ByteArrayInputStream(array)) {
            try (final var out = new ObjectInputStream(baos)) {
                final var deserialized = (Num) out.readObject();
                assertNotSame(o, deserialized);
                assertEquals(deserialized.doubleValue(), o.doubleValue());
            }

        }
    }

    @Test
    public void testIsNaNOrNull() {
        // Test null value
        assertTrue("null value should return true", Num.isNaNOrNull(null));

        // Test NaN instance
        assertTrue("NaN instance should return true", Num.isNaNOrNull(NaN));

        // Test DoubleNum with Double.NaN (edge case - DoubleNum doesn't override
        // isNaN())
        // This should work regardless of which factory is being used for the test
        final Num doubleNaN = DoubleNum.valueOf(Double.NaN);
        assertTrue("DoubleNum with Double.NaN should return true", Num.isNaNOrNull(doubleNaN));

        // Test valid DecimalNum values
        final Num validDecimal = DecimalNum.valueOf(42.5);
        assertFalse("Valid DecimalNum should return false", Num.isNaNOrNull(validDecimal));

        // Test valid DoubleNum values
        final Num validDouble = DoubleNum.valueOf(42.5);
        assertFalse("Valid DoubleNum should return false", Num.isNaNOrNull(validDouble));

        // Test zero values
        final Num zero = numOf(0);
        assertFalse("Zero value should return false", Num.isNaNOrNull(zero));

        // Test negative values
        final Num negative = numOf(-10.5);
        assertFalse("Negative value should return false", Num.isNaNOrNull(negative));

        // Test positive values
        final Num positive = numOf(100.25);
        assertFalse("Positive value should return false", Num.isNaNOrNull(positive));

        // Test very small values
        final Num small = numOf(0.0001);
        assertFalse("Small value should return false", Num.isNaNOrNull(small));

        // Test very large values
        final Num large = numOf(1e10);
        assertFalse("Large value should return false", Num.isNaNOrNull(large));

        // Test infinity (if supported by the factory)
        if (this.numFactory instanceof DoubleNumFactory) {
            final Num positiveInfinity = DoubleNum.valueOf(Double.POSITIVE_INFINITY);
            // Double.POSITIVE_INFINITY is not NaN, so should return false
            assertFalse("Positive infinity should return false", Num.isNaNOrNull(positiveInfinity));

            final Num negativeInfinity = DoubleNum.valueOf(Double.NEGATIVE_INFINITY);
            // Double.NEGATIVE_INFINITY is not NaN, so should return false
            assertFalse("Negative infinity should return false", Num.isNaNOrNull(negativeInfinity));
        }
    }

}
