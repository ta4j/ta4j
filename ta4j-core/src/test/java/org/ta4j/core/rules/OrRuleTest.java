/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Rule;

public class OrRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;

    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
    }

    @Test
    public void isSatisfied() {
        assertTrue(satisfiedRule.or(BooleanRule.FALSE).isSatisfied(0));
        assertTrue(BooleanRule.FALSE.or(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.or(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.or(unsatisfiedRule).isSatisfied(0));

        assertTrue(satisfiedRule.or(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.or(satisfiedRule).isSatisfied(10));
        assertTrue(unsatisfiedRule.or(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.or(unsatisfiedRule).isSatisfied(10));
    }
}
