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
package org.ta4j.core.trading.rules;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class IsLowestRuleTest {

    private Indicator<Decimal> indicator;
    private IsLowestRule rule;
    
    @Before
    public void setUp() {
    	indicator = new FixedDecimalIndicator(1, -5, 3, -6, 5, -7, 0, -1, 2, -8);
        rule = new IsLowestRule(indicator, 3);
    }
    
    @Test
    public void isSatisfied() {
    		assertTrue(rule.isSatisfied(0));
		assertTrue(rule.isSatisfied(1));
		assertFalse(rule.isSatisfied(2));
		assertTrue(rule.isSatisfied(3));
		assertFalse(rule.isSatisfied(4));
		assertTrue(rule.isSatisfied(5));
		assertFalse(rule.isSatisfied(6));
		assertFalse(rule.isSatisfied(7));
		assertFalse(rule.isSatisfied(8));
		assertTrue(rule.isSatisfied(9));
    }
}
