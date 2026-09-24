/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Shared support for capturing trace logs in black-box tests. While open, every
 * root appender (the test configuration's console, or a stderr appender a CLI
 * run installed) is detached so captured logs never reach build output.
 */
public final class TraceTestLogger {

    private final Set<String> configuredLoggerNames = new HashSet<>();
    private final List<Appender> detachedAppenders = new ArrayList<>();
    private LoggerContext loggerContext;
    private StringWriter logOutput;
    private Appender appender;
    private Level originalLevel;
    private LoggerConfig rootLoggerConfig;

    public void open() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        rootLoggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        originalLevel = rootLoggerConfig.getLevel();

        detachedAppenders.addAll(rootLoggerConfig.getAppenders().values());
        for (Appender detached : detachedAppenders) {
            rootLoggerConfig.removeAppender(detached.getName());
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

    public void close() {
        if (loggerContext == null) {
            return;
        }

        if (appender != null) {
            appender.stop();
            Configuration config = loggerContext.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.removeAppender(appender.getName());
        }

        Configuration config = loggerContext.getConfiguration();
        for (String loggerName : configuredLoggerNames) {
            config.removeLogger(loggerName);
        }
        configuredLoggerNames.clear();

        for (Appender detached : detachedAppenders) {
            rootLoggerConfig.addAppender(detached, null, null);
        }
        detachedAppenders.clear();

        if (originalLevel != null) {
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(originalLevel);
        }
        loggerContext.updateLoggers();
    }

    public void setLoggerLevel(Class<?> loggerClass, Level level) {
        String loggerName = loggerClass.getName();
        Configuration config = loggerContext.getConfiguration();
        config.removeLogger(loggerName);
        config.addLogger(loggerName, new LoggerConfig(loggerName, level, true));
        configuredLoggerNames.add(loggerName);
        loggerContext.updateLoggers();
    }

    public void clearLoggerLevel(Class<?> loggerClass) {
        String loggerName = loggerClass.getName();
        Configuration config = loggerContext.getConfiguration();
        config.removeLogger(loggerName);
        configuredLoggerNames.remove(loggerName);
        loggerContext.updateLoggers();
    }

    public void clear() {
        logOutput.getBuffer().setLength(0);
    }

    public String getLogOutput() {
        return logOutput.toString();
    }
}
