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

public class TestUtilsTest extends AbstractIndicatorTest {

    public TestUtilsTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testStringNum() {
        // exact matches only
        assertNumNotEquals("0.1234", numOf(0.123456789));
        assertNumNotEquals("0.123", numOf(0.123456789));
        assertNumNotEquals("0.123456789", numOf(0.1234));
        assertNumNotEquals("0.123456789", numOf(0.123));
        assertNumEquals("0.123456789", numOf(0.123456789));
        // fails for BigDecimalNum only
        //assertNumEquals("0.12345678", numOf(0.123456789));
        // fails for DoubleNum only
        //assertNumNotEquals("0.12345", numOf(0.123456789));
        assertNumNotEquals("0.12345", numOf(0.12344));
        assertNumNotEquals("0.12345", numOf(0.123));
        assertNumNotEquals("0.12344", numOf(0.12345));
        assertNumNotEquals("0.123", numOf(0.12345));
    }

    @Test
    public void testDoubleNum() {
        // inexact matches allowed
        assertNumEquals(0.1234, numOf(0.123456789)); // different from testStringNum!
        assertNumNotEquals(0.123, numOf(0.123456789));
        assertNumEquals(0.123456789, numOf(0.1234)); // different from testStringNum!
        assertNumNotEquals(0.123456789, numOf(0.123));
        assertNumEquals(0.123456789, numOf(0.123456789));
        assertNumEquals(0.12345678, numOf(0.123456789)); // different from testStringNum!
        assertNumEquals(0.12345, numOf(0.123456789)); // different from testStringNum!
        assertNumEquals(0.12345, numOf(0.12344)); // different from testStringNum!
        assertNumNotEquals(0.12345, numOf(0.123));
        assertNumEquals(0.12344, numOf(0.12345)); // different from testStringNum!
        assertNumNotEquals(0.123, numOf(0.12345));
    }

    @Test
    public void testNumNum() {
        // exact matches only (identical results to testStringNum)
        assertNumNotEquals(numOf(0.1234), numOf(0.123456789));
        assertNumNotEquals(numOf(0.123), numOf(0.123456789));
        assertNumNotEquals(numOf(0.123456789), numOf(0.1234));
        assertNumNotEquals(numOf(0.123456789), numOf(0.123));
        assertNumEquals(numOf(0.123456789), numOf(0.123456789));
        // fails for BigDecimalNum only
        //assertNumEquals(numOf(0.12345678), numOf(0.123456789));
        // fails for DoubleNum only
        //assertNumNotEquals(numOf(0.12345), numOf(0.123456789));
        assertNumNotEquals(numOf(0.12345), numOf(0.12344));
        assertNumNotEquals(numOf(0.12345), numOf(0.123));
        assertNumNotEquals(numOf(0.12344), numOf(0.12345));
        assertNumNotEquals(numOf(0.123), numOf(0.12345));
    }

    @Test
    public void testIndicatorIndicator() {
        // inexact matches allowed
        assertIndicatorEquals( // different from testStringNum!
                new MockIndicator(numFunction, numOf(0.1234)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.123)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals( // different from testStringNum!
                new MockIndicator(numFunction, numOf(0.123456789)),
                new MockIndicator(numFunction, numOf(0.1234)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.123456789)),
                new MockIndicator(numFunction, numOf(0.123)));
        assertIndicatorEquals(
                new MockIndicator(numFunction, numOf(0.123456789)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals( // different from testStringNum!
                new MockIndicator(numFunction, numOf(0.12345678)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals( // different from testStringNum!
                new MockIndicator(numFunction, numOf(0.12345)),
                new MockIndicator(numFunction, numOf(0.123456789)));
        assertIndicatorEquals( // different from testStringNum!
                new MockIndicator(numFunction, numOf(0.12345)),
                new MockIndicator(numFunction, numOf(0.12344)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.12345)),
                new MockIndicator(numFunction, numOf(0.123)));
        assertIndicatorEquals( // different from testStringNum!
                new MockIndicator(numFunction, numOf(0.12344)),
                new MockIndicator(numFunction, numOf(0.12345)));
        assertIndicatorNotEquals(
                new MockIndicator(numFunction, numOf(0.123)),
                new MockIndicator(numFunction, numOf(0.12345)));
    }
}
