/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OnBalanceVolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public OnBalanceVolumeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10).volume(4).add();
        series.barBuilder().closePrice(5).volume(2).add();
        series.barBuilder().closePrice(6).volume(3).add();
        series.barBuilder().closePrice(7).volume(8).add();
        series.barBuilder().closePrice(7).volume(6).add();
        series.barBuilder().closePrice(6).volume(10).add();

        var obv = new OnBalanceVolumeIndicator(series);
        assertNumEquals(0, obv.getValue(0));
        assertNumEquals(-2, obv.getValue(1));
        assertNumEquals(1, obv.getValue(2));
        assertNumEquals(9, obv.getValue(3));
        assertNumEquals(9, obv.getValue(4));
        assertNumEquals(-1, obv.getValue(5));
    }

    @Test
    public void noStackOverflowError() {
        var bigSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10000; i++) {
            bigSeries.barBuilder().closePrice(i).volume(0).add();
        }
        var obv = new OnBalanceVolumeIndicator(bigSeries);
        // If a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertNumEquals(0, obv.getValue(9999));
    }
}
