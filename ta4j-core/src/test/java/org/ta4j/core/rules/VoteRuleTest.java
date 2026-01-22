/*
 * SPDX-License-Identifier: MIT
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
