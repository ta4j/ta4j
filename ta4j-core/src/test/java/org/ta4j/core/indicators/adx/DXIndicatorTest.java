/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DXIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public DXIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new DXIndicator(data, (int) params[0]), numFactory);
    }

    @Test
    public void unstableBarsFollowDirectionalIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();

        Indicator<Num> oneBar = getIndicator(series, 1);
        assertEquals(2, oneBar.getCountOfUnstableBars());

        Indicator<Num> threeBars = getIndicator(series, 3);
        assertEquals(4, threeBars.getCountOfUnstableBars());

        Indicator<Num> thirteenBars = getIndicator(series, 13);
        assertEquals(14, thirteenBars.getCountOfUnstableBars());
    }
}
