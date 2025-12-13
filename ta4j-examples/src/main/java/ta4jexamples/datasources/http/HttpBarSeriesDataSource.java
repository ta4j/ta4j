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
