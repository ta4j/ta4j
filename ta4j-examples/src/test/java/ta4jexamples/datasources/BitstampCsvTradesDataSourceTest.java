/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * Unit tests for the {@link BitstampCsvTradesDataSource} class.
 */
public class BitstampCsvTradesDataSourceTest {

    @Test
    public void testMain() {
        BitstampCsvTradesDataSource.main(null);
    }

    @Test
    public void testLoadSeriesWithStandardNamingPattern() {
        // Test loading Bitstamp BTC data using domain-driven interface with standard
        // naming pattern
        // Pattern: Bitstamp-{ticker}-{interval}-{startDate}_{endDate}.csv
        String expectedFile = "Bitstamp-BTC-USD-PT5M-20131125_20131201.csv";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        BitstampCsvTradesDataSource dataSource = new BitstampCsvTradesDataSource();
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
        BarSeries series = BitstampCsvTradesDataSource
                .loadBitstampSeries("Bitstamp-BTC-USD-PT5M-20131125_20131201.csv");

        assertNotNull(series, "Should load series from direct filename with Bitstamp prefix");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
    }

    @Test
    public void testLoadSeriesWithDefaultFile() {
        // Test loading default Bitstamp file
        BarSeries series = BitstampCsvTradesDataSource.loadBitstampSeries();

        assertNotNull(series, "Should load default Bitstamp series");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
    }

    @Test
    public void testLoadSeriesWithNonExistentTicker() {
        BitstampCsvTradesDataSource dataSource = new BitstampCsvTradesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        BarSeries series = dataSource.loadSeries("NONEXISTENT-USD", Duration.ofMinutes(5), start, end);

        assertNull(series, "Should return null for non-existent ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidDateRange() {
        BitstampCsvTradesDataSource dataSource = new BitstampCsvTradesDataSource();
        Instant start = Instant.parse("2013-12-01T23:59:59Z");
        Instant end = Instant.parse("2013-11-25T00:00:00Z"); // End before start

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofMinutes(5), start, end);
        }, "Should throw IllegalArgumentException when end date is before start date");
    }

    @Test
    public void testLoadSeriesWithNullTicker() {
        BitstampCsvTradesDataSource dataSource = new BitstampCsvTradesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries(null, Duration.ofMinutes(5), start, end);
        }, "Should throw IllegalArgumentException for null ticker");
    }

    @Test
    public void testLoadSeriesWithEmptyTicker() {
        BitstampCsvTradesDataSource dataSource = new BitstampCsvTradesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("", Duration.ofMinutes(5), start, end);
        }, "Should throw IllegalArgumentException for empty ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidInterval() {
        BitstampCsvTradesDataSource dataSource = new BitstampCsvTradesDataSource();
        Instant start = Instant.parse("2013-11-25T00:00:00Z");
        Instant end = Instant.parse("2013-12-01T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ZERO, start, end);
        }, "Should throw IllegalArgumentException for zero interval");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofSeconds(-1), start, end);
        }, "Should throw IllegalArgumentException for negative interval");
    }
}
