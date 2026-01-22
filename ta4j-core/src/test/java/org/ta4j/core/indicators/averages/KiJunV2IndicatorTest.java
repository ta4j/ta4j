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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class KiJunV2IndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public KiJunV2IndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void kijunv2IndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(KiJunV2IndicatorTest.class, "KiJunV2.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        KiJunV2Indicator kijunv2 = new KiJunV2Indicator(new HighPriceIndicator(barSeries),
                new LowPriceIndicator(barSeries), 9);

        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = kijunv2.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

}
