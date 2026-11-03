/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AccumulationDistributionIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public AccumulationDistributionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void oneBarWindowKeepsAccumulatedMoneyFlow() {
        BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        bounded.setMaximumBarCount(1);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        AccumulationDistributionIndicator actual = new AccumulationDistributionIndicator(bounded);
        AccumulationDistributionIndicator expected = new AccumulationDistributionIndicator(unbounded);
        for (int index = 0; index < 4; index++) {
            bounded.barBuilder().closePrice(3).highPrice(4).lowPrice(0).volume(10).add();
            unbounded.barBuilder().closePrice(3).highPrice(4).lowPrice(0).volume(10).add();
            assertNumEquals(5 * index, expected.getValue(index));
            assertNumEquals(expected.getValue(index), actual.getValue(index));
            if (index > 0) {
                assertNumEquals(5 * index, actual.getValue(index - 1));
                assertNumEquals(5 * index, actual.getValue(0));
                assertNumEquals(5 * index, actual.getValue(index));
            }
        }
    }

    @Test
    public void accumulationDistribution() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10d).highPrice(12d).lowPrice(8d).volume(200d).add(); // 2-2 * 200 / 4
        series.barBuilder().closePrice(8d).highPrice(10d).lowPrice(7d).volume(100d).add(); // 1-2 *100 / 3
        series.barBuilder().closePrice(9d).highPrice(15d).lowPrice(6d).volume(300d).add(); // 3-6 *300 /9
        series.barBuilder().closePrice(20d).highPrice(40d).lowPrice(5d).volume(50d).add(); // 15-20 *50 / 35
        series.barBuilder().closePrice(30d).highPrice(30d).lowPrice(3d).volume(600d).add(); // 27-0 *600 /27

        var ac = new AccumulationDistributionIndicator(series);
        assertNumEquals(0, ac.getValue(0));
        assertNumEquals(-100d / 3, ac.getValue(1));
        assertNumEquals(-100d - (100d / 3), ac.getValue(2));
        assertNumEquals((-250d / 35) + (-100d - (100d / 3)), ac.getValue(3));
        assertNumEquals(600d + ((-250d / 35) + (-100d - (100d / 3))), ac.getValue(4));
    }
}
