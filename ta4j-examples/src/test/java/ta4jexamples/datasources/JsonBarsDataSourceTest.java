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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assume.assumeThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link JsonBarsDataSource} class.
 * <p>
 * This test class verifies the behavior of the {@code JsonBarsDataSource} when
 * loading bar series data from various JSON input streams, including valid
 * Coinbase and Binance formatted data, as well as edge cases such as a null
 * input stream.
 * </p>
 */
public class JsonBarsDataSourceTest {

    @Test
    public void testLoadCoinbaseInputStream() {
        String jsonFile = "Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFile);
        assumeThat("File " + jsonFile + " does not exist", inputStream, is(notNullValue()));

        BarSeries series = JsonBarsDataSource.loadSeries(inputStream);

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
        String jsonFile = "Binance-ETH-USD-PT5M-2023-3-13_2023-3-15.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFile);
        assumeThat("File " + jsonFile + " does not exist", inputStream, is(notNullValue()));

        BarSeries series = JsonBarsDataSource.loadSeries(inputStream);

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
        BarSeries series = JsonBarsDataSource.loadSeries((InputStream) null);
        assertNull(series, "Should return null for null input stream");
    }

    @Test
    public void testLoadSeriesFromValidFile() {
        String jsonFile = "Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json";
        String resourcePath = Objects.requireNonNull(getClass().getClassLoader().getResource(jsonFile)).getPath();

        BarSeries series = JsonBarsDataSource.loadSeries(resourcePath);

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

        BarSeries series = JsonBarsDataSource.loadSeries(nonExistentPath);

        assertNull(series, "Should return null for non-existent file");
    }

    @Test
    public void testLoadSeriesFromNullFilename() {
        BarSeries series = JsonBarsDataSource.loadSeries((String) null);

        assertNull(series, "Should return null for null filename");
    }

    @Test
    public void testLoadSeriesFromEmptyFilename() {
        BarSeries series = JsonBarsDataSource.loadSeries("");

        assertNull(series, "Should return null for empty filename");
    }

    @Test
    public void testLoadSeriesFromInvalidJsonFile() {
        // Create a temporary file with invalid JSON content
        String invalidJsonContent = "invalid json content";
        String tempFilePath = null;

        try {
            Path tempFile = Files.createTempFile("invalid", ".json");
            tempFilePath = tempFile.toString();
            Files.write(tempFile, invalidJsonContent.getBytes());

            BarSeries series = JsonBarsDataSource.loadSeries(tempFilePath);

            assertNull(series, "Should return null for invalid JSON file");
        } catch (Exception e) {
            fail("Unexpected exception during test setup: " + e.getMessage());
        } finally {
            // Clean up temporary file
            if (tempFilePath != null) {
                try {
                    Files.deleteIfExists(Paths.get(tempFilePath));
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}
