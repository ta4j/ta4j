/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;

public class DoubleNumTest {

    @Test
    public void testEqualsDoubleNumWithDecimalNum() {
        final DecimalNum decimalNum = DecimalNum.valueOf(3.0);
        final DoubleNum doubleNum = DoubleNum.valueOf(3.0);

        assertFalse(doubleNum.equals(decimalNum));
    }

    @Test
    public void testZeroEquals() {
        final Num num1 = DoubleNum.valueOf(-0.0);
        final Num num2 = DoubleNum.valueOf(0.0);

        assertTrue(num1.isEqual(num2));
    }

    @Test
    public void testExpZero() {
        final Num zero = DoubleNum.valueOf(0);
        final Num result = zero.exp();
        assertNumEquals(1, result);
        assertNumEquals(Math.exp(0), result);
    }

    @Test
    public void testExpOne() {
        final Num one = DoubleNum.valueOf(1);
        final Num result = one.exp();
        assertNumEquals(Math.exp(1), result);
    }

    @Test
    public void testExpNegativeOne() {
        final Num negOne = DoubleNum.valueOf(-1);
        final Num result = negOne.exp();
        assertNumEquals(Math.exp(-1), result);
    }

    @Test
    public void testExpTwo() {
        final Num two = DoubleNum.valueOf(2);
        final Num result = two.exp();
        assertNumEquals(Math.exp(2), result);
    }

    @Test
    public void testExpSmallValue() {
        final Num small = DoubleNum.valueOf(0.1);
        final Num result = small.exp();
        assertNumEquals(Math.exp(0.1), result);
    }

    @Test
    public void testExpLargeValue() {
        final Num large = DoubleNum.valueOf(10);
        final Num result = large.exp();
        assertNumEquals(Math.exp(10), result);
    }

    @Test
    public void testExpNegativeValue() {
        final Num neg = DoubleNum.valueOf(-5);
        final Num result = neg.exp();
        assertNumEquals(Math.exp(-5), result);
    }
}
