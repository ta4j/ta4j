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
package eu.verdelhan.ta4j.trading.rules;

import eu.verdelhan.ta4j.trading.rules.FixedRule;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FixedRuleTest {

    private FixedRule fixedRule;
    
    @Test
    public void isSatisfied() {
        fixedRule = new FixedRule();
        assertFalse(fixedRule.isSatisfied(0));
        assertFalse(fixedRule.isSatisfied(1));
        assertFalse(fixedRule.isSatisfied(2));
        assertFalse(fixedRule.isSatisfied(9));
        
        fixedRule = new FixedRule(1, 2, 3);
        assertFalse(fixedRule.isSatisfied(0));
        assertTrue(fixedRule.isSatisfied(1));
        assertTrue(fixedRule.isSatisfied(2));
        assertTrue(fixedRule.isSatisfied(3));
        assertFalse(fixedRule.isSatisfied(4));
        assertFalse(fixedRule.isSatisfied(5));
        assertFalse(fixedRule.isSatisfied(6));
        assertFalse(fixedRule.isSatisfied(7));
        assertFalse(fixedRule.isSatisfied(8));
        assertFalse(fixedRule.isSatisfied(9));
        assertFalse(fixedRule.isSatisfied(10));
    }
}
        