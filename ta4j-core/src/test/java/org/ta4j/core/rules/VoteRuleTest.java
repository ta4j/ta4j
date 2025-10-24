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
package org.ta4j.core.rules;

import java.util.Collections;

import org.junit.Test;
import org.ta4j.core.Rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VoteRuleTest {

    @Test
    public void isSatisfied() {
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(0, BooleanRule.TRUE));
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(1, new Rule[]{}));
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(2, BooleanRule.TRUE));

        assertThrows(IllegalArgumentException.class, () -> new VoteRule(0, Collections.singletonList(BooleanRule.TRUE)));
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(1, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(2, Collections.singletonList(BooleanRule.TRUE)));

        assertTrue(new VoteRule(1, BooleanRule.TRUE).isSatisfied(0));
        assertFalse(new VoteRule(1, BooleanRule.FALSE).isSatisfied(0));

        assertTrue(new VoteRule(1, BooleanRule.FALSE, BooleanRule.TRUE).isSatisfied(0));
        assertFalse(new VoteRule(2, BooleanRule.FALSE, BooleanRule.TRUE).isSatisfied(0));
    }
}
