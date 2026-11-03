/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class NotRuleTest {

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
    public void constructorRejectsNullRule() {
        assertThrows(NullPointerException.class, () -> new NotRule(null));
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
        assertTrue(logContent.contains("Negated Rule#isSatisfied"), "Verbose mode should log the negated child");
        assertTrue(logContent.contains("Not Wrapper#isSatisfied"), "Verbose mode should log the parent");
        assertTrue(logContent.contains("path=root.ruleToNegate depth=1"),
                "Verbose mode should attribute the negated rule path");
    }
}
