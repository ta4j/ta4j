package org.ta4j.core;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.setPrecision;
import static org.ta4j.core.num.NaN.NaN;


public class NumTest extends AbstractIndicatorTest {

    Num a;
    Num b;

    public NumTest(Function<Number, Num> numFunction) {
        super(numFunction);
        a = numOf(1.000001);
        b = numOf(0.000001);
    }

    @Test(expected = java.lang.AssertionError.class)
    public void failValueOf() {
        assertEquals(1.0, a.minus(b));
    }

    @Test
    public void testValueOfPass() {
        valueOf("0.0000000000000001");
    }

    @Test(expected = java.lang.AssertionError.class)
    public void testValueOfFail() {
        valueOf("0.00000000000000001");
    }

    public void valueOf(String precision) {
        setPrecision(precision);
        assertNumEquals(0.33333333333333333332, numOf(0.33333333333333333332));
        assertNumEquals(1, numOf(1d));
        assertNumEquals(2.54, numOf(new BigDecimal("2.54")));

        assertNumEquals(0.33, numOf(0.33));
        assertNumEquals(1, numOf(1));
        assertNumEquals(2.54, numOf(new BigDecimal(2.54)));

        assertEquals(numOf(1.0), a.minus(b));
        assertNumEquals(1.0, a.minus(b));
        assertTrue(a.minus(b).equals(numOf(1.0)));
        assertFalse(a.minus(b).equals(1.0));

        assertNumEquals(numOf(1.0), a.minus(b));
        assertNumEquals(BigDecimalNum.valueOf(1.0), a.minus(b));
        assertNumEquals(BigDecimalNum.valueOf("1.0"), a.minus(b));
        assertNumEquals(DoubleNum.valueOf(1.0), a.minus(b));

        assertTrue(a.minus(b).equals(BigDecimalNum.valueOf("1.0")));
        assertNumEquals(BigDecimalNum.valueOf("1.0"), a.minus(b));

        assertNumEquals(numOf(0.9999999999999999), a.minus(b));
        assertNumEquals(BigDecimalNum.valueOf(0.9999999999999999), a.minus(b));
        assertNumEquals(BigDecimalNum.valueOf("0.9999999999999999"), a.minus(b));
        assertNumEquals(DoubleNum.valueOf(0.9999999999999999), a.minus(b));

        assertTrue(a.minus(b).equals(DoubleNum.valueOf("1.0")));
        assertEquals(DoubleNum.valueOf("1.0"), a.minus(b));

        assertFalse(BigDecimalNum.valueOf(0.9999999999999999).equals(BigDecimalNum.valueOf(1)));
        Num first = DoubleNum.valueOf(0.9999999999999999);
        Num second = DoubleNum.valueOf(1);
        // inexact delta compare
        assertTrue(first.equals(second));
        // exact Double.compare()
        assertFalse(isEqual(first, second));
    }

    private boolean isEqual(Num first, Num second) {
        return (!first.isGreaterThan(second) && !first.isLessThan(second));
    }

    @Test
    public void testMultiplicationSymmetrically(){
        Num decimalFromString = numOf(new BigDecimal("0.33"));
        Num decimalFromDouble = numOf(45.33);
        assertEquals(decimalFromString.multipliedBy(decimalFromDouble), decimalFromDouble.multipliedBy(decimalFromString));

        Num doubleNumFromString = numOf(new BigDecimal("0.33"));
        Num doubleNumFromDouble = numOf(10.33);
        assertNumEquals(doubleNumFromString.multipliedBy(doubleNumFromDouble), doubleNumFromDouble.multipliedBy(doubleNumFromString));
    }

    @Test(expected = java.lang.ClassCastException.class)
    public void testFailDifferentNumsAdd(){
        Num a = BigDecimalNum.valueOf(12);
        Num b = DoubleNum.valueOf(12);
        a.plus(b);
    }

    @Test(expected = java.lang.ClassCastException.class)
    public void testFailDifferentNumsCompare(){
        Num a = BigDecimalNum.valueOf(12);
        Num b = DoubleNum.valueOf(13);
        a.isEqual(b);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFailNaNtoInt(){
        NaN.intValue();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFailNaNtoLong(){
        NaN.longValue();
    }


    @Test
    public void testNaN(){
        Num a = NaN;
        Num eleven = BigDecimalNum.valueOf(11);

        Num mustBeNaN = a.plus(eleven);
        assertNumEquals(mustBeNaN,NaN);

        mustBeNaN = a.minus(eleven);
        assertNumEquals(mustBeNaN,NaN);

        mustBeNaN = a.dividedBy(a);
        assertNumEquals(mustBeNaN,NaN);

        mustBeNaN = a.multipliedBy(NaN);
        assertNumEquals(mustBeNaN,NaN);

        mustBeNaN = a.max(eleven);
        assertNumEquals(mustBeNaN,NaN);

        mustBeNaN = eleven.min(a);
        assertNumEquals(mustBeNaN,NaN);

        mustBeNaN = a.pow(12);
        assertNumEquals(mustBeNaN,NaN);

        Double nanDouble = a.doubleValue();
        assertEquals(Double.NaN, nanDouble);

        Float nanFloat = a.floatValue();
        assertEquals(Float.NaN, nanFloat);

        assertTrue(NaN.equals(a)); // NaN == NaN -> true

    }

    @Test
    public void testArithmetic(){
        Num ten = numOf(10);
        Num million = numOf(1000000);
        assertNumEquals(10, ten);
        assertNumEquals("1000000.0", million);

        Num zero = ten.minus(ten);
        assertNumEquals(0, zero);

        Num hundred = ten.multipliedBy(ten);
        assertNumEquals(100, hundred);

        Num hundredMillion = hundred.multipliedBy(million);
        assertNumEquals(100000000,hundredMillion);

        assertNumEquals(hundredMillion.dividedBy(hundred),million);
        assertNumEquals(0,hundredMillion.remainder(hundred));

        Num five = ten.numOf(5); // generate new value with NumFunction
        Num zeroDotTwo = ten.numOf(0.2); // generate new value with NumFunction
        Num fiveHundred54 = ten.numOf(554); // generate new value with NumFunction
        assertNumEquals(0,hundredMillion.remainder(five));

        assertNumEquals(0.00032, zeroDotTwo.pow(5));
        assertNumEquals(554,fiveHundred54.max(five));
        assertNumEquals(5,fiveHundred54.min(five));
        assertTrue(fiveHundred54.isGreaterThan(five));
        assertFalse(five.isGreaterThan(five.function().apply(5)));
        assertFalse(five.isGreaterThanOrEqual(fiveHundred54));
        assertFalse(five.isGreaterThanOrEqual(five.function().apply(6)));
        assertTrue(five.isGreaterThanOrEqual(five.function().apply(5)));

        assertTrue(five.equals(five.function().apply(5)));
        assertTrue(five.equals(five.function().apply(5.0)));
        assertTrue(five.equals(five.function().apply((float)5)));
        assertTrue(five.equals(five.function().apply((short)5)));

        assertFalse(five.equals(five.function().apply(4.9)));
        assertFalse(five.equals(five.function().apply(6)));
        assertFalse(five.equals(five.function().apply((float)15)));
        assertFalse(five.equals(five.function().apply((short)45)));
    }

    //TODO: add precision tests for BigDecimalNum
}
