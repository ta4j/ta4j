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

    @Test
    public void anchorsWindowAtBeginIndexAfterRemoval() {
        // Evict the first four bars so beginIndex = 4; the retained (close, volume)
        // pairs sit at absolute indices 4..9: (5,10) (6,5) (7,14) (8,7) (9,18) (10,9).
        int i = 10;
        var now = Instant.now();
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] closes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        double[] volumes = { 4, 1, 6, 3, 10, 5, 14, 7, 18, 9 };
        for (int j = 0; j < closes.length; j++) {
            pruned.barBuilder().endTime(now.minusSeconds(i--)).closePrice(closes[j]).volume(volumes[j]).add();
        }
        pruned.setMaximumBarCount(6);

        var covar = new CovarianceIndicator(new ClosePriceIndicator(pruned), new VolumeIndicator(pruned, 1), 6);

        // Window [4..7]: closes {5,6,7,8}, volumes {10,5,14,7}: sum of products = 0
        assertNumEquals(0, covar.getValue(7));
        // Window [4..8]: sum of products = 18 over 5 observations
        assertNumEquals(3.6, covar.getValue(8));
        // Window [4..9]: sum of products = 13.5 over 6 observations
        assertNumEquals(2.25, covar.getValue(9));
    }

    @Test
    public void acceptsAlignedInputsFromSeparateSeries() {
        BarSeries xSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries ySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var now = Instant.now();
        for (int j = 0; j < 5; j++) {
            xSeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(2 * (j + 1)).add();
            ySeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(5 * (j + 1)).add();
        }

        var covar = new CovarianceIndicator(new ClosePriceIndicator(xSeries), new ClosePriceIndicator(ySeries), 5);

        // Perfectly linear pairs (2,5), (4,10), (6,15), (8,20), (10,25):
        // population covariance = sum((x - 6)(y - 15)) / 5 = 100 / 5
        assertNumEquals(20, covar.getValue(4));
    }
}
