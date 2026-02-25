/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({ "deprecation", "removal" })
class DeprecatedJsonDataSourcesNotifierTest {

    private LoggerContext loggerContext;
    private LoggerConfig loggerConfig;
    private Level originalLevel;
    private Appender appender;
    private StringWriter logOutput;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        loggerConfig = config.getLoggerConfig("org.ta4j.core.utils.DeprecationNotifier");
        originalLevel = loggerConfig.getLevel();

        logOutput = new StringWriter();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        appender = WriterAppender.newBuilder()
                .setTarget(logOutput)
                .setLayout(layout)
                .setName("DeprecatedJsonDataSourcesNotifierAppender")
                .build();
        appender.start();

        loggerConfig.addAppender(appender, Level.WARN, null);
        loggerConfig.setLevel(Level.WARN);
        loggerContext.updateLoggers();
    }

    @AfterEach
    void tearDown() {
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
    }

    @Test
    void deprecatedJsonHelpersEmitMigrationWarnings() {
        BarSeries series = buildSeries();

        JsonBarsSerializer.loadSeries((java.io.InputStream) null);
        GsonBarSeries.from(series);
        GsonBarData.from(series.getBar(0));

        String logContent = logOutput.toString();

        assertTrue(logContent.contains("ta4jexamples.datasources.json.JsonBarsSerializer is deprecated"));
        assertTrue(logContent.contains("ta4jexamples.datasources.json.GsonBarSeries is deprecated"));
        assertTrue(logContent.contains("ta4jexamples.datasources.json.GsonBarData is deprecated"));
        assertTrue(logContent.contains("Use ta4jexamples.datasources.json.JsonFileBarSeriesDataSource instead."));
        assertTrue(logContent.contains("scheduled for removal in 0.24.0"));

        assertEquals(1, countOccurrences(logContent, "ta4jexamples.datasources.json.JsonBarsSerializer is deprecated"));
        assertEquals(1, countOccurrences(logContent, "ta4jexamples.datasources.json.GsonBarSeries is deprecated"));
        assertEquals(1, countOccurrences(logContent, "ta4jexamples.datasources.json.GsonBarData is deprecated"));
    }

    private static BarSeries buildSeries() {
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName("deprecated-json-series");
        BarSeries series = builder.build();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-01T00:00:00Z"))
                .openPrice(100)
                .highPrice(110)
                .lowPrice(90)
                .closePrice(105)
                .volume(1000)
                .amount(105000)
                .add();
        return series;
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
