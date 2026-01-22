/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import org.ta4j.core.BarSeries;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * Common interface for data sources that load BarSeries using business domain
 * concepts (ticker, interval, date range).
 * <p>
 * This interface abstracts away implementation details (files, APIs, databases)
 * and allows users to work with trading domain concepts. File-based
 * implementations will search for files matching the ticker/interval/date range
 * criteria, while API-based implementations will fetch data from remote
 * sources.
 * <p>
 * <strong>Domain-Driven Usage:</strong>
 *
 * <pre>
 * // All data sources work with business concepts
 * BarSeriesDataSource yahoo = new YahooFinanceHttpBarSeriesDataSource(true);
 * BarSeriesDataSource csv = new CsvFileBarSeriesDataSource();
 * BarSeriesDataSource json = new JsonFileBarSeriesDataSource();
 *
 * // Same interface, different implementations
 * BarSeries aapl = yahoo.loadSeries("AAPL", Duration.ofDays(1), Instant.parse("2023-01-01T00:00:00Z"),
 *         Instant.parse("2023-12-31T23:59:59Z"));
 *
 * BarSeries btc = csv.loadSeries("BTC-USD", Duration.ofDays(1), Instant.parse("2023-01-01T00:00:00Z"),
 *         Instant.parse("2023-12-31T23:59:59Z"));
 * </pre>
 * <p>
 * <strong>Direct Source Loading:</strong>
 * <p>
 * For cases where you know the exact source identifier (filename, URL, etc.),
 * use {@link #loadSeries(String)}:
 *
 * <pre>
 * // Direct filename loading (bypasses search logic)
 * BarSeries series = csv.loadSeries("AAPL-PT1D-20130102_20131231.csv");
 * </pre>
 * <p>
 * <strong>Implementation Behavior:</strong>
 * <ul>
 * <li><strong>File-based sources</strong> (CsvFileBarSeriesDataSource,
 * JsonFileBarSeriesDataSource, BitStampCsvTradesFileBarSeriesDataSource):
 * Search for files matching ticker/interval/date range patterns in the
 * classpath or configured directories. The exact filename pattern is
 * implementation-specific.</li>
 * <li><strong>API-based sources</strong> (YahooFinanceHttpBarSeriesDataSource):
 * Fetch data from the API. If caching is enabled, will first check for cached
 * files matching the criteria.</li>
 * </ul>
 *
 * @since 0.20
 */
public interface BarSeriesDataSource {

    /**
     * Loads a BarSeries using business domain concepts.
     * <p>
     * This is the primary method for loading data. Implementations interpret the
     * parameters according to their capabilities:
     * <ul>
     * <li><strong>File-based sources</strong>: Search for files matching the
     * ticker, interval, and date range. The search pattern is
     * implementation-specific (e.g.,
     * "{ticker}_bars_from_{startDate}_{endDate}.csv").</li>
     * <li><strong>API-based sources</strong>: Fetch data from the API for the
     * specified ticker, interval, and date range. May check cache first if caching
     * is enabled.</li>
     * </ul>
     *
     * @param ticker   the ticker symbol or identifier (e.g., "AAPL", "BTC-USD",
     *                 "MSFT")
     * @param interval the bar interval (e.g., Duration.ofDays(1) for daily bars,
     *                 Duration.ofHours(1) for hourly bars)
     * @param start    the start date/time for the data range (inclusive)
     * @param end      the end date/time for the data range (inclusive)
     * @return a BarSeries containing the loaded data, or null if no matching data
     *         is found or loading fails
     * @throws IllegalArgumentException if any parameter is invalid (e.g., null
     *                                  ticker, negative interval, start after end)
     */
    BarSeries loadSeries(String ticker, Duration interval, Instant start, Instant end);

    /**
     * Loads a BarSeries directly from a known source identifier.
     * <p>
     * This method bypasses the search/fetch logic and loads directly from the
     * specified source. Use this when you know the exact source identifier (e.g.,
     * filename, URL, resource name).
     * <p>
     * For file-based sources, this is typically a filename or resource path. For
     * API-based sources, this might be a cached file path or a direct API endpoint.
     *
     * @param source the source identifier (filename, resource name, URL, etc.)
     * @return a BarSeries containing the loaded data, or null if loading fails
     * @throws IllegalArgumentException if the source parameter is invalid or
     *                                  unsupported
     */
    BarSeries loadSeries(String source);

    /**
     * Loads a BarSeries from an InputStream.
     * <p>
     * This method is optional - implementations that don't support InputStream
     * loading should throw {@link UnsupportedOperationException}.
     * <p>
     * The caller is responsible for closing the InputStream after this method
     * returns. Implementations should not close the stream unless they fully
     * consume it.
     *
     * @param inputStream the input stream containing the data
     * @return a BarSeries containing the loaded data, or null if loading fails
     * @throws UnsupportedOperationException if this data source doesn't support
     *                                       InputStream loading
     * @throws IllegalArgumentException      if the inputStream is null or invalid
     */
    default BarSeries loadSeries(InputStream inputStream) {
        throw new UnsupportedOperationException(
                "InputStream loading not supported by " + this.getClass().getSimpleName());
    }

    /**
     * Returns the source name for this data source. This is used for building file
     * search patterns, cache file names, and other source-specific identifiers.
     * <p>
     * For example, a Yahoo Finance data source would return "YahooFinance", which
     * would be used to build cache file names like "YahooFinance-AAPL-1d-...".
     * <p>
     * File-based sources that don't use a source prefix (e.g., generic CSV files)
     * should return an empty string.
     *
     * @return the source name, or an empty string if no source prefix is used
     */
    default String getSourceName() {
        return "";
    }
}
