/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class FisherIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    protected BarSeries series;

    public FisherIndicatorTest(NumFactory numFactory) {
        super(null, numFactory);
    }

    @Before
    public void setUp() {

        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("NaN test").build();
        var now = Instant.now();
        int i = 20;
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(44.98)
                .closePrice(45.05)
                .highPrice(45.17)
                .lowPrice(44.96)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.05)
                .closePrice(45.10)
                .highPrice(45.15)
                .lowPrice(44.99)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.11)
                .closePrice(45.19)
                .highPrice(45.32)
                .lowPrice(45.11)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.19)
                .closePrice(45.14)
                .highPrice(45.25)
                .lowPrice(45.04)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.12)
                .closePrice(45.15)
                .highPrice(45.20)
                .lowPrice(45.10)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.15)
                .closePrice(45.14)
                .highPrice(45.20)
                .lowPrice(45.10)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.13)
                .closePrice(45.10)
                .highPrice(45.16)
                .lowPrice(45.07)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.12)
                .closePrice(45.15)
                .highPrice(45.22)
                .lowPrice(45.10)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.15)
                .closePrice(45.22)
                .highPrice(45.27)
                .lowPrice(45.14)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.24)
                .closePrice(45.43)
                .highPrice(45.45)
                .lowPrice(45.20)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.43)
                .closePrice(45.44)
                .highPrice(45.50)
                .lowPrice(45.39)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.43)
                .closePrice(45.55)
                .highPrice(45.60)
                .lowPrice(45.35)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.58)
                .closePrice(45.55)
                .highPrice(45.61)
                .lowPrice(45.39)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.45)
                .closePrice(45.01)
                .highPrice(45.55)
                .lowPrice(44.80)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(45.03)
                .closePrice(44.23)
                .highPrice(45.04)
                .lowPrice(44.17)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(44.23)
                .closePrice(43.95)
                .highPrice(44.29)
                .lowPrice(43.81)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(43.91)
                .closePrice(43.08)
                .highPrice(43.99)
                .lowPrice(43.08)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(43.07)
                .closePrice(43.55)
                .highPrice(43.65)
                .lowPrice(43.06)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i--))
                .openPrice(43.56)
                .closePrice(43.95)
                .highPrice(43.99)
                .lowPrice(43.53)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(i))
                .openPrice(43.93)
                .closePrice(44.47)
                .highPrice(44.58)
                .lowPrice(43.93)
                .add();
    }

    @Test
    public void fisher() {
        var fisher = new FisherIndicator(series);

        assertNumEquals(0.6448642008177138, fisher.getValue(10));
        assertNumEquals(0.8361770425706673, fisher.getValue(11));
        assertNumEquals(0.9936697984965788, fisher.getValue(12));
        assertNumEquals(0.8324807235379169, fisher.getValue(13));
        assertNumEquals(0.5026313552592737, fisher.getValue(14));
        assertNumEquals(0.06492516204615063, fisher.getValue(15));
    }
}
