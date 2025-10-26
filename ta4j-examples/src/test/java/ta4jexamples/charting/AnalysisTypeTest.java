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
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AnalysisType} enum.
 */
public class AnalysisTypeTest {

    @Test
    public void testAnalysisTypeValues() {
        // Test that all expected analysis types exist
        AnalysisType[] types = AnalysisType.values();
        assertEquals(5, types.length);

        assertEquals(AnalysisType.MOVING_AVERAGE_10, types[0]);
        assertEquals(AnalysisType.MOVING_AVERAGE_20, types[1]);
        assertEquals(AnalysisType.MOVING_AVERAGE_50, types[2]);
        assertEquals(AnalysisType.MOVING_AVERAGE_100, types[3]);
        assertEquals(AnalysisType.MOVING_AVERAGE_200, types[4]);
    }

    @Test
    public void testAnalysisTypeDataSetNotNull() {
        // Test that each analysis type has a non-null dataset
        for (AnalysisType type : AnalysisType.values()) {
            assertNotNull("Dataset should not be null for " + type, type.dataSet);
        }
    }

    @Test
    public void testAnalysisTypeDataSetType() {
        // Test that each analysis type has the correct dataset type
        for (AnalysisType type : AnalysisType.values()) {
            assertTrue("Dataset should be instance of MovingAverageAnalysisDataSet for " + type,
                    type.dataSet instanceof MovingAverageAnalysisDataSet);
        }
    }

    @Test
    public void testMovingAveragePeriods() {
        // Test that each analysis type has the correct time period
        assertEquals(10, ((MovingAverageAnalysisDataSet) AnalysisType.MOVING_AVERAGE_10.dataSet).timePeriod);
        assertEquals(20, ((MovingAverageAnalysisDataSet) AnalysisType.MOVING_AVERAGE_20.dataSet).timePeriod);
        assertEquals(50, ((MovingAverageAnalysisDataSet) AnalysisType.MOVING_AVERAGE_50.dataSet).timePeriod);
        assertEquals(100, ((MovingAverageAnalysisDataSet) AnalysisType.MOVING_AVERAGE_100.dataSet).timePeriod);
        assertEquals(200, ((MovingAverageAnalysisDataSet) AnalysisType.MOVING_AVERAGE_200.dataSet).timePeriod);
    }

    @Test
    public void testValueOf() {
        // Test valueOf method
        assertEquals(AnalysisType.MOVING_AVERAGE_10, AnalysisType.valueOf("MOVING_AVERAGE_10"));
        assertEquals(AnalysisType.MOVING_AVERAGE_20, AnalysisType.valueOf("MOVING_AVERAGE_20"));
        assertEquals(AnalysisType.MOVING_AVERAGE_50, AnalysisType.valueOf("MOVING_AVERAGE_50"));
        assertEquals(AnalysisType.MOVING_AVERAGE_100, AnalysisType.valueOf("MOVING_AVERAGE_100"));
        assertEquals(AnalysisType.MOVING_AVERAGE_200, AnalysisType.valueOf("MOVING_AVERAGE_200"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueOfInvalid() {
        AnalysisType.valueOf("INVALID_TYPE");
    }

    @Test
    public void testOrdinal() {
        // Test ordinal values
        assertEquals(0, AnalysisType.MOVING_AVERAGE_10.ordinal());
        assertEquals(1, AnalysisType.MOVING_AVERAGE_20.ordinal());
        assertEquals(2, AnalysisType.MOVING_AVERAGE_50.ordinal());
        assertEquals(3, AnalysisType.MOVING_AVERAGE_100.ordinal());
        assertEquals(4, AnalysisType.MOVING_AVERAGE_200.ordinal());
    }

    @Test
    public void testToString() {
        // Test toString method
        assertEquals("MOVING_AVERAGE_10", AnalysisType.MOVING_AVERAGE_10.toString());
        assertEquals("MOVING_AVERAGE_20", AnalysisType.MOVING_AVERAGE_20.toString());
        assertEquals("MOVING_AVERAGE_50", AnalysisType.MOVING_AVERAGE_50.toString());
        assertEquals("MOVING_AVERAGE_100", AnalysisType.MOVING_AVERAGE_100.toString());
        assertEquals("MOVING_AVERAGE_200", AnalysisType.MOVING_AVERAGE_200.toString());
    }

    @Test
    public void testDataSetGetAnalysis() {
        // Test that each analysis type can generate analysis data
        DefaultOHLCDataset dataset = ChartingTestFixtures.linearOhlcDataset("Test", 2);

        for (AnalysisType type : AnalysisType.values()) {
            assertNotNull("Analysis should not be null for " + type, type.dataSet.getAnalysis(dataset));
        }
    }

    @Test
    public void testDataSetGetAnalysisWithNull() {
        // Test that each analysis type handles null input properly
        for (AnalysisType type : AnalysisType.values()) {
            try {
                type.dataSet.getAnalysis(null);
                fail("Should throw IllegalArgumentException for null input");
            } catch (IllegalArgumentException e) {
                assertEquals("Data cannot be null", e.getMessage());
            }
        }
    }

}
