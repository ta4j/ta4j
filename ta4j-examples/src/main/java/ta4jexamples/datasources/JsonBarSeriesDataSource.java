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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import ta4jexamples.datasources.json.AdaptiveBarSeriesTypeAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * A data source for BarSeries objects that can adapt to different JSON formats.
 * This class provides methods to load BarSeries data from JSON format,
 * specifically supporting multiple exchange formats such as Binance and
 * Coinbase. It uses Gson with a custom TypeAdapter to handle the
 * deserialization process. The data source can read from either an InputStream
 * or a file path.
 * <p>
 * Implements {@link BarSeriesDataSource} to support domain-driven loading by
 * ticker, interval, and date range. Searches for JSON files matching the
 * specified criteria in the classpath.
 *
 * @since 0.19
 */
public class JsonBarSeriesDataSource implements BarSeriesDataSource {
    private static final Gson TYPEADAPTER_GSON = new GsonBuilder()
            .registerTypeAdapter(BarSeries.class, new AdaptiveBarSeriesTypeAdapter())
            .create();
    private static final Logger LOG = LogManager.getLogger(JsonBarSeriesDataSource.class);
    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FILENAME_DATETIME_HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter FILENAME_DATETIME_MINUTE_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMddHHmm");

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

        // Build search patterns for JSON files
        // Standard pattern:
        // {Exchange}-{ticker}-{interval}-{startDateTime}_{endDateTime}.json
        // Exchange prefixes: Coinbase-, Binance- (required for JSON files as they're
        // exchange-specific)
        // DateTime format depends on interval: minutes -> HHmm, hours -> HH, days ->
        // date only
        // Note: Existing files use date-only format (yyyyMMdd), but the code supports
        // granular formats for future files with multiple files per day
        DateTimeFormatter dateTimeFormatter = getDateTimeFormatterForInterval(interval);
        String startDateTimeStr = start.atZone(ZoneOffset.UTC).format(dateTimeFormatter);
        String endDateTimeStr = end.atZone(ZoneOffset.UTC).format(dateTimeFormatter);

        // Also prepare date-only format since existing files use this format
        String startDateStr = start.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
        String endDateStr = end.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);

        String intervalStr = formatIntervalForFilename(interval);

        // Try with exchange prefixes (Coinbase-, Binance-)
        // NOTE: All branches must call filterSeriesByDateRange() to ensure data is
        // filtered
        // to the requested date range, even when files contain broader date ranges.
        String[] exchangePrefixes = { "Coinbase-", "Binance-" };
        for (String exchange : exchangePrefixes) {
            // Try exact pattern with interval-appropriate format:
            // {Exchange}-{ticker}-{interval}-{startDateTime}_{endDateTime}.json
            String pattern = exchange + ticker.toUpperCase() + "-" + intervalStr + "-" + startDateTimeStr + "_"
                    + endDateTimeStr + ".json";
            BarSeries series = loadFromSource(pattern);
            if (series != null && !series.isEmpty()) {
                return filterSeriesByDateRange(series, start, end);
            }

            // Fallback to date-only format for existing files
            // NOTE: Date-only format may match files with broader date ranges, so filtering
            // is required
            String patternDateOnly = exchange + ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_"
                    + endDateStr + ".json";
            series = loadFromSource(patternDateOnly);
            if (series != null && !series.isEmpty()) {
                return filterSeriesByDateRange(series, start, end);
            }
        }

        // Try without exchange prefix as fallback (for generic JSON files)
        String pattern = ticker.toUpperCase() + "-" + intervalStr + "-" + startDateTimeStr + "_" + endDateTimeStr
                + ".json";
        BarSeries series = loadFromSource(pattern);
        if (series != null && !series.isEmpty()) {
            return filterSeriesByDateRange(series, start, end);
        }

        // Fallback to date-only format
        // NOTE: Date-only format may match files with broader date ranges, so filtering
        // is required
        String patternDateOnly = ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_" + endDateStr
                + ".json";
        series = loadFromSource(patternDateOnly);
        if (series != null && !series.isEmpty()) {
            return filterSeriesByDateRange(series, start, end);
        }

        LOG.debug("No JSON file found matching ticker: {}, interval: {}, date range: {} to {}", ticker, interval, start,
                end);
        return null;
    }

    /**
     * Default instance for backward compatibility with static method calls. Use
     * this instance when migrating from static methods to instance methods.
     */
    public static final JsonBarSeriesDataSource DEFAULT_INSTANCE = new JsonBarSeriesDataSource();

    @Override
    public BarSeries loadSeries(String source) {
        return loadFromSource(source);
    }

    @Override
    public BarSeries loadSeries(InputStream inputStream) {
        return loadFromStream(inputStream);
    }

    /**
     * Internal implementation for loading from a file.
     */
    private BarSeries loadFromSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(source)) {
            return loadFromStream(fis);
        } catch (Exception e) {
            // Try as classpath resource
            InputStream resourceStream = JsonBarSeriesDataSource.class.getClassLoader().getResourceAsStream(source);
            if (resourceStream != null) {
                try (resourceStream) {
                    return loadFromStream(resourceStream);
                } catch (Exception resourceException) {
                    LOG.debug("Unable to load bars from classpath resource: {}", source, resourceException);
                    return null;
                }
            }
            LOG.debug("Unable to load bars from file: {}", source, e);
            return null;
        }
    }

    /**
     * Internal implementation for loading from InputStream.
     * <p>
     * This method fully consumes the stream but does not close it, as per the
     * interface contract. The caller is responsible for closing the stream.
     */
    private BarSeries loadFromStream(InputStream inputStream) {
        if (inputStream == null) {
            LOG.debug("Input stream is null, returning null");
            return null;
        }

        // Read the stream fully into a String without closing it
        // This ensures we fully consume the stream while respecting the contract
        // that the caller is responsible for closing it
        String jsonContent;
        try {
            jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.debug("Unable to read from input stream", e);
            return null;
        }

        // Parse the JSON content from the String
        try (JsonReader reader = new JsonReader(new java.io.StringReader(jsonContent))) {
            return TYPEADAPTER_GSON.fromJson(reader, BarSeries.class);
        } catch (Exception e) {
            LOG.debug("Unable to load bars from JSON using TypeAdapter", e);
            return null;
        }
    }

    /**
     * Determines the appropriate DateTimeFormatter for filename datetime formatting
     * based on the interval. For minute-level intervals, includes hours and
     * minutes. For hour-level intervals, includes hours. For day-level intervals,
     * uses date only.
     *
     * @param interval the bar interval
     * @return the appropriate DateTimeFormatter
     */
    private DateTimeFormatter getDateTimeFormatterForInterval(Duration interval) {
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
    private String formatIntervalForFilename(Duration interval) {
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
    private BarSeries filterSeriesByDateRange(BarSeries series, Instant start, Instant end) {
        if (series == null || series.isEmpty()) {
            return null;
        }

        var filteredSeries = new org.ta4j.core.BaseBarSeriesBuilder().withName(series.getName()).build();
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
