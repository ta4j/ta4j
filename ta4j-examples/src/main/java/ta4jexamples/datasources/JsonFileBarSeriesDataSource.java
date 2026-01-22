/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import ta4jexamples.datasources.file.AbstractFileBarSeriesDataSource;
import ta4jexamples.datasources.json.AdaptiveBarSeriesTypeAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

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
public class JsonFileBarSeriesDataSource extends AbstractFileBarSeriesDataSource {
    private static final Gson TYPEADAPTER_GSON = new GsonBuilder()
            .registerTypeAdapter(BarSeries.class, new AdaptiveBarSeriesTypeAdapter())
            .create();
    private static final Logger LOG = LogManager.getLogger(JsonFileBarSeriesDataSource.class);

    /**
     * Creates a new JsonFileBarSeriesDataSource with no source prefix.
     */
    public JsonFileBarSeriesDataSource() {
        super("");
    }

    /**
     * Default instance for backward compatibility with static method calls. Use
     * this instance when migrating from static methods to instance methods.
     */
    public static final JsonFileBarSeriesDataSource DEFAULT_INSTANCE = new JsonFileBarSeriesDataSource();

    @Override
    protected String getFileExtension() {
        return "json";
    }

    @Override
    protected BarSeries searchAndLoadFile(String ticker, String intervalStr, String sourcePrefix,
            String startDateTimeStr, String endDateTimeStr, String startDateStr, String endDateStr, Duration interval,
            Instant start, Instant end) {
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

        return null;
    }

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
            InputStream resourceStream = JsonFileBarSeriesDataSource.class.getClassLoader().getResourceAsStream(source);
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
}
