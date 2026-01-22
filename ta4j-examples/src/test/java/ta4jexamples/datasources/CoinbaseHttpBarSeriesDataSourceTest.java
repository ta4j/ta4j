/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import ta4jexamples.datasources.http.AbstractHttpBarSeriesDataSource;
import ta4jexamples.datasources.http.HttpClientWrapper;
import ta4jexamples.datasources.http.HttpResponseWrapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link CoinbaseHttpBarSeriesDataSource} class.
 * <p>
 * This test class verifies the behavior of the
 * {@code CoinbaseHttpBarSeriesDataSource} when loading bar series data from
 * Coinbase API, including successful responses, error handling, pagination, and
 * edge cases.
 * </p>
 */
@SuppressWarnings("unchecked")
public class CoinbaseHttpBarSeriesDataSourceTest {

    private static final String VALID_JSON_RESPONSE = """
            {
                "candles": [
                    {
                        "start": "1609459200",
                        "low": "99.0",
                        "high": "105.0",
                        "open": "100.0",
                        "close": "104.0",
                        "volume": "1000000"
                    },
                    {
                        "start": "1609545600",
                        "low": "100.0",
                        "high": "106.0",
                        "open": "101.0",
                        "close": "105.0",
                        "volume": "1100000"
                    },
                    {
                        "start": "1609632000",
                        "low": "101.0",
                        "high": "107.0",
                        "open": "102.0",
                        "close": "106.0",
                        "volume": "1200000"
                    }
                ]
            }
            """;

