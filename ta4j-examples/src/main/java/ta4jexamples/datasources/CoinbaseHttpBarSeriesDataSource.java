/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import ta4jexamples.datasources.http.AbstractHttpBarSeriesDataSource;
import ta4jexamples.datasources.http.DefaultHttpClientWrapper;
import ta4jexamples.datasources.http.HttpClientWrapper;
import ta4jexamples.datasources.http.HttpResponseWrapper;
import ta4jexamples.datasources.json.AdaptiveBarSeriesTypeAdapter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Loads OHLCV data from Coinbase Advanced Trade API.
 * <p>
 * This loader fetches historical price data from Coinbase's public market data
 * API without requiring authentication. It supports all Coinbase trading pairs
 * (e.g., BTC-USD, ETH-USD).
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>
 * // Load 1 year of daily data for Bitcoin (using days)
 * BarSeries series = CoinbaseHttpBarSeriesDataSource.loadSeries("BTC-USD", 365);
 *
 * // Load 500 bars of hourly data for Ethereum (using bar count)
 * BarSeries ethSeries = CoinbaseHttpBarSeriesDataSource.loadSeries("ETH-USD", CoinbaseInterval.ONE_HOUR, 500);
 *
 * // Load data for a specific date range
 * Instant start = Instant.parse("2023-01-01T00:00:00Z");
 * Instant end = Instant.parse("2023-12-31T23:59:59Z");
 * BarSeries btcSeries = CoinbaseHttpBarSeriesDataSource.loadSeries("BTC-USD", CoinbaseInterval.ONE_DAY, start, end);
 * </pre>
 * <p>
 * <strong>Response Caching:</strong> To enable response caching for faster
 * subsequent requests, use the constructor with {@code enableResponseCaching}:
 *
 * <pre>
 * CoinbaseHttpBarSeriesDataSource loader = new CoinbaseHttpBarSeriesDataSource(true);
 * BarSeries series = loader.loadSeriesInstance("BTC-USD", CoinbaseInterval.ONE_DAY, start, end);
 * </pre>
 * <p>
 * To use a custom cache directory, use the constructor with
 * {@code responseCacheDir}:
 *
 * <pre>
 * CoinbaseHttpBarSeriesDataSource loader = new CoinbaseHttpBarSeriesDataSource("/path/to/cache");
 * BarSeries series = loader.loadSeriesInstance("BTC-USD", CoinbaseInterval.ONE_DAY, start, end);
 * </pre>
 * <p>
 * When caching is enabled, responses are saved to the cache directory (default:
 * {@code temp/responses}) and reused for requests within the cache validity
 * period (based on the interval). For example, daily data is cached for the
 * day, 15-minute data is cached for 15 minutes, etc. Historical data (end date
 * in the past) is cached indefinitely.
 * <p>
 * <strong>Unit Testing:</strong> For unit testing with a mock HttpClient, use
 * the constructor:
 *
 * <pre>
 * HttpClientWrapper mockHttpClient = mock(HttpClientWrapper.class);
 * CoinbaseHttpBarSeriesDataSource loader = new CoinbaseHttpBarSeriesDataSource(mockHttpClient);
 * // Use loader instance methods or inject into your code
 * </pre>
 * <p>
 * <strong>API Limits:</strong> Coinbase API has a maximum of 350 candles per
 * request. This implementation automatically paginates large requests into
 * multiple API calls and merges the results.
 * <p>
 * <strong>Note:</strong> This uses Coinbase's public market data endpoint which
 * does not require authentication. For production use with higher rate limits,
 * consider using authenticated endpoints.
 *
 * @since 0.20
 */
public class CoinbaseHttpBarSeriesDataSource extends AbstractHttpBarSeriesDataSource {

    public static final String COINBASE_API_URL = "https://api.coinbase.com/api/v3/brokerage/market/products/";
    public static final int MAX_CANDLES_PER_REQUEST = 350;

    private static final Logger LOG = LogManager.getLogger(CoinbaseHttpBarSeriesDataSource.class);

    @Override
    public String getSourceName() {
        return "Coinbase";
    }

