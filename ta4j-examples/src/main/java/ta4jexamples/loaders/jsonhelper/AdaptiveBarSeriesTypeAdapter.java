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
package ta4jexamples.loaders.jsonhelper;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A TypeAdapter implementation for deserializing JSON data into BarSeries
 * objects. This adapter supports multiple JSON formats by detecting the
 * structure of the input data. It currently handles two formats: - Coinbase
 * format: identified by the presence of a "candles" array - Binance format:
 * identified by the presence of an "ohlc" array
 * <p>
 * The adapter parses the JSON input and converts it into a BaseBarSeries
 * instance populated with the appropriate bar data. The bar data is sorted by
 * timestamp for the Coinbase format to ensure chronological order.
 * <p>
 * Write operations are not supported by this adapter and will throw an
 * exception.
 * <p>
 * The adapter uses internal lightweight data classes to temporarily hold the
 * parsed JSON data before converting it into the final BarSeries format.
 * <p>
 * Logging is implemented to track which format is being parsed during
 * deserialization.
 *
 * @since 0.19
 */
public class AdaptiveBarSeriesTypeAdapter extends TypeAdapter<BarSeries> {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveBarSeriesTypeAdapter.class);

    @Override
    public void write(JsonWriter out, BarSeries value) throws IOException {
        // Not implemented for this use case
        throw new UnsupportedOperationException("Write operation not supported");
    }

    /**
     * Reads a BarSeries from the provided JsonReader by detecting the format based
     * on available fields. The method parses the JSON input and delegates to
     * appropriate parsing methods depending on whether the root object contains
     * "candles" or "ohlc" fields.
     *
     * @param in the JsonReader to read the JSON data from
     * @return the parsed BarSeries object
     * @throws JsonParseException if the JSON format is unknown or neither "candles"
     *                            nor "ohlc" fields are found
     * @since 0.19
     */
    @Override
    public BarSeries read(JsonReader in) {
        JsonElement json = JsonParser.parseReader(in);
        JsonObject root = json.getAsJsonObject();

        // Detect format based on available fields
        if (root.has("candles")) {
            return parseCoinbaseFormat(root);
        } else if (root.has("ohlc")) {
            return parseBinanceFormat(root);
        } else {
            throw new JsonParseException("Unknown format - neither 'candles' nor 'ohlc' found");
        }
    }

    /**
     * Parses a JSON object in Coinbase format into a BarSeries. The input JSON is
     * expected to contain a "candles" array where each element represents a bar
     * with start time, open, high, low, close, and volume values.
     *
     * @param root the JsonObject representing the root of the JSON data in Coinbase
     *             format
     * @return a BarSeries populated with data parsed from the Coinbase format JSON
     */
    private BarSeries parseCoinbaseFormat(JsonObject root) {
        LOG.trace("Parsing Coinbase format");

        JsonArray candles = root.getAsJsonArray("candles");
        List<CoinbaseBar> barList = new ArrayList<>();

        for (JsonElement candle : candles) {
            JsonObject candleObj = candle.getAsJsonObject();
            barList.add(new CoinbaseBar(candleObj.get("start").getAsString(), candleObj.get("open").getAsString(),
                    candleObj.get("high").getAsString(), candleObj.get("low").getAsString(),
                    candleObj.get("close").getAsString(), candleObj.get("volume").getAsString()));
        }

        // Sort by timestamp
        barList.sort(Comparator.comparingLong(CoinbaseBar::getStartTime));

        // Build series
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("CoinbaseData").build();
        for (CoinbaseBar bar : barList) {
            bar.addToSeries(series);
        }

        return series;
    }

    /**
     * Parses a JSON object in Binance format into a BarSeries. The input JSON is
     * expected to contain an "ohlc" array where each element represents a bar with
     * end time, open price, high price, low price, close price, volume, and amount
     * values.
     *
     * @param root the JsonObject representing the root of the JSON data in Binance
     *             format
     * @return a BarSeries populated with data parsed from the Binance format JSON
     */
    private BarSeries parseBinanceFormat(JsonObject root) {
        LOG.trace("Parsing Binance format");

        JsonArray ohlc = root.getAsJsonArray("ohlc");
        String seriesName = root.has("name") ? root.get("name").getAsString() : "BinanceData";

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();

        for (JsonElement barElement : ohlc) {
            JsonObject barObj = barElement.getAsJsonObject();
            BinanceBar bar = new BinanceBar(barObj.get("endTime").getAsLong(), barObj.get("openPrice").getAsNumber(),
                    barObj.get("highPrice").getAsNumber(), barObj.get("lowPrice").getAsNumber(),
                    barObj.get("closePrice").getAsNumber(), barObj.get("volume").getAsNumber(),
                    barObj.get("amount").getAsNumber());
            bar.addToSeries(series);
        }

        return series;
    }

    // Lightweight data classes for internal use only
    private record CoinbaseBar(String start, String open, String high, String low, String close, String volume) {

        public long getStartTime() {
            return Long.parseLong(start);
        }

        public void addToSeries(BaseBarSeries series) {
            Instant endTime = Instant.ofEpochSecond(getStartTime());
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(volume)
                    .add();
        }
    }

    private record BinanceBar(long endTime, Number openPrice, Number highPrice, Number lowPrice, Number closePrice,
            Number volume, Number amount) {

        public void addToSeries(BaseBarSeries series) {
            Instant endTimeInstant = Instant.ofEpochMilli(endTime);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTimeInstant)
                    .openPrice(openPrice)
                    .highPrice(highPrice)
                    .lowPrice(lowPrice)
                    .closePrice(closePrice)
                    .volume(volume)
                    .amount(amount)
                    .add();
        }
    }
}
