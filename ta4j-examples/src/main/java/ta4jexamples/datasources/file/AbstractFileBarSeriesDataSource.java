/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import ta4jexamples.datasources.BarSeriesDataSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Abstract base class for file-based BarSeries data sources.
 * <p>
 * This class provides common infrastructure for file-based data sources,
 * including:
 * <ul>
 * <li>Parameter validation for ticker, interval, and date range</li>
 * <li>Filename pattern building based on interval and date range</li>
 * <li>Date range filtering for BarSeries</li>
 * <li>DateTimeFormatter utilities for filename formatting</li>
 * </ul>
 * <p>
 * Subclasses should implement the file format-specific logic such as:
 * <ul>
 * <li>Loading and parsing files (CSV, JSON, etc.)</li>
 * <li>File searching with format-specific patterns</li>
 * <li>Format-specific data transformation</li>
 * </ul>
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>
 * public class MyFileDataSource extends AbstractFileBarSeriesDataSource {
 *     public MyFileDataSource() {
 *         super("MySource");
 *     }
 *
 *     // Implement abstract methods for format-specific loading
 * }
 * </pre>
 *
 * @since 0.20
 */
public abstract class AbstractFileBarSeriesDataSource implements BarSeriesDataSource {

    private static final Logger LOG = LogManager.getLogger(AbstractFileBarSeriesDataSource.class);

    /**
     * Date format for filenames: yyyyMMdd
     */
    protected static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Date-time format for filenames with hours: yyyyMMddHH
     */
    protected static final DateTimeFormatter FILENAME_DATETIME_HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /**
     * Date-time format for filenames with minutes: yyyyMMddHHmm
     */
    protected static final DateTimeFormatter FILENAME_DATETIME_MINUTE_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMddHHmm");

    private final String sourceName;

    /**
     * Creates a new AbstractFileBarSeriesDataSource with the specified source name.
     *
     * @param sourceName the source name for building file search patterns (e.g.,
     *                   "Bitstamp", "Coinbase"). Use empty string for generic
     *                   sources without a prefix.
     */
    protected AbstractFileBarSeriesDataSource(String sourceName) {
        this.sourceName = sourceName != null ? sourceName : "";
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public BarSeries loadSeries(String ticker, Duration interval, Instant start, Instant end) {
        validateParameters(ticker, interval, start, end);

        // Build search patterns for filename matching
        // Standard pattern:
        // {sourcePrefix}{ticker}-{interval}-{startDateTime}_{endDateTime}.{extension}
        // DateTime format depends on interval: minutes -> HHmm, hours -> HH, days ->
        // date only
        DateTimeFormatter dateTimeFormatter = getDateTimeFormatterForInterval(interval);
        String startDateTimeStr = start.atZone(ZoneOffset.UTC).format(dateTimeFormatter);
        String endDateTimeStr = end.atZone(ZoneOffset.UTC).format(dateTimeFormatter);

        // Also prepare date-only format since existing files use this format
        String startDateStr = start.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
        String endDateStr = end.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);

        String intervalStr = formatIntervalForFilename(interval);
        String sourcePrefix = sourceName.isEmpty() ? "" : sourceName + "-";

        // Delegate to subclass for format-specific file searching
        BarSeries series = searchAndLoadFile(ticker, intervalStr, sourcePrefix, startDateTimeStr, endDateTimeStr,
                startDateStr, endDateStr, interval, start, end);

        if (series != null && !series.isEmpty()) {
            return series;
        }

        LOG.debug("No {} file found matching ticker: {}, interval: {}, date range: {} to {}", getFileExtension(),
                ticker, interval, start, end);
        return null;
    }

    /**
     * Validates the parameters for loading a series.
     *
     * @param ticker   the ticker symbol
     * @param interval the bar interval
     * @param start    the start date/time
     * @param end      the end date/time
     * @throws IllegalArgumentException if any parameter is invalid
     */
    protected void validateParameters(String ticker, Duration interval, Instant start, Instant end) {
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
    }

