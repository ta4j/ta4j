/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
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

    private LoggerContext loggerContext;
    private StringWriter logOutput;
    private Appender appender;
    private Appender consoleAppender;
    private Level originalLevel;
    private LoggerConfig rootLoggerConfig;

    @Before
    public void setUp() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        rootLoggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);

        // Store original level
        originalLevel = rootLoggerConfig.getLevel();

        // Temporarily remove Console appender to prevent trace logs from going to
        // stdout
        // This keeps build output clean while still allowing us to capture trace logs
        consoleAppender = rootLoggerConfig.getAppenders().get("Console");
        if (consoleAppender != null) {
            rootLoggerConfig.removeAppender("Console");
        }

        // Set trace level for strategy loggers
        rootLoggerConfig.setLevel(Level.TRACE);
        loggerContext.updateLoggers();

        // Create a string writer to capture log output
        logOutput = new StringWriter();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        appender = WriterAppender.newBuilder().setTarget(logOutput).setLayout(layout).setName("TestAppender").build();
        appender.start();

        // Add appender to root logger to capture trace logs
        rootLoggerConfig.addAppender(appender, Level.TRACE, null);
        loggerContext.updateLoggers();
    }

    @After
    public void tearDown() {
        if (appender != null) {
            appender.stop();
            Configuration config = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.removeAppender(appender.getName());
        }

        // Restore Console appender if it was removed
        if (consoleAppender != null) {
            rootLoggerConfig.addAppender(consoleAppender, null, null);
        }

        // Restore original level
        if (originalLevel != null) {
            Configuration config = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(originalLevel);
            loggerContext.updateLoggers();
        }
    }

    @Test
    public void traceLoggingUsesClassNameWhenNoCustomNameSet() {
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);
        logOutput.getBuffer().setLength(0);

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain class name when no custom name is set",
                logContent.contains("BaseStrategy#shouldEnter"));
    }

    @Test
    public void traceLoggingUsesCustomNameWhenSet() {
        Strategy strategy = new BaseStrategy("My Custom Strategy", new FixedRule(1), new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);
        logOutput.getBuffer().setLength(0);

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain custom name when set",
                logContent.contains("My Custom Strategy#shouldEnter"));
        assertFalse("Trace log should not contain class name when custom name is set",
                logContent.contains("BaseStrategy#shouldEnter"));
    }

    @Test
    public void traceLoggingUsesCustomNameForShouldExit() {
        Strategy strategy = new BaseStrategy("5min Entry Strategy", new FixedRule(1), new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);
        logOutput.getBuffer().setLength(0);

        strategy.shouldExit(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain custom name for shouldExit",
                logContent.contains("5min Entry Strategy#shouldExit"));
        assertFalse("Trace log should not contain class name when custom name is set",
                logContent.contains("BaseStrategy#shouldExit"));
    }

    @Test
    public void traceLoggingUsesClassNameForShouldExitWhenNoCustomName() {
        Strategy strategy = new BaseStrategy(new FixedRule(1), new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);
        logOutput.getBuffer().setLength(0);

        strategy.shouldExit(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain class name for shouldExit when no custom name is set",
                logContent.contains("BaseStrategy#shouldExit"));
    }

    @Test
    public void traceLoggingWorksForBothShouldEnterAndShouldExit() {
        Strategy strategy = new BaseStrategy("Multi-Timeframe Strategy", new FixedRule(1), new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);
        logOutput.getBuffer().setLength(0);

        strategy.shouldEnter(0, new BaseTradingRecord());
        strategy.shouldExit(1, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain custom name for shouldEnter",
                logContent.contains("Multi-Timeframe Strategy#shouldEnter"));
        assertTrue("Trace log should contain custom name for shouldExit",
                logContent.contains("Multi-Timeframe Strategy#shouldExit"));
    }

    @Test
    public void traceLoggingWorksForMultipleStrategiesWithDifferentNames() {
        Strategy strategy1 = new BaseStrategy("Strategy 5min", new FixedRule(1), new FixedRule(2));
        Strategy strategy2 = new BaseStrategy("Strategy 15min", new FixedRule(3), new FixedRule(4));
        strategy1.setTraceMode(Rule.TraceMode.VERBOSE);
        strategy2.setTraceMode(Rule.TraceMode.VERBOSE);

        logOutput.getBuffer().setLength(0);
        strategy1.shouldEnter(0, new BaseTradingRecord());
        strategy2.shouldEnter(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("First strategy should use its custom name in trace log",
                logContent.contains("Strategy 5min#shouldEnter"));
        assertTrue("Second strategy should use its custom name in trace log",
                logContent.contains("Strategy 15min#shouldEnter"));
    }

    @Test
    public void traceLoggingIncludesPrefixForStrategyTraces() {
        Strategy strategy = new BaseStrategy("Test Strategy", new FixedRule(1), new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);
        logOutput.getBuffer().setLength(0);

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Trace log should include >>> prefix for strategy traces", logContent.contains(">>>"));
        assertTrue("Trace log should contain custom name after prefix",
                logContent.contains(">>> Test Strategy#shouldEnter"));
    }

    @Test
    public void traceLoggingIsOffByDefaultForStrategy() {
        Strategy strategy = new BaseStrategy("Default Off Strategy", new FixedRule(1), new FixedRule(2));
        logOutput.getBuffer().setLength(0);

        strategy.shouldEnter(0, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertFalse("Strategy trace log should be empty unless trace mode is explicitly enabled",
                logContent.contains("Default Off Strategy#shouldEnter"));
    }

    @Test
    public void traceLoggingRollupModeEvaluatesEntryRuleWithScopedTracePolicy() {
        FixedRule child1 = new FixedRule(1);
        child1.setName("Entry Child 1");
        child1.setTraceMode(Rule.TraceMode.VERBOSE);
        FixedRule child2 = new FixedRule(1);
        child2.setName("Entry Child 2");
        child2.setTraceMode(Rule.TraceMode.VERBOSE);
        AndRule entryRule = new AndRule(child1, child2);
        entryRule.setName("Entry Composite");
        Strategy strategy = new BaseStrategy("Trace Strategy", entryRule, new FixedRule(2));
        strategy.setTraceMode(Rule.TraceMode.ROLLUP);

        logOutput.getBuffer().setLength(0);
        strategy.shouldEnter(1, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Rollup mode should log the strategy decision",
                logContent.contains(">>> Trace Strategy#shouldEnter"));
        assertTrue("Rollup mode should log the entry rule", logContent.contains("Entry Composite#isSatisfied"));
        assertTrue("Rollup mode should mark the scoped root path",
                logContent.contains("traceMode=ROLLUP ruleType=AndRule path=entryRule depth=0 parent=Trace Strategy"));
        assertFalse("Rollup mode should suppress first child logs", logContent.contains("Entry Child 1#isSatisfied"));
        assertFalse("Rollup mode should suppress second child logs", logContent.contains("Entry Child 2#isSatisfied"));
        assertTrue("Rollup mode should not mutate child trace mode",
                child1.getTraceMode() == Rule.TraceMode.VERBOSE && child2.getTraceMode() == Rule.TraceMode.VERBOSE);
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
        strategy.setTraceMode(Rule.TraceMode.VERBOSE);

        logOutput.getBuffer().setLength(0);
        strategy.shouldExit(2, new BaseTradingRecord());

        String logContent = logOutput.toString();
        assertTrue("Verbose mode should log the strategy exit decision",
                logContent.contains(">>> Trace Strategy#shouldExit"));
        assertTrue("Verbose mode should log the exit rule", logContent.contains("Exit Composite#isSatisfied"));
        assertTrue("Verbose mode should log first exit child", logContent.contains("Exit Child 1#isSatisfied"));
        assertTrue("Verbose mode should log second exit child", logContent.contains("Exit Child 2#isSatisfied"));
        assertTrue("Verbose mode should attribute the first child path",
                logContent.contains("path=exitRule.rule1 depth=1"));
        assertTrue("Verbose mode should attribute the second child path",
                logContent.contains("path=exitRule.rule2 depth=1"));
    }
}
