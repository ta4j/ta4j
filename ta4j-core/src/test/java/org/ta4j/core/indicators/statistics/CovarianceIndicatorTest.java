/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CovarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;

    public CovarianceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        int i = 20;
        var now = Instant.now();
        BarSeries data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(6).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(7).volume(105).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(10).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(13).volume(120).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(15).volume(180).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(200).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(85).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(70).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(5).volume(90).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(110).add();
        data.barBuilder().endTime(now.minusSeconds(i)).closePrice(10).volume(95).add();
        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void usingBarCount5UsingClosePriceAndVolume() {
        var covar = new CovarianceIndicator(close, volume, 5);

        assertNumEquals(0, covar.getValue(0));
        assertNumEquals(26.25, covar.getValue(1));
        assertNumEquals(63.3333, covar.getValue(2));
        assertNumEquals(143.75, covar.getValue(3));
        assertNumEquals(156, covar.getValue(4));
        assertNumEquals(60.8, covar.getValue(5));
        assertNumEquals(15.2, covar.getValue(6));
        assertNumEquals(-17.6, covar.getValue(7));
        assertNumEquals(4, covar.getValue(8));
        assertNumEquals(11.6, covar.getValue(9));
        assertNumEquals(-14.4, covar.getValue(10));
        assertNumEquals(-100.2, covar.getValue(11));
        assertNumEquals(-70.0, covar.getValue(12));
        assertNumEquals(24.6, covar.getValue(13));
        assertNumEquals(35.0, covar.getValue(14));
        assertNumEquals(-19.0, covar.getValue(15));
        assertNumEquals(-47.8, covar.getValue(16));
        assertNumEquals(11.4, covar.getValue(17));
        assertNumEquals(55.8, covar.getValue(18));
        assertNumEquals(33.4, covar.getValue(19));
    }

    @Test
    public void firstValueShouldBeZero() {
        var covar = new CovarianceIndicator(close, volume, 5);
        assertNumEquals(0, covar.getValue(0));
    }

    @Test
    public void shouldBeZeroWhenBarCountIs1() {
        var covar = new CovarianceIndicator(close, volume, 1);
        assertNumEquals(0, covar.getValue(3));
        assertNumEquals(0, covar.getValue(8));
    }
}
