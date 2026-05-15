/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class FixedRuleTest {

    private TraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUpLogger() {
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

    @Test
    public void isSatisfied() {
        FixedRule fixedRule = new FixedRule();
        assertFalse(fixedRule.isSatisfied(0));
        assertFalse(fixedRule.isSatisfied(1));
        assertFalse(fixedRule.isSatisfied(2));
        assertFalse(fixedRule.isSatisfied(9));

        fixedRule = new FixedRule(1, 2, 3);
        assertFalse(fixedRule.isSatisfied(0));
        assertTrue(fixedRule.isSatisfied(1));
        assertTrue(fixedRule.isSatisfied(2));
        assertTrue(fixedRule.isSatisfied(3));
        assertFalse(fixedRule.isSatisfied(4));
        assertFalse(fixedRule.isSatisfied(5));
        assertFalse(fixedRule.isSatisfied(6));
        assertFalse(fixedRule.isSatisfied(7));
        assertFalse(fixedRule.isSatisfied(8));
        assertFalse(fixedRule.isSatisfied(9));
        assertFalse(fixedRule.isSatisfied(10));
    }

    @Test
    public void traceModePublicContractHasOnlySummaryAndVerbose() {
        String[] modeNames = Arrays.stream(Rule.TraceMode.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] { "SUMMARY", "VERBOSE" }, modeNames);
    }

    @Test
    public void traceLoggingFollowsLoggerTraceByDefault() {
        FixedRule rule = new FixedRule(1);
        ruleTraceTestLogger.clear();

        rule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("TRACE logging should be enough to emit a default verbose rule trace",
                logContent.contains("FixedRule#isSatisfied"));
        assertTrue("Default rule traces should use verbose mode", logContent.contains("mode=VERBOSE"));
    }

    @Test
    public void traceLoggingUsesClassNameWhenNoCustomNameSet() {
        FixedRule rule = new FixedRule(1);
        ruleTraceTestLogger.clear();

        rule.isSatisfied(0);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Trace log should contain class name when no custom name is set",
                logContent.contains("FixedRule#isSatisfied"));
    }

    @Test
    public void traceLoggingUsesCustomNameWhenSet() {
        FixedRule rule = new FixedRule(1);
        rule.setName("My Custom Entry Rule");
        ruleTraceTestLogger.clear();

        rule.isSatisfied(0);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Trace log should contain custom name when set",
                logContent.contains("My Custom Entry Rule#isSatisfied"));
        assertFalse("Trace log should not contain class name when custom name is set",
                logContent.contains("FixedRule#isSatisfied"));
    }

    @Test
    public void traceLoggingFallsBackToClassNameWhenCustomNameIsReset() {
        FixedRule rule = new FixedRule(1);
        rule.setName("My Custom Rule");
        rule.setName(null);
        ruleTraceTestLogger.clear();

        rule.isSatisfied(0);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Trace log should fall back to class name when custom name is reset",
                logContent.contains("FixedRule#isSatisfied"));
        assertFalse("Trace log should not contain custom name after reset",
                logContent.contains("My Custom Rule#isSatisfied"));
    }

    @Test
    public void traceLoggingWorksForDifferentCustomNames() {
        FixedRule rule1 = new FixedRule(1);
        rule1.setName("Entry Rule 5min");

        FixedRule rule2 = new FixedRule(2);
        rule2.setName("Exit Rule 15min");

        ruleTraceTestLogger.clear();
        rule1.isSatisfied(1);
        rule2.isSatisfied(2);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("First rule should use its custom name in trace log",
                logContent.contains("Entry Rule 5min#isSatisfied"));
        assertTrue("Second rule should use its custom name in trace log",
                logContent.contains("Exit Rule 15min#isSatisfied"));
    }

    @Test
    public void traceLoggingCanBeDisabledByLoggerLevel() {
        FixedRule rule = new FixedRule(1);
        ruleTraceTestLogger.setLoggerLevel(FixedRule.class, Level.INFO);
        ruleTraceTestLogger.clear();

        rule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertFalse("Trace log should be empty when the rule logger is not TRACE",
                logContent.contains("FixedRule#isSatisfied"));
    }

    @Test
    public void traceLoggingCanBeScopedToSingleEvaluation() {
        FixedRule rule = new FixedRule(1);
        ruleTraceTestLogger.clear();

        assertTrue(rule.isSatisfiedWithTraceMode(1, Rule.TraceMode.VERBOSE));

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Scoped verbose evaluation should emit trace output", logContent.contains("FixedRule#isSatisfied"));
        assertTrue("Scoped verbose evaluation should mark verbose mode", logContent.contains("mode=VERBOSE"));
        assertTrue("Scoped verbose evaluation should keep root path", logContent.contains("path=root"));

        ruleTraceTestLogger.clear();
        assertTrue(rule.isSatisfied(1));
        assertTrue("A scoped evaluation should not suppress later default TRACE behavior",
                ruleTraceTestLogger.getLogOutput().contains("mode=VERBOSE"));
    }

    @Test
    public void scopedTraceEvaluationSkipsTraceScopeWhenLoggerTraceIsDisabled() {
        FrameObservingFixedRule rule = new FrameObservingFixedRule(1);
        ruleTraceTestLogger.setLoggerLevel(FrameObservingFixedRule.class, Level.INFO);
        ruleTraceTestLogger.clear();

        assertTrue(rule.isSatisfiedWithTraceMode(1, Rule.TraceMode.VERBOSE));

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertFalse("Scoped trace evaluation should not create a trace frame when logger trace is disabled",
                rule.observedTraceFrame());
        assertFalse("Scoped trace evaluation should not emit trace output when logger trace is disabled",
                logContent.contains("FrameObservingFixedRule#isSatisfied"));
    }

    @Test
    public void serializeAndDeserialize() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();
        FixedRule rule = new FixedRule(1, 4, 5);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    private static final class FrameObservingFixedRule extends FixedRule {

        private boolean observedTraceFrame;

        private FrameObservingFixedRule(int index) {
            super(index);
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            observedTraceFrame = RuleTraceContext.currentFrame() != null;
            return super.isSatisfied(index, tradingRecord);
        }

        private boolean observedTraceFrame() {
            return observedTraceFrame;
        }
    }
}
