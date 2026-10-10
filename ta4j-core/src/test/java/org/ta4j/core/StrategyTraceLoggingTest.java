/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.FixedRule;

/**
 * Tests for trace logging in strategies, verifying that custom names are used
 * in trace logs when set, and class names are used as fallback.
 */
public class StrategyTraceLoggingTest {

    private TraceTestLogger traceTestLogger;

    @BeforeEach
    public void setUp() {
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @AfterEach
    public void tearDown() {
        traceTestLogger.close();
    }

    @Test
    public void traceLoggingUsesClassNameWhenNoCustomNameSet() {
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("BaseStrategy#shouldEnter"),
                "Trace log should contain class name when no custom name is set");
    }

    @Test
    public void traceLoggingExplainsUnstableStrategyDecision() {
        Strategy strategy = new BaseStrategy("Unstable Strategy", new FixedRule(1), new FixedRule(2), 3);
        traceTestLogger.clear();

        assertFalse(strategy.shouldEnter(1, new BaseTradingRecord()));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains(">>> Unstable Strategy#shouldEnter(1): false"),
                "Strategy trace should include the false entry decision");
        assertTrue(logContent.contains("reason=unstable"), "Strategy trace should explain unstable-bar suppression");
        assertTrue(logContent.contains("unstableBars=3"), "Strategy trace should include the unstable bar count");
    }

    @Test
    public void traceLoggingUsesCustomNameWhenSet() {
        Strategy strategy = new BaseStrategy("My Custom Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("My Custom Strategy#shouldEnter"),
                "Trace log should contain custom name when set");
        assertFalse(logContent.contains("BaseStrategy#shouldEnter"),
                "Trace log should not contain class name when custom name is set");
    }

    @Test
    public void traceLoggingUsesCustomNameForShouldExit() {
        Strategy strategy = new BaseStrategy("5min Entry Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldExit(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("5min Entry Strategy#shouldExit"),
                "Trace log should contain custom name for shouldExit");
        assertFalse(logContent.contains("BaseStrategy#shouldExit"),
                "Trace log should not contain class name when custom name is set");
    }

    @Test
    public void traceLoggingUsesClassNameForShouldExitWhenNoCustomName() {
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldExit(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("BaseStrategy#shouldExit"),
                "Trace log should contain class name for shouldExit when no custom name is set");
    }

    @Test
    public void traceLoggingWorksForBothShouldEnterAndShouldExit() {
        Strategy strategy = new BaseStrategy("Multi-Timeframe Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());
        strategy.shouldExit(1, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("Multi-Timeframe Strategy#shouldEnter"),
                "Trace log should contain custom name for shouldEnter");
        assertTrue(logContent.contains("Multi-Timeframe Strategy#shouldExit"),
                "Trace log should contain custom name for shouldExit");
    }

    @Test
    public void traceLoggingWorksForMultipleStrategiesWithDifferentNames() {
        Strategy strategy1 = new BaseStrategy("Strategy 5min", new FixedRule(1), new FixedRule(2));
        Strategy strategy2 = new BaseStrategy("Strategy 15min", new FixedRule(3), new FixedRule(4));

        traceTestLogger.clear();
        strategy1.shouldEnter(0, new BaseTradingRecord());
        strategy2.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("Strategy 5min#shouldEnter"),
                "First strategy should use its custom name in trace log");
        assertTrue(logContent.contains("Strategy 15min#shouldEnter"),
                "Second strategy should use its custom name in trace log");
    }

    @Test
    public void traceLoggingIncludesPrefixForStrategyTraces() {
        Strategy strategy = new BaseStrategy("Test Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains(">>>"), "Trace log should include >>> prefix for strategy traces");
        assertTrue(logContent.contains(">>> Test Strategy#shouldEnter"),
                "Trace log should contain custom name after prefix");
    }

    @Test
    public void traceLoggingFollowsStrategyLoggerTraceByDefault() {
        Strategy strategy = new BaseStrategy("Default Trace Strategy", new FixedRule(1), new FixedRule(2));
        traceTestLogger.clear();

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("Default Trace Strategy#shouldEnter"),
                "TRACE logging should emit strategy decisions without mutable trace state");
        assertTrue(logContent.contains("mode=VERBOSE"), "Default strategy traces should use verbose mode");
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
        assertTrue(logContent.contains(">>> Trace Strategy#shouldEnter"),
                "Summary mode should log the strategy decision");
        assertTrue(logContent.contains("Entry Composite#isSatisfied"), "Summary mode should log the entry rule");
        assertTrue(logContent.contains("mode=SUMMARY ruleType=AndRule path=root depth=0"),
                "Summary mode should mark the scoped root path");
        assertFalse(logContent.contains("Entry Child 1#isSatisfied"), "Summary mode should suppress first child logs");
        assertFalse(logContent.contains("Entry Child 2#isSatisfied"), "Summary mode should suppress second child logs");
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
        assertTrue(logContent.contains(">>> Trace Strategy#shouldExit"),
                "Verbose mode should log the strategy exit decision");
        assertTrue(logContent.contains("Exit Composite#isSatisfied"), "Verbose mode should log the exit rule");
        assertTrue(logContent.contains("Exit Child 1#isSatisfied"), "Verbose mode should log first exit child");
        assertTrue(logContent.contains("Exit Child 2#isSatisfied"), "Verbose mode should log second exit child");
        assertTrue(logContent.contains("path=root.rule1 depth=1"),
                "Verbose mode should attribute the first child path");
        assertTrue(logContent.contains("path=root.rule2 depth=1"),
                "Verbose mode should attribute the second child path");
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
        assertFalse(logContent.contains(">>> Trace Strategy#shouldEnter"),
                "Strategy should not emit strategy logs when the strategy logger is not tracing");
        assertTrue(logContent.contains("Entry Child#isSatisfied"),
                "A TRACE-enabled child logger should still emit its own default trace");
        assertTrue(logContent.contains("path=root depth=0"), "Child trace should not inherit a strategy parent frame");
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
        assertTrue(logContent.contains(">>> Scoped Strategy#shouldEnter"),
                "Scoped verbose evaluation should log the strategy decision");
        assertTrue(logContent.contains("Scoped Entry Composite#isSatisfied"),
                "Scoped verbose evaluation should log the entry composite");
        assertTrue(logContent.contains("Scoped Entry Child 1#isSatisfied"),
                "Scoped verbose evaluation should log first child");
        assertTrue(logContent.contains("Scoped Entry Child 2#isSatisfied"),
                "Scoped verbose evaluation should log second child");

        traceTestLogger.clear();
        assertTrue(strategy.shouldEnter(1, new BaseTradingRecord()));
        assertTrue(traceTestLogger.getLogOutput().contains("mode=VERBOSE"),
                "A scoped strategy evaluation should not suppress later default TRACE behavior");
    }
}
