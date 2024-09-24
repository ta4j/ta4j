/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

public class JustOnceRuleTest {

    private JustOnceRule rule;

    @Before
    public void setUp() {
        rule = new JustOnceRule();
    }

    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(10));
        assertFalse(rule.isSatisfied(11));
        assertFalse(rule.isSatisfied(12));
        assertFalse(rule.isSatisfied(13));
        assertFalse(rule.isSatisfied(14));
    }

    @Test
    public void isSatisfiedInReverseOrder() {
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(0));
    }

    @Test
    public void isSatisfiedWithInnerSatisfiedRule() {
        JustOnceRule rule = new JustOnceRule(new BooleanRule(true));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(0));
    }

    @Test
    public void isSatisfiedWithInnerNonSatisfiedRule() {
        JustOnceRule rule = new JustOnceRule(new BooleanRule(false));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(0));
    }

    @Test
    public void isSatisfiedWithInnerRule() {
        JustOnceRule rule = new JustOnceRule(new FixedRule(1, 3, 5));
        assertFalse(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(1));
    }
}