    private static final HttpClientWrapper DEFAULT_HTTP_CLIENT = new DefaultHttpClientWrapper();
    private static final CoinbaseHttpBarSeriesDataSource DEFAULT_INSTANCE = new CoinbaseHttpBarSeriesDataSource(
            DEFAULT_HTTP_CLIENT);

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with a default HttpClient. For
     * unit testing, use {@link #CoinbaseHttpBarSeriesDataSource(HttpClientWrapper)}
     * to inject a mock HttpClientWrapper.
     */
    public CoinbaseHttpBarSeriesDataSource() {
        super(DEFAULT_HTTP_CLIENT, false);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with a default HttpClient and
     * caching option.
     *
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    public CoinbaseHttpBarSeriesDataSource(boolean enableResponseCaching) {
        super(DEFAULT_HTTP_CLIENT, enableResponseCaching);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with a default HttpClient and
     * custom cache directory. Response caching is automatically enabled when a
     * cache directory is specified.
     *
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    public CoinbaseHttpBarSeriesDataSource(String responseCacheDir) {
        super(DEFAULT_HTTP_CLIENT, responseCacheDir);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with the specified
     * HttpClientWrapper. This constructor allows dependency injection of a mock
     * HttpClientWrapper for unit testing.
     *
     * @param httpClient the HttpClientWrapper to use for API requests (can be a
     *                   mock for testing)
     */
    public CoinbaseHttpBarSeriesDataSource(HttpClientWrapper httpClient) {
        super(httpClient, false);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with the specified
     * HttpClientWrapper and caching option. This constructor allows dependency
     * injection of a mock HttpClientWrapper for unit testing and enables response
     * caching.
     *
     * @param httpClient            the HttpClientWrapper to use for API requests
     *                              (can be a mock for testing)
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    public CoinbaseHttpBarSeriesDataSource(HttpClientWrapper httpClient, boolean enableResponseCaching) {
        super(httpClient, enableResponseCaching);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with the specified HttpClient.
     * This is a convenience constructor that wraps the HttpClient in a
     * DefaultHttpClientWrapper.
     *
     * @param httpClient the HttpClient to use for API requests
     */
    public CoinbaseHttpBarSeriesDataSource(HttpClient httpClient) {
        super(httpClient, false);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with the specified HttpClient
     * and caching option.
     *
     * @param httpClient            the HttpClient to use for API requests
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    public CoinbaseHttpBarSeriesDataSource(HttpClient httpClient, boolean enableResponseCaching) {
        super(httpClient, enableResponseCaching);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with the specified
     * HttpClientWrapper and custom cache directory. Response caching is
     * automatically enabled when a cache directory is specified.
     *
     * @param httpClient       the HttpClientWrapper to use for API requests (can be
     *                         a mock for testing)
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    public CoinbaseHttpBarSeriesDataSource(HttpClientWrapper httpClient, String responseCacheDir) {
        super(httpClient, responseCacheDir);
    }

    /**
     * Creates a new CoinbaseHttpBarSeriesDataSource with the specified HttpClient
     * and custom cache directory. Response caching is automatically enabled when a
     * cache directory is specified.
     *
     * @param httpClient       the HttpClient to use for API requests
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    public CoinbaseHttpBarSeriesDataSource(HttpClient httpClient, String responseCacheDir) {
        super(httpClient, responseCacheDir);
    }

    /**
     * Loads historical OHLCV data for a given product ID within a specified date
     * range. This is the base method that all other convenience methods delegate
     * to.
     * <p>
     * <strong>Automatic Pagination:</strong> If the requested date range would
     * exceed 350 candles (Coinbase's maximum per request), this method
     * automatically splits the request into multiple smaller chunks, fetches them
     * sequentially, and merges the results into a single BarSeries. This ensures
     * reliable data retrieval for large date ranges while respecting API limits.
     *
     * @param productId     the product ID (e.g., "BTC-USD", "ETH-USD")
     * @param interval      the bar interval (must be one of the supported Coinbase
     *                      intervals)
     * @param startDateTime the start date/time for the data range (inclusive)
     * @param endDateTime   the end date/time for the data range (inclusive)
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime) {
        return DEFAULT_INSTANCE.loadSeriesInstance(productId, interval, startDateTime, endDateTime);
    }

    /**
     * Loads historical OHLCV data for a given product ID with a specified number of
     * bars. The end date/time is set to the current time, and the start date/time
     * is calculated based on the bar count and interval.
     * <p>
     * <strong>Note:</strong> If the calculated date range would exceed 350 candles,
     * this method will automatically paginate the request into multiple API calls
     * and merge the results. This ensures reliable data retrieval for large bar
     * counts.
     *
     * @param productId the product ID (e.g., "BTC-USD", "ETH-USD")
     * @param interval  the bar interval (must be one of the supported Coinbase
     *                  intervals)
     * @param barCount  the number of bars to fetch
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String productId, CoinbaseInterval interval, int barCount) {
        if (barCount <= 0) {
            LOG.error("Bar count must be greater than 0");
            return null;
        }

        Instant endDateTime = Instant.now();
        Duration totalDuration = interval.getDuration().multipliedBy(barCount);
        Instant startDateTime = endDateTime.minus(totalDuration);

        return loadSeries(productId, interval, startDateTime, endDateTime);
    }

    /**
     * Loads historical OHLCV data for a given product ID with daily bars.
     * Convenience method that uses the number of days to calculate the date range.
     *
     * @param productId the product ID (e.g., "BTC-USD", "ETH-USD")
     * @param days      the number of days of historical data to fetch
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String productId, int days) {
        return loadSeries(productId, CoinbaseInterval.ONE_DAY, days);
    }

    /**
     * Loads historical OHLCV data for a given product ID with a specified interval.
     * Convenience method that uses the number of days to calculate the date range.
     *
     * @param productId the product ID (e.g., "BTC-USD", "ETH-USD")
     * @param days      the number of days of historical data to fetch
     * @param interval  the bar interval (must be one of the supported Coinbase
     *                  intervals)
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String productId, int days, CoinbaseInterval interval) {
        if (days <= 0) {
            LOG.error("Days must be greater than 0");
            return null;
        }

        Instant endDateTime = Instant.now();
        Instant startDateTime = endDateTime.minusSeconds(days * 86400L);

        return loadSeries(productId, interval, startDateTime, endDateTime);
    }

    /**
     * Parses the Coinbase API JSON response into a BarSeries using
     * AdaptiveBarSeriesTypeAdapter.
     * <p>
     * This method reuses the existing AdaptiveBarSeriesTypeAdapter which already
     * supports Coinbase format and handles null values. The adapter correctly
     * interprets Coinbase's "start" field as the start of the candle period and
     * calculates the end time as start + interval. The known interval from the API
     * request is used directly.
     *
     * @param jsonResponse the JSON response string from Coinbase API
     * @param productId    the product ID (used as series name)
     * @param barInterval  the known bar interval from the API request
     * @return a BarSeries containing the parsed data, or null if parsing fails
     */
    private static BarSeries parseCoinbaseResponse(String jsonResponse, String productId, Duration barInterval) {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

            // Use AdaptiveBarSeriesTypeAdapter's static helper method which handles
            // Coinbase format correctly (treats "start" as start time, calculates end as
            // start + interval)
            // and uses the known interval from the API request
            BarSeries series = AdaptiveBarSeriesTypeAdapter.parseCoinbaseFormat(root, productId, barInterval);

            if (series == null || series.isEmpty()) {
                LOG.error("No candles found in Coinbase response for product: {}", productId);
                return null;
            }

            LOG.debug("Successfully loaded {} bars for product {}", series.getBarCount(), productId);
            return series;

        } catch (Exception e) {
            LOG.error("Error parsing Coinbase response for product {}: {}", productId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Merges multiple BarSeries into a single BarSeries, removing duplicates and
     * sorting chronologically. Uses a TreeMap keyed by timestamp to automatically
     * handle deduplication and sorting.
     *
     * @param chunks      list of BarSeries to merge
     * @param productId   the product ID (for the merged series name)
     * @param barInterval the bar interval
     * @return a merged BarSeries
     */
    private static BarSeries mergeBarSeries(List<BarSeries> chunks, String productId, Duration barInterval) {
        // Use TreeMap to automatically sort by timestamp and deduplicate
        TreeMap<Instant, BarData> barMap = new TreeMap<>();

        // Collect all bars from all chunks
        for (BarSeries chunk : chunks) {
            for (int i = 0; i < chunk.getBarCount(); i++) {
                var bar = chunk.getBar(i);
                Instant endTime = bar.getEndTime();

                // If we already have a bar at this timestamp, keep the first one
                barMap.putIfAbsent(endTime, new BarData(bar));
            }
        }

        // Build the merged series
        BarSeries merged = new BaseBarSeriesBuilder().withName(productId).build();

        for (BarData barData : barMap.values()) {
            merged.barBuilder()
                    .timePeriod(barInterval)
                    .endTime(barData.endTime)
                    .openPrice(barData.open)
                    .highPrice(barData.high)
                    .lowPrice(barData.low)
                    .closePrice(barData.close)
                    .volume(barData.volume)
                    .amount(0)
                    .add();
        }

        LOG.debug("Merged {} chunks into {} unique bars for product {}", chunks.size(), merged.getBarCount(),
                productId);
        return merged;
    }

    /**
     * Instance method that loads historical OHLCV data for a given product ID with
     * a specified number of bars. The end date/time is set to the current time, and
     * the start date/time is calculated based on the bar count and interval.
     *
     * @param productId the product ID (e.g., "BTC-USD", "ETH-USD")
     * @param interval  the bar interval (must be one of the supported Coinbase
     *                  intervals)
     * @param barCount  the number of bars to fetch
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public BarSeries loadSeriesInstance(String productId, CoinbaseInterval interval, int barCount) {
        return loadSeriesInstance(productId, interval, barCount, null);
    }

    /**
     * Instance method that loads historical OHLCV data for a given product ID with
     * a specified number of bars and optional notes for cache file naming. The end
     * date/time is set to the current time, and the start date/time is calculated
     * based on the bar count and interval.
     *
     * @param productId the product ID (e.g., "BTC-USD", "ETH-USD")
     * @param interval  the bar interval (must be one of the supported Coinbase
     *                  intervals)
     * @param barCount  the number of bars to fetch
     * @param notes     optional notes to include in cache filename (for uniqueness,
     *                  e.g., test identifiers)
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public BarSeries loadSeriesInstance(String productId, CoinbaseInterval interval, int barCount, String notes) {
        if (barCount <= 0) {
            LOG.error("Bar count must be greater than 0");
            return null;
        }

        Instant endDateTime = Instant.now();
        Duration totalDuration = interval.getDuration().multipliedBy(barCount);
        Instant startDateTime = endDateTime.minus(totalDuration);

        return loadSeriesInstance(productId, interval, startDateTime, endDateTime, notes);
    }

    @Override
    public BarSeries loadSeries(String productId, Duration interval, Instant start, Instant end) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        // Map Duration to CoinbaseInterval
        CoinbaseInterval cbInterval = mapDurationToInterval(interval);
        if (cbInterval == null) {
            LOG.warn("Unsupported interval duration: {}. Falling back to ONE_DAY", interval);
            cbInterval = CoinbaseInterval.ONE_DAY;
        }

        return loadSeriesInstance(productId, cbInterval, start, end);
    }

    @Override
    public BarSeries loadSeries(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Source cannot be null or empty");
        }

        // Check if it's a cache file path
        String sourcePrefix = getSourceName().isEmpty() ? "" : getSourceName() + "-";
        if (source.startsWith(responseCacheDir) || (!sourcePrefix.isEmpty() && source.contains(sourcePrefix))) {
            Path cacheFile = Paths.get(source);
            if (Files.exists(cacheFile)) {
                String cachedResponse = readFromCache(cacheFile);
                if (cachedResponse != null) {
                    // Try to extract product ID from filename
                    String filename = cacheFile.getFileName().toString();
                    // Format: {sourceName}-PRODUCTID-INTERVAL-START-END[_NOTES].json
                    // Remove extension
                    String baseName = filename.replace(".json", "");
                    String[] parts = baseName.split("-");
                    if (parts.length >= 5) {
                        String productId = parts[1];
                        // Try to determine interval from filename
                        CoinbaseInterval interval = CoinbaseInterval.ONE_DAY; // Default
                        try {
                            interval = parseIntervalFromApiValue(parts[2]);
                        } catch (IllegalArgumentException e) {
                            LOG.debug("Could not parse interval from filename, using default: {}", e.getMessage());
                        }

                        return parseCoinbaseResponse(cachedResponse, productId, interval.getDuration());
                    }
                }
            }
        }

        // If not a cache file, return null (could be extended to parse other formats)
        return null;
    }

    /**
     * Maps a Duration to the closest matching CoinbaseInterval.
     *
     * @param duration the duration to map
     * @return the matching CoinbaseInterval, or null if no close match is found
     */
    private CoinbaseInterval mapDurationToInterval(Duration duration) {
        long seconds = duration.getSeconds();
        for (CoinbaseInterval interval : CoinbaseInterval.values()) {
            if (interval.getDuration().getSeconds() == seconds) {
                return interval;
            }
        }
        return null;
    }

    /**
     * Parses a CoinbaseInterval from its API value string.
     *
     * @param apiValue the API value (e.g., "ONE_MINUTE", "ONE_DAY")
     * @return the matching CoinbaseInterval
     * @throws IllegalArgumentException if no matching interval is found
     */
    private CoinbaseInterval parseIntervalFromApiValue(String apiValue) {
        for (CoinbaseInterval interval : CoinbaseInterval.values()) {
            if (interval.getApiValue().equals(apiValue)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Unknown interval API value: " + apiValue);
    }

    /**
     * Instance method that performs the actual loading logic. This method uses the
     * instance's HttpClient (which can be injected for testing).
     */
    public BarSeries loadSeriesInstance(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime) {
        return loadSeriesInstance(productId, interval, startDateTime, endDateTime, null);
    }

    /**
     * Instance method that performs the actual loading logic with optional notes.
     * This method uses the instance's HttpClient (which can be injected for
     * testing).
     *
     * @param productId     the product ID
     * @param interval      the interval
     * @param startDateTime the start date/time
     * @param endDateTime   the end date/time
     * @param notes         optional notes to include in cache filename (for
     *                      uniqueness)
     * @return the BarSeries or null if request fails
     */
    public BarSeries loadSeriesInstance(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        if (productId == null || productId.trim().isEmpty()) {
            LOG.error("Product ID cannot be null or empty");
            return null;
        }

        if (startDateTime == null || endDateTime == null) {
            LOG.error("Start and end date/time cannot be null");
            return null;
        }

        if (startDateTime.isAfter(endDateTime)) {
            LOG.error("Start date/time must be before or equal to end date/time");
            return null;
        }

        // Calculate if we need pagination (max 350 candles per request)
        Duration requestedRange = Duration.between(startDateTime, endDateTime);
        long requestedBars = requestedRange.dividedBy(interval.getDuration());

        if (requestedBars > MAX_CANDLES_PER_REQUEST) {
            LOG.debug(
                    "Requested date range would result in {} bars (max {}). "
                            + "Splitting into multiple requests and combining results.",
                    requestedBars, MAX_CANDLES_PER_REQUEST);
            return loadSeriesPaginated(productId, interval, startDateTime, endDateTime, notes);
        }

        // Single request for smaller ranges
        return loadSeriesSingleRequest(productId, interval, startDateTime, endDateTime, notes);
    }

    /**
     * Generates the cache file path for a given request.
     *
     * @param productId     the product ID
     * @param interval      the interval
     * @param startDateTime the start date/time (will be truncated)
     * @param endDateTime   the end date/time (will be truncated)
     * @param notes         optional notes section to append to filename (can be
     *                      null or empty)
     * @return the cache file path
     */
    private Path getCacheFilePath(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        return getCacheFilePath(productId, startDateTime, endDateTime, interval.getDuration(), notes);
    }

    /**
     * Generates the cache file path for a given request (without notes).
     *
     * @param productId     the product ID
     * @param interval      the interval
     * @param startDateTime the start date/time (will be truncated)
     * @param endDateTime   the end date/time (will be truncated)
     * @return the cache file path
     */
    private Path getCacheFilePath(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime) {
        return getCacheFilePath(productId, interval, startDateTime, endDateTime, null);
    }

    /**
     * Makes a single API request for the specified date range with optional notes.
     * This is used for requests that don't exceed 350 candles. If caching is
     * enabled, checks cache first before making the API request.
     *
     * @param productId     the product ID
     * @param interval      the interval
     * @param startDateTime the start date/time
     * @param endDateTime   the end date/time
     * @param notes         optional notes to include in cache filename (for
     *                      uniqueness)
     * @return the BarSeries or null if request fails
     */
    private BarSeries loadSeriesSingleRequest(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        // Check cache first if caching is enabled
        if (enableResponseCaching) {
            // Try exact match first (with or without notes)
            Path cacheFile = getCacheFilePath(productId, interval, startDateTime, endDateTime, notes);
            if (isCacheValid(cacheFile, interval.getDuration(), endDateTime)) {
                String cachedResponse = readFromCache(cacheFile);
                if (cachedResponse != null) {
                    LOG.debug("Using cached response for {} ({} to {})", productId, startDateTime, endDateTime);
                    return parseCoinbaseResponse(cachedResponse, productId, interval.getDuration());
                }
            }
            // Also try without notes (for backward compatibility)
            if (notes != null && !notes.trim().isEmpty()) {
                Path cacheFileNoNotes = getCacheFilePath(productId, interval, startDateTime, endDateTime);
                if (isCacheValid(cacheFileNoNotes, interval.getDuration(), endDateTime)) {
                    String cachedResponse = readFromCache(cacheFileNoNotes);
                    if (cachedResponse != null) {
                        LOG.debug("Using cached response for {} ({} to {})", productId, startDateTime, endDateTime);
                        return parseCoinbaseResponse(cachedResponse, productId, interval.getDuration());
                    }
                }
            }
        }

        try {
            String encodedProductId = URLEncoder.encode(productId.trim(), StandardCharsets.UTF_8);
            long startTimestamp = startDateTime.getEpochSecond();
            long endTimestamp = endDateTime.getEpochSecond();

            String url = String.format("%s%s/candles?start=%d&end=%d&granularity=%s&limit=%d", COINBASE_API_URL,
                    encodedProductId, startTimestamp, endTimestamp, interval.getApiValue(), MAX_CANDLES_PER_REQUEST);

            LOG.trace("Fetching data from Coinbase: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponseWrapper<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.error("Coinbase API returned status code: {}", response.statusCode());
                return null;
            }

            String responseBody = response.body();
            LOG.trace("Response body: {}", responseBody);

            // Cache the response if caching is enabled
            if (enableResponseCaching) {
                Path cacheFile = getCacheFilePath(productId, interval, startDateTime, endDateTime, notes);
                writeToCache(cacheFile, responseBody);
            }

            return parseCoinbaseResponse(responseBody, productId, interval.getDuration());

        } catch (IOException | InterruptedException e) {
            LOG.error("Error fetching data from Coinbase for product {}: {}", productId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Loads data by splitting a large date range into multiple smaller requests
     * (pagination). Each chunk respects the 350 candle limit, and results are
     * merged chronologically.
     *
     * @param productId     the product ID
     * @param interval      the bar interval
     * @param startDateTime the start date/time
     * @param endDateTime   the end date/time
     * @param notes         optional notes to include in cache filename (for
     *                      uniqueness)
     * @return a BarSeries containing all merged data, or null if all requests fail
     */
    private BarSeries loadSeriesPaginated(String productId, CoinbaseInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        List<BarSeries> chunks = new ArrayList<>();
        Instant currentStart = startDateTime;
        int requestCount = 0;

        // Calculate chunk size (350 candles worth of time)
        Duration chunkSize = interval.getDuration().multipliedBy(MAX_CANDLES_PER_REQUEST);

        // Calculate number of chunks needed
        Duration totalRange = Duration.between(startDateTime, endDateTime);
        int estimatedChunks = (int) Math.ceil((double) totalRange.toSeconds() / chunkSize.toSeconds());
        LOG.trace("Splitting request into approximately {} chunks", estimatedChunks);

        while (currentStart.isBefore(endDateTime)) {
            // Calculate chunk end time (don't exceed the requested end time)
            Instant chunkEnd = currentStart.plus(chunkSize);
            if (chunkEnd.isAfter(endDateTime)) {
                chunkEnd = endDateTime;
            }

            requestCount++;
            LOG.trace("Fetching chunk {}/? ({} to {})", requestCount, currentStart, chunkEnd);

            BarSeries chunk = loadSeriesSingleRequest(productId, interval, currentStart, chunkEnd, notes);
            if (chunk != null && chunk.getBarCount() > 0) {
                chunks.add(chunk);
                LOG.trace("Successfully loaded chunk {} with {} bars", requestCount, chunk.getBarCount());
            } else {
                LOG.warn("Chunk {} returned no data or failed", requestCount);
            }

            // Move to next chunk (start from the end of current chunk)
            currentStart = chunkEnd;

            // If we've reached the end, break
            if (chunkEnd.equals(endDateTime) || !currentStart.isBefore(endDateTime)) {
                break;
            }

            // Add a small delay between requests to avoid rate limiting
            try {
                Thread.sleep(100); // 100ms delay between requests
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted during pagination delay");
                break;
            }
        }

        if (chunks.isEmpty()) {
            LOG.error("All paginated requests failed for product {}", productId);
            return null;
        }

        LOG.debug("Successfully fetched {} chunks, merging {} total bars", chunks.size(),
                chunks.stream().mapToInt(BarSeries::getBarCount).sum());

        return mergeBarSeries(chunks, productId, interval.getDuration());
    }

    /**
     * Supported intervals for Coinbase API. These correspond to the intervals that
     * Coinbase's Advanced Trade API supports.
     */
    public enum CoinbaseInterval {
        /**
         * 1 minute bars
         */
        ONE_MINUTE(Duration.ofMinutes(1), "ONE_MINUTE"),
        /**
         * 5 minute bars
         */
        FIVE_MINUTE(Duration.ofMinutes(5), "FIVE_MINUTE"),
        /**
         * 15 minute bars
         */
        FIFTEEN_MINUTE(Duration.ofMinutes(15), "FIFTEEN_MINUTE"),
        /**
         * 30 minute bars
         */
        THIRTY_MINUTE(Duration.ofMinutes(30), "THIRTY_MINUTE"),
        /**
         * 1 hour bars
         */
        ONE_HOUR(Duration.ofHours(1), "ONE_HOUR"),
        /**
         * 2 hour bars
         */
        TWO_HOUR(Duration.ofHours(2), "TWO_HOUR"),
        /**
         * 4 hour bars
         */
        FOUR_HOUR(Duration.ofHours(4), "FOUR_HOUR"),
        /**
         * 6 hour bars
         */
        SIX_HOUR(Duration.ofHours(6), "SIX_HOUR"),
        /**
         * 1 day bars
         */
        ONE_DAY(Duration.ofDays(1), "ONE_DAY");

        private final Duration duration;
        private final String apiValue;

        CoinbaseInterval(Duration duration, String apiValue) {
            this.duration = duration;
            this.apiValue = apiValue;
        }

        /**
         * Returns the Duration for this interval.
         *
         * @return the Duration
         */
        public Duration getDuration() {
            return duration;
        }

        /**
         * Returns the API string value for this interval.
         *
         * @return the API string value
         */
        public String getApiValue() {
            return apiValue;
        }
    }

    /**
     * Helper class to hold bar data during merging.
     */
    private static class BarData {
        final Instant endTime;
        final double open;
        final double high;
        final double low;
        final double close;
        final double volume;

        BarData(Bar bar) {
            this.endTime = bar.getEndTime();
            this.open = bar.getOpenPrice().doubleValue();
            this.high = bar.getHighPrice().doubleValue();
            this.low = bar.getLowPrice().doubleValue();
            this.close = bar.getClosePrice().doubleValue();
            this.volume = bar.getVolume().doubleValue();
        }
    }

}
