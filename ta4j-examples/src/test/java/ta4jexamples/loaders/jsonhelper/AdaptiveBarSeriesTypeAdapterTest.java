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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AdaptiveBarSeriesTypeAdapterTest {

    private final Gson gson = new GsonBuilder().registerTypeAdapter(BarSeries.class, new AdaptiveBarSeriesTypeAdapter())
            .create();

    @Test
    void coinbaseCandlesUseActualInterval() {
        String coinbaseJson = """
                {
                  "candles": [
                    {"start":"1700000600","open":"2.0","high":"2.5","low":"1.8","close":"2.1","volume":"300"},
                    {"start":"1700000000","open":"1.0","high":"1.5","low":"0.9","close":"1.4","volume":"100"},
                    {"start":"1700000300","open":"1.4","high":"1.8","low":"1.2","close":"1.7","volume":"200"}
                  ]
                }
                """;

        BarSeries series = gson.fromJson(coinbaseJson, BarSeries.class);

        assertNotNull(series, "Series should be created");
        assertEquals(3, series.getBarCount(), "Three bars expected");

        Duration expectedDuration = Duration.ofMinutes(5);
        long[] expectedStartEpochSeconds = { 1700000000L, 1700000300L, 1700000600L };

        for (int i = 0; i < series.getBarCount(); i++) {
            var bar = series.getBar(i);
            Instant expectedStart = Instant.ofEpochSecond(expectedStartEpochSeconds[i]);
            Instant expectedEnd = expectedStart.plus(expectedDuration);

            assertEquals(expectedDuration, bar.getTimePeriod(), "Bar " + i + " should preserve the 5-minute interval");
            assertEquals(expectedStart, bar.getBeginTime(), "Bar " + i + " should begin at the candle start timestamp");
            assertEquals(expectedEnd, bar.getEndTime(), "Bar " + i + " should end at start plus the detected interval");
        }
    }

    @Test
    void binanceCandlesUseActualInterval() {
        String binanceJson = """
                {
                  "name": "ETH/USD_PT5M@BinanceUS",
                  "ohlc": [
                    {"endTime":1700000600000,"openPrice":102.0,"highPrice":105.0,"lowPrice":101.0,"closePrice":104.0,"volume":20.0,"amount":0.1},
                    {"endTime":1700000000000,"openPrice":100.0,"highPrice":103.0,"lowPrice":99.0,"closePrice":102.0,"volume":10.0,"amount":0.05},
                    {"endTime":1700000300000,"openPrice":102.0,"highPrice":104.0,"lowPrice":101.0,"closePrice":103.0,"volume":15.0,"amount":0.08}
                  ]
                }
                """;

        BarSeries series = gson.fromJson(binanceJson, BarSeries.class);

        assertNotNull(series, "Series should be created");
        assertEquals("ETH/USD_PT5M@BinanceUS", series.getName(), "Series name should be preserved");
        assertEquals(3, series.getBarCount(), "Three bars expected");

        Duration expectedDuration = Duration.ofMinutes(5);
        long[] expectedEndEpochMillis = { 1700000000000L, 1700000300000L, 1700000600000L };

        for (int i = 0; i < series.getBarCount(); i++) {
            var bar = series.getBar(i);
            Instant expectedEnd = Instant.ofEpochMilli(expectedEndEpochMillis[i]);
            Instant expectedStart = expectedEnd.minus(expectedDuration);

            assertEquals(expectedDuration, bar.getTimePeriod(), "Bar " + i + " should preserve the 5-minute interval");
            assertEquals(expectedStart, bar.getBeginTime(),
                    "Bar " + i + " should begin at end minus the detected interval");
            assertEquals(expectedEnd, bar.getEndTime(), "Bar " + i + " should end at the candle timestamp");
        }
    }
}
