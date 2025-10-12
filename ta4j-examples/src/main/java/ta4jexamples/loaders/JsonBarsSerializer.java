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
package ta4jexamples.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.ta4j.core.BarSeries;
import ta4jexamples.loaders.jsonhelper.GsonBarSeries;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonBarsSerializer {

    private static final Logger LOG = Logger.getLogger(JsonBarsSerializer.class.getName());

    public static void persistSeries(BarSeries series, String filename) {
        GsonBarSeries exportableSeries = GsonBarSeries.from(series);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        FileWriter writer = null;
        try {
            writer = new FileWriter(filename);
            gson.toJson(exportableSeries, writer);
            LOG.info("Bar series '" + series.getName() + "' successfully saved to '" + filename + "'");
        } catch (IOException e) {
            e.printStackTrace();
            LOG.log(Level.SEVERE, "Unable to store bars in JSON", e);
        } finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static BarSeries loadSeries(String filename) {
        Gson gson = new Gson();
        FileReader reader = null;
        BarSeries result = null;
        try {
            reader = new FileReader(filename);
            GsonBarSeries loadedSeries = gson.fromJson(reader, GsonBarSeries.class);

            result = loadedSeries.toBarSeries();
            LOG.info("Bar series '" + result.getName() + "' successfully loaded. #Entries: " + result.getBarCount());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            LOG.log(Level.SEVERE, "Unable to load bars from JSON", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Loads a BarSeries from the provided InputStream containing JSON data. The
     * method parses the JSON content using Gson library and converts it to a
     * BarSeries object. If the input stream is null or parsing fails, appropriate
     * warning or error messages are logged and null is returned.
     *
     * @param inputStream the input stream containing JSON data to be parsed into a
     *                    BarSeries
     * @return the loaded BarSeries object, or null if loading fails or input stream
     *         is null
     * @since 0.19
     */
    public static BarSeries loadSeries(InputStream inputStream) {
        if (inputStream == null) {
            LOG.log(Level.WARNING, "Input stream is null, returning null");
            return null;
        }

        Gson gson = new Gson();
        InputStreamReader reader = null;
        BarSeries result = null;
        try {
            reader = new InputStreamReader(inputStream);
            GsonBarSeries loadedSeries = gson.fromJson(reader, GsonBarSeries.class);

            if (loadedSeries == null) {
                LOG.log(Level.WARNING, "Failed to parse JSON, loadedSeries is null");
                return null;
            }

            result = loadedSeries.toBarSeries();
            LOG.info("Bar series '" + result.getName() + "' successfully loaded. #Entries: " + result.getBarCount());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unable to load bars from JSON", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing input stream reader", e);
            }
        }
        return result;
    }
}
