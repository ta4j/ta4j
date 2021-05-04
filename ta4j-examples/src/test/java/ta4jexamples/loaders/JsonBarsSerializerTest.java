/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class JsonBarsSerializerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testJsonFileCanBeWrittenAndLoaded() throws IOException {
        BarSeries series = CsvBarsLoader.loadAppleIncSeries();
        int initialSeriesBarCount = series.getBarCount();
        String testFilename = folder.getRoot().getAbsolutePath() + File.separator + "bitstamp_series.json";
        File fileToBeWritten = new File(testFilename);
        assertFalse(fileToBeWritten.exists());
        JsonBarsSerializer.persistSeries(series, testFilename);
        assertTrue(fileToBeWritten.exists());

        BarSeries loadedSeries = JsonBarsSerializer.loadSeries(testFilename);
        assertEquals(initialSeriesBarCount, loadedSeries.getBarCount());

        int randomIndex = ThreadLocalRandom.current().nextInt(series.getBeginIndex(), series.getEndIndex());
        Bar randomInitialBar = series.getBar(randomIndex);
        Bar randomNewBar = loadedSeries.getBar(randomIndex);

        assertEquals(randomInitialBar.getEndTime(), randomNewBar.getEndTime());
        assertEquals(randomInitialBar.getOpenPrice(), randomNewBar.getOpenPrice());
        assertTrue(randomInitialBar.getOpenPrice().getDelegate() instanceof BigDecimal);
        assertTrue(randomNewBar.getOpenPrice().getDelegate() instanceof BigDecimal);
        assertEquals(randomInitialBar.getHighPrice(), randomNewBar.getHighPrice());
        assertEquals(randomInitialBar.getLowPrice(), randomNewBar.getLowPrice());
        assertEquals(randomInitialBar.getClosePrice(), randomNewBar.getClosePrice());
        assertEquals(randomInitialBar.getVolume(), randomNewBar.getVolume());
        assertEquals(randomInitialBar.getAmount(), randomNewBar.getAmount());
    }
}
