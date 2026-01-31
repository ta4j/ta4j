/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PVOIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries barSeries;

    public PVOIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        barSeries.barBuilder().closePrice(0).volume(10).add();
        barSeries.barBuilder().closePrice(0).volume(11).add();
        barSeries.barBuilder().closePrice(0).volume(12).add();
        barSeries.barBuilder().closePrice(0).volume(13).add();
        barSeries.barBuilder().closePrice(0).volume(150).add();
        barSeries.barBuilder().closePrice(0).volume(155).add();
        barSeries.barBuilder().closePrice(0).volume(160).add();
    }

    @Test
    public void createPvoIndicator() {
        var pvo = new PVOIndicator(barSeries);
        // PVO uses PPO with shortBarCount=12, longBarCount=26, so unstable period is 26
        // Series only has 7 bars, so all indices (0-6) are in unstable period
        // All values will return NaN because long EMA returns NaN during its unstable
        // period
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            assertThat(Double.isNaN(pvo.getValue(i).doubleValue())).isTrue();
        }
    }
}
