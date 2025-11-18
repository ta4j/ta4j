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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class VoteRuleTest {

    @Test
    public void testRequiredVotesZero() {
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(0, BooleanRule.TRUE));
    }

    @Test
    public void testRulesIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(1, new Rule[] {}));
        List<Rule> rules = null;
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(1, rules));
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(1, Collections.emptyList()));
    }

    @Test
    public void testNullEntriesAreRejected() {
        assertThrows(NullPointerException.class, () -> new VoteRule(1, BooleanRule.TRUE, null));
        List<Rule> rulesWithNull = Arrays.asList(BooleanRule.TRUE, null);
        assertThrows(NullPointerException.class, () -> new VoteRule(1, rulesWithNull));
    }

    @Test
    public void testRequiredVotesExceedsRulesSize() {
        assertThrows(IllegalArgumentException.class, () -> new VoteRule(2, BooleanRule.TRUE));
    }

    @Test
    public void isSatisfied() {
        Rule[] rules = { BooleanRule.TRUE, BooleanRule.FALSE, BooleanRule.TRUE };
        assertTrue(new VoteRule(1, rules).isSatisfied(0));
        assertTrue(new VoteRule(2, rules).isSatisfied(0));
        assertFalse(new VoteRule(3, rules).isSatisfied(0));
    }

    @Test
    public void serializeAndDeserialize() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();
        VoteRule rule = new VoteRule(2, BooleanRule.TRUE, BooleanRule.FALSE, BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
