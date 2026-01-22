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

public class SMMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SMMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void smmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(SMMAIndicatorTest.class, "SMMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        SMMAIndicator ma = new SMMAIndicator(new ClosePriceIndicator(barSeries), 10);

        for (int i = 0; i < barSeries.getBarCount(); i++) {

            Num expected = mock.getValue(i);
            Num value = ma.getValue(i);

            assertNumEquals(expected.doubleValue(), value);

        }
    }

}
