/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.FixedRule;

/**
 * Tests for trace logging in strategies, verifying that custom names are used
 * in trace logs when set, and class names are used as fallback.
 */
public class StrategyTraceLoggingTest {

    private TraceTestLogger traceTestLogger;

    @Before
    public void setUp() {
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @After
    public void tearDown() {
        traceTestLogger.close();
    }

    @Test
    public void traceLoggingUsesClassNameWhenNoCustomNameSet() {
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace log should contain class name when no custom name is set",
                logContent.contains("BaseStrategy#shouldEnter"));
    }

    @Test
    public void traceLoggingExplainsUnstableStrategyDecision() {
        Strategy strategy = new BaseStrategy("Unstable Strategy", new FixedRule(1), new FixedRule(2), 3);
        traceTestLogger.clear();

        assertFalse(strategy.shouldEnter(1, new BaseTradingRecord()));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Strategy trace should include the false entry decision",
                logContent.contains(">>> Unstable Strategy#shouldEnter(1): false"));
        assertTrue("Strategy trace should explain unstable-bar suppression", logContent.contains("reason=unstable"));
        assertTrue("Strategy trace should include the unstable bar count", logContent.contains("unstableBars=3"));
    }

    @Test
    public void traceLoggingUsesCustomNameWhenSet() {
        Strategy strategy = new BaseStrategy("My Custom Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace log should contain custom name when set",
                logContent.contains("My Custom Strategy#shouldEnter"));
        assertFalse("Trace log should not contain class name when custom name is set",
                logContent.contains("BaseStrategy#shouldEnter"));
    }

    @Test
    public void traceLoggingUsesCustomNameForShouldExit() {
        Strategy strategy = new BaseStrategy("5min Entry Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldExit(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace log should contain custom name for shouldExit",
                logContent.contains("5min Entry Strategy#shouldExit"));
        assertFalse("Trace log should not contain class name when custom name is set",
                logContent.contains("BaseStrategy#shouldExit"));
    }

    @Test
    public void traceLoggingUsesClassNameForShouldExitWhenNoCustomName() {
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldExit(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace log should contain class name for shouldExit when no custom name is set",
                logContent.contains("BaseStrategy#shouldExit"));
    }

    @Test
    public void traceLoggingWorksForBothShouldEnterAndShouldExit() {
        Strategy strategy = new BaseStrategy("Multi-Timeframe Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());
        strategy.shouldExit(1, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace log should contain custom name for shouldEnter",
                logContent.contains("Multi-Timeframe Strategy#shouldEnter"));
        assertTrue("Trace log should contain custom name for shouldExit",
                logContent.contains("Multi-Timeframe Strategy#shouldExit"));
    }

    @Test
    public void traceLoggingWorksForMultipleStrategiesWithDifferentNames() {
        Strategy strategy1 = new BaseStrategy("Strategy 5min", new FixedRule(1), new FixedRule(2));
        Strategy strategy2 = new BaseStrategy("Strategy 15min", new FixedRule(3), new FixedRule(4));

        traceTestLogger.clear();
        strategy1.shouldEnter(0, new BaseTradingRecord());
        strategy2.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("First strategy should use its custom name in trace log",
                logContent.contains("Strategy 5min#shouldEnter"));
        assertTrue("Second strategy should use its custom name in trace log",
                logContent.contains("Strategy 15min#shouldEnter"));
    }

    @Test
    public void traceLoggingIncludesPrefixForStrategyTraces() {
        Strategy strategy = new BaseStrategy("Test Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace log should include >>> prefix for strategy traces", logContent.contains(">>>"));
        assertTrue("Trace log should contain custom name after prefix",
                logContent.contains(">>> Test Strategy#shouldEnter"));
    }

    @Test
    public void traceLoggingFollowsStrategyLoggerTraceByDefault() {
        Strategy strategy = new BaseStrategy("Default Trace Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("TRACE logging should emit strategy decisions without mutable trace state",
                logContent.contains("Default Trace Strategy#shouldEnter"));
        assertTrue("Default strategy traces should use verbose mode", logContent.contains("mode=VERBOSE"));
    }

    @Test
    public void traceLoggingSummaryModeEvaluatesEntryRuleWithScopedTracePolicy() {
        FixedRule child1 = new FixedRule(1);
        child1.setName("Entry Child 1");
        FixedRule child2 = new FixedRule(1);
        child2.setName("Entry Child 2");
        AndRule entryRule = new AndRule(child1, child2);
        entryRule.setName("Entry Composite");
        Strategy strategy = new BaseStrategy("Trace Strategy", entryRule, new FixedRule(2));

        traceTestLogger.clear();
        strategy.shouldEnterWithTraceMode(1, new BaseTradingRecord(), Rule.TraceMode.SUMMARY);

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Summary mode should log the strategy decision",
                logContent.contains(">>> Trace Strategy#shouldEnter"));
        assertTrue("Summary mode should log the entry rule", logContent.contains("Entry Composite#isSatisfied"));
        assertTrue("Summary mode should mark the scoped root path",
                logContent.contains("mode=SUMMARY ruleType=AndRule path=root depth=0"));
        assertFalse("Summary mode should suppress first child logs", logContent.contains("Entry Child 1#isSatisfied"));
        assertFalse("Summary mode should suppress second child logs", logContent.contains("Entry Child 2#isSatisfied"));
    }

    @Test
    public void traceLoggingVerboseModeEvaluatesExitRuleWithScopedTracePolicy() {
        FixedRule child1 = new FixedRule(2);
        child1.setName("Exit Child 1");
        FixedRule child2 = new FixedRule(2);
        child2.setName("Exit Child 2");
        AndRule exitRule = new AndRule(child1, child2);
        exitRule.setName("Exit Composite");
        Strategy strategy = new BaseStrategy("Trace Strategy", new FixedRule(1), exitRule);

        traceTestLogger.clear();
        strategy.shouldExit(2, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log the strategy exit decision",
                logContent.contains(">>> Trace Strategy#shouldExit"));
        assertTrue("Verbose mode should log the exit rule", logContent.contains("Exit Composite#isSatisfied"));
        assertTrue("Verbose mode should log first exit child", logContent.contains("Exit Child 1#isSatisfied"));
        assertTrue("Verbose mode should log second exit child", logContent.contains("Exit Child 2#isSatisfied"));
        assertTrue("Verbose mode should attribute the first child path",
                logContent.contains("path=root.rule1 depth=1"));
        assertTrue("Verbose mode should attribute the second child path",
                logContent.contains("path=root.rule2 depth=1"));
    }

    @Test
    public void traceLoggingDoesNotCreateStrategyScopeWhenStrategyLoggerTraceIsDisabled() {
        FixedRule entryRule = new FixedRule(1);
        entryRule.setName("Entry Child");
        Strategy strategy = new BaseStrategy("Trace Strategy", entryRule, new FixedRule(2));

        traceTestLogger.setLoggerLevel(BaseStrategy.class, Level.INFO);
        traceTestLogger.setLoggerLevel(FixedRule.class, Level.TRACE);
        traceTestLogger.clear();
        try {
            strategy.shouldEnter(1, new BaseTradingRecord());
        } finally {
            traceTestLogger.clearLoggerLevel(FixedRule.class);
            traceTestLogger.clearLoggerLevel(BaseStrategy.class);
        }

        String logContent = traceTestLogger.getLogOutput();
        assertFalse("Strategy should not emit strategy logs when the strategy logger is not tracing",
                logContent.contains(">>> Trace Strategy#shouldEnter"));
        assertTrue("A TRACE-enabled child logger should still emit its own default trace",
                logContent.contains("Entry Child#isSatisfied"));
        assertTrue("Child trace should not inherit a strategy parent frame", logContent.contains("path=root depth=0"));
    }

    @Test
    public void traceLoggingCanBeScopedToSingleStrategyEvaluationWithoutMutatingStrategyMode() {
        FixedRule child1 = new FixedRule(1);
        child1.setName("Scoped Entry Child 1");
        FixedRule child2 = new FixedRule(1);
        child2.setName("Scoped Entry Child 2");
        AndRule entryRule = new AndRule(child1, child2);
        entryRule.setName("Scoped Entry Composite");
        Strategy strategy = new BaseStrategy("Scoped Strategy", entryRule, new FixedRule(2));

        traceTestLogger.clear();
        assertTrue(strategy.shouldEnterWithTraceMode(1, new BaseTradingRecord(), Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Scoped verbose evaluation should log the strategy decision",
                logContent.contains(">>> Scoped Strategy#shouldEnter"));
        assertTrue("Scoped verbose evaluation should log the entry composite",
                logContent.contains("Scoped Entry Composite#isSatisfied"));
        assertTrue("Scoped verbose evaluation should log first child",
                logContent.contains("Scoped Entry Child 1#isSatisfied"));
        assertTrue("Scoped verbose evaluation should log second child",
                logContent.contains("Scoped Entry Child 2#isSatisfied"));

        traceTestLogger.clear();
        assertTrue(strategy.shouldEnter(1, new BaseTradingRecord()));
        assertTrue("A scoped strategy evaluation should not suppress later default TRACE behavior",
                traceTestLogger.getLogOutput().contains("mode=VERBOSE"));
    }
}
