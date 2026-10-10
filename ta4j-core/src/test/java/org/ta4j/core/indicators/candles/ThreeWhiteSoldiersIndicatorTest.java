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
import java.time.Duration;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ThreeWhiteSoldiersIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /**
     * Pattern index: bars 0-9 adaptive baseline, 10-12 pattern.
     */
    private static final int PATTERN_INDEX = 12;

    public ThreeWhiteSoldiersIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidThreeWhiteSoldiers() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9); // index 10: first soldier
        addBar(series, 16.5, 20, 20, 16); // index 11: second soldier, open inside the first body
        addBar(series, 18, 22, 22, 17.9); // index 12: third soldier, open inside the second body

        assertTrue(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstSoldierMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 14, 15.2, 13.8); // index 10: first soldier, bearish, short upper shadow
        addBar(series, 14.5, 16.5, 16.5, 14); // open inside the first body [14, 15]
        addBar(series, 15.5, 17, 17, 15.1); // open inside the second body [14.5, 16.5]

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstSoldierMustHaveShortUpperShadow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 19, 14.9); // index 10: upper shadow 1.0, above 0.1 x 4.0
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 22, 22, 17.9);

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondSoldierOpenMustBeInsideFirstBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 18.5, 20, 20, 16); // index 11: open above the first body high 18
        addBar(series, 18, 22, 22, 17.9);

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondSoldierCloseMustAdvance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 17.5, 17.5, 16); // index 11: close not above 18
        addBar(series, 17, 17.8, 17.8, 16.9); // open inside the second body [16.5, 17.5]

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdSoldierOpenMustBeInsideSecondBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 20.5, 22, 22, 17.9); // index 12: open above the second body high 20

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdSoldierCloseMustAdvance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 19.5, 19.5, 17.9); // index 12: close not above 20

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdSoldierMustHaveShortUpperShadow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 22, 22.6, 17.9); // index 12: upper shadow 0.6, above the 0.372 threshold

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void shortShadowBoundaryIsInclusive() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18.4, 14.9); // index 10: upper shadow exactly 0.4 = 0.1 x range 4
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 22, 22, 17.9);

        assertTrue(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void signedZeroOpenAtBodyBottomCountsAsContainment() {
        // The first body bottom is +0.0 and the second soldier opens at -0.0:
        // numerically equal endpoints must count as containment, which
        // DoubleNum would otherwise reject because it orders -0.0 below +0.0.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 0.0, 5, 5, -0.0); // index 10: first soldier, body bottom +0.0
        addBar(series, -0.0, 6, 6, -1); // index 11: second soldier opens at -0.0
        addBar(series, 0.5, 7, 7, 0); // index 12: third soldier inside the second body

        assertTrue(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void openContainmentBoundaryIsInclusive() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16); // index 11: second, open inside the first body
        addBar(series, 16.5, 22, 22, 16.5); // index 12: third, open exactly at the second body low 16.5

        assertTrue(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 5);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 22, 22, 17.9);

        assertTrue(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 22, 22, 17.9);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 15, 18, 18, 14.9); // index 5: first soldier
        addBar(series, 16.5, 20, 20, 16); // index 6: second soldier
        addBar(series, 18, 22, 22, 17.9); // index 7: third soldier

        ThreeWhiteSoldiersIndicator indicator = new ThreeWhiteSoldiersIndicator(series);
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
        addBar(series, 15, 18, 18, 14.9); // index 15: first soldier
        addBar(series, 16.5, 20, 20, 16); // index 16: second soldier
        addBar(series, 18, 22, 22, 17.9); // index 17: third soldier
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        ThreeWhiteSoldiersIndicator indicator = new ThreeWhiteSoldiersIndicator(series);
        assertFalse(indicator.getValue(16));
        assertTrue(indicator.getValue(17));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new ThreeWhiteSoldiersIndicator(series, 0));
    }

    @Test
    public void nonFiniteShadowIsNotSoldiers() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9);
        addBar(series, 16.5, 20, 20, 16);
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime(), doubleFactory.numOf(18),
                doubleFactory.numOf(Double.NaN), doubleFactory.numOf(17.9), doubleFactory.numOf(22))); // index 12:
                                                                                                       // third soldier
                                                                                                       // with NaN high

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void nonFiniteFirstSoldierOpenIsNotSoldiers() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(Double.NEGATIVE_INFINITY), doubleFactory.numOf(18), doubleFactory.numOf(14.9),
                doubleFactory.numOf(18))); // index 10: first soldier with infinite open
        addBar(series, 16.5, 20, 20, 16);
        addBar(series, 18, 22, 22, 17.9);

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void nullHighOnSoldierIsNotSoldiersRatherThanThrowing() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 15, 18, 18, 14.9); // index 10: first soldier
        addBar(series, 16.5, 20, 20, 16); // index 11: second soldier
        // index 12: third soldier without a high endpoint; the upper-shadow
        // indicator would dereference the null high before the non-finite guard.
        series.barBuilder().openPrice(18).closePrice(22).highPrice((Num) null).lowPrice(17.9).add();

        assertFalse(new ThreeWhiteSoldiersIndicator(series).getValue(PATTERN_INDEX));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new ThreeWhiteSoldiersIndicator(series), stableIndexes(series)),
                serializationFixture(series, new ThreeWhiteSoldiersIndicator(series, 3), stableIndexes(series)));
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
