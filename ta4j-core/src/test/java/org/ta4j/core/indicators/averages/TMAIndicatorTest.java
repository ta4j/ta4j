/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void tmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(TMAIndicatorTest.class, "TMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        TMAIndicator tma = new TMAIndicator(new ClosePriceIndicator(barSeries), 50);

        for (int i = 0; i < barSeries.getBarCount(); i++) {
            barSeries.getBar(i).getClosePrice();

            assertNumEquals(mock.getValue(i).doubleValue(), tma.getValue(i));
        }
    }

    @Test
    public void testTMAIndicator() {
        // Create a BarSeries with mock data

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
                .build();

        // Create the TMAIndicator with a bar count of 3
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        TMAIndicator tma = new TMAIndicator(closePrice, 3);

        // Validate TMA values
        // Use manually calculated TMA values for verification
        assertEquals("TMA at index 0", 10, tma.getValue(0).doubleValue(), 0.001);
        assertEquals("TMA at index 1", 12.5, tma.getValue(1).doubleValue(), 0.001);
        assertEquals("TMA at index 2", 15, tma.getValue(2).doubleValue(), 0.001);
        assertEquals("TMA at index 3", 21.666666, tma.getValue(3).doubleValue(), 0.001);
        assertEquals("TMA at index 4", 30, tma.getValue(4).doubleValue(), 0.001);
        assertEquals("TMA at index 5", 40, tma.getValue(5).doubleValue(), 0.001);
        assertEquals("TMA at index 6", 50, tma.getValue(6).doubleValue(), 0.001);
        assertEquals("TMA at index 7", 60, tma.getValue(7).doubleValue(), 0.001);
        assertEquals("TMA at index 8", 70, tma.getValue(8).doubleValue(), 0.001);
        assertEquals("TMA at index 9", 80, tma.getValue(9).doubleValue(), 0.001);
    }
}
