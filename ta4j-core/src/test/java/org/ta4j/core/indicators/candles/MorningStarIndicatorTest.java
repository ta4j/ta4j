/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MorningStarIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /**
     * Pattern index: bars 0-9 adaptive baseline, 10-12 pattern.
     */
    private static final int PATTERN_INDEX = 12;

    public MorningStarIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidMorningStar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 18, 18.5, 19, 17); // star: short body, real body gaps down
        addBar(series, 19, 26, 27, 18); // third: bullish close above 50% penetration (25)

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void nonFiniteThirdCandleDoesNotMatch() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 18, 18.5, 19, 17); // star: short body, real body gaps down
        // Third candle is bullish and its finite close reaches the penetration
        // threshold, but its open is negative infinity: undefined data.
        addBar(series, Double.NEGATIVE_INFINITY, 26, 27, Double.NEGATIVE_INFINITY);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void penetrationLevelStaysFiniteWhenFirstBodyOverflows() {
        // A DoubleNum bearish first candle from MAX to -MAX/2 has a body
        // magnitude of 1.5 * MAX, which overflows to positive infinity. The
        // penetration level must still come out as the finite midpoint MAX/4.
        // DecimalNum does not overflow, so this test is DoubleNum-only.
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 5);
        addBar(series, Double.MAX_VALUE, -Double.MAX_VALUE / 2, Double.MAX_VALUE, -Double.MAX_VALUE / 2);
        addBar(series, -0.75 * Double.MAX_VALUE, -0.75 * Double.MAX_VALUE + 1, -0.75 * Double.MAX_VALUE + 1,
                -0.75 * Double.MAX_VALUE);
        addBar(series, Double.MAX_VALUE / 8, Double.MAX_VALUE / 2, Double.MAX_VALUE / 2, Double.MAX_VALUE / 8);

        assertTrue(new MorningStarIndicator(series, 1, 0.5).getValue(7));
    }

    @Test
    public void firstBarMustBeBearish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 21, 20, 22, 19); // first: body 1, not above the average body of 2
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starMustHaveShortBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 17, 18.8, 19, 16.5); // star: body 1.8, short-body threshold is 1.8
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starBodyMustGapStrictlyBelowFirstBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 20, 20.5, 21, 19.5); // star: body top 20.5, not below first body bottom 20
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void starGapBoundaryIsStrict() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 19.5, 20, 20.5, 19); // star: body top exactly at first body bottom 20
        addBar(series, 19, 26, 27, 18);

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 27, 26, 28, 25); // third: bearish, close still at 26

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void signedZeroEndpointsDoNotFormAStrictGap() {
        // The first body bottom is +0.0 and the star body top is -0.0:
        // numerically equal endpoints must not satisfy the strict real-body
        // gap, which DoubleNum would otherwise accept because it orders
        // -0.0 below +0.0.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 5, 0.0, 6, -1); // first: bearish long, body bottom +0.0
        addBar(series, -1, -0.0, 0.5, -2); // star: short, body top -0.0
        addBar(series, 1, 6, 7, 0); // third: bullish long close above the level

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void signedZeroPenetrationBoundaryIsInclusive() {
        // First body bottom -5 and body 10 meet the default 50% penetration
        // exactly at +0.0, so a bullish third candle closing at -0.0 still
        // satisfies the inclusive boundary, which DoubleNum would otherwise
        // reject because it orders -0.0 below +0.0.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 5, -5, 6, -6); // first: bearish long, body bottom -5
        addBar(series, -6.5, -6, -5.5, -7); // star: short, real body gaps down
        addBar(series, -21, -0.0, 0.0, -22); // third: bullish long closing at -0.0

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseBelowPenetrationLevelFails() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 24.9, 27, 18); // third: close below penetration level 25

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 18, 18.5, 19, 17); // star: short body, real body gaps down
        addBar(series, 24.5, 25, 26, 24); // third: bullish, close at penetration, body 0.5 < baseline 2

        assertFalse(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseExactlyAtPenetrationLevelMatches() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 25, 27, 18); // third: close exactly at penetration level 25

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void customPenetrationBoundaryUsesFactoryArithmetic() {
        NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(34);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(highPrecisionFactory).build();
        for (int i = 0; i < 3; i++) {
            addBar(series, 1, 1.2, 1.3, 0.9);
        }
        addBar(series, 1.3, 0.3, 1.4, 0.2); // first: bearish long body
        addBar(series, 0.1, 0.2, 0.25, 0.05); // star: short body, real body gaps down
        addBar(series, 0.5, 1.0, 1.1, 0.4); // third: bullish close at the 70% penetration boundary

        assertTrue(new MorningStarIndicator(series, 3, 0.7).getValue(5));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 5);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 18, 18.5, 19, 17);
        addBar(series, 19, 26, 27, 18);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new MorningStarIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 30, 20, 31, 19); // index 5: first
        addBar(series, 18, 18.5, 19, 17); // index 6: star
        addBar(series, 19, 26, 27, 18); // index 7: third

        MorningStarIndicator indicator = new MorningStarIndicator(series);
        assertEquals(7, indicator.getCountOfUnstableBars());
        for (int i = 0; i < indicator.getCountOfUnstableBars(); i++) {
            assertFalse("expected false at " + i, indicator.getValue(i));
        }
        assertTrue(indicator.getValue(7));
    }

    @Test
    public void matchesWithNonZeroBeginIndexAtWarmUpBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10); // indexes 0-9, evicted
        addBaselineBars(series, 5); // indexes 10-14: baseline of the retained series
        addBar(series, 30, 20, 31, 19); // index 15: first
        addBar(series, 18, 18.5, 19, 17); // index 16: star
        addBar(series, 19, 26, 27, 18); // index 17: third
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        MorningStarIndicator indicator = new MorningStarIndicator(series);
        assertFalse(indicator.getValue(16));
        assertTrue(indicator.getValue(17));
    }

    @Test
    public void customPenetrationShiftsPenetrationLevel() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 3);
        addBar(series, 25, 20, 26, 19); // index 3: first, body 5 -> level 25 at penetration 1.0
        addBar(series, 18, 18.5, 19, 17); // index 4: star
        addBar(series, 24, 24.5, 25.5, 23); // index 5: third, close 24.5 below the full-body level 25

        MorningStarIndicator indicator = new MorningStarIndicator(series, 3, 1.0);
        assertFalse(indicator.getValue(5));

        addBar(series, 25, 20, 26, 19); // index 6: first of a fresh window
        addBar(series, 18, 18.5, 19, 17); // index 7: star
        addBar(series, 23.5, 26, 27, 23); // index 8: third, body 2.5 above baseline average 2, close above level 25
        assertTrue(indicator.getValue(8));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 0, 0.5));
    }

    @Test
    public void rejectsInvalidPenetration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new MorningStarIndicator(series, 5, 1.5));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new MorningStarIndicator(series), stableIndexes(series)),
                serializationFixture(series, new MorningStarIndicator(series, 3, 0.75), stableIndexes(series)));
    }

    private void addBaselineBars(BarSeries series, int count) {
        for (int i = 0; i < count; i++) {
            addBar(series, 10, 12, 13, 9); // bullish, body 2, range 4
        }
    }

    private void addBar(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }
}
