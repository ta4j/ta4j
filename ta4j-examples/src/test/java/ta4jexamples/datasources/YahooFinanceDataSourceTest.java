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
import ta4jexamples.datasources.http.HttpClientWrapper;
import ta4jexamples.datasources.http.HttpResponseWrapper;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link YahooFinanceDataSource} class.
 * <p>
 * This test class verifies the behavior of the {@code YahooFinanceDataSource}
 * when loading bar series data from Yahoo Finance API, including successful
 * responses, error handling, pagination, and edge cases.
 * </p>
 */
@SuppressWarnings("unchecked")
public class YahooFinanceDataSourceTest {

    private static final String VALID_JSON_RESPONSE = """
            {
                "chart": {
                    "result": [{
                        "timestamp": [1609459200, 1609545600, 1609632000],
                        "indicators": {
                            "quote": [{
                                "open": [100.0, 101.0, 102.0],
                                "high": [105.0, 106.0, 107.0],
                                "low": [99.0, 100.0, 101.0],
                                "close": [104.0, 105.0, 106.0],
                                "volume": [1000000, 1100000, 1200000]
                            }]
                        }
                    }]
                }
            }
            """;

    @Test
    public void testConstructorWithNullHttpClientWrapper() {
        assertThrows(IllegalArgumentException.class, () -> {
            new YahooFinanceDataSource((HttpClientWrapper) null);
        }, "Constructor should throw IllegalArgumentException for null HttpClientWrapper");
    }

    @Test
    public void testConstructorWithHttpClientWrapper() {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        assertNotNull(dataSource, "DataSource should be created successfully");
    }

