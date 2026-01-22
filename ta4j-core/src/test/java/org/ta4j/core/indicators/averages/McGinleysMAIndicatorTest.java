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

public class McGinleysMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public McGinleysMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void mcginleysIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(McGinleysMAIndicatorTest.class, "McGinley.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        MCGinleyMAIndicator mcg = new MCGinleyMAIndicator(new ClosePriceIndicator(barSeries), 14);

        for (int i = 1; i < barSeries.getBarCount(); i++) {

            Num expected = mock.getValue(i);
            Num value = mcg.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

}
