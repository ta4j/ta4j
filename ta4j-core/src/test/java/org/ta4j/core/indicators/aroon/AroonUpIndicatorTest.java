/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class AroonUpIndicatorTest {

    private BarSeries data;

    @Before
    public void init() {
        // FB, daily, Sept 19, 2017
        data = new MockBarSeriesBuilder().build();
        data.barBuilder().openPrice(168.28).highPrice(169.87).lowPrice(167.15).closePrice(169.64).volume(0).add();
        data.barBuilder().openPrice(168.84).highPrice(169.36).lowPrice(168.20).closePrice(168.71).volume(0).add();
        data.barBuilder().openPrice(168.88).highPrice(169.29).lowPrice(166.41).closePrice(167.74).volume(0).add();
        data.barBuilder().openPrice(168.00).highPrice(168.38).lowPrice(166.18).closePrice(166.32).volume(0).add();
        data.barBuilder().openPrice(166.89).highPrice(167.70).lowPrice(166.33).closePrice(167.24).volume(0).add();
        data.barBuilder().openPrice(165.25).highPrice(168.43).lowPrice(165.00).closePrice(168.05).volume(0).add();
        data.barBuilder().openPrice(168.17).highPrice(170.18).lowPrice(167.63).closePrice(169.92).volume(0).add();
        data.barBuilder().openPrice(170.42).highPrice(172.15).lowPrice(170.06).closePrice(171.97).volume(0).add();
        data.barBuilder().openPrice(172.41).highPrice(172.92).lowPrice(171.31).closePrice(172.02).volume(0).add();
        data.barBuilder().openPrice(171.20).highPrice(172.39).lowPrice(169.55).closePrice(170.72).volume(0).add();
        data.barBuilder().openPrice(170.91).highPrice(172.48).lowPrice(169.57).closePrice(172.09).volume(0).add();
        data.barBuilder().openPrice(171.80).highPrice(173.31).lowPrice(170.27).closePrice(173.21).volume(0).add();
        data.barBuilder().openPrice(173.09).highPrice(173.49).lowPrice(170.80).closePrice(170.95).volume(0).add();
        data.barBuilder().openPrice(172.41).highPrice(173.89).lowPrice(172.20).closePrice(173.51).volume(0).add();
        data.barBuilder().openPrice(173.87).highPrice(174.17).lowPrice(175.00).closePrice(172.96).volume(0).add();
        data.barBuilder().openPrice(173.00).highPrice(173.17).lowPrice(172.06).closePrice(173.05).volume(0).add();
        data.barBuilder().openPrice(172.26).highPrice(172.28).lowPrice(170.50).closePrice(170.96).volume(0).add();
        data.barBuilder().openPrice(170.88).highPrice(172.34).lowPrice(170.26).closePrice(171.64).volume(0).add();
        data.barBuilder().openPrice(171.85).highPrice(172.07).lowPrice(169.34).closePrice(170.01).volume(0).add();
        data.barBuilder().openPrice(170.75).highPrice(172.56).lowPrice(170.36).closePrice(172.52).volume(0).add();
    }

    @Test
    public void upAndSlowDown() {
        var arronUp = new AroonUpIndicator(data, 5);
        assertNumEquals(0, arronUp.getValue(19));
        assertNumEquals(20, arronUp.getValue(18));
        assertNumEquals(40, arronUp.getValue(17));
        assertNumEquals(60, arronUp.getValue(16));
        assertNumEquals(80, arronUp.getValue(15));
        assertNumEquals(100, arronUp.getValue(14));
        assertNumEquals(100, arronUp.getValue(13));
        assertNumEquals(100, arronUp.getValue(12));
        assertNumEquals(100, arronUp.getValue(11));
        assertNumEquals(60, arronUp.getValue(10));
        assertNumEquals(80, arronUp.getValue(9));
        assertNumEquals(100, arronUp.getValue(8));
        assertNumEquals(100, arronUp.getValue(7));
        assertNumEquals(100, arronUp.getValue(6));
        assertNumEquals(0, arronUp.getValue(5));

    }

    @Test
    public void onlyNaNValues() {
        var series = new MockBarSeriesBuilder().withName("NaN test").build();
        var now = Instant.now();
        for (long i = 0; i <= 1000; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(NaN)
                    .closePrice(NaN)
                    .highPrice(NaN)
                    .lowPrice(NaN)
                    .volume(NaN)
                    .add();
        }

        var aroonUpIndicator = new AroonUpIndicator(series, 5);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertEquals(NaN.toString(), aroonUpIndicator.getValue(i).toString());
        }
    }

    @Test
    public void naNValuesInInterval() {
        var series = new MockBarSeriesBuilder().withName("NaN test").build();
        var now = Instant.now();
        for (long i = 0; i <= 10; i++) { // (0, NaN, 2, NaN, 4, NaN, 6, NaN, 8, ...)
            Num highPrice = i % 2 == 0 ? series.numFactory().numOf(i) : NaN;
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(NaN)
                    .closePrice(NaN)
                    .highPrice(highPrice)
                    .lowPrice(NaN)
                    .volume(NaN)
                    .add();
        }

        var aroonUpIndicator = new AroonUpIndicator(series, 5);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            if (i % 2 != 0) {
                assertEquals(NaN.toString(), aroonUpIndicator.getValue(i).toString());
            } else {
                assertNumEquals(aroonUpIndicator.getValue(i).toString(), series.numFactory().numOf(100));
            }
        }
    }
}
