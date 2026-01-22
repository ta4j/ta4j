/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assume.assumeThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link JsonFileBarSeriesDataSource} class.
 * <p>
 * This test class verifies the behavior of the
 * {@code JsonFileBarSeriesDataSource} when loading bar series data from various
 * JSON input streams, including valid Coinbase and Binance formatted data, as
 * well as edge cases such as a null input stream.
 * </p>
 */
public class JsonBarSeriesDataSourceTest {

    @Test
    public void testLoadCoinbaseInputStream() {
        String jsonFile = "Coinbase-ETH-USD-PT1D-20241105_20251020.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFile);
        assumeThat("File " + jsonFile + " does not exist", inputStream, is(notNullValue()));

        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(inputStream);

        assertNotNull(series, "BarSeries should be loaded successfully with deserializer");
        assertTrue(series.getBarCount() > 0, "BarSeries should contain bars");
        assertEquals("CoinbaseData", series.getName(), "Series name should be set correctly");

        // Verify first bar data
        var firstBar = series.getBar(0);
        assertNotNull(firstBar, "First bar should not be null");
        assertTrue(firstBar.getClosePrice().doubleValue() > 0, "Close price should be positive");
        assertTrue(firstBar.getVolume().doubleValue() > 0, "Volume should be positive");
    }

    @Test
    public void testLoadBinanceInputStream() {
        String jsonFile = "Binance-ETH-USD-PT5M-20230313_20230315.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFile);
        assumeThat("File " + jsonFile + " does not exist", inputStream, is(notNullValue()));

        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(inputStream);

        assertNotNull(series, "BarSeries should be loaded successfully");
        assertTrue(series.getBarCount() > 0, "BarSeries should contain bars");

        // Verify first bar data
        var firstBar = series.getBar(0);
        assertNotNull(firstBar, "First bar should not be null");
        assertTrue(firstBar.getClosePrice().doubleValue() > 0, "Close price should be positive");
        assertTrue(firstBar.getVolume().doubleValue() > 0, "Volume should be positive");

    }

    @Test
    public void testLoadNullInputStream() {
        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries((InputStream) null);
        assertNull(series, "Should return null for null input stream");
    }

    @Test
    public void testLoadSeriesFromValidFile() {
        String jsonFile = "Coinbase-ETH-USD-PT1D-20241105_20251020.json";
        String resourcePath = Objects.requireNonNull(getClass().getClassLoader().getResource(jsonFile)).getPath();

        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resourcePath);

        assertNotNull(series, "BarSeries should be loaded successfully from file");
        assertTrue(series.getBarCount() > 0, "BarSeries should contain bars");
        assertEquals("CoinbaseData", series.getName(), "Series name should be set correctly");

        // Verify first bar data
        var firstBar = series.getBar(0);
        assertNotNull(firstBar, "First bar should not be null");
        assertTrue(firstBar.getClosePrice().doubleValue() > 0, "Close price should be positive");
        assertTrue(firstBar.getVolume().doubleValue() > 0, "Volume should be positive");

