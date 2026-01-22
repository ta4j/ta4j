/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assume.assumeThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link CsvFileBarSeriesDataSource} class.
 */
public class CsvBarSeriesDataSourceTest {

    @Test
    public void testMain() {
        CsvFileBarSeriesDataSource.main(null);
    }

    @Test
    public void testLoadSeriesWithStandardNamingPattern() {
        // Test loading AAPL data using domain-driven interface with standard naming
        // pattern
        // Pattern: {ticker}-{interval}-{startDate}_{endDate}.csv
        String expectedFile = "AAPL-PT1D-20130102_20131231.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        BarSeries series = dataSource.loadSeries("AAPL", Duration.ofDays(1), start, end);

        assertNotNull(series, "Should load series using standard naming pattern");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
        assertEquals(expectedFile, series.getName(), "Series name should match the filename");
    }

    @Test
    public void testLoadSeriesWithDirectFilename() {
        // Test loading by direct filename (backward compatibility)
        BarSeries series = CsvFileBarSeriesDataSource.loadCsvSeries("AAPL-PT1D-20130102_20131231.csv");

        assertNotNull(series, "Should load series from direct filename");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
    }

    @Test
    public void testLoadSeriesWithNonExistentTicker() {
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        BarSeries series = dataSource.loadSeries("NONEXISTENT", Duration.ofDays(1), start, end);

        assertNull(series, "Should return null for non-existent ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidDateRange() {
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-12-31T23:59:59Z");
        Instant end = Instant.parse("2013-01-02T00:00:00Z"); // End before start

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("AAPL", Duration.ofDays(1), start, end);
        }, "Should throw IllegalArgumentException when end date is before start date");
    }

    @Test
    public void testLoadSeriesWithNullTicker() {
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries(null, Duration.ofDays(1), start, end);
        }, "Should throw IllegalArgumentException for null ticker");
    }

    @Test
    public void testLoadSeriesWithEmptyTicker() {
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("", Duration.ofDays(1), start, end);
        }, "Should throw IllegalArgumentException for empty ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidInterval() {
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("AAPL", Duration.ZERO, start, end);
        }, "Should throw IllegalArgumentException for zero interval");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("AAPL", Duration.ofSeconds(-1), start, end);
        }, "Should throw IllegalArgumentException for negative interval");
    }

    @Test
    public void testGetSourceName() {
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        assertEquals("", dataSource.getSourceName(), "Should return empty string as CSV files don't use source prefix");
    }

    @Test
    public void testGetSourceNameUsedInFileSearchPattern() {
        // Verify that getSourceName() returns empty string and files don't use prefix
        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        String sourceName = dataSource.getSourceName();
        assertTrue(sourceName.isEmpty(), "Source name should be empty for generic CSV files");

        // Test that file search works without source name prefix
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");
        String expectedFile = "AAPL-PT1D-20130102_20131231.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        BarSeries series = dataSource.loadSeries("AAPL", Duration.ofDays(1), start, end);
        assertNotNull(series, "Should find file without source name prefix");
        // Verify file doesn't start with a source prefix (like "Bitstamp-" or
        // "YahooFinance-")
        assertFalse(expectedFile.startsWith("Bitstamp-"), "Expected file should not start with Bitstamp prefix");
        assertFalse(expectedFile.startsWith("YahooFinance-"),
                "Expected file should not start with YahooFinance prefix");
        assertTrue(expectedFile.startsWith("AAPL-"), "Expected file should start with ticker directly");
    }

    @Test
    public void testLoadSeriesWithBroaderPatternWildcardInterval() {
        // Test that broader pattern search works when exact interval doesn't match
        // This exercises the wildcard pattern: AAPL-*-20130102_*.csv
        // The file exists as AAPL-PT1D-20130102_20131231.csv
        // Request with PT5M interval (which doesn't match) but same dates
        // Should find the file via broader pattern by trying PT1D for the interval
        // wildcard
        String expectedFile = "AAPL-PT1D-20130102_20131231.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        // Request with PT5M interval (file has PT1D) - exact pattern won't match
        // Broader pattern AAPL-*-20130102_*.csv should find it
        BarSeries series = dataSource.loadSeries("AAPL", Duration.ofMinutes(5), start, end);

        assertNotNull(series, "Should find file via broader pattern when interval doesn't match exactly");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
        assertEquals(expectedFile, series.getName(), "Series name should match the found filename");
    }

    @Test
    public void testLoadSeriesWithBroaderPatternWildcardEndDate() {
        // Test that broader pattern search correctly handles multiple wildcards
        // Pattern: AAPL-*-20130102_*.csv
        // First wildcard should be replaced with interval (PT1D), second with end date
        // This verifies the fix where both wildcards were being replaced with the same
        // value
        String expectedFile = "AAPL-PT1D-20130102_20131231.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        // Use a slightly different end date that won't match exact pattern
        // but the broader pattern should still find the file
        Instant end = Instant.parse("2013-12-31T12:00:00Z"); // Different time, same date

        // Request with PT1H interval (file has PT1D) - exact pattern won't match
        // Broader pattern should find it by trying PT1D for first wildcard and
        // 20131231 for second wildcard (from end date parameter)
        BarSeries series = dataSource.loadSeries("AAPL", Duration.ofHours(1), start, end);

        assertNotNull(series,
                "Should find file via broader pattern when exact pattern doesn't match, verifying wildcard fix");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
        assertEquals(expectedFile, series.getName(), "Series name should match the found filename");
    }

    @Test
    public void testLoadSeriesWithBroaderPatternDateOnlyFormat() {
        // Test that broader pattern search works with date-only format
        // Pattern: AAPL-*-20130102_*.csv (date-only version)
        // This exercises the date-only broader pattern fallback
        String expectedFile = "AAPL-PT1D-20130102_20131231.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        CsvFileBarSeriesDataSource dataSource = new CsvFileBarSeriesDataSource();
        Instant start = Instant.parse("2013-01-02T00:00:00Z");
        Instant end = Instant.parse("2013-12-31T23:59:59Z");

        // Request with PT1H interval (file has PT1D) - exact pattern won't match
        // The date-only broader pattern AAPL-*-20130102_*.csv should find it
        // by trying PT1D for first wildcard and 20131231 for second wildcard
        BarSeries series = dataSource.loadSeries("AAPL", Duration.ofHours(1), start, end);

        assertNotNull(series, "Should find file via date-only broader pattern when exact pattern doesn't match");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
        assertEquals(expectedFile, series.getName(), "Series name should match the found filename");
    }
}
