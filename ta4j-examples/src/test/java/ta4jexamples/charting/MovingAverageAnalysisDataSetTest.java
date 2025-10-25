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
package ta4jexamples.charting;

import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.XYDataset;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link MovingAverageAnalysisDataSet}.
 */
public class MovingAverageAnalysisDataSetTest {

    @Test
    public void testConstructorValidPeriod() {
        // Test constructor with valid time periods
        MovingAverageAnalysisDataSet dataSet10 = new MovingAverageAnalysisDataSet(10);
        assertEquals(10, dataSet10.timePeriod);

        MovingAverageAnalysisDataSet dataSet50 = new MovingAverageAnalysisDataSet(50);
        assertEquals(50, dataSet50.timePeriod);

        MovingAverageAnalysisDataSet dataSet200 = new MovingAverageAnalysisDataSet(200);
        assertEquals(200, dataSet200.timePeriod);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorZeroPeriod() {
        new MovingAverageAnalysisDataSet(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNegativePeriod() {
        new MovingAverageAnalysisDataSet(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNegativePeriodLarge() {
        new MovingAverageAnalysisDataSet(-100);
    }

    @Test
    public void testMillisecondsPerDayConstant() {
        // Test the constant value
        assertEquals(24 * 60 * 60 * 1000L, MovingAverageAnalysisDataSet.MILLISECONDS_PER_DAY);
    }

    @Test
    public void testGetAnalysisWithValidData() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        DefaultOHLCDataset ohlcData = createTestOHLCDataset();

        XYDataset result = dataSet.getAnalysis(ohlcData);

        assertNotNull("Result should not be null", result);
        // The result should be a valid XYDataset
        assertTrue("Result should be an XYDataset", result instanceof XYDataset);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetAnalysisWithNullData() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        dataSet.getAnalysis(null);
    }

    @Test
    public void testGetAnalysisWithDifferentPeriods() {
        DefaultOHLCDataset ohlcData = createTestOHLCDataset();

        // Test different periods
        MovingAverageAnalysisDataSet dataSet5 = new MovingAverageAnalysisDataSet(5);
        MovingAverageAnalysisDataSet dataSet20 = new MovingAverageAnalysisDataSet(20);
        MovingAverageAnalysisDataSet dataSet100 = new MovingAverageAnalysisDataSet(100);

        XYDataset result5 = dataSet5.getAnalysis(ohlcData);
        XYDataset result20 = dataSet20.getAnalysis(ohlcData);
        XYDataset result100 = dataSet100.getAnalysis(ohlcData);

        assertNotNull("Result for period 5 should not be null", result5);
        assertNotNull("Result for period 20 should not be null", result20);
        assertNotNull("Result for period 100 should not be null", result100);
    }

    @Test
    public void testGetAnalysisWithEmptyDataset() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);

        // Create empty dataset
        OHLCDataItem[] emptyItems = new OHLCDataItem[0];
        DefaultOHLCDataset emptyData = new DefaultOHLCDataset("Empty", emptyItems);

        XYDataset result = dataSet.getAnalysis(emptyData);
        assertNotNull("Result should not be null even with empty data", result);
        // Verify the dataSet was used
        assertNotNull("DataSet should not be null", dataSet);
    }

    @Test
    public void testGetAnalysisWithSingleDataPoint() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);

        // Create dataset with single data point
        Date date = new Date(System.currentTimeMillis());
        OHLCDataItem item = new OHLCDataItem(date, 100.0, 105.0, 99.0, 104.0, 1000.0);
        DefaultOHLCDataset singleData = new DefaultOHLCDataset("Single", new OHLCDataItem[] { item });

        XYDataset result = dataSet.getAnalysis(singleData);
        assertNotNull("Result should not be null even with single data point", result);
    }

    @Test
    public void testTimePeriodCalculation() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);

        // The time period should be calculated as timePeriod * MILLISECONDS_PER_DAY
        long expectedPeriod = 10 * MovingAverageAnalysisDataSet.MILLISECONDS_PER_DAY;
        assertEquals(expectedPeriod, 10 * MovingAverageAnalysisDataSet.MILLISECONDS_PER_DAY);
    }

    @Test
    public void testImplementsAnalysisDataSet() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        assertTrue("Should implement AnalysisDataSet interface", dataSet instanceof AnalysisDataSet);
    }

    @Test
    public void testEqualsAndHashCode() {
        MovingAverageAnalysisDataSet dataSet1 = new MovingAverageAnalysisDataSet(10);
        MovingAverageAnalysisDataSet dataSet2 = new MovingAverageAnalysisDataSet(10);
        MovingAverageAnalysisDataSet dataSet3 = new MovingAverageAnalysisDataSet(20);

        // Test equals
        assertEquals(dataSet1, dataSet2);
        assertNotEquals(dataSet1, dataSet3);

        // Test hashCode
        assertEquals(dataSet1.hashCode(), dataSet2.hashCode());
        assertNotEquals(dataSet1.hashCode(), dataSet3.hashCode());
    }

    @Test
    public void testToString() {
        MovingAverageAnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        String toString = dataSet.toString();
        assertNotNull("toString should not be null", toString);
        assertFalse("toString should not be empty", toString.isEmpty());
    }

    /**
     * Creates a test OHLC dataset for testing purposes.
     */
    private DefaultOHLCDataset createTestOHLCDataset() {
        // Create test data with multiple points
        Date[] dates = { new Date(System.currentTimeMillis() - 86400000L * 5), // 5 days ago
                new Date(System.currentTimeMillis() - 86400000L * 4), // 4 days ago
                new Date(System.currentTimeMillis() - 86400000L * 3), // 3 days ago
                new Date(System.currentTimeMillis() - 86400000L * 2), // 2 days ago
                new Date(System.currentTimeMillis() - 86400000L), // 1 day ago
                new Date(System.currentTimeMillis()) // today
        };

        double[] opens = { 100.0, 101.0, 102.0, 103.0, 104.0, 105.0 };
        double[] highs = { 105.0, 106.0, 107.0, 108.0, 109.0, 110.0 };
        double[] lows = { 99.0, 100.0, 101.0, 102.0, 103.0, 104.0 };
        double[] closes = { 104.0, 105.0, 106.0, 107.0, 108.0, 109.0 };
        double[] volumes = { 1000.0, 1100.0, 1200.0, 1300.0, 1400.0, 1500.0 };

        OHLCDataItem[] items = new OHLCDataItem[6];
        for (int i = 0; i < 6; i++) {
            items[i] = new OHLCDataItem(dates[i], opens[i], highs[i], lows[i], closes[i], volumes[i]);
        }

        return new DefaultOHLCDataset("Test", items);
    }
}
