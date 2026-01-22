/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Abstract base class for HTTP-based BarSeries data sources.
 * <p>
 * This class provides common infrastructure for HTTP-based data sources,
 * including:
 * <ul>
 * <li>HTTP client management via {@link HttpClientWrapper}</li>
 * <li>Response caching to disk for faster subsequent requests</li>
 * <li>Cache validation and management</li>
 * <li>Common cache file operations</li>
 * </ul>
 * <p>
 * Subclasses should implement the API-specific logic such as:
 * <ul>
 * <li>Building API-specific URLs and request parameters</li>
 * <li>Parsing API-specific response formats</li>
 * <li>Defining interval enums and their API values</li>
 * <li>Implementing interval-specific timestamp truncation logic</li>
 * </ul>
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>
 * public class MyHttpDataSource extends AbstractHttpBarSeriesDataSource {
 *     public MyHttpDataSource() {
 *         super(new DefaultHttpClientWrapper(), false);
 *     }
 *
 *     // Implement abstract methods and API-specific logic
 * }
 * </pre>
 *
 * @since 0.20
 */
public abstract class AbstractHttpBarSeriesDataSource implements HttpBarSeriesDataSource {

    /**
     * Default directory for caching HTTP responses.
     */
    public static final String DEFAULT_RESPONSE_CACHE_DIR = "temp/responses";

    private static final Logger LOG = LogManager.getLogger(AbstractHttpBarSeriesDataSource.class);

    protected final HttpClientWrapper httpClient;
    protected final boolean enableResponseCaching;
    protected final String responseCacheDir;

