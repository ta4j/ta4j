/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

public class AroonUpIndicatorTest {

    private BarSeries data;

    @BeforeEach
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
        data.barBuilder().openPrice(173.87).highPrice(175.00).lowPrice(172.96).closePrice(174.17).volume(0).add();
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

    @Test
    public void newestHighExpiresAndFormingBarCanBecomeTheHigh() {
        var series = new MockBarSeriesBuilder().build();
        var indicator = new AroonUpIndicator(series, 2);
        for (int price : new int[] { 4, 1, 4, 2, 1 }) {
            series.barBuilder().openPrice(price).highPrice(price).lowPrice(price).closePrice(price).volume(0).add();
            indicator.getValue(series.getEndIndex());
        }
        assertNumEquals(100, indicator.getValue(2));
        assertNumEquals(50, indicator.getValue(3));
        assertNumEquals(0, indicator.getValue(4));
        series.addPrice(5);
        assertNumEquals(100, indicator.getValue(4));
        series.barBuilder().openPrice(2).highPrice(2).lowPrice(2).closePrice(2).volume(0).add();
        assertNumEquals(50, indicator.getValue(5));
    }

    @Test
    public void rawDoubleNaNAndInfinitiesMatchPriorScanOnAdvanceAndRebuild() {
        double[][] cases = { { 1, 2, Double.NaN, 3, 4, 5 },
                { Double.NaN, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY },
                { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 1, Double.POSITIVE_INFINITY } };

        for (double[] prices : cases) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
            for (int i = 0; i < prices.length; i++) {
                series.addBar(nonFinitePriceBar(i, prices[i]));
            }
            AroonUpIndicator up = new AroonUpIndicator(series, 2);
            AroonDownIndicator down = new AroonDownIndicator(series, 2);
            for (int i = 0; i < prices.length; i++) {
                int expectedUp = priorScan(prices, i, true);
                int expectedDown = priorScan(prices, i, false);
                assertNumEquals(expectedUp, up.getValue(i));
                assertNumEquals(expectedDown, down.getValue(i));
                assertNumEquals(expectedUp, new AroonUpIndicator(series, 2).getValue(i));
                assertNumEquals(expectedDown, new AroonDownIndicator(series, 2).getValue(i));
            }
        }
    }

    @Test
    public void rawDoubleNaNIsReevaluatedOnReplacementAndAppend() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        AroonUpIndicator up = new AroonUpIndicator(series, 2);
        AroonDownIndicator down = new AroonDownIndicator(series, 2);
        series.addBar(nonFinitePriceBar(0, 1));
        assertNumEquals(100, up.getValue(0));
        assertNumEquals(100, down.getValue(0));
        series.addBar(nonFinitePriceBar(1, 2));
        assertNumEquals(100, up.getValue(1));
        assertNumEquals(50, down.getValue(1));
        series.addBar(nonFinitePriceBar(2, 3));
        assertNumEquals(100, up.getValue(2));
        assertNumEquals(0, down.getValue(2));

        series.addBar(nonFinitePriceBar(2, Double.NaN), true);
        assertNumEquals(0, up.getValue(2));
        assertNumEquals(0, down.getValue(2));
        series.addBar(nonFinitePriceBar(2, Double.POSITIVE_INFINITY), true);
        assertNumEquals(100, up.getValue(2));
        assertNumEquals(0, down.getValue(2));
        series.addBar(nonFinitePriceBar(2, Double.NaN), true);
        assertNumEquals(0, up.getValue(2));
        assertNumEquals(0, down.getValue(2));

        series.addBar(nonFinitePriceBar(3, 3));
        assertNumEquals(0, up.getValue(3));
        assertNumEquals(0, down.getValue(3));
        series.addBar(nonFinitePriceBar(4, Double.NEGATIVE_INFINITY));
        assertNumEquals(0, up.getValue(4));
        assertNumEquals(100, down.getValue(4));
        series.replaceBar(2, nonFinitePriceBar(2, 2));
        assertNumEquals(50, up.getValue(4));
        assertNumEquals(100, down.getValue(4));
        series.replaceBar(2, nonFinitePriceBar(2, Double.NaN));
        assertNumEquals(0, up.getValue(4));
        assertNumEquals(100, down.getValue(4));
        series.addBar(nonFinitePriceBar(5, Double.POSITIVE_INFINITY));
        assertNumEquals(100, up.getValue(5));
        assertNumEquals(50, down.getValue(5));
    }

    @Test
    public void rawDoubleNaNMatchesPriorScanAsBoundedSeriesAdvances() {
        double[] prices = { 1, 2, Double.NaN, 3, 4, 5, Double.NaN, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        series.setMaximumBarCount(4);
        AroonUpIndicator up = new AroonUpIndicator(series, 2);
        AroonDownIndicator down = new AroonDownIndicator(series, 2);
        for (int i = 0; i < prices.length; i++) {
            series.addBar(nonFinitePriceBar(i, prices[i]));
            assertNumEquals(priorScan(prices, i, true), up.getValue(i));
            assertNumEquals(priorScan(prices, i, false), down.getValue(i));
        }
    }

    private static NonFiniteBar nonFinitePriceBar(int index, double price) {
        Num value = DoubleNum.valueOf(price);
        return new NonFiniteBar(Instant.EPOCH.plus(Duration.ofDays(index)), DoubleNum.ONE, value, value, DoubleNum.ONE);
    }

    private static int priorScan(double[] prices, int index, boolean maximum) {
        int start = Math.max(0, index - 2);
        double extreme = prices[index];
        for (int i = index - 1; i >= start; i--) {
            int comparison = Double.compare(extreme, prices[i]);
            if (maximum ? comparison < 0 : comparison > 0) {
                extreme = prices[i];
            }
        }
        int elapsed = 0;
        for (int i = index; i > start; i--) {
            if (prices[i] == extreme) {
                break;
            }
            elapsed++;
        }
        return (2 - elapsed) * 50;
    }

}
