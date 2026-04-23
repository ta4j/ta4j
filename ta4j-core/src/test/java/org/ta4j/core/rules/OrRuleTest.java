/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class OrRuleTest {

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
        series = new MockBarSeriesBuilder().withData(1).build();
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
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

    @Test
    public void traceLoggingRollupModeSuppressesChildRuleLogs() {
        Rule rule1 = new FixedRule(2);
        rule1.setName("First Rule");
        rule1.setTraceMode(Rule.TraceMode.VERBOSE);
        Rule rule2 = new FixedRule(1);
        rule2.setName("Second Rule");
        rule2.setTraceMode(Rule.TraceMode.VERBOSE);

        OrRule orRule = new OrRule(rule1, rule2);
        orRule.setName("FirstOrSecond");
        orRule.setTraceMode(Rule.TraceMode.ROLLUP);

        ruleTraceTestLogger.clear();
        orRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Rollup mode should still log the parent composite rule",
                logContent.contains("FirstOrSecond#isSatisfied"));
        assertFalse("Rollup mode should suppress child rule logs", logContent.contains("First Rule#isSatisfied"));
        assertFalse("Rollup mode should suppress child rule logs", logContent.contains("Second Rule#isSatisfied"));
        assertEquals("Rollup mode should not mutate first child trace mode", Rule.TraceMode.VERBOSE,
                rule1.getTraceMode());
        assertEquals("Rollup mode should not mutate second child trace mode", Rule.TraceMode.VERBOSE,
                rule2.getTraceMode());
    }

    @Test
    public void traceLoggingVerboseModePreservesChildRuleLogs() {
        Rule rule1 = new FixedRule(2);
        rule1.setName("First Rule");
        Rule rule2 = new FixedRule(1);
        rule2.setName("Second Rule");

        OrRule orRule = new OrRule(rule1, rule2);
        orRule.setName("FirstOrSecond");
        orRule.setTraceMode(Rule.TraceMode.VERBOSE);

        ruleTraceTestLogger.clear();
        orRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log the parent composite rule",
                logContent.contains("FirstOrSecond#isSatisfied"));
        assertTrue("Verbose mode should keep first child rule logs", logContent.contains("First Rule#isSatisfied"));
        assertTrue("Verbose mode should keep second child rule logs", logContent.contains("Second Rule#isSatisfied"));
        assertTrue("Verbose mode should attribute the second rule path",
                logContent.contains("path=root.rule2 depth=1"));
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = unsatisfiedRule.or(BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
