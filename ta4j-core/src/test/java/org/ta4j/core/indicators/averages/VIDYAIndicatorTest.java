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

public class VIDYAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VIDYAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void vidyaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(VIDYAIndicatorTest.class, "VIDYA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        VIDYAIndicator vidya = new VIDYAIndicator(new ClosePriceIndicator(barSeries), 9, 20);

        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = vidya.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

}
