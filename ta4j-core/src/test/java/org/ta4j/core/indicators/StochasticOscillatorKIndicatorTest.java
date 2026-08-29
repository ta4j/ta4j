/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class StochasticOscillatorKIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public StochasticOscillatorKIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        data.barBuilder().openPrice(116.00).closePrice(119.13).highPrice(119.50).lowPrice(116.00).add();
        data.barBuilder().openPrice(116.00).closePrice(116.75).highPrice(119.94).lowPrice(116.00).add();
        data.barBuilder().openPrice(111.63).closePrice(113.50).highPrice(118.44).lowPrice(111.63).add();
        data.barBuilder().openPrice(110.06).closePrice(111.56).highPrice(114.19).lowPrice(110.06).add();
        data.barBuilder().openPrice(109.63).closePrice(112.25).highPrice(112.81).lowPrice(109.63).add();
        data.barBuilder().openPrice(109.13).closePrice(110.00).highPrice(113.44).lowPrice(109.13).add();
        data.barBuilder().openPrice(110.38).closePrice(113.50).highPrice(115.81).lowPrice(110.38).add();
        data.barBuilder().openPrice(114.06).closePrice(117.13).highPrice(117.50).lowPrice(114.06).add();
        data.barBuilder().openPrice(114.81).closePrice(115.63).highPrice(118.44).lowPrice(114.81).add();
        data.barBuilder().openPrice(113.13).closePrice(114.13).highPrice(116.88).lowPrice(113.13).add();
        data.barBuilder().openPrice(116.19).closePrice(118.81).highPrice(119.00).lowPrice(116.19).add();
        data.barBuilder().openPrice(117.00).closePrice(117.38).highPrice(119.75).lowPrice(117.00).add();
        data.barBuilder().openPrice(116.88).closePrice(119.13).highPrice(119.13).lowPrice(116.88).add();
        data.barBuilder().openPrice(114.56).closePrice(115.38).highPrice(119.44).lowPrice(114.56).add();

    }

    @Test
    public void stochasticOscilatorKParam14() {

        var sof = new StochasticOscillatorKIndicator(data, 14);

        assertNumEquals(313 / 3.5, sof.getValue(0));
        assertNumEquals(1000 / 10.81, sof.getValue(12));
        assertNumEquals(57.8168, sof.getValue(13));
    }
}
