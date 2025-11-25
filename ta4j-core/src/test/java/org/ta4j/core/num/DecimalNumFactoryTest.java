/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;

class DecimalNumFactoryTest {

    private static final NumFactory decimalFactory = DecimalNumFactory.getInstance();

    @Test
    final void testDefaultNum() {
        assertNumEquals(-1, decimalFactory.minusOne());

        assertNumEquals(-0, decimalFactory.zero());
        assertNumEquals(0, decimalFactory.zero());
        assertNumEquals(+0, decimalFactory.zero());

        assertNumEquals(1, decimalFactory.one());
        assertNumEquals(2, decimalFactory.two());
        assertNumEquals(3, decimalFactory.three());
        assertNumEquals(100, decimalFactory.hundred());
        assertNumEquals(1000, decimalFactory.thousand());
    }

    @Test
    final void testProduces() {
        var doubleFactory = DoubleNumFactory.getInstance();

        assertTrue(decimalFactory.produces(null));
        assertTrue(decimalFactory.produces(NaN.NaN));
        assertTrue(decimalFactory.produces(decimalFactory.one()));
        assertFalse(decimalFactory.produces(doubleFactory.one()));
    }

    @Test
    void shouldComputeExpUsingConfiguredPrecision() {
        NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(40);
        Num one = highPrecisionFactory.one();
        Num e = one.exp();

        assertNumEquals("2.718281828459045235360287471352662497761", e);
    }

}
