/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

public class JsonBarsSerializerTest {

// TODO    @Rule
//    public TemporaryFolder folder = new TemporaryFolder();
//
//    @Test
//    public void testJsonFileCanBeWrittenAndLoaded() throws IOException {
//        BarSeries series = CsvBarsLoader.loadAppleIncSeries();
//        int initialSeriesBarCount = series.getBarCount();
//        String testFilename = folder.getRoot().getAbsolutePath() + File.separator + "bitstamp_series.json";
//        File fileToBeWritten = new File(testFilename);
//        assertFalse(fileToBeWritten.exists());
//        JsonBarsSerializer.persistSeries(series, testFilename);
//        assertTrue(fileToBeWritten.exists());
//
//        BarSeries loadedSeries = JsonBarsSerializer.loadSeries(testFilename);
//        assertEquals(initialSeriesBarCount, loadedSeries.getBarCount());
//
//        // TODO nonsense?
//        Bar randomInitialBar = series.getBar();
//        Bar randomNewBar = loadedSeries.getBar();
//
//        assertEquals(randomInitialBar.endTime(), randomNewBar.endTime());
//        assertEquals(randomInitialBar.openPrice(), randomNewBar.openPrice());
//        assertTrue(randomInitialBar.openPrice().getDelegate() instanceof BigDecimal);
//        assertTrue(randomNewBar.openPrice().getDelegate() instanceof BigDecimal);
//        assertEquals(randomInitialBar.highPrice(), randomNewBar.highPrice());
//        assertEquals(randomInitialBar.lowPrice(), randomNewBar.lowPrice());
//        assertEquals(randomInitialBar.closePrice(), randomNewBar.closePrice());
//        assertEquals(randomInitialBar.volume(), randomNewBar.volume());
//        assertEquals(randomInitialBar.getAmount(), randomNewBar.getAmount());
//    }
}
