/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.rules

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Rule

class XorRuleTest {
    private lateinit var satisfiedRule: Rule
    private lateinit var unsatisfiedRule: Rule
    @Before
    fun setUp() {
        satisfiedRule = BooleanRule(true)
        unsatisfiedRule = BooleanRule(false)
    }

    @Test
    fun isSatisfied() {
            Assert.assertTrue(satisfiedRule.xor(BooleanRule.FALSE).isSatisfied(0))
            Assert.assertTrue(BooleanRule.FALSE.xor(satisfiedRule).isSatisfied(0))
            Assert.assertFalse(unsatisfiedRule.xor(BooleanRule.FALSE).isSatisfied(0))
            Assert.assertFalse(BooleanRule.FALSE.xor(unsatisfiedRule).isSatisfied(0))
            Assert.assertFalse(satisfiedRule.xor(BooleanRule.TRUE).isSatisfied(10))
            Assert.assertFalse(BooleanRule.TRUE.xor(satisfiedRule).isSatisfied(10))
            Assert.assertTrue(unsatisfiedRule.xor(BooleanRule.TRUE).isSatisfied(10))
            Assert.assertTrue(BooleanRule.TRUE.xor(unsatisfiedRule).isSatisfied(10))
        }
}