    /**
     * Creates a new AbstractHttpBarSeriesDataSource with the specified
     * HttpClientWrapper and caching option, using the default cache directory.
     *
     * @param httpClient            the HttpClientWrapper to use for API requests
     *                              (can be a mock for testing)
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     * @throws IllegalArgumentException if httpClient is null
     */
    protected AbstractHttpBarSeriesDataSource(HttpClientWrapper httpClient, boolean enableResponseCaching) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClientWrapper cannot be null");
        }
        this.httpClient = httpClient;
        this.enableResponseCaching = enableResponseCaching;
        this.responseCacheDir = DEFAULT_RESPONSE_CACHE_DIR;
        if (enableResponseCaching) {
            ensureCacheDirectoryExists();
        }
    }

    /**
     * Creates a new AbstractHttpBarSeriesDataSource with the specified
     * HttpClientWrapper and custom cache directory. Response caching is
     * automatically enabled when a cache directory is specified.
     *
     * @param httpClient       the HttpClientWrapper to use for API requests (can be
     *                         a mock for testing)
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     * @throws IllegalArgumentException if httpClient is null or responseCacheDir is
     *                                  null or empty
     */
    protected AbstractHttpBarSeriesDataSource(HttpClientWrapper httpClient, String responseCacheDir) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClientWrapper cannot be null");
        }
        if (responseCacheDir == null || responseCacheDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Response cache directory cannot be null or empty");
        }
        this.httpClient = httpClient;
        this.enableResponseCaching = true;
        this.responseCacheDir = responseCacheDir.trim();
        ensureCacheDirectoryExists();
    }

    /**
     * Creates a new AbstractHttpBarSeriesDataSource with the specified HttpClient.
     * This is a convenience constructor that wraps the HttpClient in a
     * DefaultHttpClientWrapper, using the default cache directory.
     *
     * @param httpClient            the HttpClient to use for API requests
     * @param enableResponseCaching if true, responses will be cached to disk for
     *                              faster subsequent requests
     */
    protected AbstractHttpBarSeriesDataSource(HttpClient httpClient, boolean enableResponseCaching) {
        this(new DefaultHttpClientWrapper(httpClient), enableResponseCaching);
    }

    /**
     * Creates a new AbstractHttpBarSeriesDataSource with the specified HttpClient
     * and custom cache directory. This is a convenience constructor that wraps the
     * HttpClient in a DefaultHttpClientWrapper. Response caching is automatically
     * enabled when a cache directory is specified.
     *
     * @param httpClient       the HttpClient to use for API requests
     * @param responseCacheDir the directory path for caching responses (can be
     *                         relative or absolute)
     */
    protected AbstractHttpBarSeriesDataSource(HttpClient httpClient, String responseCacheDir) {
        this(new DefaultHttpClientWrapper(httpClient), responseCacheDir);
    }

    @Override
    public HttpClientWrapper getHttpClient() {
        return httpClient;
    }

    @Override
    public boolean isResponseCachingEnabled() {
        return enableResponseCaching;
    }

    /**
     * Returns the response cache directory path.
     *
     * @return the cache directory path
     */
    public String getResponseCacheDir() {
        return responseCacheDir;
    }

    /**
     * Ensures the cache directory exists, creating it if necessary.
     */
    protected void ensureCacheDirectoryExists() {
        try {
            Path cacheDir = Paths.get(responseCacheDir);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                LOG.debug("Created cache directory: {}", cacheDir.toAbsolutePath());
            }
        } catch (IOException e) {
            LOG.warn("Failed to create cache directory: {}", e.getMessage());
        }
    }

    /**
     * Truncates a timestamp based on the interval duration to enable cache hits for
     * requests within the same time period. This is a generic implementation that
     * works with Duration. Subclasses can override this method to provide more
     * precise truncation logic for specific interval types (e.g., week boundaries,
     * month boundaries).
     * <p>
     * For example, for 1-day intervals, timestamps are truncated to the start of
     * the day. For 15-minute intervals, timestamps are truncated to the start of
     * the 15-minute period.
     *
     * @param instant  the timestamp to truncate
     * @param interval the interval duration to use for truncation
     * @return the truncated timestamp
     */
    protected Instant truncateTimestampForCache(Instant instant, Duration interval) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        ZonedDateTime truncated;

        long seconds = interval.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days >= 30) {
            // For monthly intervals (approximately 30 days), truncate to start of month
            truncated = zdt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (days >= 7) {
            // For weekly intervals (approximately 7 days), truncate to start of week
            // (Monday)
            int daysFromMonday = zdt.getDayOfWeek().getValue() - java.time.DayOfWeek.MONDAY.getValue();
            if (daysFromMonday < 0) {
                daysFromMonday += 7; // Handle Sunday (value 7)
            }
            truncated = zdt.minusDays(daysFromMonday).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (days >= 1) {
            // For daily intervals, truncate to start of day
            truncated = zdt.withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (hours >= 1) {
            // For hourly intervals, truncate to start of hour period
            int hour = (int) ((zdt.getHour() / hours) * hours);
            truncated = zdt.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        } else if (minutes >= 1) {
            // For minute intervals, truncate to start of minute period
            int minute = (int) ((zdt.getMinute() / minutes) * minutes);
            truncated = zdt.withMinute(minute).withSecond(0).withNano(0);
        } else {
            // For second-level intervals, truncate to start of second
            int second = (int) ((zdt.getSecond() / seconds) * seconds);
            truncated = zdt.withSecond(second).withNano(0);
        }

        return truncated.toInstant();
    }

    /**
     * Generates the cache file path for a given request.
     *
     * @param symbol        the symbol (ticker, product ID, etc.)
     * @param startDateTime the start date/time (will be truncated)
     * @param endDateTime   the end date/time (will be truncated)
     * @param interval      the interval duration (used for truncation and filename)
     * @param notes         optional notes section to append to filename (can be
     *                      null or empty)
     * @return the cache file path
     */
    protected Path getCacheFilePath(String symbol, Instant startDateTime, Instant endDateTime, Duration interval,
            String notes) {
        Instant truncatedStart = truncateTimestampForCache(startDateTime, interval);
        Instant truncatedEnd = truncateTimestampForCache(endDateTime, interval);

        String sourcePrefix = getSourceName().isEmpty() ? "" : getSourceName() + "-";
        String notesSuffix = (notes != null && !notes.trim().isEmpty()) ? "_" + notes.trim() : "";
        // Use Duration.toString() for standardized format (e.g., "PT24H" for 1 day,
        // "PT1H" for 1 hour)
        String durationString = interval.toString();
        String filename = String.format("%s%s-%s-%d-%d%s.json", sourcePrefix,
                symbol.toUpperCase().replaceAll("[^A-Z0-9-]", "_"), durationString, truncatedStart.getEpochSecond(),
                truncatedEnd.getEpochSecond(), notesSuffix);

        return Paths.get(responseCacheDir, filename);
    }

    /**
     * Checks if a cache file exists and is still valid based on the interval. For
     * historical data (end date in the past), cache is valid indefinitely. For
     * current data (end date is recent), cache expires after the interval duration.
     * <p>
     * Cache validity is determined by the file's modification time (when the cache
     * file was created), not access time. This is appropriate because we want to
     * know how old the cached data is, not when it was last read.
     *
     * @param cacheFile   the cache file path
     * @param interval    the interval duration
     * @param endDateTime the end date/time of the request
     * @return true if cache is valid, false otherwise
     */
    protected boolean isCacheValid(Path cacheFile, Duration interval, Instant endDateTime) {
        if (!Files.exists(cacheFile)) {
            return false;
        }

        try {
            // Check file modification time (when the cache file was created/written)
            Instant fileModified = Files.getLastModifiedTime(cacheFile).toInstant();
            Instant now = Instant.now();

            // If the request is for historical data (end date is in the past), cache is
            // valid indefinitely
            if (endDateTime.isBefore(now.minus(interval))) {
                return true;
            }

            // For current/recent data, cache expires after the interval duration
            Duration age = Duration.between(fileModified, now);
            return age.compareTo(interval) < 0;
        } catch (IOException e) {
            LOG.warn("Failed to check cache file validity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Reads a cached response from disk.
     *
     * @param cacheFile the cache file path
     * @return the cached JSON response, or null if read fails
     */
    protected String readFromCache(Path cacheFile) {
        try {
            String content = Files.readString(cacheFile, StandardCharsets.UTF_8);
            LOG.debug("Cache hit: {}", cacheFile.getFileName());
            return content;
        } catch (IOException e) {
            LOG.warn("Failed to read from cache: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Writes a response to the cache.
     *
     * @param cacheFile the cache file path
     * @param response  the JSON response to cache
     */
    protected void writeToCache(Path cacheFile, String response) {
        try {
            // Ensure parent directory exists
            Path parentDir = cacheFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(cacheFile, response, StandardCharsets.UTF_8);
            LOG.debug("Cached response: {}", cacheFile.getFileName());
        } catch (IOException e) {
            LOG.warn("Failed to write to cache: {}", e.getMessage());
        }
    }

    /**
     * Deletes all cache files in the cache directory that belong to this data
     * source. Files are identified by the source name prefix (e.g., "Coinbase-",
     * "YahooFinance-").
     *
     * @return the number of files deleted
     */
    public int deleteAllCacheFiles() {
        return deleteCacheFilesOlderThan(Duration.ZERO);
    }

    /**
     * Deletes cache files that are older than the specified maximum age. Files are
     * identified by the source name prefix (e.g., "Coinbase-", "YahooFinance-") and
     * are deleted based on their file modification time (when the cache file was
     * created/written).
     * <p>
     * <strong>Note:</strong> This method uses file modification time, not access
     * time. Modification time represents when the cache file was created (when the
     * response was cached), which is appropriate for determining cache age. Access
     * time (when the file was last read) is not used because it's not reliably
     * available across all platforms and filesystems.
     *
     * @param maxAge the maximum age of files to keep (files older than this will be
     *               deleted). Use {@link Duration#ZERO} to delete all files.
     * @return the number of files deleted
     */
    public int deleteCacheFilesOlderThan(Duration maxAge) {
        if (!Files.exists(Paths.get(responseCacheDir))) {
            return 0;
        }

        String sourcePrefix = getSourceName().isEmpty() ? "" : getSourceName() + "-";
        int deletedCount = 0;
        Instant cutoffTime = Instant.now().minus(maxAge);

        try {
            Path cacheDir = Paths.get(responseCacheDir);
            for (Path path : Files.list(cacheDir).toList()) {
                if (Files.isRegularFile(path)) {
                    String filename = path.getFileName().toString();
                    // Check if file belongs to this data source
                    if (sourcePrefix.isEmpty() || filename.startsWith(sourcePrefix)) {
                        try {
                            // If maxAge is ZERO, delete all files regardless of age
                            if (maxAge.isZero() || Files.getLastModifiedTime(path).toInstant().isBefore(cutoffTime)) {
                                Files.delete(path);
                                deletedCount++;
                                LOG.debug("Deleted cache file: {}", filename);
                            }
                        } catch (IOException e) {
                            LOG.warn("Failed to delete cache file {}: {}", filename, e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to list cache directory: {}", e.getMessage());
            return deletedCount;
        }

        if (deletedCount > 0) {
            LOG.info("Deleted {} stale cache file(s) with prefix '{}'", deletedCount, sourcePrefix);
        }

        return deletedCount;
    }

    /**
     * Deletes stale cache files. A file is considered stale if it's older than 30
     * days and the end timestamp encoded in the filename indicates it's for
     * historical data (data that won't change). This is a conservative approach
     * that preserves recent caches and historical data caches that might still be
     * useful.
     * <p>
     * For more control, use {@link #deleteCacheFilesOlderThan(Duration)} or
     * {@link #deleteAllCacheFiles()}.
     *
     * @return the number of files deleted
     */
    public int deleteStaleCacheFiles() {
        return deleteStaleCacheFiles(Duration.ofDays(30));
    }

    /**
     * Deletes stale cache files older than the specified age. A file is considered
     * stale if it's older than the specified age. Files are identified by the
     * source name prefix (e.g., "Coinbase-", "YahooFinance-").
     *
     * @param maxAge the maximum age of files to keep (files older than this will be
     *               deleted)
     * @return the number of files deleted
     */
    public int deleteStaleCacheFiles(Duration maxAge) {
        return deleteCacheFilesOlderThan(maxAge);
    }
}
