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
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.indicators.MACDVIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

@SuppressWarnings({ "deprecation", "removal" })
public class DeprecationNotifierTest {

    private LoggerContext loggerContext;
    private LoggerConfig loggerConfig;
    private Level originalLevel;
    private Appender appender;
    private StringWriter logOutput;

    @Before
    public void setUp() {
        DeprecationNotifier.resetForTests();

        loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        loggerConfig = config.getLoggerConfig(DeprecationNotifier.class.getName());
        originalLevel = loggerConfig.getLevel();

        logOutput = new StringWriter();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        appender = WriterAppender.newBuilder()
                .setTarget(logOutput)
                .setLayout(layout)
                .setName("DeprecationNotifierAppender")
                .build();
        appender.start();

        loggerConfig.addAppender(appender, Level.WARN, null);
        loggerConfig.setLevel(Level.WARN);
        loggerContext.updateLoggers();
    }

    @After
    public void tearDown() {
        if (loggerConfig != null && appender != null) {
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
        }
        if (loggerConfig != null && originalLevel != null) {
            loggerConfig.setLevel(originalLevel);
        }
        if (loggerContext != null) {
            loggerContext.updateLoggers();
        }
        DeprecationNotifier.resetForTests();
    }

    @Test
    public void warnOnceLogsSingleMessagePerDeprecatedType() {
        DeprecationNotifier.warnOnce(MACDVIndicator.class, "org.ta4j.core.indicators.macd.MACDVIndicator", "0.24.0");
        DeprecationNotifier.warnOnce(MACDVIndicator.class, "org.ta4j.core.indicators.macd.MACDVIndicator", "0.24.0");

        String logContent = logOutput.toString();
        assertThat(logContent).contains(
                "org.ta4j.core.indicators.MACDVIndicator is deprecated since 0.22.3 and is scheduled for removal in 0.24.0. Use org.ta4j.core.indicators.macd.MACDVIndicator instead.");
        assertThat(countOccurrences(logContent, "org.ta4j.core.indicators.MACDVIndicator is deprecated since"))
                .isEqualTo(1);
    }

    @Test
    public void macdvShimConstructorEmitsDeprecationWarning() {
        BarSeries series = new MockBarSeriesBuilder()
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                        27, 28)
                .build();

        new MACDVIndicator(series);
        new MACDVIndicator(series, 12, 26, 9);

        String logContent = logOutput.toString();
        assertThat(logContent).contains("scheduled for removal in 0.24.0");
        assertThat(logContent).contains("org.ta4j.core.indicators.macd.MACDVIndicator");
        assertThat(countOccurrences(logContent, "org.ta4j.core.indicators.MACDVIndicator is deprecated since"))
                .isEqualTo(1);
    }

    @Test
    public void movedCriteriaShimsEmitDeprecationWarnings() {
        new MaximumDrawdownCriterion();
        new MaximumDrawdownCriterion();
        new ReturnOverMaxDrawdownCriterion();
        new ReturnOverMaxDrawdownCriterion();

        String logContent = logOutput.toString();
        assertThat(logContent).contains(
                "org.ta4j.core.criteria.MaximumDrawdownCriterion is deprecated since 0.19 and is scheduled for removal in 0.24.0.");
        assertThat(logContent).contains("Use org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion instead.");
        assertThat(logContent).contains(
                "org.ta4j.core.criteria.ReturnOverMaxDrawdownCriterion is deprecated since 0.19 and is scheduled for removal in 0.24.0.");
        assertThat(logContent).contains("Use org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion instead.");

        assertThat(countOccurrences(logContent, "org.ta4j.core.criteria.MaximumDrawdownCriterion is deprecated"))
                .isEqualTo(1);
        assertThat(countOccurrences(logContent, "org.ta4j.core.criteria.ReturnOverMaxDrawdownCriterion is deprecated"))
                .isEqualTo(1);
    }

    @Test
    public void warnOnceSupportsUnsetRemovalVersion() {
        DeprecationNotifier.warnOnce(MACDVIndicator.class, "org.ta4j.core.indicators.macd.MACDVIndicator", null);

        String logContent = logOutput.toString();
        assertThat(logContent).contains(
                "org.ta4j.core.indicators.MACDVIndicator is deprecated and will be removed at some point in the future.");
        assertThat(logContent).contains("Consider yourself fairly warned!");
        assertThat(logContent).contains("Use org.ta4j.core.indicators.macd.MACDVIndicator instead.");
        assertThat(logContent).doesNotContain("scheduled for removal in");
    }

    @Test
    public void warnOnceSupportsBlankRemovalVersion() {
        DeprecationNotifier.warnOnce(MACDVIndicator.class, "org.ta4j.core.indicators.macd.MACDVIndicator", "   ");

        String logContent = logOutput.toString();
        assertThat(logContent).contains(
                "org.ta4j.core.indicators.MACDVIndicator is deprecated and will be removed at some point in the future.");
        assertThat(logContent).contains("Consider yourself fairly warned!");
        assertThat(logContent).contains("Use org.ta4j.core.indicators.macd.MACDVIndicator instead.");
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
}
