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
import org.junit.Test;

public class AbsoluteIndicatorTest {

    @Test
    public void constantIndicators() {
        AbsoluteIndicator positiveInd = new AbsoluteIndicator(new ConstantIndicator<Decimal>(Decimal.valueOf(1337)));
        AbsoluteIndicator zeroInd = new AbsoluteIndicator(new ConstantIndicator<Decimal>(Decimal.ZERO));
        AbsoluteIndicator negativeInd = new AbsoluteIndicator(new ConstantIndicator<Decimal>(Decimal.valueOf(-42.42)));
        for (int i = 0; i < 10; i++) {
            assertDecimalEquals(positiveInd.getValue(i), 1337);
            assertDecimalEquals(zeroInd.getValue(i), 0);
            assertDecimalEquals(negativeInd.getValue(i), 42.42);
        }
    }
}
