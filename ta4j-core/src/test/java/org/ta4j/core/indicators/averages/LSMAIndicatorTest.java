/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LSMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public LSMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void lsmaIndicatorTest() {

        var barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(23, 20, 15, 19, 18, 14, 16, 13, 10, 13, 12, 11, 10, 10, 12, 10, 11, 14, 14, 11, 10, 10, 10,
                        10, 10, 12, 10, 11, 10, 15, 11, 10, 15, 16, 15, 19, 17, 13, 11, 10, 10, 13, 16, 21, 24, 24, 22,
                        21, 26, 23, 19, 16, 13, 11, 15, 18, 16, 18, 13, 10, 10, 12, 16, 17, 12, 14, 13, 10, 10, 10, 10,
                        14, 11, 10, 10, 10, 15, 13, 10, 10, 14, 12, 11, 15, 17, 16, 11, 10, 10, 15, 17, 15, 18, 20, 19,
                        22, 24, 26, 25, 28)
                .build();

        LSMAIndicator lsma = new LSMAIndicator(new ClosePriceIndicator(barSeries), 5);

        // unstable bars skipped, unpredictable results
        int j = 10;
        assertNumEquals(11.2, lsma.getValue(j++));
        assertNumEquals(11.4, lsma.getValue(j++));
        assertNumEquals(10.8, lsma.getValue(j++));
        assertNumEquals(9.6, lsma.getValue(j++));
        assertNumEquals(10.8, lsma.getValue(j++));
        assertNumEquals(10.6, lsma.getValue(j++));
        assertNumEquals(11.0, lsma.getValue(j++));
        assertNumEquals(12.8, lsma.getValue(j++));
        assertNumEquals(13.8, lsma.getValue(j++));
        assertNumEquals(13.0, lsma.getValue(j++));

    }

    @Test
    public void lsmaIndicatorTest1() {

        MockIndicator mock = CsvTestUtils.getCsvFile(LSMAIndicatorTest.class, "LSMA1.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        LSMAIndicator lsma = new LSMAIndicator(new ClosePriceIndicator(barSeries), 20);

        for (int i = 20; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = lsma.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

    @Test
    public void lsmaIndicatorTest2() {

        MockIndicator mock = CsvTestUtils.getCsvFile(LSMAIndicatorTest.class, "LSMA2.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        LSMAIndicator lsma = new LSMAIndicator(new ClosePriceIndicator(barSeries), 20);

        int unstableBars = lsma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertNumEquals(NaN.NaN, lsma.getValue(i));
        }

        for (int i = unstableBars; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = lsma.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

    @Test
    public void lsmaIndicatorTest3() {

        MockIndicator mock = CsvTestUtils.getCsvFile(LSMAIndicatorTest.class, "LSMA3.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        LSMAIndicator lsma = new LSMAIndicator(new ClosePriceIndicator(barSeries), 20);

        for (int i = 20; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = lsma.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

}
