/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class VoteRuleTest {

    private TraceTestLogger ruleTraceTestLogger;

    @BeforeEach
    public void setUpLogger() {
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();
    }

    @AfterEach
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

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

    @Test
    public void traceLoggingSummaryModeEmitsVoteSummaryAndSuppressesChildren() {
        FixedRule rule1 = new FixedRule(1);
        rule1.setName("Vote Rule 1");
        FixedRule rule2 = new FixedRule(2);
        rule2.setName("Vote Rule 2");
        FixedRule rule3 = new FixedRule(3);
        rule3.setName("Vote Rule 3");
        VoteRule voteRule = new VoteRule(2, rule1, rule2, rule3);
        voteRule.setName("Vote Summary");

        ruleTraceTestLogger.clear();
        voteRule.isSatisfiedWithTraceMode(2, null, Rule.TraceMode.SUMMARY);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue(logContent.contains("Vote Summary#isSatisfied"), "Summary mode should log voting rule");
        assertTrue(logContent.contains("votes=1"), "Summary mode should include vote count");
        assertTrue(logContent.contains("requiredVotes=2"), "Summary mode should include required votes");
        assertTrue(logContent.contains("evaluatedRules=3"), "Summary mode should include evaluated rule count");
        assertFalse(logContent.contains("Vote Rule 1#isSatisfied"), "Summary mode should suppress first child log");
        assertFalse(logContent.contains("Vote Rule 2#isSatisfied"), "Summary mode should suppress second child log");
        assertFalse(logContent.contains("Vote Rule 3#isSatisfied"), "Summary mode should suppress third child log");
    }
}
