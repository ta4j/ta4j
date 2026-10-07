/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class OrRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;
    private TraceTestLogger ruleTraceTestLogger;

    @BeforeEach
    public void setUp() {
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();

        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1).build();
    }

    @AfterEach
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
        assertTrue(logContent.contains("FirstOrSecond#isSatisfied"),
                "Summary mode should still log the parent composite rule");
        assertFalse(logContent.contains("First Rule#isSatisfied"), "Summary mode should suppress child rule logs");
        assertFalse(logContent.contains("Second Rule#isSatisfied"), "Summary mode should suppress child rule logs");
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
        assertTrue(logContent.contains("FirstOrSecond#isSatisfied"),
                "Verbose mode should log the parent composite rule");
        assertTrue(logContent.contains("First Rule#isSatisfied"), "Verbose mode should keep first child rule logs");
        assertTrue(logContent.contains("Second Rule#isSatisfied"), "Verbose mode should keep second child rule logs");
        assertTrue(logContent.contains("path=root.rule2 depth=1"),
                "Verbose mode should attribute the second rule path");
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = unsatisfiedRule.or(BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
