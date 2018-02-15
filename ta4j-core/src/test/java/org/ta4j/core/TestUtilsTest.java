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
        assertNumEquals("3.1415", numOf(3.14159265358979)); // expected within precision
        assertNumNotEquals("3.141", numOf(3.14159265358979)); // expected outside precision
        assertNumEquals("3.14159265358979", numOf(3.1415)); // actual within precision
        assertNumNotEquals("3.14159265358979", numOf(3.141)); // actual outside precision
    }

    @Test
    public void testIntNum() {
        assertNumEquals(3, numOf(3));
        assertNumEquals(3, numOf(3.0));
        assertNumNotEquals(3, numOf(3.0001));
        assertNumEquals(3, numOf(3.00001));
        assertNumNotEquals(3, numOf(3.14159265358979));
        assertNumNotEquals(4, numOf(3.14159265358979));
        assertNumNotEquals(4, numOf(3.99));
        assertNumEquals(4, numOf(3.99991));
    }

    @Test
    public void testDoubleNum() {
        assertNumEquals(3.14159, numOf(3.14158));
        assertNumNotEquals(3.14159, numOf(3.141));
        assertNumEquals(3.14158, numOf(3.14159));
        assertNumNotEquals(3.141, numOf(3.14159));
    }

}
