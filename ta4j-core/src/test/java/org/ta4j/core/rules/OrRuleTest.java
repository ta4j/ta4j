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
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class OrRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;
    private TraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUp() {
        ruleTraceTestLogger = new TraceTestLogger();
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
    public void traceLoggingSummaryModeSuppressesChildRuleLogs() {
        Rule rule1 = new FixedRule(2);
        rule1.setName("First Rule");
        Rule rule2 = new FixedRule(1);
        rule2.setName("Second Rule");

        OrRule orRule = new OrRule(rule1, rule2);
        orRule.setName("FirstOrSecond");

        ruleTraceTestLogger.clear();
        orRule.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.SUMMARY);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Summary mode should still log the parent composite rule",
                logContent.contains("FirstOrSecond#isSatisfied"));
        assertFalse("Summary mode should suppress child rule logs", logContent.contains("First Rule#isSatisfied"));
        assertFalse("Summary mode should suppress child rule logs", logContent.contains("Second Rule#isSatisfied"));
    }

    @Test
    public void traceLoggingVerboseModePreservesChildRuleLogs() {
        Rule rule1 = new FixedRule(2);
        rule1.setName("First Rule");
        Rule rule2 = new FixedRule(1);
        rule2.setName("Second Rule");

        OrRule orRule = new OrRule(rule1, rule2);
        orRule.setName("FirstOrSecond");

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
