/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CMOIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CMOIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(21.27, 22.19, 22.08, 22.47, 22.48, 22.53, 22.23, 21.43, 21.24, 21.29, 22.15, 22.39, 22.38,
                        22.61, 23.36, 24.05, 24.75, 24.83, 23.95, 23.63, 23.82, 23.87, 23.15, 23.19, 23.10, 22.65,
                        22.48, 22.87, 22.93, 22.91)
                .build();
    }

    @Test
    public void dpo() {
        var cmo = new CMOIndicator(new ClosePriceIndicator(series), 9);

        assertNumEquals(85.1351, cmo.getValue(5));
        assertNumEquals(53.9326, cmo.getValue(6));
        assertNumEquals(6.2016, cmo.getValue(7));
        assertNumEquals(-1.083, cmo.getValue(8));
        assertNumEquals(0.7092, cmo.getValue(9));
        assertNumEquals(-1.4493, cmo.getValue(10));
        assertNumEquals(10.7266, cmo.getValue(11));
        assertNumEquals(-3.5857, cmo.getValue(12));
        assertNumEquals(4.7619, cmo.getValue(13));
        assertNumEquals(24.1983, cmo.getValue(14));
        assertNumEquals(47.644, cmo.getValue(15));
    }
}
