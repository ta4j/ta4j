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
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ThreeInsideUpIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /**
     * Pattern index: bars 0-9 adaptive baseline, 10-12 pattern.
     */
    private static final int PATTERN_INDEX = 12;

    public ThreeInsideUpIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidThreeInsideUp() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish long body
        addBar(series, 22, 22.5, 23, 21.5); // second: short body inside the first body
        addBar(series, 29, 33, 34, 28); // third: bullish close beyond the first open (30)

        assertTrue(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void customAveragePeriodGatesPatternOnExtendedBaseline() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 33, 34, 28);

        assertTrue(new ThreeInsideUpIndicator(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD)
                .getValue(PATTERN_INDEX));
        // A 20-candle baseline cannot complete behind the harami at index 11,
        // so the forwarded period suppresses the otherwise-matching pattern.
        assertFalse(new ThreeInsideUpIndicator(series, 20).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustBeBearish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 33, 34, 28);

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 21, 20, 22, 19); // first: body 1, not above the average body of 2
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 33, 34, 28);

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondBarMustHaveShortBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 25, 25.5, 21.5); // second: body 3, short-body threshold is 1.8
        addBar(series, 29, 33, 34, 28);

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondBarMustBeInsideFirstBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 31, 31.5, 32, 30.5); // second: short body outside the first body [20, 30]
        addBar(series, 29, 33, 34, 28);

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseBoundaryIsStrict() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 30, 30.5, 28.5); // third: close exactly at the first open 30

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseJustBeyondFirstOpenMatches() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 30.01, 31, 28.5); // third: close just beyond the first open 30

        assertTrue(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 34, 31, 35, 30); // third: bearish, close still beyond the first open 30

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseBelowFirstOpenFails() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 28.5, 29.5, 30, 28); // third: close below the first open 30

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarWithNonFiniteOpenDoesNotMatch() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        series.addBar(
                new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime(), doubleFactory.numOf(Double.NaN),
                        doubleFactory.numOf(34), doubleFactory.numOf(28), doubleFactory.numOf(33))); // third: NaN open

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarWithNonFiniteCloseDoesNotMatch() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime(), doubleFactory.numOf(29),
                doubleFactory.numOf(34), doubleFactory.numOf(28), doubleFactory.numOf(Double.NaN))); // third: NaN close

        assertFalse(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 5);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 33, 34, 28);

        assertTrue(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19);
        addBar(series, 22, 22.5, 23, 21.5);
        addBar(series, 29, 33, 34, 28);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new ThreeInsideUpIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 30, 20, 31, 19); // index 5: first
        addBar(series, 22, 22.5, 23, 21.5); // index 6: second
        addBar(series, 29, 33, 34, 28); // index 7: third

        ThreeInsideUpIndicator indicator = new ThreeInsideUpIndicator(series);
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
        addBar(series, 22, 22.5, 23, 21.5); // index 16: second
        addBar(series, 29, 33, 34, 28); // index 17: third
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        ThreeInsideUpIndicator indicator = new ThreeInsideUpIndicator(series);
        assertFalse(indicator.getValue(16));
        assertTrue(indicator.getValue(17));
    }

    @Test
    public void cachedMatchIsInvalidatedWhenBaselineWindowRollsPast() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 15; i++) {
            addBar(series, 0, 10, 10, 0); // baseline bodies at 10..14
        }
        addBar(series, 25, 5, 25, 5); // first at index 15
        addBar(series, 20, 22, 22, 20); // second at index 16
        addBar(series, 23, 26, 26, 23); // third at index 17
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, 10, 10, 0);
        }
        ThreeInsideUpIndicator indicator = new ThreeInsideUpIndicator(series);
        assertTrue(indicator.getValue(17));
        series.setMaximumBarCount(9); // beginIndex advances past the baseline window
        assertFalse(indicator.getValue(17));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new ThreeInsideUpIndicator(series, 0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new ThreeInsideUpIndicator(series), stableIndexes(series)),
                serializationFixture(series, new ThreeInsideUpIndicator(series, 3), stableIndexes(series)));
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
