/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.Bar;
import java.util.Collections;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

public class CandleThresholdSupportTest {

    @Test
    public void isValidRequiresFullPriorWindow() {
        BarSeries series = series(8, 10, 0, 0);
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        for (int index = 0; index < 5; index++) {
            assertFalse("expected warm-up below the boundary at " + index, support.isValid(index));
            assertFalse(support.isLongBody(index));
            assertFalse(support.isShortBody(index));
            assertFalse(support.isDoji(index));
        }
        for (int index = 5; index < 8; index++) {
            assertTrue("expected a valid threshold at " + index, support.isValid(index));
        }
    }

    @Test
    public void thresholdsExcludeCurrentCandle() {
        // The doji boundary at index 5 can only hold if the window is [0,4]:
        // including the small range of candle 5 would shrink the threshold.
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 5, 10, 0, 0); // body 10, range 10
        addBar(series, 1, 0, 0); // body 1, range 1
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        assertTrue(support.isDoji(5));
        assertTrue(support.isShortBody(5));
        assertFalse(support.isLongBody(5));
    }

    @Test
    public void longBodyIsStrictAgainstPriorAverage() {
        CandleThresholdSupport atBoundary = support(10, 0, 0, 10, 0, 0);
        CandleThresholdSupport aboveBoundary = support(10, 0, 0, 10.1, 0, 0);

        assertFalse(atBoundary.isLongBody(5));
        assertTrue(aboveBoundary.isLongBody(5));
    }

    @Test
    public void shortBodyIsStrictAgainstPriorAverage() {
        CandleThresholdSupport atBoundary = support(10, 0, 0, 5, 0, 0);
        CandleThresholdSupport belowBoundary = support(10, 0, 0, 4.9, 0, 0);

        assertFalse(atBoundary.isShortBody(5));
        assertTrue(belowBoundary.isShortBody(5));
    }

    @Test
    public void dojiBoundaryIsInclusive() {
        CandleThresholdSupport atBoundary = support(10, 0, 0, 1, 9, 0);
        CandleThresholdSupport aboveBoundary = support(10, 0, 0, 1.01, 8.99, 0);

        assertTrue(atBoundary.isDoji(5));
        assertFalse(aboveBoundary.isDoji(5));
    }

    @Test
    public void longShadowIsStrictAgainstPriorAverageBody() {
        BarSeries atBoundarySeries = new MockBarSeriesBuilder().build();
        addBars(atBoundarySeries, 5, 10, 0, 0);
        addBar(atBoundarySeries, 10, 20, 0);
        BarSeries aboveBoundarySeries = new MockBarSeriesBuilder().build();
        addBars(aboveBoundarySeries, 5, 10, 0, 0);
        addBar(aboveBoundarySeries, 10, 20.01, 0);

        assertFalse(new CandleThresholdSupport(atBoundarySeries).isLongShadow(5,
                new UpperShadowIndicator(atBoundarySeries)));
        assertTrue(new CandleThresholdSupport(aboveBoundarySeries).isLongShadow(5,
                new UpperShadowIndicator(aboveBoundarySeries)));
    }

    @Test
    public void shortShadowBoundaryIsInclusive() {
        BarSeries atBoundarySeries = new MockBarSeriesBuilder().build();
        addBars(atBoundarySeries, 5, 10, 0, 0);
        addBar(atBoundarySeries, 9, 1, 0);
        BarSeries aboveBoundarySeries = new MockBarSeriesBuilder().build();
        addBars(aboveBoundarySeries, 5, 10, 0, 0);
        addBar(aboveBoundarySeries, 8.99, 1.01, 0);

        assertTrue(new CandleThresholdSupport(atBoundarySeries).isShortShadow(5,
                new UpperShadowIndicator(atBoundarySeries)));
        assertFalse(new CandleThresholdSupport(aboveBoundarySeries).isShortShadow(5,
                new UpperShadowIndicator(aboveBoundarySeries)));
    }

    @Test
    public void nearIsInclusiveAndSymmetric() {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 5, 10, 0, 0);
        addBar(series, 1, 0, 0);
        CandleThresholdSupport support = new CandleThresholdSupport(series);
        Indicator<Num> body = new CandleBodyIndicator(series);
        Indicator<Num> atBoundary = new ConstantIndicator<>(series, series.numFactory().numOf(2));
        Indicator<Num> aboveBoundary = new ConstantIndicator<>(series, series.numFactory().numOf(2.01));

        assertTrue(support.isNear(5, body, atBoundary));
        assertTrue(support.isNear(5, atBoundary, body));
        assertFalse(support.isNear(5, body, aboveBoundary));
    }

    @Test
    public void customPeriodShiftsWarmUpBoundary() {
        BarSeries series = series(4, 10, 0, 0);

        CandleThresholdSupport support = new CandleThresholdSupport(series, 3);
        assertFalse(support.isValid(2));
        assertTrue(support.isValid(3));

        CandleThresholdSupport singlePeriodSupport = new CandleThresholdSupport(series, 1);
        assertFalse(singlePeriodSupport.isValid(0));
        assertTrue(singlePeriodSupport.isValid(1));
    }

    @Test
    public void baselineAccessorsExposeShiftedWindowAverage() {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 4, 10, 3, 4); // each window bar: body 10, range 17
        addBar(series, 1, 0, 0); // evaluation bar: body 1, range 1
        CandleThresholdSupport support = new CandleThresholdSupport(series, 3);

        assertEquals(10d, support.priorAverageBody().getValue(4).doubleValue(), 1e-12);
        assertEquals(17d, support.priorAverageRange().getValue(4).doubleValue(), 1e-12);
    }

    @Test
    public void rejectsAveragePeriodBelowOne() {
        BarSeries series = new MockBarSeriesBuilder().build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new CandleThresholdSupport(series, 0));
        assertTrue(e.getMessage().contains("averagePeriod"));
    }

    @Test
    public void rejectsAveragePeriodAtIntegerMaxValue() {
        BarSeries series = new MockBarSeriesBuilder().build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new CandleThresholdSupport(series, Integer.MAX_VALUE));
        assertTrue(e.getMessage().contains("averagePeriod"));
        assertThrows(IllegalArgumentException.class,
                () -> CandleThresholdSupport.forSeries(new MockBarSeriesBuilder().build(), Integer.MAX_VALUE));
    }

    @Test
    public void nonFiniteMeasurementsAreNeverClassified() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        addBar(series, 1, 0, 0);
        CandleThresholdSupport support = new CandleThresholdSupport(series);
        Indicator<Num> body = new CandleBodyIndicator(series);
        Indicator<Num> nan = new ConstantIndicator<>(series, series.numFactory().numOf(Double.NaN));
        Indicator<Num> infinity = new ConstantIndicator<>(series, series.numFactory().numOf(Double.POSITIVE_INFINITY));

        // Inclusive classifiers must not treat a NaN measurement as "not greater".
        assertFalse(support.isShortShadow(5, nan));
        assertFalse(support.isNear(5, nan, body));
        assertFalse(support.isNear(5, body, nan));
        // Strict classifiers must not classify NaN or infinity measurements.
        assertFalse(support.isLongShadow(5, nan));
        assertFalse(support.isLongShadow(5, infinity));
        assertFalse(support.isShortShadow(5, infinity));
    }

    @Test
    public void boundaryArithmeticDoesNotOverflowAtHighBeginIndex() {
        Bar bar = new TimeBarBuilder(DecimalNumFactory.getInstance()).timePeriod(Duration.ofDays(1))
                .endTime(Instant.EPOCH)
                .openPrice(1)
                .highPrice(2)
                .lowPrice(0.5)
                .closePrice(1)
                .volume(1)
                .build();
        BarSeries series = new BaseBarSeriesBuilder().withBars(List.of(bar))
                .withBeginIndex(Integer.MAX_VALUE - 2)
                .build();
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        // beginIndex + averagePeriod overflows int arithmetic; the boundary must
        // still sit beyond every index the series can address.
        assertFalse(support.isValid(Integer.MAX_VALUE - 1));
        assertFalse(support.isValid(Integer.MAX_VALUE));
    }

    @Test
    public void boundarySemanticsHoldWithDecimalNum() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        addBar(series, 10, 0, 0); // body == 1.0 * prior average body: not long
        addBar(series, 1, 0, 0); // body == 0.1 * prior average range: doji
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        assertFalse(support.isLongBody(5));
        assertTrue(support.isDoji(6));
    }

    @Test
    public void forSeriesInternsBySeriesAndPeriod() {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 6, 10, 0, 0);
        BarSeries otherSeries = new MockBarSeriesBuilder().build();
        addBars(otherSeries, 6, 10, 0, 0);

        assertSame(CandleThresholdSupport.forSeries(series, 5), CandleThresholdSupport.forSeries(series, 5));
        assertNotSame(CandleThresholdSupport.forSeries(series, 5), CandleThresholdSupport.forSeries(series, 7));
        assertNotSame(CandleThresholdSupport.forSeries(series, 5), CandleThresholdSupport.forSeries(otherSeries, 5));
    }

    @Test
    public void forSeriesStaysInternedAcrossManyPeriods() {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 6, 10, 0, 0);

        CandleThresholdSupport periodFive = CandleThresholdSupport.forSeries(series, 5);
        for (int period = 1; period <= 32; period++) {
            CandleThresholdSupport support = CandleThresholdSupport.forSeries(series, period);
            if (period == 5) {
                assertSame(periodFive, support);
            } else {
                assertNotSame(periodFive, support);
            }
        }
    }

    @Test
    public void internedSupportSharesPrimitiveInstances() {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 5, 10, 3, 4);
        addBar(series, 8, 2, 1);
        CandleThresholdSupport support = CandleThresholdSupport.forSeries(series, 5);

        assertSame(support.bodyIndicator(), CandleThresholdSupport.forSeries(series, 5).bodyIndicator());
        assertSame(support.rangeIndicator(), CandleThresholdSupport.forSeries(series, 5).rangeIndicator());
        assertSame(support.upperShadow(), CandleThresholdSupport.forSeries(series, 5).upperShadow());
        assertSame(support.lowerShadow(), CandleThresholdSupport.forSeries(series, 5).lowerShadow());

        assertEquals(8d, support.bodyIndicator().getValue(5).doubleValue(), 1e-12);
        assertEquals(11d, support.rangeIndicator().getValue(5).doubleValue(), 1e-12);
        assertEquals(2d, support.upperShadow().getValue(5).doubleValue(), 1e-12);
        assertEquals(1d, support.lowerShadow().getValue(5).doubleValue(), 1e-12);
    }

    @Test
    public void forSeriesRejectsInvalidInput() {
        assertThrows(NullPointerException.class, () -> CandleThresholdSupport.forSeries(null, 5));
        assertThrows(IllegalArgumentException.class,
                () -> CandleThresholdSupport.forSeries(new MockBarSeriesBuilder().build(), 0));
    }

    @Test
    public void rejectsPeriodsWhoseWarmUpAdditionsOverflow() {
        BarSeries series = new MockBarSeriesBuilder().build();
        assertThrows(IllegalArgumentException.class, () -> CandleThresholdSupport.forSeries(series, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> CandleThresholdSupport.forSeries(series, Integer.MAX_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> new CandleThresholdSupport(series, Integer.MAX_VALUE - 1));
    }

    @Test
    public void isNearReturnsFalseForNullOperand() {
        BarSeries series = series(8, 10, 0, 0);
        CandleThresholdSupport support = new CandleThresholdSupport(series);
        Indicator<Num> missing = new MockIndicator(series, 0, Collections.nCopies(8, null));

        assertFalse(support.isNear(5, missing, support.bodyIndicator()));
        assertFalse(support.isNear(5, support.bodyIndicator(), missing));
    }

    /**
     * Builds a five-candle window of uniform bars followed by one evaluation candle
     * and wraps the result in a support with the default period.
     */
    private static CandleThresholdSupport support(double body, double upperShadow, double lowerShadow, double evalBody,
            double evalUpperShadow, double evalLowerShadow) {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, 5, body, upperShadow, lowerShadow);
        addBar(series, evalBody, evalUpperShadow, evalLowerShadow);
        return new CandleThresholdSupport(series);
    }

    private static BarSeries series(int count, double body, double upperShadow, double lowerShadow) {
        BarSeries series = new MockBarSeriesBuilder().build();
        addBars(series, count, body, upperShadow, lowerShadow);
        return series;
    }

    private static void addBars(BarSeries series, int count, double body, double upperShadow, double lowerShadow) {
        for (int index = 0; index < count; index++) {
            addBar(series, body, upperShadow, lowerShadow);
        }
    }

    /**
     * Adds a bar with the given body, upper shadow, and lower shadow: the open is
     * 0, the close equals the body, and high/low follow from the shadows.
     */
    private static void addBar(BarSeries series, double body, double upperShadow, double lowerShadow) {
        final double close = body;
        final double open = 0;
        final double high = close + upperShadow;
        final double low = open - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }
}