    /**
     * Searches for and loads a file matching the specified patterns. Subclasses
     * should implement this method to handle format-specific file searching logic.
     * <p>
     * This method is called by
     * {@link #loadSeries(String, Duration, Instant, Instant)} with pre-formatted
     * date strings and interval strings. Subclasses should try multiple patterns
     * (exact match, date-only format, broader patterns) and return the first
     * matching file.
     *
     * @param ticker           the ticker symbol (already validated)
     * @param intervalStr      the formatted interval string (e.g., "PT1D", "PT5M")
     * @param sourcePrefix     the source prefix (e.g., "Bitstamp-", or empty
     *                         string)
     * @param startDateTimeStr the formatted start date-time string
     * @param endDateTimeStr   the formatted end date-time string
     * @param startDateStr     the formatted start date string (date only)
     * @param endDateStr       the formatted end date string (date only)
     * @param interval         the interval duration
     * @param start            the start date/time
     * @param end              the end date/time
     * @return the loaded BarSeries, or null if no matching file is found
     */
    protected abstract BarSeries searchAndLoadFile(String ticker, String intervalStr, String sourcePrefix,
            String startDateTimeStr, String endDateTimeStr, String startDateStr, String endDateStr, Duration interval,
            Instant start, Instant end);

    /**
     * Returns the file extension (without the dot) for this data source. Used in
     * log messages and file pattern building.
     *
     * @return the file extension (e.g., "csv", "json")
     */
    protected abstract String getFileExtension();

    /**
     * Determines the appropriate DateTimeFormatter for filename datetime formatting
     * based on the interval. For minute-level intervals, includes hours and
     * minutes. For hour-level intervals, includes hours. For day-level intervals,
     * uses date only.
     *
     * @param interval the bar interval
     * @return the appropriate DateTimeFormatter
     */
    protected DateTimeFormatter getDateTimeFormatterForInterval(Duration interval) {
        long seconds = interval.getSeconds();
        if (seconds < 3600) {
            // Interval is in minutes or seconds - include hours and minutes
            return FILENAME_DATETIME_MINUTE_FORMAT;
        } else if (seconds < 86400) {
            // Interval is in hours - include hours
            return FILENAME_DATETIME_HOUR_FORMAT;
        } else {
            // Interval is in days or longer - date only
            return FILENAME_DATE_FORMAT;
        }
    }

    /**
     * Formats a Duration as an ISO 8601 interval string for use in filenames.
     *
     * @param interval the duration to format
     * @return the ISO 8601 duration string (e.g., "PT1D", "PT5M", "PT1H")
     */
    protected String formatIntervalForFilename(Duration interval) {
        long seconds = interval.getSeconds();
        if (seconds % 86400 == 0) {
            return "PT" + (seconds / 86400) + "D";
        } else if (seconds % 3600 == 0) {
            return "PT" + (seconds / 3600) + "H";
        } else if (seconds % 60 == 0) {
            return "PT" + (seconds / 60) + "M";
        } else {
            return "PT" + seconds + "S";
        }
    }

    /**
     * Filters a BarSeries to only include bars within the specified date range.
     *
     * @param series the series to filter
     * @param start  the start date (inclusive)
     * @param end    the end date (inclusive)
     * @return a new BarSeries containing only bars within the date range, or null
     *         if no bars match
     */
    protected BarSeries filterSeriesByDateRange(BarSeries series, Instant start, Instant end) {
        if (series == null || series.isEmpty()) {
            return null;
        }

        var filteredSeries = new BaseBarSeriesBuilder().withName(series.getName()).build();
        for (int i = 0; i < series.getBarCount(); i++) {
            var bar = series.getBar(i);
            Instant barEnd = bar.getEndTime();
            if (!barEnd.isBefore(start) && !barEnd.isAfter(end)) {
                filteredSeries.addBar(bar);
            }
        }

        return filteredSeries.isEmpty() ? null : filteredSeries;
    }
}
