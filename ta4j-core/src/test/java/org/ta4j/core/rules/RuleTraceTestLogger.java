/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.io.StringWriter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Shared support for capturing rule trace logs across rule tests.
 */
final class RuleTraceTestLogger {

    private LoggerContext loggerContext;
    private StringWriter logOutput;
    private Appender appender;
    private Appender consoleAppender;
    private Level originalLevel;
    private LoggerConfig rootLoggerConfig;

    void open() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        rootLoggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);

        originalLevel = rootLoggerConfig.getLevel();

        consoleAppender = rootLoggerConfig.getAppenders().get("Console");
        if (consoleAppender != null) {
            rootLoggerConfig.removeAppender("Console");
        }

        rootLoggerConfig.setLevel(Level.TRACE);
        loggerContext.updateLoggers();

        logOutput = new StringWriter();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        appender = WriterAppender.newBuilder().setTarget(logOutput).setLayout(layout).setName("TestAppender").build();
        appender.start();
        rootLoggerConfig.addAppender(appender, Level.TRACE, null);
        loggerContext.updateLoggers();
    }

    void close() {
        if (loggerContext == null) {
            return;
        }

        if (appender != null) {
            appender.stop();
            Configuration config = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.removeAppender(appender.getName());
        }

        if (consoleAppender != null) {
            rootLoggerConfig.addAppender(consoleAppender, null, null);
        }

        if (originalLevel != null) {
            Configuration config = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(originalLevel);
            loggerContext.updateLoggers();
        }
    }

    void clear() {
        logOutput.getBuffer().setLength(0);
    }

    String getLogOutput() {
        return logOutput.toString();
    }
}
