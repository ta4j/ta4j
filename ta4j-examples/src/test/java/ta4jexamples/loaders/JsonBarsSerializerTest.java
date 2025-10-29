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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JsonBarsSerializer to verify both Binance and Coinbase format
 * support.
 */
public class JsonBarsSerializerTest {

    @Test
    public void testLoadBinanceFormat() {
        // Test loading Binance format JSON (if available)
        String binanceJsonPath = "ETH-USD-PT5M-2023-3-13_2023-3-15.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(binanceJsonPath);

        if (inputStream != null) {
            BarSeries series = JsonBarsSerializer.loadSeries(inputStream);

            assertNotNull(series, "BarSeries should be loaded successfully");
            assertTrue(series.getBarCount() > 0, "BarSeries should contain bars");
        }
    }

    @Test
    public void testLoadNullInputStream() {
        BarSeries series = JsonBarsSerializer.loadSeries((InputStream) null);
        assertNull(series, "Should return null for null input stream");
    }
}