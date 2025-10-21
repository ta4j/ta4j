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

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.io.InputStream;

import static org.junit.Assume.assumeNotNull;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link AdaptiveJsonBarsSerializer} class.
 * <p>
 * This test class verifies the behavior of the
 * {@code AdaptiveJsonBarsSerializer} when loading bar series data from various
 * JSON input streams, including valid Coinbase and Binance formatted data, as
 * well as edge cases such as a null input stream.
 * </p>
 */
public class AdaptiveJsonBarsSerializerTest {

    @Test
    public void testLoadCoinbaseInputStream() {
        String coinbaseJsonPath = "Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(coinbaseJsonPath);
        assumeNotNull(inputStream);

        BarSeries series = AdaptiveJsonBarsSerializer.loadSeries(inputStream);

        assertNotNull(series, "BarSeries should be loaded successfully with deserializer");
        assertTrue(series.getBarCount() > 0, "BarSeries should contain bars");
        assertEquals("CoinbaseData", series.getName(), "Series name should be set correctly");

        // Verify first bar data
        var firstBar = series.getBar(0);
        assertNotNull(firstBar, "First bar should not be null");
        assertTrue(firstBar.getClosePrice().doubleValue() > 0, "Close price should be positive");
        assertTrue(firstBar.getVolume().doubleValue() > 0, "Volume should be positive");
    }

    @Test
    public void testLoadBinanceInputStream() {
        String binanceJsonPath = "Binance-ETH-USD-PT5M-2023-3-13_2023-3-15.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(binanceJsonPath);
        assumeNotNull(inputStream);

        BarSeries series = AdaptiveJsonBarsSerializer.loadSeries(inputStream);

        assertNotNull(series, "BarSeries should be loaded successfully");
        assertTrue(series.getBarCount() > 0, "BarSeries should contain bars");

        // Verify first bar data
        var firstBar = series.getBar(0);
        assertNotNull(firstBar, "First bar should not be null");
        assertTrue(firstBar.getClosePrice().doubleValue() > 0, "Close price should be positive");
        assertTrue(firstBar.getVolume().doubleValue() > 0, "Volume should be positive");

    }

    @Test
    public void testLoadNullInputStream() {
        BarSeries series = AdaptiveJsonBarsSerializer.loadSeries((InputStream) null);
        assertNull(series, "Should return null for null input stream");
    }
}
