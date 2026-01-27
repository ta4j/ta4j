/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import com.google.gson.JsonArray;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Loads OHLCV data from Yahoo Finance API.
 * <p>
 * This loader fetches historical price data from Yahoo Finance's public API
 * without requiring an API key. It supports stocks, ETFs, and cryptocurrencies.
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>
 * // Load 1 year of daily data for Apple stock (using days)
 * BarSeries series = YahooFinanceHttpBarSeriesDataSource.loadSeries("AAPL", 365);
 *
 * // Load 500 bars of hourly data for Bitcoin (using bar count)
 * BarSeries btcSeries = YahooFinanceHttpBarSeriesDataSource.loadSeries("BTC-USD", YahooFinanceInterval.HOUR_1, 500);
 *
 * // Load data for a specific date range
 * Instant start = Instant.parse("2023-01-01T00:00:00Z");
 * Instant end = Instant.parse("2023-12-31T23:59:59Z");
 * BarSeries msftSeries = YahooFinanceHttpBarSeriesDataSource.loadSeries("MSFT", YahooFinanceInterval.DAY_1, start,
 *         end);
 * </pre>
 * <p>
 * <strong>Response Caching:</strong> To enable response caching for faster
 * subsequent requests, use the constructor with {@code enableResponseCaching}:
 *
 * <pre>
 * YahooFinanceHttpBarSeriesDataSource loader = new YahooFinanceHttpBarSeriesDataSource(true);
 * BarSeries series = loader.loadSeriesInstance("AAPL", YahooFinanceInterval.DAY_1, start, end);
 * </pre>
 * <p>
 * To use a custom cache directory, use the constructor with
 * {@code responseCacheDir}:
 *
 * <pre>
 * YahooFinanceHttpBarSeriesDataSource loader = new YahooFinanceHttpBarSeriesDataSource("/path/to/cache");
 * BarSeries series = loader.loadSeriesInstance("AAPL", YahooFinanceInterval.DAY_1, start, end);
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
 * YahooFinanceHttpBarSeriesDataSource loader = new YahooFinanceHttpBarSeriesDataSource(mockHttpClient);
 * // Use loader instance methods or inject into your code
 * </pre>
 * <p>
 * <strong>Note:</strong> Yahoo Finance is an unofficial API and may have rate
 * limits or availability issues. For production use, consider using official
 * APIs like Alpha Vantage, Polygon.io, or IEX Cloud.
 *
 * @since 0.20
 */
public class YahooFinanceHttpBarSeriesDataSource extends AbstractHttpBarSeriesDataSource {

    public static final String YAHOO_FINANCE_API_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private static final Logger LOG = LogManager.getLogger(YahooFinanceHttpBarSeriesDataSource.class);

    @Override
    public String getSourceName() {
        return "YahooFinance";
    }

