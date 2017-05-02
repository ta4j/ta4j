/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Decimal;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import org.junit.Before;
import org.junit.Test;

public class DifferenceIndicatorTest {
    
    private ConstantIndicator<Decimal> constantIndicator;
    
    private FixedIndicator<Decimal> mockIndicator;

    private DifferenceIndicator differenceIndicator;
    
    @Before
    public void setUp() {
        constantIndicator = new ConstantIndicator<Decimal>(Decimal.valueOf(6));
        mockIndicator = new FixedIndicator<Decimal>(
                Decimal.valueOf("-2.0"),
                Decimal.valueOf("0.00"),
                Decimal.valueOf("1.00"),
                Decimal.valueOf("2.53"),
                Decimal.valueOf("5.87"),
                Decimal.valueOf("6.00"),
                Decimal.valueOf("10.0")
        );
        differenceIndicator = new DifferenceIndicator(constantIndicator, mockIndicator);
    }

    @Test
    public void getValue() {
        assertDecimalEquals(differenceIndicator.getValue(0), "8");
        assertDecimalEquals(differenceIndicator.getValue(1), "6");
        assertDecimalEquals(differenceIndicator.getValue(2), "5");
        assertDecimalEquals(differenceIndicator.getValue(3), "3.47");
        assertDecimalEquals(differenceIndicator.getValue(4), "0.13");
        assertDecimalEquals(differenceIndicator.getValue(5), "0");
        assertDecimalEquals(differenceIndicator.getValue(6), "-4");
    }
}
