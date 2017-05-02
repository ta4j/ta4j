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

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import org.junit.Assert;
import org.junit.Test;

public class FixedIndicatorTest {
    
    private FixedDecimalIndicator fixedDecimalIndicator;
    
    private FixedBooleanIndicator fixedBooleanIndicator;

    @Test
    public void getValueOnFixedDecimalIndicator() {
        fixedDecimalIndicator = new FixedDecimalIndicator(13.37, 42, -17);
        assertDecimalEquals(fixedDecimalIndicator.getValue(0), 13.37);
        assertDecimalEquals(fixedDecimalIndicator.getValue(1), 42);
        assertDecimalEquals(fixedDecimalIndicator.getValue(2), -17);
        
        fixedDecimalIndicator = new FixedDecimalIndicator("3.0", "-123.456", "0");
        assertDecimalEquals(fixedDecimalIndicator.getValue(0), "3");
        assertDecimalEquals(fixedDecimalIndicator.getValue(1), "-123.456");
        assertDecimalEquals(fixedDecimalIndicator.getValue(2), "0.0");
        
    }

    @Test
    public void getValueOnFixedBooleanIndicator() {
        fixedBooleanIndicator = new FixedBooleanIndicator(false, false, true, false, true);
        Assert.assertFalse(fixedBooleanIndicator.getValue(0));
        Assert.assertFalse(fixedBooleanIndicator.getValue(1));
        Assert.assertTrue(fixedBooleanIndicator.getValue(2));
        Assert.assertFalse(fixedBooleanIndicator.getValue(3));
        Assert.assertTrue(fixedBooleanIndicator.getValue(4));
    }
}
