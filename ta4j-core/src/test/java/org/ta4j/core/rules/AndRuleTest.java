/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AndRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;
    private RuleTraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUp() {
        ruleTraceTestLogger = new RuleTraceTestLogger();
        ruleTraceTestLogger.open();

        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

    @Test
    public void isSatisfied() {
        assertFalse(satisfiedRule.and(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.and(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.and(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.and(unsatisfiedRule).isSatisfied(0));

        assertTrue(satisfiedRule.and(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.and(satisfiedRule).isSatisfied(10));
        assertFalse(unsatisfiedRule.and(BooleanRule.TRUE).isSatisfied(10));
        assertFalse(BooleanRule.TRUE.and(unsatisfiedRule).isSatisfied(10));
    }

    @Test
    public void traceLoggingRollupModeSuppressesChildRuleLogs() {
        Rule rule1 = new FixedRule(1);
        rule1.setName("Entry Rule");
        Rule rule2 = new FixedRule(1, 2);
        rule2.setName("Exit Rule");

        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("EntryAndExit");
        andRule.setTraceMode(Rule.TraceMode.ROLLUP);

        ruleTraceTestLogger.clear();
        andRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Rollup mode should still log the parent composite rule",
                logContent.contains("EntryAndExit#isSatisfied"));
        assertFalse("Rollup mode should suppress child rule logs", logContent.contains("Entry Rule#isSatisfied"));
        assertFalse("Rollup mode should suppress child rule logs", logContent.contains("Exit Rule#isSatisfied"));
        assertTrue("Rollup mode should restore child trace mode",
                rule1.getTraceMode() == Rule.TraceMode.VERBOSE && rule2.getTraceMode() == Rule.TraceMode.VERBOSE);
    }

    @Test
    public void traceLoggingVerboseModePreservesChildRuleLogs() {
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule 1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule 2");

        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("Rule Pair");
        andRule.setTraceMode(Rule.TraceMode.VERBOSE);

        ruleTraceTestLogger.clear();
        andRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log the parent composite rule", logContent.contains("Rule Pair#isSatisfied"));
        assertTrue("Verbose mode should keep child rule logs", logContent.contains("Rule 1#isSatisfied"));
        assertTrue("Verbose mode should keep child rule logs", logContent.contains("Rule 2#isSatisfied"));
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = satisfiedRule.and(BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
