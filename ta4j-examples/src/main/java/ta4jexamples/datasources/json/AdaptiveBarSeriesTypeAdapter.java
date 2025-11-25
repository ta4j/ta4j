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
package ta4jexamples.datasources.jsonhelper;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger LOG = LogManager.getLogger(AdaptiveBarSeriesTypeAdapter.class);

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
        Duration lastDuration = null;
        for (int i = 0; i < barList.size(); i++) {
            CoinbaseBar bar = barList.get(i);
            Instant previousStart = i > 0 ? barList.get(i - 1).getStartInstant() : null;
            Instant currentStart = bar.getStartInstant();
            Instant nextStart = i + 1 < barList.size() ? barList.get(i + 1).getStartInstant() : null;
            Duration duration = inferDuration(previousStart, currentStart, nextStart, lastDuration);
            lastDuration = duration;
            Instant endTime = currentStart.plus(duration);
            bar.addToSeries(series, endTime, duration);
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

        List<BinanceBar> bars = new ArrayList<>();
        for (JsonElement barElement : ohlc) {
            JsonObject barObj = barElement.getAsJsonObject();
            BinanceBar bar = new BinanceBar(barObj.get("endTime").getAsLong(), barObj.get("openPrice").getAsNumber(),
                    barObj.get("highPrice").getAsNumber(), barObj.get("lowPrice").getAsNumber(),
                    barObj.get("closePrice").getAsNumber(), barObj.get("volume").getAsNumber(),
                    barObj.get("amount").getAsNumber());
            bars.add(bar);
        }

        bars.sort(Comparator.comparingLong(BinanceBar::endTime));

        Duration lastDuration = null;
        for (int i = 0; i < bars.size(); i++) {
            BinanceBar bar = bars.get(i);
            Instant previousEnd = i > 0 ? bars.get(i - 1).getEndInstant() : null;
            Instant currentEnd = bar.getEndInstant();
            Instant nextEnd = i + 1 < bars.size() ? bars.get(i + 1).getEndInstant() : null;
            Duration duration = inferDuration(previousEnd, currentEnd, nextEnd, lastDuration);
            lastDuration = duration;
            bar.addToSeries(series, currentEnd, duration);
        }

        return series;
    }

    private static Duration inferDuration(Instant previous, Instant current, Instant next, Duration fallback) {
        Duration candidate = null;
        if (next != null) {
            candidate = Duration.between(current, next);
        } else if (previous != null) {
            candidate = Duration.between(previous, current);
        }

        if (candidate != null) {
            if (candidate.isNegative()) {
                candidate = candidate.negated();
            }
            if (!candidate.isZero()) {
                return candidate;
            }
        }

        if (fallback != null && !fallback.isZero() && !fallback.isNegative()) {
            return fallback;
        }

        return Duration.ofSeconds(1);
    }

    // Lightweight data classes for internal use only
    private record CoinbaseBar(String start, String open, String high, String low, String close, String volume) {

        public long getStartTime() {
            return Long.parseLong(start);
        }

        public Instant getStartInstant() {
            return Instant.ofEpochSecond(getStartTime());
        }

        public void addToSeries(BaseBarSeries series, Instant endTime, Duration timePeriod) {
            series.barBuilder()
                    .timePeriod(timePeriod)
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

        public Instant getEndInstant() {
            return Instant.ofEpochMilli(endTime);
        }

        public void addToSeries(BaseBarSeries series, Instant endTimeInstant, Duration timePeriod) {
            series.barBuilder()
                    .timePeriod(timePeriod)
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