        // Verify last bar data
        var lastBar = series.getBar(series.getBarCount() - 1);
        assertNotNull(lastBar, "Last bar should not be null");
        assertTrue(lastBar.getClosePrice().doubleValue() > 0, "Last bar close price should be positive");
        assertTrue(lastBar.getVolume().doubleValue() > 0, "Last bar volume should be positive");
    }

    @Test
    public void testLoadSeriesFromNonExistentFile() {
        String nonExistentPath = "non-existent-file.json";

        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(nonExistentPath);

        assertNull(series, "Should return null for non-existent file");
    }

    @Test
    public void testLoadSeriesFromNullFilename() {
        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries((String) null);

        assertNull(series, "Should return null for null filename");
    }

    @Test
    public void testLoadSeriesFromEmptyFilename() {
        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries("");

        assertNull(series, "Should return null for empty filename");
    }

    @Test
    public void testLoadSeriesFromInvalidJsonFile() {
        // Create a temporary file with invalid JSON content in temp/ directory
        String invalidJsonContent = "invalid json content";
        Path tempDir = Paths.get("temp");
        Path tempFile = null;

        try {
            // Ensure temp directory exists
            Files.createDirectories(tempDir);

            // Create temp file in temp/ directory
            tempFile = tempDir.resolve("invalid-" + System.currentTimeMillis() + ".json");
            Files.write(tempFile, invalidJsonContent.getBytes());

            BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(tempFile.toString());

            assertNull(series, "Should return null for invalid JSON file");
        } catch (Exception e) {
            fail("Unexpected exception during test setup: " + e.getMessage());
        } finally {
            // Clean up temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    @Test
    public void testLoadSeriesWithStandardNamingPatternCoinbase() {
        // Test loading Coinbase data using domain-driven interface with standard naming
        // pattern
        // Pattern: {Exchange}-{ticker}-{interval}-{startDate}_{endDate}.json
        String expectedFile = "Coinbase-ETH-USD-PT1D-20241105_20251020.json";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2024-11-05T00:00:00Z");
        Instant end = Instant.parse("2025-10-20T23:59:59Z");

        BarSeries series = dataSource.loadSeries("ETH-USD", Duration.ofDays(1), start, end);

        assertNotNull(series, "Should load series using standard naming pattern with Coinbase prefix");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
        assertEquals("CoinbaseData", series.getName(), "Series name should be set correctly");
    }

    @Test
    public void testLoadSeriesWithStandardNamingPatternBinance() {
        // Test loading Binance data using domain-driven interface with standard naming
        // pattern
        String expectedFile = "Binance-ETH-USD-PT5M-20230313_20230315.json";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2023-03-13T00:00:00Z");
        Instant end = Instant.parse("2023-03-15T23:59:59Z");

        BarSeries series = dataSource.loadSeries("ETH-USD", Duration.ofMinutes(5), start, end);

        assertNotNull(series, "Should load series using standard naming pattern with Binance prefix");
        assertTrue(series.getBarCount() > 0, "Series should contain bars");
    }

    @Test
    public void testLoadSeriesWithNonExistentTicker() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-12-31T23:59:59Z");

        BarSeries series = dataSource.loadSeries("NONEXISTENT-USD", Duration.ofDays(1), start, end);

        assertNull(series, "Should return null for non-existent ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidDateRange() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2024-12-31T23:59:59Z");
        Instant end = Instant.parse("2024-01-01T00:00:00Z"); // End before start

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("ETH-USD", Duration.ofDays(1), start, end);
        }, "Should throw IllegalArgumentException when end date is before start date");
    }

    @Test
    public void testLoadSeriesWithNullTicker() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-12-31T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries(null, Duration.ofDays(1), start, end);
        }, "Should throw IllegalArgumentException for null ticker");
    }

    @Test
    public void testLoadSeriesWithEmptyTicker() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-12-31T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("", Duration.ofDays(1), start, end);
        }, "Should throw IllegalArgumentException for empty ticker");
    }

    @Test
    public void testLoadSeriesWithInvalidInterval() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-12-31T23:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("ETH-USD", Duration.ZERO, start, end);
        }, "Should throw IllegalArgumentException for zero interval");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("ETH-USD", Duration.ofSeconds(-1), start, end);
        }, "Should throw IllegalArgumentException for negative interval");
    }

    @Test
    public void testLoadSeriesWithNullDates() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("ETH-USD", Duration.ofDays(1), null, Instant.now());
        }, "Should throw IllegalArgumentException for null start date");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("ETH-USD", Duration.ofDays(1), Instant.now(), null);
        }, "Should throw IllegalArgumentException for null end date");
    }

    @Test
    public void testGetSourceName() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        assertEquals("", dataSource.getSourceName(),
                "Should return empty string as JSON files use exchange-specific prefixes (Coinbase-, Binance-)");
    }

    @Test
    public void testGetSourceNameUsedInFileSearchPattern() {
        // Verify that getSourceName() returns empty string and files use exchange
        // prefixes
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        String sourceName = dataSource.getSourceName();
        assertTrue(sourceName.isEmpty(),
                "Source name should be empty for JSON files (exchange-specific prefixes used)");

        // Test that file search works with exchange prefixes (not source name prefix)
        Instant start = Instant.parse("2024-11-05T00:00:00Z");
        Instant end = Instant.parse("2025-10-20T23:59:59Z");
        String expectedFile = "Coinbase-ETH-USD-PT1D-20241105_20251020.json";
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(expectedFile);
        assumeThat("File " + expectedFile + " does not exist", resourceStream, is(notNullValue()));

        BarSeries series = dataSource.loadSeries("ETH-USD", Duration.ofDays(1), start, end);
        assertNotNull(series, "Should find file using exchange prefix (not source name prefix)");
        assertTrue(expectedFile.startsWith("Coinbase-") || expectedFile.startsWith("Binance-"),
                "Expected file should use exchange prefix, not source name prefix");
    }
}
