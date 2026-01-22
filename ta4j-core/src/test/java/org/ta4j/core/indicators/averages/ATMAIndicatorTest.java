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
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ATMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ATMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void atmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(ATMAIndicatorTest.class, "ATMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        ATMAIndicator atma = new ATMAIndicator(new ClosePriceIndicator(barSeries), 10);

        for (int i = 10; i < barSeries.getBarCount(); i++) {
            barSeries.getBar(i).getClosePrice();

            assertNumEquals(mock.getValue(i).doubleValue(), atma.getValue(i));
        }
    }

}
