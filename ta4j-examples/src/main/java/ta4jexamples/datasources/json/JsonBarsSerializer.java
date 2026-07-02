/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.utils.DeprecationNotifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @deprecated
 *             <p>
 *             Use {@link JsonFileBarSeriesDataSource} instead. Scheduled for
 *             removal in 0.24.0.
 *             </p>
 */
@Deprecated(since = "0.19", forRemoval = true)
public class JsonBarsSerializer {

    private static final Logger LOG = LogManager.getLogger(JsonBarsSerializer.class);

    public JsonBarsSerializer() {
        warnDeprecatedUse();
    }

    private static void warnDeprecatedUse() {
        DeprecationNotifier.warnOnce(JsonBarsSerializer.class,
                "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource", "0.24.0");
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public static void persistSeries(BarSeries series, String filename) {
        warnDeprecatedUse();
        LegacyJsonBarSeriesPayload exportableSeries = LegacyJsonBarSeriesPayload.from(series);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8)) {
            gson.toJson(exportableSeries, writer);
            LOG.debug("Bar series '{}' successfully saved to '{}'", series.getName(), filename);
        } catch (IOException e) {
            LOG.error("Unable to store bars in JSON", e);
        }
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public static BarSeries loadSeries(String filename) {
        warnDeprecatedUse();
        Gson gson = new Gson();
        BarSeries result = null;
        try (Reader reader = Files.newBufferedReader(Path.of(filename), StandardCharsets.UTF_8)) {
            LegacyJsonBarSeriesPayload loadedSeries = gson.fromJson(reader, LegacyJsonBarSeriesPayload.class);

            result = LegacyJsonBarSeriesPayload.toBarSeriesOrNull(loadedSeries);
            if (result == null) {
                LOG.warn("Failed to parse JSON, loadedSeries is null");
                return null;
            }
            LOG.debug("Bar series '" + result.getName() + "' successfully loaded. #Entries: " + result.getBarCount());
        } catch (IOException e) {
            LOG.error("Unable to load bars from JSON", e);
        }
        return result;
    }

    /**
     * Loads a BarSeries from the provided InputStream containing JSON data. The
     * method parses the JSON content using Gson library and converts it to a
     * BarSeries object. If the input stream is null or parsing fails, appropriate
     * warning or error messages are logged and null is returned.
     *
     * @deprecated
     *             <p>
     *             Use {@link JsonFileBarSeriesDataSource#loadSeries(String)}
     *             instead.
     *
     * @param inputStream the input stream containing JSON data to be parsed into a
     *                    BarSeries
     * @return the loaded BarSeries object, or null if loading fails or input stream
     *         is null
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BarSeries loadSeries(InputStream inputStream) {
        warnDeprecatedUse();
        if (inputStream == null) {
            LOG.warn("Input stream is null, returning null");
            return null;
        }

        Gson gson = new Gson();
        BarSeries result = null;
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            LegacyJsonBarSeriesPayload loadedSeries = gson.fromJson(reader, LegacyJsonBarSeriesPayload.class);

            result = LegacyJsonBarSeriesPayload.toBarSeriesOrNull(loadedSeries);
            if (result == null) {
                LOG.warn("Failed to parse JSON, loadedSeries is null");
                return null;
            }

            LOG.debug("Bar series '" + result.getName() + "' successfully loaded. #Entries: " + result.getBarCount());
        } catch (Exception e) {
            LOG.error("Unable to load bars from JSON", e);
        }
        return result;
    }
}
