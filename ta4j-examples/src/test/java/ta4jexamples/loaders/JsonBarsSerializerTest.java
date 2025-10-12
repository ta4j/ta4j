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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public class JsonBarsSerializerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testJsonFileCanBeWrittenAndLoaded() {
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

    @Test
    public void testJsonCanBeLoadedFromInputStream() throws IOException {
        // First, create a test series and persist it to get valid JSON
        BarSeries originalSeries = CsvBarsLoader.loadAppleIncSeries();
        String testFilename = folder.getRoot().getAbsolutePath() + File.separator + "test_series.json";
        JsonBarsSerializer.persistSeries(originalSeries, testFilename);
        
        // Read the JSON content from the file
        String jsonContent = Files.readString(java.nio.file.Paths.get(testFilename));
        
        // Create an InputStream from the JSON content
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));
        
        // Load the series from the InputStream
        BarSeries loadedSeries = JsonBarsSerializer.loadSeries(inputStream);
        
        // Verify the loaded series matches the original
        assertNotNull(loadedSeries);
        assertEquals(originalSeries.getBarCount(), loadedSeries.getBarCount());
        assertEquals(originalSeries.getName(), loadedSeries.getName());
        
        // Verify a few random bars match exactly
        int randomIndex = ThreadLocalRandom.current().nextInt(originalSeries.getBeginIndex(), originalSeries.getEndIndex());
        Bar originalBar = originalSeries.getBar(randomIndex);
        Bar loadedBar = loadedSeries.getBar(randomIndex);
        
        assertEquals(originalBar.getEndTime(), loadedBar.getEndTime());
        assertEquals(originalBar.getOpenPrice(), loadedBar.getOpenPrice());
        assertEquals(originalBar.getHighPrice(), loadedBar.getHighPrice());
        assertEquals(originalBar.getLowPrice(), loadedBar.getLowPrice());
        assertEquals(originalBar.getClosePrice(), loadedBar.getClosePrice());
        assertEquals(originalBar.getVolume(), loadedBar.getVolume());
        assertEquals(originalBar.getAmount(), loadedBar.getAmount());
        
        // Verify the delegate types are preserved
        assertTrue(originalBar.getOpenPrice().getDelegate() instanceof BigDecimal);
        assertTrue(loadedBar.getOpenPrice().getDelegate() instanceof BigDecimal);
    }

    @Test
    public void testJsonLoadFromInputStreamWithNullStream() {
        // Test that null InputStream returns null
        BarSeries result = JsonBarsSerializer.loadSeries((InputStream) null);
        assertNull(result);
    }

    @Test
    public void testJsonLoadFromInputStreamWithEmptyStream() {
        // Test with empty InputStream
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        BarSeries result = JsonBarsSerializer.loadSeries(emptyStream);
        assertNull(result);
    }

    @Test
    public void testJsonLoadFromInputStreamWithInvalidJson() {
        // Test with invalid JSON content
        String invalidJson = "This is not valid JSON content";
        InputStream invalidStream = new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8));
        BarSeries result = JsonBarsSerializer.loadSeries(invalidStream);
        assertNull(result);
    }
}
