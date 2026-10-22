/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.display;

import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.ChartingTestFixtures;

/**
 * Unit tests for {@link ChartDataExtractor}.
 */
class ChartDataExtractorTest {

    private final ChartDataExtractor extractor = new ChartDataExtractor();

    @Test
    void testExtractDataTextFromTimeSeriesCollection() {
        // Create a TimeSeriesCollection with test data
        TimeSeries timeSeries = new TimeSeries("Test Indicator");
        Date baseDate = new Date();
        timeSeries.add(new Minute(baseDate), 100.0);
        timeSeries.add(new Minute(new Date(baseDate.getTime() + 60000)), 101.5);
        timeSeries.add(new Minute(new Date(baseDate.getTime() + 120000)), 102.0);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(timeSeries);

        // Test extraction from TimeSeriesCollection
        String result = extractor.extractDataText(dataset, 0, 0);

        assertNotNull(result, "Extracted text should not be null");
        assertFalse(result.isEmpty(), "Extracted text should not be empty");
        assertTrue(result.contains("Test Indicator"), "Extracted text should contain series name");
        assertTrue(result.contains("100"), "Extracted text should contain value");
    }

    @Test
    void testExtractDataTextFromTimeSeriesCollectionWithMultipleSeries() {
        // Create a TimeSeriesCollection with multiple series
        TimeSeries series1 = new TimeSeries("Series 1");
        TimeSeries series2 = new TimeSeries("Series 2");
        Date baseDate = new Date();
        series1.add(new Minute(baseDate), 100.0);
        series2.add(new Minute(baseDate), 200.0);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series1);
        dataset.addSeries(series2);

        // Test extraction from first series
        String result1 = extractor.extractDataText(dataset, 0, 0);
        assertNotNull(result1, "Extracted text from first series should not be null");
        assertTrue(result1.contains("Series 1"), "Extracted text should contain first series name");
        assertTrue(result1.contains("100"), "Extracted text should contain first series value");

        // Test extraction from second series
        String result2 = extractor.extractDataText(dataset, 1, 0);
        assertNotNull(result2, "Extracted text from second series should not be null");
        assertTrue(result2.contains("Series 2"), "Extracted text should contain second series name");
        assertTrue(result2.contains("200"), "Extracted text should contain second series value");
    }

    @Test
    void testExtractDataTextFromTimeSeriesCollectionHandlesInvalidIndex() {
        TimeSeries timeSeries = new TimeSeries("Test Indicator");
        Date baseDate = new Date();
        timeSeries.add(new Minute(baseDate), 100.0);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(timeSeries);

        // Test with invalid item index (should handle gracefully)
        String result = extractor.extractDataText(dataset, 0, 999);
        // Should return null for invalid index
        assertNull(result, "Should return null for invalid index");
    }

    @Test
    void testExtractDataTextFromDefaultOHLCDataset() {
        // Create a DefaultOHLCDataset
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(true);

        // Test extraction from DefaultOHLCDataset
        String result = extractor.extractDataText(dataset, 0, 0);

        assertNotNull(result, "Extracted text should not be null");
        assertFalse(result.isEmpty(), "Extracted text should not be empty");
        assertTrue(result.contains("O:"), "Extracted text should contain Open price");
        assertTrue(result.contains("H:"), "Extracted text should contain High price");
        assertTrue(result.contains("L:"), "Extracted text should contain Low price");
        assertTrue(result.contains("C:"), "Extracted text should contain Close price");
        assertTrue(result.contains("V:"), "Extracted text should contain Volume");
    }

    @Test
    void testExtractDataTextFromXYSeriesCollection() {
        // Create an XYSeriesCollection
        XYSeries series = new XYSeries("Test Series");
        long baseTime = System.currentTimeMillis();
        series.add(baseTime, 100.0);
        series.add(baseTime + 60000, 101.5);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);

        // Test extraction from XYSeriesCollection
        String result = extractor.extractDataText(dataset, 0, 0);

        assertNotNull(result, "Extracted text should not be null");
        assertFalse(result.isEmpty(), "Extracted text should not be empty");
        assertTrue(result.contains("100"), "Extracted text should contain value");
        assertTrue(result.contains("Date:"), "Extracted text should contain date label");
        assertTrue(result.contains("Value:"), "Extracted text should contain value label");
    }

    @Test
    void testExtractDataTextReturnsNullForEmptyDataset() {
        // Create an empty XYSeriesCollection
        XYSeriesCollection emptyDataset = new XYSeriesCollection();

        // Test with empty dataset (no series)
        String result = extractor.extractDataText(emptyDataset, 0, 0);
        assertNull(result, "Should return null for empty dataset");
    }

    @Test
    void testExtractDataTextReturnsNullForInvalidSeriesIndex() {
        TimeSeries timeSeries = new TimeSeries("Test Indicator");
        Date baseDate = new Date();
        timeSeries.add(new Minute(baseDate), 100.0);

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(timeSeries);

        // Test with invalid series index
        String result = extractor.extractDataText(dataset, 999, 0);
        assertNull(result, "Should return null for invalid series index");
    }
}
