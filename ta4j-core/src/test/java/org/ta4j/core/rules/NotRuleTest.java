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
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class NotRuleTest {

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
        assertFalse(satisfiedRule.negation().isSatisfied(0));
        assertTrue(unsatisfiedRule.negation().isSatisfied(0));

        assertFalse(satisfiedRule.negation().isSatisfied(10));
        assertTrue(unsatisfiedRule.negation().isSatisfied(10));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, satisfiedRule.negation());
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, satisfiedRule.negation());
    }

    @Test
    public void traceLoggingVerboseModePreservesNegatedRuleLog() {
        FixedRule ruleToNegate = new FixedRule(1);
        ruleToNegate.setName("Negated Rule");
        NotRule notRule = new NotRule(ruleToNegate);
        notRule.setName("Not Wrapper");

        ruleTraceTestLogger.clear();
        notRule.isSatisfiedWithTraceMode(1, Rule.TraceMode.VERBOSE);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log the negated child", logContent.contains("Negated Rule#isSatisfied"));
        assertTrue("Verbose mode should log the parent", logContent.contains("Not Wrapper#isSatisfied"));
        assertTrue("Verbose mode should attribute the negated rule path",
                logContent.contains("path=root.ruleToNegate depth=1"));
    }
}
