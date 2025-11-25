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
import ta4jexamples.datasources.jsonhelper.AdaptiveBarSeriesTypeAdapter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A serializer for BarSeries objects that can adapt to different JSON formats.
 * This class provides methods to deserialize BarSeries data from JSON format,
 * specifically supporting multiple exchange formats such as Binance and
 * Coinbase. It uses Gson with a custom TypeAdapter to handle the
 * deserialization process. The serializer can read from either an InputStream
 * or a file path.
 *
 * @since 0.19
 */
public class AdaptiveJsonBarsSerializer {
    private static final Gson TYPEADAPTER_GSON = new GsonBuilder()
            .registerTypeAdapter(BarSeries.class, new AdaptiveBarSeriesTypeAdapter())
            .create();
    private static final Logger LOG = LogManager.getLogger(AdaptiveJsonBarsSerializer.class);

    /**
     * Loads a BarSeries from the provided input stream containing JSON data. The
     * method uses a Gson TypeAdapter to deserialize the JSON data into a BarSeries
     * object. The TypeAdapter is configured to handle both Binance and Coinbase
     * JSON formats. If the input stream is null, the method logs a warning and
     * returns null. If an exception occurs during deserialization, the method logs
     * an error and returns null.
     *
     * @param inputStream the input stream containing JSON data to be deserialized
     * @return the deserialized BarSeries object, or null if the input stream is
     *         null or an error occurs
     * @since 0.19
     */
    public static BarSeries loadSeries(InputStream inputStream) {
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
     * Loads a BarSeries from a JSON file. This method attempts to open and read the
     * specified file, then deserializes its contents into a BarSeries object. It
     * uses the {@link #loadSeries(InputStream)} method internally to perform the
     * actual deserialization. If the file cannot be opened or an error occurs
     * during deserialization, the method logs an error and returns null.
     *
     * @param filename the path to the file containing JSON data to be deserialized
     * @return the deserialized BarSeries object, or null if an error occurs
     * @since 0.19
     */
    public static BarSeries loadSeries(String filename) {
        try (FileInputStream fis = new FileInputStream(filename)) {
            return loadSeries(fis);
        } catch (Exception e) {
            LOG.debug("Unable to load bars from file: {}", filename, e);
            return null;
        }
    }

}
