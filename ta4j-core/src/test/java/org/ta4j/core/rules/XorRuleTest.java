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

public class XorRuleTest {

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
        assertTrue(satisfiedRule.xor(BooleanRule.FALSE).isSatisfied(0));
        assertTrue(BooleanRule.FALSE.xor(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.xor(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.xor(unsatisfiedRule).isSatisfied(0));

        assertFalse(satisfiedRule.xor(BooleanRule.TRUE).isSatisfied(10));
        assertFalse(BooleanRule.TRUE.xor(satisfiedRule).isSatisfied(10));
        assertTrue(unsatisfiedRule.xor(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.xor(unsatisfiedRule).isSatisfied(10));
    }

    @Test
    public void serializeAndDeserialize() {
        Rule xor = satisfiedRule.xor(unsatisfiedRule);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, xor);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, xor);
    }

    @Test
    public void traceLoggingVerboseModePreservesBothChildRuleLogs() {
        FixedRule rule1 = new FixedRule(1);
        rule1.setName("Xor Rule 1");
        FixedRule rule2 = new FixedRule(2);
        rule2.setName("Xor Rule 2");
        XorRule xorRule = new XorRule(rule1, rule2);
        xorRule.setName("Xor Pair");
        xorRule.setTraceMode(Rule.TraceMode.VERBOSE);

        ruleTraceTestLogger.clear();
        xorRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log first XOR child", logContent.contains("Xor Rule 1#isSatisfied"));
        assertTrue("Verbose mode should log second XOR child", logContent.contains("Xor Rule 2#isSatisfied"));
        assertTrue("Verbose mode should log parent XOR", logContent.contains("Xor Pair#isSatisfied"));
        assertTrue("Verbose mode should attribute first child path", logContent.contains("path=root.rule1 depth=1"));
        assertTrue("Verbose mode should attribute second child path", logContent.contains("path=root.rule2 depth=1"));
    }
}
