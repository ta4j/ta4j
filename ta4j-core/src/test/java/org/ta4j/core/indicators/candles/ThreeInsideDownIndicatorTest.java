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
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ThreeInsideDownIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    /**
     * Pattern index: bars 0-9 adaptive baseline, 10-12 pattern.
     */
    private static final int PATTERN_INDEX = 12;

    public ThreeInsideDownIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesValidThreeInsideDown() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19); // first: bullish long body
        addBar(series, 27.5, 27, 28, 26.5); // second: short body inside the first body
        addBar(series, 22, 18, 23, 17); // third: bearish close below the first open (20)

        assertTrue(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void customAveragePeriodGatesPatternOnExtendedBaseline() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 22, 18, 23, 17);

        assertTrue(new ThreeInsideDownIndicator(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD)
                .getValue(PATTERN_INDEX));
        // A 20-candle baseline cannot complete behind the harami at index 11,
        // so the forwarded period suppresses the otherwise-matching pattern.
        assertFalse(new ThreeInsideDownIndicator(series, 20).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustBeBullish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 30, 20, 31, 19); // first: bearish
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 22, 18, 23, 17);

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void firstBarMustHaveLongBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 21, 20, 22, 19); // first: body 1, not above the average body of 2
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 22, 18, 23, 17);

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondBarMustHaveShortBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 25, 28, 28.5, 24.5); // second: body 3, short-body threshold is 1.8
        addBar(series, 22, 18, 23, 17);

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void secondBarMustBeInsideFirstBody() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 18.5, 18, 19, 17.5); // second: short body outside the first body [20, 30]
        addBar(series, 22, 18, 23, 17);

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseBoundaryIsStrict() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 21, 20, 22, 19.5); // third: close exactly at the first open 20

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseJustBeyondFirstOpenMatches() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 21, 19.99, 22, 19.5); // third: close just below the first open 20

        assertTrue(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdBarMustBeBearish() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 18, 19, 20, 17.5); // third: bullish, close still below the first open 20

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void thirdCloseAboveFirstOpenFails() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 22, 20.5, 23, 19.5); // third: close above the first open 20

        assertFalse(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextBeforeBaselineDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 50, 100, 120, 40); // free context: outside pattern and baseline windows
        }
        addBaselineBars(series, 5);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 22, 18, 23, 17);

        assertTrue(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void contextAfterPatternDoesNotChangeResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 10);
        addBar(series, 20, 30, 31, 19);
        addBar(series, 27.5, 27, 28, 26.5);
        addBar(series, 22, 18, 23, 17);
        addBar(series, 50, 100, 120, 40); // outside the pattern window

        assertTrue(new ThreeInsideDownIndicator(series).getValue(PATTERN_INDEX));
    }

    @Test
    public void falseBeforeWarmUpBoundaryAndTrueAtBoundary() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBaselineBars(series, 5);
        addBar(series, 20, 30, 31, 19); // index 5: first
        addBar(series, 27.5, 27, 28, 26.5); // index 6: second
        addBar(series, 22, 18, 23, 17); // index 7: third

        ThreeInsideDownIndicator indicator = new ThreeInsideDownIndicator(series);
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
        addBar(series, 20, 30, 31, 19); // index 15: first
        addBar(series, 27.5, 27, 28, 26.5); // index 16: second
        addBar(series, 22, 18, 23, 17); // index 17: third
        addBaselineBars(series, 2);
        series.setMaximumBarCount(10);

        ThreeInsideDownIndicator indicator = new ThreeInsideDownIndicator(series);
        assertFalse(indicator.getValue(16));
        assertTrue(indicator.getValue(17));
    }

    @Test
    public void rejectsInvalidAveragePeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        assertThrows(IllegalArgumentException.class, () -> new ThreeInsideDownIndicator(series, 0));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new ThreeInsideDownIndicator(series), stableIndexes(series)),
                serializationFixture(series, new ThreeInsideDownIndicator(series, 3), stableIndexes(series)));
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