    @Test
    public void testConstructorWithHttpClient() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(httpClient);
        assertNotNull(dataSource, "DataSource should be created successfully");
    }

    @Test
    public void testLoadSeriesWithValidResponse() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(3, series.getBarCount(), "Should have 3 bars");
        assertEquals("AAPL", series.getName(), "Series name should match ticker");
        assertTrue(series.getBar(0).getClosePrice().doubleValue() > 0, "Close price should be positive");
    }

    @Test
    public void testLoadSeriesWithNullTicker() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance(null, YahooFinanceDataSource.YahooFinanceInterval.DAY_1, start,
                end);

        assertNull(series, "Should return null for null ticker");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithEmptyTicker() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("   ", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null for empty ticker");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithNullStartDateTime() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                null, end);

        assertNull(series, "Should return null for null start date");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithNullEndDateTime() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, null);

        assertNull(series, "Should return null for null end date");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithInvalidDateRange() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-03T00:00:00Z");
        Instant end = Instant.parse("2021-01-01T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null when start is after end");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testLoadSeriesWithHttpError() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(404);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null for HTTP error status");
    }

    @Test
    public void testLoadSeriesWithIOException() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null when IOException occurs");
    }

    @Test
    public void testLoadSeriesWithInterruptedException() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null when InterruptedException occurs");
    }

    @Test
    public void testLoadSeriesWithInvalidJson() throws IOException, InterruptedException {
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("invalid json");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null for invalid JSON");
    }

    @Test
    public void testLoadSeriesWithEmptyResults() throws IOException, InterruptedException {
        String emptyResultsJson = """
                {
                    "chart": {
                        "result": []
                    }
                }
                """;

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(emptyResultsJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null for empty results");
    }

    @Test
    public void testLoadSeriesWithNullValuesInData() throws IOException, InterruptedException {
        String jsonWithNulls = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609459200, null, 1609632000],
                            "indicators": {
                                "quote": [{
                                    "open": [100.0, null, 102.0],
                                    "high": [105.0, 106.0, 107.0],
                                    "low": [99.0, 100.0, 101.0],
                                    "close": [104.0, null, 106.0],
                                    "volume": [1000000, 1100000, 1200000]
                                }]
                            }
                        }]
                    }
                }
                """;

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonWithNulls);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(2, series.getBarCount(), "Should skip bars with null values");
    }

    @Test
    public void testLoadSeriesStaticMethodWithBarCount() throws IOException, InterruptedException {
        // Note: Static methods use DEFAULT_INSTANCE which uses real HttpClient
        // This test verifies the static method signature and error handling
        BarSeries series = YahooFinanceDataSource.loadSeries("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                0);

        assertNull(series, "Should return null for bar count <= 0");
    }

    @Test
    public void testLoadSeriesStaticMethodWithDays() throws IOException, InterruptedException {
        // Note: Static methods use DEFAULT_INSTANCE which uses real HttpClient
        // This test verifies the static method signature
        // In a real scenario, this would make an actual API call
        // For unit testing, we focus on instance methods with mocked HttpClient
        BarSeries series = YahooFinanceDataSource.loadSeries("AAPL", 0);

        assertNull(series, "Should return null for days <= 0");
    }

    @Test
    public void testYahooFinanceIntervalEnum() {
        YahooFinanceDataSource.YahooFinanceInterval interval = YahooFinanceDataSource.YahooFinanceInterval.DAY_1;
        assertEquals(Duration.ofDays(1), interval.getDuration(), "Duration should match");
        assertEquals("1d", interval.getApiValue(), "API value should match");
    }

    @Test
    public void testLoadSeriesSkipsNullVolume() throws IOException, InterruptedException {
        String jsonWithNullVolume = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609459200],
                            "indicators": {
                                "quote": [{
                                    "open": [100.0],
                                    "high": [105.0],
                                    "low": [99.0],
                                    "close": [104.0],
                                    "volume": [null]
                                }]
                            }
                        }]
                    }
                }
                """;

        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);
        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonWithNullVolume);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        YahooFinanceDataSource dataSource = new YahooFinanceDataSource(mockClient);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-02T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(1, series.getBarCount(), "Should have 1 bar");
        assertEquals(0.0, series.getBar(0).getVolume().doubleValue(), 0.001, "Null volume should be 0");
    }

    /**
     * Test subclass that allows overriding the conservative limit for testing
     * pagination.
     */
    private static class TestableYahooFinanceDataSource extends YahooFinanceDataSource {
        private final Duration testConservativeLimit;

        TestableYahooFinanceDataSource(HttpClientWrapper httpClient, Duration testConservativeLimit) {
            super(httpClient);
            this.testConservativeLimit = testConservativeLimit;
        }

        @Override
        protected Duration getConservativeLimit(YahooFinanceInterval interval) {
            return testConservativeLimit;
        }
    }

    @Test
    public void testPaginationWithMultipleChunks() throws IOException, InterruptedException {
        // Use a very small conservative limit (1 day) to force pagination
        Duration testLimit = Duration.ofDays(1);
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        // First chunk response
        String chunk1Json = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609459200, 1609545600],
                            "indicators": {
                                "quote": [{
                                    "open": [100.0, 101.0],
                                    "high": [105.0, 106.0],
                                    "low": [99.0, 100.0],
                                    "close": [104.0, 105.0],
                                    "volume": [1000000, 1100000]
                                }]
                            }
                        }]
                    }
                }
                """;

        // Second chunk response
        String chunk2Json = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609632000, 1609718400],
                            "indicators": {
                                "quote": [{
                                    "open": [102.0, 103.0],
                                    "high": [107.0, 108.0],
                                    "low": [101.0, 102.0],
                                    "close": [106.0, 107.0],
                                    "volume": [1200000, 1300000]
                                }]
                            }
                        }]
                    }
                }
                """;

        HttpResponseWrapper<String> mockResponse1 = mock(HttpResponseWrapper.class);
        HttpResponseWrapper<String> mockResponse2 = mock(HttpResponseWrapper.class);

        when(mockResponse1.statusCode()).thenReturn(200);
        when(mockResponse1.body()).thenReturn(chunk1Json);
        when(mockResponse2.statusCode()).thenReturn(200);
        when(mockResponse2.body()).thenReturn(chunk2Json);

        // Return different responses for different requests
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse1)
                .thenReturn(mockResponse2)
                .thenReturn(mockResponse2); // In case there are more calls

        TestableYahooFinanceDataSource dataSource = new TestableYahooFinanceDataSource(mockClient, testLimit);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z"); // 2 days, exceeds 1-day limit

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(4, series.getBarCount(), "Should have 4 bars from 2 chunks");
        assertEquals("AAPL", series.getName(), "Series name should match ticker");

        // Verify that multiple HTTP requests were made (pagination occurred)
        verify(mockClient, atLeast(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testPaginationWithOverlappingTimestamps() throws IOException, InterruptedException {
        // Test that pagination correctly deduplicates overlapping timestamps
        Duration testLimit = Duration.ofDays(1);
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        // Both chunks have the same timestamp (overlap scenario)
        String chunk1Json = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609459200],
                            "indicators": {
                                "quote": [{
                                    "open": [100.0],
                                    "high": [105.0],
                                    "low": [99.0],
                                    "close": [104.0],
                                    "volume": [1000000]
                                }]
                            }
                        }]
                    }
                }
                """;

        String chunk2Json = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609459200, 1609545600],
                            "indicators": {
                                "quote": [{
                                    "open": [100.0, 101.0],
                                    "high": [105.0, 106.0],
                                    "low": [99.0, 100.0],
                                    "close": [104.0, 105.0],
                                    "volume": [1000000, 1100000]
                                }]
                            }
                        }]
                    }
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

        TestableYahooFinanceDataSource dataSource = new TestableYahooFinanceDataSource(mockClient, testLimit);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        // Should deduplicate the overlapping timestamp, so only 2 unique bars
        assertEquals(2, series.getBarCount(), "Should deduplicate overlapping timestamps");
    }

    @Test
    public void testPaginationWithFailedChunk() throws IOException, InterruptedException {
        // Test that pagination continues even if one chunk fails
        Duration testLimit = Duration.ofDays(1);
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        String chunk1Json = """
                {
                    "chart": {
                        "result": [{
                            "timestamp": [1609459200],
                            "indicators": {
                                "quote": [{
                                    "open": [100.0],
                                    "high": [105.0],
                                    "low": [99.0],
                                    "close": [104.0],
                                    "volume": [1000000]
                                }]
                            }
                        }]
                    }
                }
                """;

        HttpResponseWrapper<String> mockResponse1 = mock(HttpResponseWrapper.class);
        HttpResponseWrapper<String> mockResponse2 = mock(HttpResponseWrapper.class);

        when(mockResponse1.statusCode()).thenReturn(200);
        when(mockResponse1.body()).thenReturn(chunk1Json);
        when(mockResponse2.statusCode()).thenReturn(404); // Second chunk fails

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse1)
                .thenReturn(mockResponse2);

        TestableYahooFinanceDataSource dataSource = new TestableYahooFinanceDataSource(mockClient, testLimit);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        assertEquals(1, series.getBarCount(), "Should have 1 bar from successful chunk");
    }

    @Test
    public void testPaginationWithAllChunksFailing() throws IOException, InterruptedException {
        // Test that pagination returns null if all chunks fail
        Duration testLimit = Duration.ofDays(1);
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(500); // All chunks fail

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        TestableYahooFinanceDataSource dataSource = new TestableYahooFinanceDataSource(mockClient, testLimit);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-03T00:00:00Z");

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNull(series, "Should return null when all chunks fail");
    }

    @Test
    public void testNoPaginationWhenUnderLimit() throws IOException, InterruptedException {
        // Test that pagination is NOT triggered when range is under the limit
        Duration testLimit = Duration.ofDays(10); // Large limit
        HttpClientWrapper mockClient = mock(HttpClientWrapper.class);

        HttpResponseWrapper<String> mockResponse = mock(HttpResponseWrapper.class);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(VALID_JSON_RESPONSE);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        TestableYahooFinanceDataSource dataSource = new TestableYahooFinanceDataSource(mockClient, testLimit);
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2021-01-02T00:00:00Z"); // Only 1 day, under 10-day limit

        BarSeries series = dataSource.loadSeriesInstance("AAPL", YahooFinanceDataSource.YahooFinanceInterval.DAY_1,
                start, end);

        assertNotNull(series, "BarSeries should not be null");
        // Should only make one request (no pagination)
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
