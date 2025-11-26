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
import java.io.InputStream;
import java.io.InputStreamReader;
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
public class JsonBarsDataSource implements BarSeriesDataSource {
    private static final Gson TYPEADAPTER_GSON = new GsonBuilder()
            .registerTypeAdapter(BarSeries.class, new AdaptiveBarSeriesTypeAdapter())
            .create();
    private static final Logger LOG = LogManager.getLogger(JsonBarsDataSource.class);
    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

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
        // Standard pattern: {Exchange}-{ticker}-{interval}-{startDate}_{endDate}.json
        // Exchange prefixes: Coinbase-, Binance- (required for JSON files as they're
        // exchange-specific)
        String startDateStr = start.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
        String endDateStr = end.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
        String intervalStr = formatIntervalForFilename(interval);

        // Try with exchange prefixes (Coinbase-, Binance-)
        String[] exchangePrefixes = { "Coinbase-", "Binance-" };
        for (String exchange : exchangePrefixes) {
            // Try exact pattern: {Exchange}-{ticker}-{interval}-{startDate}_{endDate}.json
            String pattern = exchange + ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_" + endDateStr
                    + ".json";
            BarSeries series = loadFromSource(pattern);
            if (series != null && !series.isEmpty()) {
                return filterSeriesByDateRange(series, start, end);
            }
        }

        // Try without exchange prefix as fallback (for generic JSON files)
        String pattern = ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_" + endDateStr + ".json";
        BarSeries series = loadFromSource(pattern);
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
    public static final JsonBarsDataSource DEFAULT_INSTANCE = new JsonBarsDataSource();

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
            InputStream resourceStream = JsonBarsDataSource.class.getClassLoader().getResourceAsStream(source);
            if (resourceStream != null) {
                return loadFromStream(resourceStream);
            }
            LOG.debug("Unable to load bars from file: {}", source, e);
            return null;
        }
    }

    /**
     * Internal implementation for loading from InputStream.
     */
    private BarSeries loadFromStream(InputStream inputStream) {
        if (inputStream == null) {
            LOG.debug("Input stream is null, returning null");
            return null;
        }

        try (JsonReader reader = new JsonReader(new InputStreamReader(inputStream))) {
            return TYPEADAPTER_GSON.fromJson(reader, BarSeries.class);
        } catch (Exception e) {
            LOG.debug("Unable to load bars from JSON using TypeAdapter", e);
            return null;
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
