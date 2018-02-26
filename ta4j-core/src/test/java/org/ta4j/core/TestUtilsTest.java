package org.ta4j.core;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumNotEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;
import static org.ta4j.core.TestUtils.setPrecision;

public class TestUtilsTest extends AbstractIndicatorTest {

    public TestUtilsTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testStringNumPass() {
        stringNum("0.0001");
    }

    @Test(expected = java.lang.AssertionError.class)
    public void testStringNumFail() {
        stringNum("0.00001");
    }

    public void stringNum(String precision) {
        setPrecision(precision);
        assertNumEquals("0.1234", numOf(0.123456789));
        assertNumNotEquals("0.123", numOf(0.123456789));
        assertNumEquals("0.123456789", numOf(0.1234));
        assertNumNotEquals("0.123456789", numOf(0.123));
        assertNumEquals("0.123456789", numOf(0.123456789));
        assertNumEquals("0.12345678", numOf(0.123456789));
        assertNumEquals("0.12345", numOf(0.123456789));
        assertNumEquals("0.12345", numOf(0.12344));
        assertNumNotEquals("0.12345", numOf(0.123));
        assertNumEquals("0.12344", numOf(0.12345));
        assertNumNotEquals("0.123", numOf(0.12345));
    }

    @Test
    public void testDoubleNumPass() {
        doubleNum("0.0001");
    }

    @Test(expected = java.lang.AssertionError.class)
    public void testDoubleNumFail() {
        doubleNum("0.00001");
    }

    public void doubleNum(String precision) {
        setPrecision(precision);
        assertNumEquals(0.1234, numOf(0.123456789));
        assertNumNotEquals(0.123, numOf(0.123456789));
        assertNumEquals(0.123456789, numOf(0.1234));
        assertNumNotEquals(0.123456789, numOf(0.123));
        assertNumEquals(0.123456789, numOf(0.123456789));
        assertNumEquals(0.12345678, numOf(0.123456789));
        assertNumEquals(0.12345, numOf(0.123456789));
        assertNumEquals(0.12345, numOf(0.12344));
        assertNumNotEquals(0.12345, numOf(0.123));
        assertNumEquals(0.12344, numOf(0.12345));
        assertNumNotEquals(0.123, numOf(0.12345));
    }

    @Test
    public void testNumNumPass() {
        numNum("0.0001");
    }

    @Test(expected = java.lang.AssertionError.class)
    public void testNumNumFail() {
        numNum("0.00001");
    }

    public void numNum(String precision) {
        setPrecision(precision);
        assertNumEquals(numOf(0.1234), numOf(0.123456789));
        assertNumNotEquals(numOf(0.123), numOf(0.123456789));
        assertNumEquals(numOf(0.123456789), numOf(0.1234));
        assertNumNotEquals(numOf(0.123456789), numOf(0.123));
        assertNumEquals(numOf(0.123456789), numOf(0.123456789));
        assertNumEquals(numOf(0.12345678), numOf(0.123456789));
        assertNumEquals(numOf(0.12345), numOf(0.123456789));
        assertNumEquals(numOf(0.12345), numOf(0.12344));
        assertNumNotEquals(numOf(0.12345), numOf(0.123));
        assertNumEquals(numOf(0.12344), numOf(0.12345));
        assertNumNotEquals(numOf(0.123), numOf(0.12345));
    }

    @Test
    public void testIndicatorIndicatorPass() {
        indicatorIndicator("0.0001");
    }

    @Test(expected = java.lang.AssertionError.class)
    public void testIndicatorIndicatorFail() {
        indicatorIndicator("0.00001");
    }

    public void indicatorIndicator(String precision) {
        setPrecision(precision);
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.1234)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.123)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.123456789)),
                new MockIndicator(numFunction, numOf(0.1234)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.123456789)),
                new MockIndicator(numFunction, numOf(0.123)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.123456789)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.12345678)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.12345)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.12345)),
                new MockIndicator(numFunction, numOf(0.12344)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.12345)),
                new MockIndicator(numFunction, numOf(0.123)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.12344)),
                new MockIndicator(numFunction, numOf(0.12345)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.123)),
                new MockIndicator(numFunction, numOf(0.12345)));
    }
}