    private static final HttpClientWrapper DEFAULT_HTTP_CLIENT = new DefaultHttpClientWrapper();
    private static final YahooFinanceHttpBarSeriesDataSource DEFAULT_INSTANCE = new YahooFinanceHttpBarSeriesDataSource(
            DEFAULT_HTTP_CLIENT);

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with a default HttpClient.
     * For unit testing, use
     * {@link #YahooFinanceHttpBarSeriesDataSource(HttpClientWrapper)} to inject a
     * mock HttpClientWrapper.
     */
    public YahooFinanceHttpBarSeriesDataSource() {
        super(DEFAULT_HTTP_CLIENT, false);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with a default HttpClient
     * and caching option.
     *
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    public YahooFinanceHttpBarSeriesDataSource(boolean enableResponseCaching) {
        super(DEFAULT_HTTP_CLIENT, enableResponseCaching);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with a default HttpClient
     * and custom cache directory. Response caching is automatically enabled when a
     * cache directory is specified.
     *
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    public YahooFinanceHttpBarSeriesDataSource(String responseCacheDir) {
        super(DEFAULT_HTTP_CLIENT, responseCacheDir);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with the specified
     * HttpClientWrapper. This constructor allows dependency injection of a mock
     * HttpClientWrapper for unit testing.
     *
     * @param httpClient the HttpClientWrapper to use for API requests (can be a
     *                   mock for testing)
     */
    public YahooFinanceHttpBarSeriesDataSource(HttpClientWrapper httpClient) {
        super(httpClient, false);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with the specified
     * HttpClientWrapper and caching option. This constructor allows dependency
     * injection of a mock HttpClientWrapper for unit testing and enables response
     * caching.
     *
     * @param httpClient            the HttpClientWrapper to use for API requests
     *                              (can be a mock for testing)
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    public YahooFinanceHttpBarSeriesDataSource(HttpClientWrapper httpClient, boolean enableResponseCaching) {
        super(httpClient, enableResponseCaching);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with the specified
     * HttpClient. This is a convenience constructor that wraps the HttpClient in a
     * DefaultHttpClientWrapper.
     *
     * @param httpClient the HttpClient to use for API requests
     */
    public YahooFinanceHttpBarSeriesDataSource(HttpClient httpClient) {
        super(httpClient, false);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with the specified
     * HttpClient and caching option.
     *
     * @param httpClient            the HttpClient to use for API requests
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    public YahooFinanceHttpBarSeriesDataSource(HttpClient httpClient, boolean enableResponseCaching) {
        super(httpClient, enableResponseCaching);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with the specified
     * HttpClientWrapper and custom cache directory. Response caching is
     * automatically enabled when a cache directory is specified.
     *
     * @param httpClient       the HttpClientWrapper to use for API requests (can be
     *                         a mock for testing)
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    public YahooFinanceHttpBarSeriesDataSource(HttpClientWrapper httpClient, String responseCacheDir) {
        super(httpClient, responseCacheDir);
    }

    /**
     * Creates a new YahooFinanceHttpBarSeriesDataSource with the specified
     * HttpClient and custom cache directory. Response caching is automatically
     * enabled when a cache directory is specified.
     *
     * @param httpClient       the HttpClient to use for API requests
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    public YahooFinanceHttpBarSeriesDataSource(HttpClient httpClient, String responseCacheDir) {
        super(httpClient, responseCacheDir);
    }

    /**
     * Loads historical OHLCV data for a given ticker symbol within a specified date
     * range. This is the base method that all other convenience methods delegate
     * to.
     * <p>
     * <strong>Automatic Pagination:</strong> If the requested date range exceeds
     * conservative API limits, this method automatically splits the request into
     * multiple smaller chunks, fetches them sequentially, and merges the results
     * into a single BarSeries. This ensures reliable data retrieval for large date
     * ranges while respecting API rate limits.
     * <p>
     * <strong>API Limits:</strong> Yahoo Finance's unofficial API has practical
     * limits:
     * <ul>
     * <li>Rate limits: ~2000 requests/hour per IP (may result in temporary bans if
     * exceeded)</li>
     * <li>Data range limits (approximate, may vary):
     * <ul>
     * <li>Intraday (1m-4h): Typically 60-90 days maximum per request</li>
     * <li>Daily (1d): Typically 2-5 years maximum per request</li>
     * <li>Weekly/Monthly (1wk, 1mo): Can request many years per request</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * <strong>Conservative Limits (triggers pagination):</strong>
     * <ul>
     * <li>Intraday (1m-4h): 30 days per chunk</li>
     * <li>Hourly (1h, 4h): 60 days per chunk</li>
     * <li>Daily (1d): 1 year per chunk</li>
     * <li>Weekly/Monthly (1wk, 1mo): 5 years per chunk</li>
     * </ul>
     *
     * @param ticker        the ticker symbol (e.g., "AAPL", "MSFT", "BTC-USD",
     *                      "ETH-USD")
     * @param interval      the bar interval (must be one of the supported Yahoo
     *                      Finance intervals)
     * @param startDateTime the start date/time for the data range (inclusive)
     * @param endDateTime   the end date/time for the data range (inclusive)
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime) {
        return DEFAULT_INSTANCE.loadSeriesInstance(ticker, interval, startDateTime, endDateTime);
    }

    /**
     * Loads historical OHLCV data for a given ticker symbol with a specified number
     * of bars. The end date/time is set to the current time, and the start
     * date/time is calculated based on the bar count and interval.
     * <p>
     * <strong>Note:</strong> If the calculated date range exceeds conservative API
     * limits, this method will automatically paginate the request into multiple API
     * calls and merge the results. This ensures reliable data retrieval for large
     * bar counts.
     *
     * @param ticker   the ticker symbol (e.g., "AAPL", "MSFT", "BTC-USD",
     *                 "ETH-USD")
     * @param interval the bar interval (must be one of the supported Yahoo Finance
     *                 intervals)
     * @param barCount the number of bars to fetch
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String ticker, YahooFinanceInterval interval, int barCount) {
        if (barCount <= 0) {
            LOG.error("Bar count must be greater than 0");
            return null;
        }

        Instant endDateTime = Instant.now();
        Duration totalDuration = interval.getDuration().multipliedBy(barCount);
        Instant startDateTime = endDateTime.minus(totalDuration);

        return loadSeries(ticker, interval, startDateTime, endDateTime);
    }

    /**
     * Loads historical OHLCV data for a given ticker symbol with daily bars.
     * Convenience method that uses the number of days to calculate the date range.
     *
     * @param ticker the ticker symbol (e.g., "AAPL", "MSFT", "BTC-USD", "ETH-USD")
     * @param days   the number of days of historical data to fetch
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String ticker, int days) {
        return loadSeries(ticker, YahooFinanceInterval.DAY_1, days);
    }

    /**
     * Loads historical OHLCV data for a given ticker symbol with a specified
     * interval. Convenience method that uses the number of days to calculate the
     * date range.
     *
     * @param ticker   the ticker symbol (e.g., "AAPL", "MSFT", "BTC-USD",
     *                 "ETH-USD")
     * @param days     the number of days of historical data to fetch
     * @param interval the bar interval (must be one of the supported Yahoo Finance
     *                 intervals)
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public static BarSeries loadSeries(String ticker, int days, YahooFinanceInterval interval) {
        if (days <= 0) {
            LOG.error("Days must be greater than 0");
            return null;
        }

        Instant endDateTime = Instant.now();
        Instant startDateTime = endDateTime.minusSeconds(days * 86400L);

        return loadSeries(ticker, interval, startDateTime, endDateTime);
    }

    /**
     * Parses the Yahoo Finance API JSON response into a BarSeries.
     */
    private static BarSeries parseYahooFinanceResponse(String jsonResponse, String ticker, Duration barInterval) {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonObject chart = root.getAsJsonObject("chart");
            JsonArray results = chart.getAsJsonArray("result");

            if (results == null || results.isEmpty()) {
                LOG.error("No results found in Yahoo Finance response for ticker: {}", ticker);
                return null;
            }

            JsonObject result = results.get(0).getAsJsonObject();

            // Get timestamps array
            JsonArray timestamps = result.getAsJsonArray("timestamp");
            if (timestamps == null) {
                LOG.error("No timestamp data found in Yahoo Finance response for ticker: {}", ticker);
                return null;
            }

            JsonObject indicators = result.getAsJsonObject("indicators");
            if (indicators == null) {
                LOG.error("No indicators found in Yahoo Finance response for ticker: {}", ticker);
                return null;
            }

            JsonArray quotes = indicators.getAsJsonArray("quote");

            if (quotes == null || quotes.isEmpty()) {
                LOG.error("No quote data found in Yahoo Finance response for ticker: {}", ticker);
                return null;
            }

            JsonObject quote = quotes.get(0).getAsJsonObject();
            JsonArray opens = quote.getAsJsonArray("open");
            JsonArray highs = quote.getAsJsonArray("high");
            JsonArray lows = quote.getAsJsonArray("low");
            JsonArray closes = quote.getAsJsonArray("close");
            JsonArray volumes = quote.getAsJsonArray("volume");

            BarSeries series = new BaseBarSeriesBuilder().withName(ticker).build();

            int dataLength = timestamps.size();

            for (int i = 0; i < dataLength; i++) {
                // Skip bars with null values
                if (timestamps.get(i).isJsonNull() || opens.get(i).isJsonNull() || highs.get(i).isJsonNull()
                        || lows.get(i).isJsonNull() || closes.get(i).isJsonNull()) {
                    continue;
                }

                long timestamp = timestamps.get(i).getAsLong();
                Instant endTime = Instant.ofEpochSecond(timestamp);

                double openValue = opens.get(i).getAsDouble();
                double highValue = highs.get(i).getAsDouble();
                double lowValue = lows.get(i).getAsDouble();
                double closeValue = closes.get(i).getAsDouble();
                double volumeValue = volumes.get(i).isJsonNull() ? 0.0 : volumes.get(i).getAsDouble();

                series.barBuilder()
                        .timePeriod(barInterval)
                        .endTime(endTime)
                        .openPrice(openValue)
                        .highPrice(highValue)
                        .lowPrice(lowValue)
                        .closePrice(closeValue)
                        .volume(volumeValue)
                        .amount(0)
                        .add();
            }

            LOG.debug("Successfully loaded {} bars for ticker {}", series.getBarCount(), ticker);
            return series;

        } catch (Exception e) {
            LOG.error("Error parsing Yahoo Finance response for ticker {}: {}", ticker, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Merges multiple BarSeries into a single BarSeries, removing duplicates and
     * sorting chronologically. Uses a TreeMap keyed by timestamp to automatically
     * handle deduplication and sorting.
     *
     * @param chunks      list of BarSeries to merge
     * @param ticker      the ticker symbol (for the merged series name)
     * @param barInterval the bar interval
     * @return a merged BarSeries
     */
    private static BarSeries mergeBarSeries(List<BarSeries> chunks, String ticker, Duration barInterval) {
        // Use TreeMap to automatically sort by timestamp and deduplicate
        TreeMap<Instant, BarData> barMap = new TreeMap<>();

        // Collect all bars from all chunks
        for (BarSeries chunk : chunks) {
            for (int i = 0; i < chunk.getBarCount(); i++) {
                var bar = chunk.getBar(i);
                Instant endTime = bar.getEndTime();

                // If we already have a bar at this timestamp, keep the first one (or you could
                // merge)
                barMap.putIfAbsent(endTime, new BarData(bar));
            }
        }

        // Build the merged series
        BarSeries merged = new BaseBarSeriesBuilder().withName(ticker).build();

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

        LOG.debug("Merged {} chunks into {} unique bars for ticker {}", chunks.size(), merged.getBarCount(), ticker);
        return merged;
    }

    /**
     * Instance method that loads historical OHLCV data for a given ticker symbol
     * with a specified number of bars. The end date/time is set to the current
     * time, and the start date/time is calculated based on the bar count and
     * interval.
     *
     * @param ticker   the ticker symbol (e.g., "AAPL", "MSFT", "BTC-USD",
     *                 "ETH-USD")
     * @param interval the bar interval (must be one of the supported Yahoo Finance
     *                 intervals)
     * @param barCount the number of bars to fetch
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public BarSeries loadSeriesInstance(String ticker, YahooFinanceInterval interval, int barCount) {
        return loadSeriesInstance(ticker, interval, barCount, null);
    }

    /**
     * Instance method that loads historical OHLCV data for a given ticker symbol
     * with a specified number of bars and optional notes for cache file naming. The
     * end date/time is set to the current time, and the start date/time is
     * calculated based on the bar count and interval.
     *
     * @param ticker   the ticker symbol (e.g., "AAPL", "MSFT", "BTC-USD",
     *                 "ETH-USD")
     * @param interval the bar interval (must be one of the supported Yahoo Finance
     *                 intervals)
     * @param barCount the number of bars to fetch
     * @param notes    optional notes to include in cache filename (for uniqueness,
     *                 e.g., test identifiers)
     * @return a BarSeries containing the historical data, or null if the request
     *         fails
     */
    public BarSeries loadSeriesInstance(String ticker, YahooFinanceInterval interval, int barCount, String notes) {
        if (barCount <= 0) {
            LOG.error("Bar count must be greater than 0");
            return null;
        }

        Instant endDateTime = Instant.now();
        Duration totalDuration = interval.getDuration().multipliedBy(barCount);
        Instant startDateTime = endDateTime.minus(totalDuration);

        return loadSeriesInstance(ticker, interval, startDateTime, endDateTime, notes);
    }

    @Override
    public BarSeries loadSeries(String ticker, Duration interval, Instant start, Instant end) {
        if (ticker == null || ticker.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticker cannot be null or empty");
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

        // Map Duration to YahooFinanceInterval
        YahooFinanceInterval yfInterval = mapDurationToInterval(interval);
        if (yfInterval == null) {
            LOG.warn("Unsupported interval duration: {}. Falling back to DAY_1", interval);
            yfInterval = YahooFinanceInterval.DAY_1;
        }

        return loadSeriesInstance(ticker, yfInterval, start, end);
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
                    // Try to extract ticker from filename
                    String filename = cacheFile.getFileName().toString();
                    // Format: {sourceName}-TICKER-INTERVAL-START-END[_NOTES].json
                    // Remove extension
                    String baseName = filename.replace(".json", "");
                    // Notes section is everything after the last underscore that follows the end
                    // timestamp
                    // We need to parse: {sourceName}-TICKER-INTERVAL-START-END[_NOTES]
                    String[] parts = baseName.split("-");
                    if (parts.length >= 5) {
                        // Check if last part contains underscore (indicating notes section)
                        // Format: END or END_NOTES
                        // Notes section is ignored for parsing, so we just need to extract the ticker
                        // and interval

                        String ticker = parts[1];
                        // Try to determine interval from filename
                        YahooFinanceInterval interval = YahooFinanceInterval.DAY_1; // Default
                        try {
                            interval = parseIntervalFromApiValue(parts[2]);
                        } catch (IllegalArgumentException e) {
                            LOG.debug("Could not parse interval from filename, using default: {}", e.getMessage());
                        }
                        return parseYahooFinanceResponse(cachedResponse, ticker, interval.getDuration());
                    }
                }
            }
        }

        // If not a cache file, return null (could be extended to parse other formats)
        return null;
    }

    /**
     * Maps a Duration to the closest matching YahooFinanceInterval.
     *
     * @param duration the duration to map
     * @return the matching YahooFinanceInterval, or null if no close match is found
     */
    private YahooFinanceInterval mapDurationToInterval(Duration duration) {
        long seconds = duration.getSeconds();
        for (YahooFinanceInterval interval : YahooFinanceInterval.values()) {
            if (interval.getDuration().getSeconds() == seconds) {
                return interval;
            }
        }
        return null;
    }

    /**
     * Parses a YahooFinanceInterval from its API value string.
     *
     * @param apiValue the API value (e.g., "1m", "1d", "1wk")
     * @return the matching YahooFinanceInterval
     * @throws IllegalArgumentException if no matching interval is found
     */
    private YahooFinanceInterval parseIntervalFromApiValue(String apiValue) {
        for (YahooFinanceInterval interval : YahooFinanceInterval.values()) {
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
    public BarSeries loadSeriesInstance(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime) {
        return loadSeriesInstance(ticker, interval, startDateTime, endDateTime, null);
    }

    /**
     * Instance method that performs the actual loading logic with optional notes.
     * This method uses the instance's HttpClient (which can be injected for
     * testing).
     *
     * @param ticker        the ticker symbol
     * @param interval      the interval
     * @param startDateTime the start date/time
     * @param endDateTime   the end date/time
     * @param notes         optional notes to include in cache filename (for
     *                      uniqueness)
     * @return the BarSeries or null if request fails
     */
    public BarSeries loadSeriesInstance(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        if (ticker == null || ticker.trim().isEmpty()) {
            LOG.error("Ticker symbol cannot be null or empty");
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

        Duration requestedRange = Duration.between(startDateTime, endDateTime);
        Duration conservativeLimit = this.getConservativeLimit(interval);

        // If the requested range exceeds conservative limits, paginate the request
        if (requestedRange.compareTo(conservativeLimit) > 0) {
            LOG.debug(
                    "Requested date range ({}) exceeds conservative limit ({}) for interval {}. "
                            + "Splitting into multiple requests and combining results.",
                    requestedRange, conservativeLimit, interval);
            return loadSeriesPaginated(ticker, interval, startDateTime, endDateTime, conservativeLimit, notes);
        }

        // Single request for smaller ranges
        return loadSeriesSingleRequest(ticker, interval, startDateTime, endDateTime, notes);
    }

    /**
     * Truncates a timestamp based on the interval to enable cache hits for requests
     * within the same time period. This override provides Yahoo Finance-specific
     * truncation logic for week and month boundaries.
     * <p>
     * For example, for 1-day intervals, timestamps are truncated to the start of
     * the day. For 15-minute intervals, timestamps are truncated to the start of
     * the 15-minute period. For weekly intervals, timestamps are truncated to the
     * start of the week (Monday). For monthly intervals, timestamps are truncated
     * to the start of the month.
     *
     * @param instant  the timestamp to truncate
     * @param interval the interval to use for truncation
     * @return the truncated timestamp
     */

    /**
     * Generates the cache file path for a given request.
     *
     * @param ticker        the ticker symbol
     * @param interval      the interval
     * @param startDateTime the start date/time (will be truncated)
     * @param endDateTime   the end date/time (will be truncated)
     * @param notes         optional notes section to append to filename (can be
     *                      null or empty)
     * @return the cache file path
     */
    private Path getCacheFilePath(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        return getCacheFilePath(ticker, startDateTime, endDateTime, interval.getDuration(), notes);
    }

    /**
     * Generates the cache file path for a given request (without notes).
     *
     * @param ticker        the ticker symbol
     * @param interval      the interval
     * @param startDateTime the start date/time (will be truncated)
     * @param endDateTime   the end date/time (will be truncated)
     * @return the cache file path
     */
    private Path getCacheFilePath(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime) {
        return getCacheFilePath(ticker, interval, startDateTime, endDateTime, null);
    }

    /**
     * Makes a single API request for the specified date range with optional notes.
     * This is used for requests that don't exceed conservative limits. If caching
     * is enabled, checks cache first before making the API request.
     *
     * @param ticker        the ticker symbol
     * @param interval      the interval
     * @param startDateTime the start date/time
     * @param endDateTime   the end date/time
     * @param notes         optional notes to include in cache filename (for
     *                      uniqueness)
     * @return the BarSeries or null if request fails
     */
    private BarSeries loadSeriesSingleRequest(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime, String notes) {
        // Check cache first if caching is enabled
        if (enableResponseCaching) {
            // Try exact match first (with or without notes)
            Path cacheFile = getCacheFilePath(ticker, interval, startDateTime, endDateTime, notes);
            if (isCacheValid(cacheFile, interval.getDuration(), endDateTime)) {
                String cachedResponse = readFromCache(cacheFile);
                if (cachedResponse != null) {
                    LOG.debug("Using cached response for {} ({} to {})", ticker, startDateTime, endDateTime);
                    return parseYahooFinanceResponse(cachedResponse, ticker, interval.getDuration());
                }
            }
            // Also try without notes (for backward compatibility)
            if (notes != null && !notes.trim().isEmpty()) {
                Path cacheFileNoNotes = getCacheFilePath(ticker, interval, startDateTime, endDateTime);
                if (isCacheValid(cacheFileNoNotes, interval.getDuration(), endDateTime)) {
                    String cachedResponse = readFromCache(cacheFileNoNotes);
                    if (cachedResponse != null) {
                        LOG.debug("Using cached response for {} ({} to {})", ticker, startDateTime, endDateTime);
                        return parseYahooFinanceResponse(cachedResponse, ticker, interval.getDuration());
                    }
                }
            }
        }

        try {
            String encodedTicker = URLEncoder.encode(ticker.trim(), StandardCharsets.UTF_8);
            long period1 = startDateTime.getEpochSecond();
            long period2 = endDateTime.getEpochSecond();

            String url = String.format("%s%s?interval=%s&period1=%d&period2=%d", YAHOO_FINANCE_API_URL, encodedTicker,
                    interval.getApiValue(), period1, period2);

            LOG.trace("Fetching data from Yahoo Finance: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponseWrapper<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.error("Yahoo Finance API returned status code: {}", response.statusCode());
                return null;
            }

            String responseBody = response.body();
            LOG.trace("Response body: {}", responseBody);

            // Cache the response if caching is enabled
            if (enableResponseCaching) {
                Path cacheFile = getCacheFilePath(ticker, interval, startDateTime, endDateTime, notes);
                writeToCache(cacheFile, responseBody);
            }

            return parseYahooFinanceResponse(responseBody, ticker, interval.getDuration());

        } catch (IOException | InterruptedException e) {
            LOG.error("Error fetching data from Yahoo Finance for ticker {}: {}", ticker, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Loads data by splitting a large date range into multiple smaller requests
     * (pagination). Each chunk respects the conservative limit, and results are
     * merged chronologically.
     *
     * @param ticker        the ticker symbol
     * @param interval      the bar interval
     * @param startDateTime the start date/time
     * @param endDateTime   the end date/time
     * @param chunkSize     the maximum size for each chunk
     * @param notes         optional notes to include in cache filename (for
     *                      uniqueness)
     * @return a BarSeries containing all merged data, or null if all requests fail
     */
    private BarSeries loadSeriesPaginated(String ticker, YahooFinanceInterval interval, Instant startDateTime,
            Instant endDateTime, Duration chunkSize, String notes) {
        List<BarSeries> chunks = new ArrayList<>();
        Instant currentStart = startDateTime;
        int requestCount = 0;

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

            BarSeries chunk = loadSeriesSingleRequest(ticker, interval, currentStart, chunkEnd, notes);
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
            LOG.error("All paginated requests failed for ticker {}", ticker);
            return null;
        }

        LOG.debug("Successfully fetched {} chunks, merging {} total bars", chunks.size(),
                chunks.stream().mapToInt(BarSeries::getBarCount).sum());

        return mergeBarSeries(chunks, ticker, interval.getDuration());
    }

    /**
     * Returns the conservative (safe) maximum date range for a given interval.
     * These are smaller than the absolute maximums to ensure reliable API
     * responses. Used to determine when pagination is needed.
     * <p>
     * This method is protected to allow subclasses (e.g., in tests) to override the
     * conservative limit for testing pagination functionality.
     *
     * @param interval the bar interval
     * @return the conservative maximum date range
     */
    protected Duration getConservativeLimit(YahooFinanceInterval interval) {
        return switch (interval) {
        case MINUTE_1, MINUTE_5, MINUTE_15, MINUTE_30 -> Duration.ofDays(30); // 30 days for intraday (conservative)
        case HOUR_1, HOUR_4 -> Duration.ofDays(60); // 60 days for hourly (conservative)
        case DAY_1 -> Duration.ofDays(365); // 1 year for daily (conservative)
        case WEEK_1, MONTH_1 -> Duration.ofDays(365 * 5); // 5 years for weekly/monthly (conservative)
        };
    }

    /**
     * Supported intervals for Yahoo Finance API. These correspond to the intervals
     * that Yahoo Finance's chart API supports.
     */
    public enum YahooFinanceInterval {
        /**
         * 1 minute bars
         */
        MINUTE_1(Duration.ofMinutes(1), "1m"),
        /**
         * 5 minute bars
         */
        MINUTE_5(Duration.ofMinutes(5), "5m"),
        /**
         * 15 minute bars
         */
        MINUTE_15(Duration.ofMinutes(15), "15m"),
        /**
         * 30 minute bars
         */
        MINUTE_30(Duration.ofMinutes(30), "30m"),
        /**
         * 1 hour bars
         */
        HOUR_1(Duration.ofHours(1), "1h"),
        /**
         * 4 hour bars
         */
        HOUR_4(Duration.ofHours(4), "4h"),
        /**
         * 1 day bars
         */
        DAY_1(Duration.ofDays(1), "1d"),
        /**
         * 1 week bars
         */
        WEEK_1(Duration.ofDays(7), "1wk"),
        /**
         * 1 month bars
         */
        MONTH_1(Duration.ofDays(30), "1mo");

        private final Duration duration;
        private final String apiValue;

        YahooFinanceInterval(Duration duration, String apiValue) {
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
