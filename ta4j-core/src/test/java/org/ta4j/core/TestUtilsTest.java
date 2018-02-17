package org.ta4j.core;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumNotEquals;

public class TestUtilsTest extends AbstractIndicatorTest {

    public TestUtilsTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testStringNum() {
        // exact matches only
        assertNumNotEquals("3.1415", numOf(3.14159265358979));
        assertNumNotEquals("3.141", numOf(3.14159265358979));
        assertNumNotEquals("3.14159265358979", numOf(3.1415));
        assertNumNotEquals("3.14159265358979", numOf(3.141));
        assertNumEquals("3.14159265358979", numOf(3.14159265358979));
        // fails for BigDecimalNum only
        //assertNumEquals("3.1415926535897", numOf(3.14159265358979));
        // fails for DoubleNum only
        //assertNumNotEquals("3.14159", numOf(3.14159265358979));
        assertNumNotEquals("3.14159", numOf(3.14158));
        assertNumNotEquals("3.14159", numOf(3.141));
        assertNumNotEquals("3.14158", numOf(3.14159));
        assertNumNotEquals("3.141", numOf(3.14159));
    }

    @Test
    public void testDoubleNum() {
        // inexact matches allowed
        assertNumEquals(3.1415, numOf(3.14159265358979)); // different from testStringNum!
        assertNumNotEquals(3.141, numOf(3.14159265358979));
        assertNumEquals(3.14159265358979, numOf(3.1415)); // different from testStringNum!
        assertNumNotEquals(3.14159265358979, numOf(3.141));
        assertNumEquals(3.14159265358979, numOf(3.14159265358979));
        // fails for BigDecimalNum only
        //assertNumEquals("3.1415926535897", numOf(3.14159265358979));
        // fails for DoubleNum only
        //assertNumNotEquals("3.14159", numOf(3.14159265358979));
        assertNumEquals(3.14159, numOf(3.14158)); // different from testStringNum!
        assertNumNotEquals(3.14159, numOf(3.141));
        assertNumEquals(3.14158, numOf(3.14159)); // different from testStringNum!
        assertNumNotEquals(3.141, numOf(3.14159));
    }

    @Test
    public void testNumNum() {
        // exact matches only (identical results to testStringNum)
        assertNumNotEquals(numOf(3.1415), numOf(3.14159265358979));
        assertNumNotEquals(numOf(3.141), numOf(3.14159265358979));
        assertNumNotEquals(numOf(3.14159265358979), numOf(3.1415));
        assertNumNotEquals(numOf(3.14159265358979), numOf(3.141));
        assertNumEquals(numOf(3.14159265358979), numOf(3.14159265358979));
        // fails for BigDecimalNum only
        //assertNumEquals("3.1415926535897", numOf(3.14159265358979));
        // fails for DoubleNum only
        //assertNumNotEquals("3.14159", numOf(3.14159265358979));
        assertNumNotEquals(numOf(3.14159), numOf(3.14158));
        assertNumNotEquals(numOf(3.14159), numOf(3.141));
        assertNumNotEquals(numOf(3.14158), numOf(3.14159));
        assertNumNotEquals(numOf(3.141), numOf(3.14159));
    }

    @Test
    public void testIndicatorIndicator() {
        // TODO: implement test for assertIndicatorEquals(Indicator, Indicator) 
    }
}