    /**
     * Helper method to clean up cache files matching a pattern. This ensures tests
     * start with a clean cache state.
     *
     * @param pattern the filename pattern to match (e.g.,
     *                "Coinbase-BTC-USD-PT24H-")
     */
    private void cleanupCacheFiles(String pattern) {
        Path cacheDir = Paths.get(AbstractHttpBarSeriesDataSource.DEFAULT_RESPONSE_CACHE_DIR);
        if (Files.exists(cacheDir)) {
            try {
                Files.list(cacheDir).filter(path -> path.getFileName().toString().startsWith(pattern)).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Helper method to delete a directory and all its contents recursively.
     *
     * @param dirPath the directory path to delete
     */
    private void deleteDirectory(Path dirPath) {
        if (Files.exists(dirPath)) {
            try {
                // Delete all files in the directory first
                if (Files.isDirectory(dirPath)) {
                    try (var stream = Files.list(dirPath)) {
                        stream.forEach(path -> {
                            try {
                                if (Files.isDirectory(path)) {
                                    deleteDirectory(path);
                                } else {
                                    Files.delete(path);
                                }
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
                    }
                    // Delete the directory itself (only works if empty)
                    Files.delete(dirPath);
                } else {
                    // It's a file, not a directory
                    Files.delete(dirPath);
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Helper method to build cache file prefix using getSourceName().
     *
     * @return the cache file prefix (e.g., "Coinbase-")
     */
    private String getCachePrefix() {
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource();
        String sourceName = dataSource.getSourceName();
        return sourceName.isEmpty() ? "" : sourceName + "-";
    }

    @Test
    public void testConstructorWithNullHttpClientWrapper() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CoinbaseHttpBarSeriesDataSource((HttpClientWrapper) null);
        }, "Constructor should throw IllegalArgumentException for null HttpClientWrapper");
    }

    @Test
    public void testConstructorWithHttpClientWrapper() {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        assertNotNull(dataSource, "DataSource should be created successfully");
    }

    @Test
    public void testConstructorWithHttpClient() {
        HttpClient httpClient = HttpClient.newHttpClient();
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(httpClient);
        assertNotNull(dataSource, "DataSource should be created successfully");
    }

    @Test
    public void testLoadSeriesWithValidResponse() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(3, series.getBarCount(), "Should have 3 bars");
        assertEquals("BTC-USD", series.getName(), "Series name should match product ID");
        assertTrue(series.getBar(0).getClosePrice().doubleValue() > 0, "Close price should be positive");
    }

    @Test
    public void testLoadSeriesWithNullProductId() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance(null, CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY,
                start, end);

        assertNull(series, "Should return null for null product ID");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithEmptyProductId() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("   ",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null for empty product ID");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithNullStartDateTime() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, null, end);

        assertNull(series, "Should return null for null start date");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithNullEndDateTime() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, null);

        assertNull(series, "Should return null for null end date");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithInvalidDateRange() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-03T00:00:00Z");
        Instant end = Instant.parse("2021-01-01T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null when start is after end");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithHttpError() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(404);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null for HTTP error status");
    }

    @Test
    public void testLoadSeriesWithIOException() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null when IOException occurs");
    }

    @Test
    public void testLoadSeriesWithInterruptedException() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null when InterruptedException occurs");
    }

    @Test
    public void testLoadSeriesWithInvalidJson() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("invalid json");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null for invalid JSON");
    }

    @Test
    public void testLoadSeriesWithEmptyResults() throws IOException, InterruptedException {
        String emptyResultsJson = """
                {
                    "candles": []
                }
                """;

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(emptyResultsJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null for empty results");
    }

    @Test
    public void testLoadSeriesWithNullValuesInData() throws IOException, InterruptedException {
        String jsonWithNulls = """
                {
                    "candles": [
                        {
                            "start": "1609459200",
                            "low": "99.0",
                            "high": "105.0",
                            "open": "100.0",
                            "close": "104.0",
                            "volume": "1000000"
                        },
                        {
                            "start": null,
                            "low": "100.0",
                            "high": "106.0",
                            "open": null,
                            "close": "105.0",
                            "volume": "1100000"
                        },
                        {
                            "start": "1609632000",
                            "low": "101.0",
                            "high": "107.0",
                            "open": "102.0",
                            "close": "106.0",
                            "volume": "1200000"
                        }
                    ]
                }
                """;

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonWithNulls);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(2, series.getBarCount(), "Should skip bars with null values");
    }

    @Test
    public void testLoadSeriesStaticMethodWithBarCount() {
        // Note: Static methods use DEFAULT_INSTANCE which uses real HttpClient
        // This test verifies the static method signature and error handling
        BarSeries series = CoinbaseHttpBarSeriesDataSource.loadSeries("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, 0);

        assertNull(series, "Should return null for bar count <= 0");
    }

    @Test
    public void testLoadSeriesStaticMethodWithDays() {
        // Note: Static methods use DEFAULT_INSTANCE which uses real HttpClient
        // This test verifies the static method signature
        BarSeries series = CoinbaseHttpBarSeriesDataSource.loadSeries("BTC-USD", 0);

        assertNull(series, "Should return null for days <= 0");
    }

    @Test
    public void testCoinbaseIntervalEnum() {
        CoinbaseHttpBarSeriesDataSource.CoinbaseInterval interval = CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY;
        assertEquals(Duration.ofDays(1), interval.getDuration(), "Duration should match");
        assertEquals("ONE_DAY", interval.getApiValue(), "API value should match");
    }

    @Test
    public void testLoadSeriesSkipsNullVolume() throws IOException, InterruptedException {
        String jsonWithNullVolume = """
                {
                    "candles": [
                        {
                            "start": "1609459200",
                            "low": "99.0",
                            "high": "105.0",
                            "open": "100.0",
                            "close": "104.0",
                            "volume": null
                        }
                    ]
                }
                """;

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonWithNullVolume);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-02T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(1, series.getBarCount(), "Should have 1 bar");
        assertEquals(0.0, series.getBar(0).getVolume().doubleValue(), 0.001, "Null volume should be 0");
    }

    @Test
    public void testPaginationWithMultipleChunks() throws IOException, InterruptedException {
        // Test pagination by requesting more than 350 candles
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        // First chunk response (350 candles worth)
        StringBuilder chunk1Json = new StringBuilder("{\"candles\":[");
        for (int i = 0; i < 350; i++) {
            if (i > 0) {
                chunk1Json.append(",");
            }
            long timestamp = 1609459200L + (i * 86400L); // Daily candles
            chunk1Json.append(String.format(
                    "{\"start\":\"%d\",\"low\":\"99.0\",\"high\":\"105.0\",\"open\":\"100.0\",\"close\":\"104.0\",\"volume\":\"1000000\"}",
                    timestamp));
        }
        chunk1Json.append("]}");

        // Second chunk response - timestamp should be after first chunk ends
        // First chunk ends at: 1609459200 + (350 * 86400) = 1639699200
        // Second chunk starts at: 1639699200 + 86400 = 1639785600
        String chunk2Json = """
                {
                    "candles": [
                        {
                            "start": "1639785600",
                            "low": "101.0",
                            "high": "107.0",
                            "open": "102.0",
                            "close": "106.0",
                            "volume": "1200000"
                        }
                    ]
                }
                """;

        HttpResponseWrapper<String> mockResponse1 = mock(HttpResponseWrapper.class);
        HttpResponseWrapper<String> mockResponse2 = mock(HttpResponseWrapper.class);

        when(mockResponse1.statusCode()).thenReturn(200);
        when(mockResponse1.body()).thenReturn(chunk1Json.toString());
        when(mockResponse2.statusCode()).thenReturn(200);
        when(mockResponse2.body()).thenReturn(chunk2Json);

        // Return different responses for different requests
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse1)
                .thenReturn(mockResponse2)
                .thenReturn(mockResponse2); // In case there are more calls

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        // Request 351 days of daily data (exceeds 350 candle limit)
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-12-18T00:00:00Z"); // ~351 days

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(351, series.getBarCount(), "Should have 351 bars from 2 chunks");
        assertEquals("BTC-USD", series.getName(), "Series name should match product ID");

        // Verify that multiple HTTP requests were made (pagination occurred)
        verify(mockClient, atLeast(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testPaginationWithOverlappingTimestamps() throws IOException, InterruptedException {
        // Test that pagination correctly deduplicates overlapping timestamps
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        // Both chunks have the same timestamp (overlap scenario)
        String chunk1Json = """
                {
                    "candles": [
                        {
                            "start": "1609459200",
                            "low": "99.0",
                            "high": "105.0",
                            "open": "100.0",
                            "close": "104.0",
                            "volume": "1000000"
                        }
                    ]
                }
                """;

        String chunk2Json = """
                {
                    "candles": [
                        {
                            "start": "1609459200",
                            "low": "99.0",
                            "high": "105.0",
                            "open": "100.0",
                            "close": "104.0",
                            "volume": "1000000"
                        },
                        {
                            "start": "1609545600",
                            "low": "100.0",
                            "high": "106.0",
                            "open": "101.0",
                            "close": "105.0",
                            "volume": "1100000"
                        }
                    ]
                }
                """;

        HttpResponseWrapper<String> mockResponse1 = mock(HttpResponseWrapper.class);
        HttpResponseWrapper<String> mockResponse2 = mock(HttpResponseWrapper.class);

        when(mockResponse1.statusCode()).thenReturn(200);
        when(mockResponse1.body()).thenReturn(chunk1Json);
        when(mockResponse2.statusCode()).thenReturn(200);
        when(mockResponse2.body()).thenReturn(chunk2Json);

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse1)
                .thenReturn(mockResponse2);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        // Request range that would trigger pagination
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-12-18T00:00:00Z"); // Exceeds 350 candles

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        // Should deduplicate the overlapping timestamp, so only 2 unique bars
        assertEquals(2, series.getBarCount(), "Should deduplicate overlapping timestamps");
    }

    @Test
    public void testPaginationWithFailedChunk() throws IOException, InterruptedException {
        // Test that pagination continues even if one chunk fails
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        String chunk1Json = """
                {
                    "candles": [
                        {
                            "start": "1609459200",
                            "low": "99.0",
                            "high": "105.0",
                            "open": "100.0",
                            "close": "104.0",
                            "volume": "1000000"
                        }
                    ]
                }
                """;

        HttpResponseWrapper<String> mockResponse1 = mock(HttpResponseWrapper.class);
        HttpResponseWrapper<String> mockResponse2 = mock(HttpResponseWrapper.class);

        when(mockResponse1.statusCode()).thenReturn(200);
        when(mockResponse1.body()).thenReturn(chunk1Json);
        when(mockResponse2.statusCode()).thenReturn(404); // Second chunk fails

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse1)
                .thenReturn(mockResponse2);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-12-18T00:00:00Z"); // Exceeds 350 candles

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(1, series.getBarCount(), "Should have 1 bar from successful chunk");
    }

    @Test
    public void testPaginationWithAllChunksFailing() throws IOException, InterruptedException {
        // Test that pagination returns null if all chunks fail
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(500); // All chunks fail

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-12-18T00:00:00Z"); // Exceeds 350 candles

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNull(series, "Should return null when all chunks fail");
    }

    @Test
    public void testNoPaginationWhenUnderLimit() throws IOException, InterruptedException {
        // Test that pagination is NOT triggered when range is under 350 candles
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z"); // Only 3 days, under 350 limit

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "BarSeries should not be null");
        // Should only make one request (no pagination)
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testConstructorWithCachingEnabled() {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        assertNotNull(dataSource, "DataSource should be created successfully with caching enabled");
    }

    @Test
    public void testConstructorWithCachingDisabled() {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, false);
        assertNotNull(dataSource, "DataSource should be created successfully with caching disabled");
    }

    @Test
    public void testConstructorWithBooleanCachingParameter() {
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(true);
        assertNotNull(dataSource, "DataSource should be created successfully with caching enabled");
    }

    @Test
    public void testCacheHitForSameRequest() throws IOException, InterruptedException {
        // Clean up any existing cache files for this test
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // First request - should make API call
        BarSeries series1 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series1, "First request should return data");
        assertEquals(3, series1.getBarCount(), "Should have 3 bars");
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request with same parameters - should use cache
        BarSeries series2 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series2, "Second request should return cached data");
        assertEquals(3, series2.getBarCount(), "Should have 3 bars from cache");
        // Should not make another API call
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up cache files created by this test
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
    }

    @Test
    public void testCacheMissForDifferentProductId() throws IOException, InterruptedException {
        // Clean up any existing cache files
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
        cleanupCacheFiles(getCachePrefix() + "ETH-USD-PT24H-");

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // First request for BTC-USD
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request for ETH-USD - should be a cache miss
        dataSource.loadSeriesInstance("ETH-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
        cleanupCacheFiles(getCachePrefix() + "ETH-USD-PT24H-");
    }

    @Test
    public void testCacheMissForDifferentInterval() throws IOException, InterruptedException {
        // Clean up any existing cache files
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT1H-");

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // First request with ONE_DAY interval
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request with ONE_HOUR interval - should be a cache miss
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_HOUR, start, end);
        verify(mockClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT1H-");
    }

    @Test
    public void testCacheHitWithTruncatedTimestamps() throws IOException, InterruptedException {
        // Clean up any existing cache files
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");

        // Test that cache hits work when timestamps are truncated to the same cache key
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant baseStart = Instant.parse("2021-01-01T00:00:00Z");
        Instant baseEnd = Instant.parse("2021-01-03T00:00:00Z");

        // First request
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, baseStart,
                baseEnd);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request with slightly different timestamps that should truncate to the
        // same cache key
        // For ONE_DAY, timestamps should truncate to start of day
        Instant start2 = Instant.parse("2021-01-01T12:30:45Z"); // Same day, different time
        Instant end2 = Instant.parse("2021-01-03T18:20:10Z"); // Same day, different time

        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start2,
                end2);
        // Should use cache because timestamps truncate to the same values
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
    }

    @Test
    public void testCacheWriteOnSuccessfulRequest() throws IOException, InterruptedException {
        // Clean up any existing cache files
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);

        assertNotNull(series, "Should return data");
        // Verify that a cache file was created (indirectly by checking that second
        // request uses cache)
        BarSeries series2 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNotNull(series2, "Second request should return cached data");
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
    }

    @Test
    public void testNoCacheWriteWhenCachingDisabled() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, false);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // First request
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request - should make another API call since caching is disabled
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testCacheHitForHistoricalData() throws IOException, InterruptedException {
        // Clean up any existing cache files
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");

        // Historical data (end date in the past) should be cached indefinitely
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        // Use historical dates (more than 1 day in the past)
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        Instant end = Instant.parse("2020-01-03T00:00:00Z");

        // First request
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request - should use cache even though time has passed
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");
    }

    @Test
    public void testCacheWithBarCountMethod() throws IOException, InterruptedException {
        // Use a unique notes identifier to avoid cache collisions with other tests
        String uniqueNotes = String.valueOf(System.currentTimeMillis());
        String productId = "TEST-BARCOUNT";
        cleanupCacheFiles(getCachePrefix() + productId + "-PT24H-");

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);

        // First request using barCount method with notes
        BarSeries series1 = dataSource.loadSeriesInstance(productId,
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, 3, uniqueNotes);
        assertNotNull(series1, "Should return data");
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request with same barCount and notes - should use cache
        BarSeries series2 = dataSource.loadSeriesInstance(productId,
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, 3, uniqueNotes);
        assertNotNull(series2, "Should return data");
        // Should use cache - only 1 API call total
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + productId + "-PT24H-");
    }

    @Test
    public void testCacheDirectoryCreation() {
        // Test that cache directory is created when caching is enabled
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);

        // The constructor should attempt to create the cache directory
        // We can't easily test this without file system access, but we can verify
        // the constructor doesn't throw an exception
        assertNotNull(dataSource, "DataSource should be created successfully");
    }

    @Test
    public void testCacheWithDifferentIntervalsTruncation() throws IOException, InterruptedException {
        // Clean up any existing cache files
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT5M-");

        // Test that different intervals truncate timestamps correctly
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant start = Instant.parse("2021-01-01T12:30:45Z");
        Instant end = Instant.parse("2021-01-01T18:20:10Z");

        // Test FIVE_MINUTE - should truncate to 5-minute boundaries
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.FIVE_MINUTE, start,
                end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Same request should hit cache
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.FIVE_MINUTE, start,
                end);
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Different 5-minute period should be a cache miss
        Instant start2 = Instant.parse("2021-01-01T12:35:00Z"); // Different 5-minute period
        dataSource.loadSeriesInstance("BTC-USD", CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.FIVE_MINUTE, start2,
                end);
        verify(mockClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT5M-");
    }

    @Test
    public void testCacheWithFailedRequest() throws IOException, InterruptedException {
        // Clean up any existing cache files - important to ensure no cached success
        // response
        cleanupCacheFiles(getCachePrefix() + "BTC-USD-PT24H-");

        // Test that failed requests don't write to cache
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(404); // Failed request
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, true);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // First request fails
        BarSeries series1 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNull(series1, "Should return null for failed request");
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Second request should still make API call (no cache for failed requests)
        BarSeries series2 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNull(series2, "Should return null for failed request");
        verify(mockClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testGetSourceName() {
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource();
        assertEquals("Coinbase", dataSource.getSourceName(), "Should return 'Coinbase' as source name");
    }

    @Test
    public void testGetSourceNameUsedInCacheFileGeneration() {
        // Verify that getSourceName() is used in cache file generation
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource();
        String sourceName = dataSource.getSourceName();
        assertFalse(sourceName.isEmpty(), "Source name should not be empty");
        assertEquals("Coinbase", sourceName, "Source name should be 'Coinbase'");

        // Verify that cache prefix matches source name
        String cachePrefix = getCachePrefix();
        assertEquals(sourceName + "-", cachePrefix, "Cache prefix should be source name followed by dash");
    }

    @Test
    public void testAllCoinbaseIntervalValues() {
        // Test all interval enum values
        CoinbaseHttpBarSeriesDataSource.CoinbaseInterval[] intervals = CoinbaseHttpBarSeriesDataSource.CoinbaseInterval
                .values();

        assertTrue(intervals.length > 0, "Should have at least one interval");

        for (CoinbaseHttpBarSeriesDataSource.CoinbaseInterval interval : intervals) {
            assertNotNull(interval.getDuration(), "Duration should not be null for " + interval);
            assertNotNull(interval.getApiValue(), "API value should not be null for " + interval);
            assertFalse(interval.getApiValue().isEmpty(), "API value should not be empty for " + interval);
        }
    }

    @Test
    public void testLoadSeriesWithBarSeriesDataSourceInterface() {
        // Test the BarSeriesDataSource interface methods
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource();

        // Test loadSeries with Duration
        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries(null, Duration.ofDays(1), Instant.now().minusSeconds(86400), Instant.now());
        }, "Should throw IllegalArgumentException for null product ID");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", (Duration) null, Instant.now().minusSeconds(86400), Instant.now());
        }, "Should throw IllegalArgumentException for null interval");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofDays(1), null, Instant.now());
        }, "Should throw IllegalArgumentException for null start date");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofDays(1), Instant.now().minusSeconds(86400), null);
        }, "Should throw IllegalArgumentException for null end date");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("BTC-USD", Duration.ofDays(1), Instant.now(), Instant.now().minusSeconds(86400));
        }, "Should throw IllegalArgumentException when start is after end");
    }

    @Test
    public void testLoadSeriesWithStringSource() {
        // Test loadSeries(String source) method
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource();

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries((String) null);
        }, "Should throw IllegalArgumentException for null source");

        assertThrows(IllegalArgumentException.class, () -> {
            dataSource.loadSeries("   ");
        }, "Should throw IllegalArgumentException for empty source");

        // Test with non-existent cache file
        BarSeries series = dataSource.loadSeries("nonexistent-file.json");
        assertNull(series, "Should return null for non-existent file");
    }

    @Test
    public void testConstructorWithCustomCacheDirectory() throws IOException {
        String customCacheDir = "temp/custom";
        Path customCachePath = Paths.get(customCacheDir);
        try {
            CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(customCacheDir);
            assertNotNull(dataSource, "DataSource should be created successfully");
            assertEquals(customCacheDir, dataSource.getResponseCacheDir(),
                    "Cache directory should match the provided custom directory");
        } finally {
            // Clean up: delete the directory if it was created
            deleteDirectory(customCachePath);
        }
    }

    @Test
    public void testConstructorWithHttpClientWrapperAndCustomCacheDirectory() throws IOException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        String customCacheDir = "temp/custom-wrapper";
        Path customCachePath = Paths.get(customCacheDir);
        try {
            CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient,
                    customCacheDir);
            assertNotNull(dataSource, "DataSource should be created successfully");
            assertEquals(customCacheDir, dataSource.getResponseCacheDir(),
                    "Cache directory should match the provided custom directory");
        } finally {
            // Clean up: delete the directory if it was created
            deleteDirectory(customCachePath);
        }
    }

    @Test
    public void testConstructorWithHttpClientAndCustomCacheDirectory() throws IOException {
        HttpClient httpClient = HttpClient.newHttpClient();
        String customCacheDir = "temp/custom-http";
        Path customCachePath = Paths.get(customCacheDir);
        try {
            CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(httpClient,
                    customCacheDir);
            assertNotNull(dataSource, "DataSource should be created successfully");
            assertEquals(customCacheDir, dataSource.getResponseCacheDir(),
                    "Cache directory should match the provided custom directory");
        } finally {
            // Clean up: delete the directory if it was created
            deleteDirectory(customCachePath);
        }
    }

    @Test
    public void testConstructorWithNullCacheDirectory() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CoinbaseHttpBarSeriesDataSource((String) null);
        }, "Constructor should throw IllegalArgumentException for null cache directory");
    }

    @Test
    public void testConstructorWithEmptyCacheDirectory() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CoinbaseHttpBarSeriesDataSource("");
        }, "Constructor should throw IllegalArgumentException for empty cache directory");

        assertThrows(IllegalArgumentException.class, () -> {
            new CoinbaseHttpBarSeriesDataSource("   ");
        }, "Constructor should throw IllegalArgumentException for whitespace-only cache directory");
    }

    @Test
    public void testGetResponseCacheDirWithDefaultDirectory() {
        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource();
        assertEquals(AbstractHttpBarSeriesDataSource.DEFAULT_RESPONSE_CACHE_DIR, dataSource.getResponseCacheDir(),
                "Default cache directory should be used when not specified");
    }

    @Test
    public void testGetResponseCacheDirWithCustomDirectory() throws IOException {
        String customCacheDir = "temp/my-custom-cache";
        Path customCachePath = Paths.get(customCacheDir);
        try {
            CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(customCacheDir);
            assertEquals(customCacheDir, dataSource.getResponseCacheDir(), "Custom cache directory should be returned");
        } finally {
            // Clean up: delete the directory if it was created
            deleteDirectory(customCachePath);
        }
    }

    @Test
    public void testCacheFilesCreatedInCustomDirectory() throws IOException, InterruptedException {
        // Use a unique custom cache directory for this test
        String customCacheDir = "temp/custom-dir-test";
        Path customCachePath = Paths.get(customCacheDir);

        // Clean up any existing cache files
        if (Files.exists(customCachePath)) {
            Files.list(customCachePath)
                    .filter(path -> path.getFileName().toString().startsWith(getCachePrefix() + "BTC-USD-PT24H-"))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, customCacheDir);

        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // First request - should create cache file in custom directory
        BarSeries series1 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNotNull(series1, "First request should return data");

        // Verify cache file was created in custom directory
        assertTrue(Files.exists(customCachePath), "Custom cache directory should exist");
        boolean cacheFileExists = Files.list(customCachePath)
                .anyMatch(path -> path.getFileName().toString().startsWith(getCachePrefix() + "BTC-USD-PT24H-"));
        assertTrue(cacheFileExists, "Cache file should be created in custom directory");

        // Second request - should use cache (verify only one API call was made)
        BarSeries series2 = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNotNull(series2, "Second request should return cached data");
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Clean up: delete all files and then the directory
        deleteDirectory(customCachePath);
    }

    @Test
    public void testCacheDirectoryTrimming() throws IOException {
        // Test that cache directory paths are trimmed
        String customCacheDirWithWhitespace = "  temp/trimmed  ";
        String trimmedDir = "temp/trimmed";
        Path trimmedDirPath = Paths.get(trimmedDir);
        try {
            CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(
                    customCacheDirWithWhitespace);
            assertEquals(trimmedDir, dataSource.getResponseCacheDir(),
                    "Cache directory should be trimmed of leading/trailing whitespace");
        } finally {
            // Clean up: delete the directory if it was created
            deleteDirectory(trimmedDirPath);
        }
    }

    @Test
    public void testDeleteAllCacheFiles() throws IOException, InterruptedException {
        // Use a unique custom cache directory for this test
        String customCacheDir = "temp/delete-all-test";
        Path customCachePath = Paths.get(customCacheDir);

        // Clean up any existing cache files
        if (Files.exists(customCachePath)) {
            Files.list(customCachePath)
                    .filter(path -> path.getFileName().toString().startsWith(getCachePrefix() + "BTC-USD-PT24H-"))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, customCacheDir);

        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // Create a cache file
        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNotNull(series, "Should return data");

        // Verify cache file was created
        assertTrue(Files.exists(customCachePath), "Custom cache directory should exist");
        long fileCountBefore = Files.list(customCachePath)
                .filter(path -> path.getFileName().toString().startsWith(getCachePrefix() + "BTC-USD-PT24H-"))
                .count();
        assertTrue(fileCountBefore > 0, "Cache file should exist");

        // Delete all cache files
        int deletedCount = dataSource.deleteAllCacheFiles();
        assertTrue(deletedCount > 0, "Should have deleted at least one file");

        // Verify cache files are gone
        long fileCountAfter = Files.list(customCachePath)
                .filter(path -> path.getFileName().toString().startsWith(getCachePrefix() + "BTC-USD-PT24H-"))
                .count();
        assertEquals(0, fileCountAfter, "All cache files should be deleted");

        // Clean up: delete the directory
        deleteDirectory(customCachePath);
    }

    @Test
    public void testDeleteCacheFilesOlderThan() throws IOException, InterruptedException {
        // Use a unique custom cache directory for this test
        String customCacheDir = "temp/delete-old-test";
        Path customCachePath = Paths.get(customCacheDir);

        // Clean up any existing cache files
        if (Files.exists(customCachePath)) {
            Files.list(customCachePath)
                    .filter(path -> path.getFileName().toString().startsWith(getCachePrefix()))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, customCacheDir);

        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // Create a cache file
        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNotNull(series, "Should return data");

        // Verify cache file was created
        assertTrue(Files.exists(customCachePath), "Custom cache directory should exist");

        // Delete files older than 1 day (should not delete the file we just created)
        int deletedCount = dataSource.deleteCacheFilesOlderThan(Duration.ofDays(1));
        assertEquals(0, deletedCount, "Should not delete recently created file");

        // Delete files older than 0 seconds (should delete all files)
        deletedCount = dataSource.deleteCacheFilesOlderThan(Duration.ZERO);
        assertTrue(deletedCount > 0, "Should have deleted the cache file");

        // Verify cache files are gone
        long fileCountAfter = Files.list(customCachePath)
                .filter(path -> path.getFileName().toString().startsWith(getCachePrefix()))
                .count();
        assertEquals(0, fileCountAfter, "All cache files should be deleted");

        // Clean up: delete the directory
        deleteDirectory(customCachePath);
    }

    @Test
    public void testDeleteStaleCacheFiles() throws IOException, InterruptedException {
        // Use a unique custom cache directory for this test
        String customCacheDir = "temp/delete-stale-test";
        Path customCachePath = Paths.get(customCacheDir);

        // Clean up any existing cache files
        if (Files.exists(customCachePath)) {
            Files.list(customCachePath)
                    .filter(path -> path.getFileName().toString().startsWith(getCachePrefix()))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(mockClient, customCacheDir);

        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        // Create a cache file
        BarSeries series = dataSource.loadSeriesInstance("BTC-USD",
                CoinbaseHttpBarSeriesDataSource.CoinbaseInterval.ONE_DAY, start, end);
        assertNotNull(series, "Should return data");

        // Delete stale files (default 30 days) - should not delete recently created
        // file
        int deletedCount = dataSource.deleteStaleCacheFiles();
        assertEquals(0, deletedCount, "Should not delete recently created file");

        // Delete stale files with custom age (0 days) - should delete all files
        deletedCount = dataSource.deleteStaleCacheFiles(Duration.ZERO);
        assertTrue(deletedCount > 0, "Should have deleted the cache file");

        // Clean up: delete the directory
        deleteDirectory(customCachePath);
    }

    @Test
    public void testDeleteCacheFilesWithNonExistentDirectory() throws IOException {
        String cacheDir = "temp/non-existent-cache-dir";
        Path cacheDirPath = Paths.get(cacheDir);
        try {
            CoinbaseHttpBarSeriesDataSource dataSource = new CoinbaseHttpBarSeriesDataSource(cacheDir);
            int deletedCount = dataSource.deleteAllCacheFiles();
            assertEquals(0, deletedCount, "Should return 0 for non-existent directory");
        } finally {
            // Clean up: delete the directory if it was created by the constructor
            deleteDirectory(cacheDirPath);
        }
    }
}
