/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assume.assumeThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link BitStampCsvTradesFileBarSeriesDataSource} class.
 */
public class BitStampCSVTradesBarSeriesDataSourceTest {

    private StringWriter logOutput;
    private Appender appender;

    @BeforeEach
    public void setUp() {
        logOutput = new StringWriter();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%level %msg%n").build();
        appender = WriterAppender.newBuilder().setTarget(logOutput).setLayout(layout).setName("TestAppender").build();
        appender.start();
        config.addAppender(appender);
        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager
                .getLogger(BitStampCsvTradesFileBarSeriesDataSource.class);
        logger.addAppender(appender);
        logger.setLevel(org.apache.logging.log4j.Level.WARN);
    }

    @AfterEach
    public void tearDown() {
        if (appender != null) {
            org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager
                    .getLogger(BitStampCsvTradesFileBarSeriesDataSource.class);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testMain() {
        BitStampCsvTradesFileBarSeriesDataSource.main(null);
    }

    @Test
    public void testLoadSeriesWithStandardNamingPattern() {
        // Test loading Bitstamp BTC data using domain-driven interface with standard
        // naming pattern
        // Pattern: Bitstamp-{ticker}-{interval}-{startDate}_{endDate}.csv
        String expectedFile = "Bitstamp-BTC-USD-PT5M-20131125_20131201.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        BarSeries series = dataSource.loadSeries("BTC-USD", Duration.ofMinutes(5), start, end);

        assertNotNull(series, "Should load series using standard naming pattern with Bitstamp prefix");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
        assertEquals(expectedFile, series.getName(), "Series name should match the filename with Bitstamp prefix");
    }

    @Test
    public void testLoadSeriesWithDirectFilename() {
        // Test loading by direct filename (backward compatibility)
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource
                .loadBitstampSeries("Bitstamp-BTC-USD-PT5M-20131125_20131201.csv");

        assertNotNull(series, "Should load series from direct filename with Bitstamp prefix");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
    }

    @Test
    public void testLoadSeriesWithDefaultFile() {
        // Test loading default Bitstamp file
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();

        assertNotNull(series, "Should load default Bitstamp series");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
    }

    @Test
    public void testLoadSeriesWithNonExistentTicker() {
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        BarSeries series = dataSource.loadSeries("NONEXISTENT-USD", Duration.ofMinutes(5), start, end);

        assertNull(series, "Should return null for non-existent ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidDateRange() {
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-12-01T23:59:59Z");
        Instant end = Instant.parse("2013-11-25T00:00:00Z"); // End before start

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofMinutes(5), start, end);
        }, "Should throw IllegalArgumentException when end date is before start date");
    }

    @Test
    public void testLoadSeriesWithNullTicker() {
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries(null, Duration.ofMinutes(5), start, end);
        }, "Should throw IllegalArgumentException for null ticker");
    }

    @Test
    public void testLoadSeriesWithEmptyTicker() {
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("", Duration.ofMinutes(5), start, end);
        }, "Should throw IllegalArgumentException for empty ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidInterval() {
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ZERO, start, end);
        }, "Should throw IllegalArgumentException for zero interval");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofSeconds(-1), start, end);
        }, "Should throw IllegalArgumentException for negative interval");
    }

    @Test
    public void testGetSourceName() {
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        assertEquals("Bitstamp", dataSource.getSourceName(), "Should return 'Bitstamp' as source name");
    }

    @Test
    public void testGetSourceNameUsedInFileSearchPattern() {
        // Verify that getSourceName() is used in file search patterns
        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        String sourceName = dataSource.getSourceName();
        assertFalse(sourceName.isEmpty(), "Source name should not be empty");
        assertEquals("Bitstamp", sourceName, "Source name should be 'Bitstamp'");

        // Test that file search uses the source name prefix
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");
        String expectedFile = "Bitstamp-BTC-USD-PT5M-20131125_20131201.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        BarSeries series = dataSource.loadSeries("BTC-USD", Duration.ofMinutes(5), start, end);
        assertNotNull(series, "Should find file using source name prefix");
        assertTrue(expectedFile.startsWith(sourceName + "-"), "Expected file should start with source name prefix");
    }

    @Test
    public void testLoadSeriesWithMismatchedIntervalReturnsNull() {
        // Test that when requesting a different interval than what's in the file,
        // null is returned (file search won't find files with mismatched intervals).
        //
        // Note: The fix in filterAndAggregateSeries ensures that if a file is ever
        // found but has a mismatched interval, a warning will be logged instead of
        // failing silently. However, the current file search logic prevents this
        // scenario from occurring through the public API, so we test the expected
        // behavior (null return) rather than the warning.
        String expectedFile = "Bitstamp-BTC-USD-PT5M-20131125_20131201.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        BitStampCsvTradesFileBarSeriesDataSource dataSource = new BitStampCsvTradesFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        // First verify the file can be loaded with matching interval
        BarSeries seriesWithMatchingInterval = dataSource.loadSeries("BTC-USD", Duration.ofMinutes(5), start, end);
        assertNotNull(seriesWithMatchingInterval, "File should be loadable with matching 5-minute interval");

        // Request 1-hour bars - file search won't find the 5-minute file, so returns
        // null
        BarSeries series = dataSource.loadSeries("BTC-USD", Duration.ofHours(1), start, end);
        assertNull(series, "Should return null when no file matches the requested interval");
    }
}
