/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

import java.io.StringWriter;

/**
 * Tests for trace logging in rules, verifying that custom names are used in
 * trace logs when set, and class names are used as fallback.
 */
public class RuleTraceLoggingTest {

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

        // Set trace level for rule loggers
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
        FixedRule rule = new FixedRule(1);
        logOutput.getBuffer().setLength(0);

        rule.isSatisfied(0);

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain class name when no custom name is set",
                logContent.contains("FixedRule#isSatisfied"));
    }

    @Test
    public void traceLoggingUsesCustomNameWhenSet() {
        FixedRule rule = new FixedRule(1);
        rule.setName("My Custom Entry Rule");
        logOutput.getBuffer().setLength(0);

        rule.isSatisfied(0);

        String logContent = logOutput.toString();
        assertTrue("Trace log should contain custom name when set",
                logContent.contains("My Custom Entry Rule#isSatisfied"));
        assertFalse("Trace log should not contain class name when custom name is set",
                logContent.contains("FixedRule#isSatisfied"));
    }

    @Test
    public void traceLoggingFallsBackToClassNameWhenCustomNameIsReset() {
        FixedRule rule = new FixedRule(1);
        rule.setName("My Custom Rule");
        rule.setName(null); // Reset to default
        logOutput.getBuffer().setLength(0);

        rule.isSatisfied(0);

        String logContent = logOutput.toString();
        assertTrue("Trace log should fall back to class name when custom name is reset",
                logContent.contains("FixedRule#isSatisfied"));
        assertFalse("Trace log should not contain custom name after reset",
                logContent.contains("My Custom Rule#isSatisfied"));
    }

    @Test
    public void traceLoggingUsesCustomNameForTrailingStopLossRule() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                        .withData(100, 110, 120, 130, 117.00)
                        .build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, DecimalNumFactory.getInstance().numOf(10));
        rule.setName("5min Trailing Stop");
        logOutput.getBuffer().setLength(0);

        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, DecimalNumFactory.getInstance().numOf(114), DecimalNumFactory.getInstance().numOf(1));
        rule.isSatisfied(4, tradingRecord);

        String logContent = logOutput.toString();
        assertTrue("TrailingStopLossRule trace log should contain custom name when set",
                logContent.contains("5min Trailing Stop#isSatisfied"));
        assertFalse("TrailingStopLossRule trace log should not contain class name when custom name is set",
                logContent.contains("TrailingStopLossRule#isSatisfied"));
    }

    @Test
    public void traceLoggingUsesClassNameForTrailingStopLossRuleWhenNoCustomName() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                        .withData(100, 110, 120, 130, 117.00)
                        .build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, DecimalNumFactory.getInstance().numOf(10));
        logOutput.getBuffer().setLength(0);

        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, DecimalNumFactory.getInstance().numOf(114), DecimalNumFactory.getInstance().numOf(1));
        rule.isSatisfied(4, tradingRecord);

        String logContent = logOutput.toString();
        assertTrue("TrailingStopLossRule trace log should contain class name when no custom name is set",
                logContent.contains("TrailingStopLossRule#isSatisfied"));
    }

    @Test
    public void traceLoggingIncludesAdditionalInfoForTrailingStopLossRule() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                        .withData(100, 110, 120, 130, 117.00)
                        .build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, DecimalNumFactory.getInstance().numOf(10));
        rule.setName("Custom Stop Loss");
        logOutput.getBuffer().setLength(0);

        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, DecimalNumFactory.getInstance().numOf(114), DecimalNumFactory.getInstance().numOf(1));
        rule.isSatisfied(4, tradingRecord);

        String logContent = logOutput.toString();
        assertTrue("TrailingStopLossRule trace log should include custom name",
                logContent.contains("Custom Stop Loss#isSatisfied"));
        assertTrue("TrailingStopLossRule trace log should include current price",
                logContent.contains("Current price:"));
    }

    @Test
    public void traceLoggingWorksForMultipleRulesWithDifferentNames() {
        FixedRule rule1 = new FixedRule(1);
        rule1.setName("Entry Rule 5min");

        FixedRule rule2 = new FixedRule(2);
        rule2.setName("Exit Rule 15min");

        logOutput.getBuffer().setLength(0);
        rule1.isSatisfied(1);
        rule2.isSatisfied(2);

        String logContent = logOutput.toString();
        assertTrue("First rule should use its custom name in trace log",
                logContent.contains("Entry Rule 5min#isSatisfied"));
        assertTrue("Second rule should use its custom name in trace log",
                logContent.contains("Exit Rule 15min#isSatisfied"));
    }
}
