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

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AnalysisDataSet} interface.
 *
 * <p>
 * This test class verifies that the AnalysisDataSet interface contract is
 * properly implemented by its concrete implementations.
 * </p>
 */
public class AnalysisDataSetTest {

    @Test
    public void testAnalysisDataSetInterface() {
        // Test that the interface exists and can be implemented
        AnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        assertNotNull("AnalysisDataSet implementation should not be null", dataSet);
        assertTrue("Should implement AnalysisDataSet interface", dataSet instanceof AnalysisDataSet);
    }

    @Test
    public void testGetAnalysisMethodExists() {
        // Test that the getAnalysis method exists and can be called
        AnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        DefaultOHLCDataset ohlcData = ChartingTestFixtures.linearOhlcDataset("Test", 6);

        XYDataset result = dataSet.getAnalysis(ohlcData);
        assertNotNull("getAnalysis should return non-null result", result);
        assertTrue("Result should be an XYDataset", result instanceof XYDataset);
    }

    @Test
    public void testGetAnalysisWithNullInput() {
        // Test that implementations handle null input properly
        AnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);

        try {
            dataSet.getAnalysis(null);
            fail("Should throw IllegalArgumentException for null input");
        } catch (IllegalArgumentException e) {
            assertEquals("Data cannot be null", e.getMessage());
        }
    }

    @Test
    public void testGetAnalysisWithEmptyDataset() {
        // Test with empty dataset
        AnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        DefaultOHLCDataset emptyData = new DefaultOHLCDataset("Empty", new OHLCDataItem[0]);

        XYDataset result = dataSet.getAnalysis(emptyData);
        assertNotNull("Result should not be null even with empty dataset", result);
    }

    @Test
    public void testGetAnalysisWithSingleDataPoint() {
        // Test with single data point
        AnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        DefaultOHLCDataset singleData = ChartingTestFixtures.linearOhlcDataset("Single", 1);

        XYDataset result = dataSet.getAnalysis(singleData);
        assertNotNull("Result should not be null even with single data point", result);
    }

    @Test
    public void testGetAnalysisWithMultipleDataPoints() {
        // Test with multiple data points
        AnalysisDataSet dataSet = new MovingAverageAnalysisDataSet(10);
        DefaultOHLCDataset multiData = ChartingTestFixtures.linearOhlcDataset("Multi", 20);

        XYDataset result = dataSet.getAnalysis(multiData);
        assertNotNull("Result should not be null with multiple data points", result);
    }

    @Test
    public void testInterfaceContract() {
        // Test that the interface contract is properly defined
        // This is more of a documentation test to ensure the interface is well-defined

        // The interface should have exactly one method
        java.lang.reflect.Method[] methods = AnalysisDataSet.class.getDeclaredMethods();
        assertEquals("Interface should have exactly one method", 1, methods.length);

        // The method should be getAnalysis
        assertEquals("Method should be named getAnalysis", "getAnalysis", methods[0].getName());

        // The method should take a DefaultOHLCDataset parameter
        Class<?>[] paramTypes = methods[0].getParameterTypes();
        assertEquals("Method should have exactly one parameter", 1, paramTypes.length);
        assertEquals("Parameter should be DefaultOHLCDataset", DefaultOHLCDataset.class, paramTypes[0]);

        // The method should return XYDataset
        assertEquals("Return type should be XYDataset", XYDataset.class, methods[0].getReturnType());
    }

    @Test
    public void testImplementationConsistency() {
        // Test that all implementations follow the same contract
        AnalysisDataSet[] implementations = { new MovingAverageAnalysisDataSet(10),
                new MovingAverageAnalysisDataSet(20), new MovingAverageAnalysisDataSet(50) };

        DefaultOHLCDataset testData = ChartingTestFixtures.linearOhlcDataset("Test", 6);

        for (AnalysisDataSet impl : implementations) {
            XYDataset result = impl.getAnalysis(testData);
            assertNotNull("All implementations should return non-null result", result);
            assertTrue("All implementations should return XYDataset", result instanceof XYDataset);
        }
    }

}
