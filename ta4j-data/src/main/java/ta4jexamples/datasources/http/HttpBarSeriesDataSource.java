/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.http;

import ta4jexamples.datasources.BarSeriesDataSource;

/**
 * Marker interface for HTTP-based BarSeries data sources.
 * <p>
 * This interface extends {@link BarSeriesDataSource} to identify data sources
 * that fetch data over HTTP. Implementations of this interface typically use
 * {@link HttpClientWrapper} for making HTTP requests and may support response
 * caching.
 * <p>
 * HTTP-based data sources share common functionality such as:
 * <ul>
 * <li>HTTP request handling via {@link HttpClientWrapper}</li>
 * <li>Response caching to disk for faster subsequent requests</li>
 * <li>Cache validation and management</li>
 * <li>Pagination for large data requests</li>
 * </ul>
 * <p>
 * For shared implementation, consider extending
 * {@link AbstractHttpBarSeriesDataSource} which provides common HTTP and
 * caching infrastructure.
 *
 * @since 0.20
 */
public interface HttpBarSeriesDataSource extends BarSeriesDataSource {

    /**
     * Returns the HttpClientWrapper used by this data source for making HTTP
     * requests.
     *
     * @return the HttpClientWrapper instance
     */
    HttpClientWrapper getHttpClient();

    /**
     * Returns whether response caching is enabled for this data source.
     *
     * @return true if caching is enabled, false otherwise
     */
    boolean isResponseCachingEnabled();
}
