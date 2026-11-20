/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.assertIndicatorRoundTrips;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.SwingRetracementLevelIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SwingRetracementLevelIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    // Swing highs at 1 (13) and 5 (15) confirm at 2 and 6; swing lows at 3 (8) and
    // 7 (10) confirm at 4 and 8.
    private static final double[] HIGHS = { 11, 13, 12, 11, 12, 15, 14, 13, 14 };
    private static final double[] LOWS = { 9, 11, 10, 8, 9, 12, 11, 10, 11 };

    public SwingRetracementLevelIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void levelInterpolatesLatestConfirmedSwingPair() {
        BarSeries series = series(HIGHS, LOWS);
        RecentSwingIndicator highs = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 1, 1, 0);
        RecentSwingIndicator lows = new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 1, 1, 0);

        SwingRetracementLevelIndicator midpoint = new SwingRetracementLevelIndicator(highs, lows, 0.5);
        SwingRetracementLevelIndicator swingLow = new SwingRetracementLevelIndicator(highs, lows, 0);
        SwingRetracementLevelIndicator swingHigh = new SwingRetracementLevelIndicator(highs, lows, 1);

        // Only the swing high is confirmed at 2 and 3.
        for (int i = 0; i <= 3; i++) {
            assertThat(midpoint.getValue(i).isNaN()).as("index %s", i).isTrue();
        }
        assertThat(midpoint.getValue(4)).isEqualByComparingTo(numOf(10.5));
        assertThat(midpoint.getValue(5)).isEqualByComparingTo(numOf(10.5));
        assertThat(midpoint.getValue(6)).isEqualByComparingTo(numOf(11.5));
        assertThat(midpoint.getValue(7)).isEqualByComparingTo(numOf(11.5));
        assertThat(midpoint.getValue(8)).isEqualByComparingTo(numOf(12.5));

        assertThat(swingLow.getValue(6)).isEqualByComparingTo(numOf(8));
        assertThat(swingHigh.getValue(6)).isEqualByComparingTo(numOf(15));
        assertThat(swingLow.getValue(8)).isEqualByComparingTo(numOf(10));
        assertThat(swingHigh.getValue(8)).isEqualByComparingTo(numOf(15));

        assertIndicatorRoundTrips(series, midpoint, 3, 6, 8);
    }

    @Test
    public void inheritsUnstableBarsAndStaysNaNUntilBothSwingsExist() {
        // The swing low at 2 needs two following bars and confirms at 4; the swing
        // high at 1 confirms at 2.
        BarSeries series = series(new double[] { 10, 12, 9, 8, 9, 10 }, new double[] { 8, 9, 5, 6, 7, 8 });
        RecentSwingIndicator highs = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 1, 1, 0);
        RecentSwingIndicator lows = new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 2, 2, 0);
        SwingRetracementLevelIndicator level = new SwingRetracementLevelIndicator(highs, lows, 0.25);

        assertThat(highs.getCountOfUnstableBars()).isEqualTo(2);
        assertThat(lows.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(level.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(highs.getValue(3)).isEqualByComparingTo(numOf(12));
        assertThat(level.getValue(3).isNaN()).isTrue();
        assertThat(level.getValue(4)).isEqualByComparingTo(numOf(6.75));

        // Only a swing low exists: the high side is still missing.
        BarSeries rising = series(new double[] { 10, 9, 10, 11, 12 }, new double[] { 8, 7, 8, 9, 10 });
        SwingRetracementLevelIndicator noHigh = new SwingRetracementLevelIndicator(
                new RecentFractalSwingHighIndicator(new HighPriceIndicator(rising), 1, 1, 0),
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(rising), 1, 1, 0), 0.5);
        assertThat(noHigh.getValue(rising.getEndIndex()).isNaN()).isTrue();
    }

    @Test
    public void liveBarByBarValuesEqualFullSeriesValues() {
        double[][] bars = oscillation(60);
        BarSeries full = series(bars[0], bars[1]);
        SwingRetracementLevelIndicator fullLevel = level(full);

        BarSeries live = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        SwingRetracementLevelIndicator liveLevel = level(live);
        List<Num> liveValues = new ArrayList<>();
        for (int i = 0; i < bars[0].length; i++) {
            addBar(live, bars[0][i], bars[1][i]);
            liveValues.add(liveLevel.getValue(live.getEndIndex()));
        }

        int defined = 0;
        for (int i = 0; i <= full.getEndIndex(); i++) {
            Num expected = fullLevel.getValue(i);
            assertSameValue(i, liveValues.get(i), expected);
            assertSameValue(i, liveLevel.getValue(i), expected);
            defined += expected.isNaN() ? 0 : 1;
        }
        assertThat(defined).isGreaterThan(40);
    }

    @Test
    public void rejectsInvalidArguments() {
        BarSeries series = series(HIGHS, LOWS);
        RecentSwingIndicator highs = new RecentFractalSwingHighIndicator(series, 1);
        RecentSwingIndicator lows = new RecentFractalSwingLowIndicator(series, 1);
        BarSeries other = series(HIGHS, LOWS);

        for (Number ratio : new Number[] { -0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> new SwingRetracementLevelIndicator(highs, lows, ratio),
                    String.valueOf(ratio));
        }
        assertThrows(NullPointerException.class, () -> new SwingRetracementLevelIndicator(highs, lows, null));
        assertThrows(NullPointerException.class, () -> new SwingRetracementLevelIndicator(null, lows, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new SwingRetracementLevelIndicator(highs, new RecentFractalSwingLowIndicator(other, 1), 0.5));
    }

    @Test
    public void nonFiniteSwingPricesGiveNaN() {
        // ta4j's swing sources reject non-finite prices, but a custom swing source on a
        // primitive-backed factory can report one: the level must be NaN, not infinite.
        NumFactory doubles = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubles).build();
        for (int i = 0; i < HIGHS.length; i++) {
            addBar(series, HIGHS[i], LOWS[i]);
        }
        RecentSwingIndicator highs = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 1, 1, 0);
        RecentSwingIndicator lows = new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 1, 1, 0);
        RecentSwingIndicator infiniteHighs = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 1, 1,
                0) {
            @Override
            public Num getValue(int index) {
                Num value = super.getValue(index);
                return value.isNaN() ? value : doubles.numOf(Double.POSITIVE_INFINITY);
            }
        };
        RecentSwingIndicator infiniteLows = new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 1, 1, 0) {
            @Override
            public Num getValue(int index) {
                Num value = super.getValue(index);
                return value.isNaN() ? value : doubles.numOf(Double.NEGATIVE_INFINITY);
            }
        };

        // Both swings are confirmed at 4: the finite pair gives (13 + 8) / 2.
        assertThat(new SwingRetracementLevelIndicator(highs, lows, 0.5).getValue(4).doubleValue()).isEqualTo(10.5d);
        assertThat(Double.isInfinite(infiniteHighs.getValue(4).doubleValue())).isTrue();
        assertThat(new SwingRetracementLevelIndicator(infiniteHighs, lows, 0.5).getValue(4).isNaN()).isTrue();
        assertThat(new SwingRetracementLevelIndicator(highs, infiniteLows, 0.5).getValue(4).isNaN()).isTrue();
    }

    @Test
    public void seriesConstructorUsesDefaultSwingPair() {
        double[][] bars = oscillation(60);
        BarSeries series = series(bars[0], bars[1]);
        RecentSwingIndicators.Pair swings = RecentSwingIndicators.defaultFor(series);
        SwingRetracementLevelIndicator explicit = new SwingRetracementLevelIndicator(swings.highs(), swings.lows(),
                0.618);
        SwingRetracementLevelIndicator fromSeries = new SwingRetracementLevelIndicator(series, 0.618);

        int defined = 0;
        for (int i = 0; i <= series.getEndIndex(); i++) {
            assertSameValue(i, fromSeries.getValue(i), explicit.getValue(i));
            defined += explicit.getValue(i).isNaN() ? 0 : 1;
        }
        assertThat(defined).isPositive();
        assertThat(fromSeries.getCountOfUnstableBars()).isEqualTo(explicit.getCountOfUnstableBars());
    }

    private SwingRetracementLevelIndicator level(BarSeries series) {
        return new SwingRetracementLevelIndicator(
                new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 2, 2, 0),
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 2, 2, 0), 0.618);
    }

    /**
     * @return highs and lows of a wave with a faster ripple, so both fractal and
     *         ZigZag swings appear within a few bars
     */
    private static double[][] oscillation(int barCount) {
        double[] highs = new double[barCount];
        double[] lows = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            double close = 100 + 8 * Math.sin(i / 3.0) + 3 * Math.sin(i * 1.7);
            highs[i] = close + 1 + (i % 3) * 0.25;
            lows[i] = close - 1 - (i % 2) * 0.5;
        }
        return new double[][] { highs, lows };
    }

    private BarSeries series(double[] highs, double[] lows) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < highs.length; i++) {
            addBar(series, highs[i], lows[i]);
        }
        return series;
    }

    private static void addBar(BarSeries series, double high, double low) {
        double close = (high + low) / 2;
        series.barBuilder().openPrice(close).highPrice(high).lowPrice(low).closePrice(close).volume(1).add();
    }

    private static void assertSameValue(int index, Num actual, Num expected) {
        if (expected.isNaN()) {
            assertThat(actual.isNaN()).as("index %s", index).isTrue();
        } else {
            assertThat(actual).as("index %s", index).isEqualByComparingTo(expected);
        }
    }
}
