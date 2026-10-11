/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

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

public class DeprecationNotifierTest {

    private static final String LOGGER_NAME = DeprecationNotifier.class.getName();

    private LoggerContext loggerContext;
    private Configuration loggerConfiguration;
    private LoggerConfig loggerConfig;
    private LoggerConfig previousLoggerConfig;
    private Appender appender;
    private StringWriter logOutput;

    @Before
    public void setUp() {
        DeprecationNotifier.resetForTests();

        loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerConfiguration = loggerContext.getConfiguration();
        previousLoggerConfig = loggerConfiguration.getLoggers().get(LOGGER_NAME);

        logOutput = new StringWriter();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        appender = WriterAppender.newBuilder()
                .setTarget(logOutput)
                .setLayout(layout)
                .setName("DeprecationNotifierAppender")
                .build();
        appender.start();

        loggerConfig = new LoggerConfig(LOGGER_NAME, Level.WARN, false);
        loggerConfig.addAppender(appender, Level.WARN, null);
        loggerConfiguration.addLogger(LOGGER_NAME, loggerConfig);
        loggerContext.updateLoggers();
    }

    @After
    public void tearDown() {
        if (loggerConfiguration != null) {
            loggerConfiguration.removeLogger(LOGGER_NAME);
            if (previousLoggerConfig != null) {
                loggerConfiguration.addLogger(LOGGER_NAME, previousLoggerConfig);
            }
        }
        if (appender != null) {
            appender.stop();
        }
        if (loggerContext != null) {
            loggerContext.updateLoggers();
        }
        DeprecationNotifier.resetForTests();
    }

    @Test
    public void warnOnceLogsSingleMessagePerDeprecatedType() {
        Class<?> deprecatedType = DeprecatedSinceFixture.class;

        DeprecationNotifier.warnOnce(deprecatedType, "replacement.Type", "0.24.0");
        DeprecationNotifier.warnOnce(deprecatedType, "replacement.Type", "0.24.0");

        String logContent = logOutput.toString();
        assertThat(logContent).contains(deprecatedType.getName()
                + " is deprecated since 1.2.3 and is scheduled for removal in 0.24.0. Use replacement.Type instead.");
        assertThat(countOccurrences(logContent, deprecatedType.getName() + " is deprecated since")).isEqualTo(1);
    }

    @Test
    public void warnOnceSupportsUnsetRemovalVersion() {
        Class<?> deprecatedType = DeprecatedWithoutSinceFixture.class;

        DeprecationNotifier.warnOnce(deprecatedType, "replacement.Type", null);

        String logContent = logOutput.toString();
        assertThat(logContent)
                .contains(deprecatedType.getName() + " is deprecated and will be removed at some point in the future.");
        assertThat(logContent).contains("Consider yourself fairly warned!");
        assertThat(logContent).contains("Use replacement.Type instead.");
        assertThat(logContent).doesNotContain("scheduled for removal in");
    }

    @Test
    public void warnOnceSupportsBlankRemovalVersion() {
        Class<?> deprecatedType = DeprecatedWithoutSinceFixture.class;

        DeprecationNotifier.warnOnce(deprecatedType, "replacement.Type", "   ");

        String logContent = logOutput.toString();
        assertThat(logContent)
                .contains(deprecatedType.getName() + " is deprecated and will be removed at some point in the future.");
        assertThat(logContent).contains("Consider yourself fairly warned!");
        assertThat(logContent).contains("Use replacement.Type instead.");
        assertThat(logContent).doesNotContain("scheduled for removal in");
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    @Deprecated(since = "1.2.3", forRemoval = true)
    private static final class DeprecatedSinceFixture {
    }

    @Deprecated
    private static final class DeprecatedWithoutSinceFixture {
    }
}
