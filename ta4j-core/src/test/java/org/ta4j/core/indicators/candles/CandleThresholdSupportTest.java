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
import java.lang.ref.WeakReference;
import java.util.Map;
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
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
    public void nanMeasurementsAreNeverClassified() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        addBar(series, 1, 0, 0);
        CandleThresholdSupport support = new CandleThresholdSupport(series);
        Indicator<Num> body = new CandleBodyIndicator(series);
        Indicator<Num> nan = new ConstantIndicator<>(series, series.numFactory().numOf(Double.NaN));

        // Inclusive classifiers must not treat a NaN measurement as "not greater".
        assertFalse(support.isShortShadow(5, nan));
        assertFalse(support.isNear(5, nan, body));
        assertFalse(support.isNear(5, body, nan));
        // Strict classifiers must not classify a NaN measurement.
        assertFalse(support.isLongShadow(5, nan));
    }

    @Test
    public void overflowedMagnitudesRemainDecidable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        addBar(series, 1, 0, 0);
        CandleThresholdSupport support = new CandleThresholdSupport(series);
        Indicator<Num> infinity = new ConstantIndicator<>(series, series.numFactory().numOf(Double.POSITIVE_INFINITY));

        // A non-finite magnitude from finite operands stays decidable: it is
        // longer than any finite baseline, but never at most one.
        assertTrue(support.isLongShadow(5, infinity));
        assertFalse(support.isShortShadow(5, infinity));
    }

    @Test
    public void overflowedBodyFromFiniteEndpointsQualifiesAsLongBodyWithDoubleNum() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        addExtremeCandle(series, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE);
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        // |close - open| = 2 * Double.MAX_VALUE overflows to infinity; the
        // strict comparison against the finite baseline stays decidable.
        assertTrue(support.isLongBody(5));
        assertFalse(support.isShortBody(5));
        assertFalse(support.isDoji(5));
    }

    @Test
    public void overflowedUpperShadowFromFiniteEndpointsQualifiesAsLongShadowWithDoubleNum() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        addExtremeCandle(series, -Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE);
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        // high - max(open, close) overflows to infinity from finite endpoints;
        // it is longer than the finite baseline and never a short shadow.
        assertTrue(support.isLongShadow(5, support.upperShadow()));
        assertFalse(support.isShortShadow(5, support.upperShadow()));
    }

    @Test
    public void extremeCandleClassifiesIdenticallyWithDecimalNum() {
        BarSeries bodySeries = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        addBars(bodySeries, 5, 10, 0, 0);
        addExtremeCandle(bodySeries, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE);
        CandleThresholdSupport bodySupport = new CandleThresholdSupport(bodySeries);

        // DecimalNum computes the magnitudes exactly; both factories must agree.
        assertTrue(bodySupport.isLongBody(5));
        assertFalse(bodySupport.isShortBody(5));
        assertFalse(bodySupport.isDoji(5));

        BarSeries shadowSeries = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        addBars(shadowSeries, 5, 10, 0, 0);
        addExtremeCandle(shadowSeries, -Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE);
        CandleThresholdSupport shadowSupport = new CandleThresholdSupport(shadowSeries);

        assertTrue(shadowSupport.isLongShadow(5, shadowSupport.upperShadow()));
        assertFalse(shadowSupport.isShortShadow(5, shadowSupport.upperShadow()));
    }

    @Test
    public void nonFiniteSourcePriceDisqualifiesLongShadow() {
        // A bar whose high price is non-finite is missing data: the derived
        // shadow is unavailable and must never qualify as long, even though
        // the measurement itself is infinite.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        series.barBuilder()
                .openPrice(1)
                .closePrice(2)
                .highPrice(series.numFactory().numOf(Double.POSITIVE_INFINITY))
                .lowPrice(0)
                .add();
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        assertFalse(support.isLongShadow(5, support.upperShadow()));
    }

    @Test
    public void overflowedShadowBeatsDoubledBaselineAcrossFactories() {
        // A period-1 baseline of 0.75 * MAX doubles to infinity under the old
        // product comparison, collapsing the 1.8 * MAX upper shadow to the same
        // value in DoubleNum. Dividing the shadow by the factor first preserves
        // the strict ordering, so both factories agree.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            addBar(series, 0.75 * Double.MAX_VALUE, 0, 0); // index 0: baseline body
            addExtremeCandle(series, -0.8 * Double.MAX_VALUE, -0.8 * Double.MAX_VALUE, Double.MAX_VALUE,
                    -0.8 * Double.MAX_VALUE); // index 1: upper shadow 1.8 * MAX
            CandleThresholdSupport support = new CandleThresholdSupport(series, 1);

            assertTrue(support.isLongShadow(1, support.upperShadow()));
        }
    }

    @Test
    public void overflowedPriorAndCurrentBodiesStayStrictlyDecidableAcrossFactories() {
        // The prior baseline body and the evaluated body both overflow the raw
        // magnitude (1.5 * MAX and 1.8 * MAX collapse to infinity in DoubleNum);
        // the half-scale comparison keeps the strict ordering decidable so both
        // factories agree that the larger body is long.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            addExtremeCandle(series, 0.75 * Double.MAX_VALUE, -0.75 * Double.MAX_VALUE, 0.75 * Double.MAX_VALUE,
                    -0.75 * Double.MAX_VALUE); // index 0: prior body 1.5 * MAX
            addExtremeCandle(series, 0.9 * Double.MAX_VALUE, -0.9 * Double.MAX_VALUE, 0.9 * Double.MAX_VALUE,
                    -0.9 * Double.MAX_VALUE); // index 1: body 1.8 * MAX
            CandleThresholdSupport support = new CandleThresholdSupport(series, 1);

            assertTrue(support.isLongBody(1));
        }
    }

    @Test
    public void overflowedPriorRangeKeepsShortShadowThresholdDecidableAcrossFactories() {
        // The period-1 prior range doubles to 2 * MAX and overflows in
        // DoubleNum; applying the short-shadow factor before restoring the
        // full-scale baseline keeps the threshold finite (0.2 * MAX), so the
        // 0.5 * MAX shadow is rejected as not short instead of qualifying
        // against an infinite threshold.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            addExtremeCandle(series, 0, 0, Double.MAX_VALUE, -Double.MAX_VALUE); // index 0: range 2 * MAX
            addExtremeCandle(series, 0, 0, 0.5 * Double.MAX_VALUE, 0); // index 1: upper shadow 0.5 * MAX
            CandleThresholdSupport support = new CandleThresholdSupport(series, 1);

            assertFalse(support.isShortShadow(1, support.upperShadow()));
        }
    }

    @Test
    public void subnormalBodyIsNotErasedByHalfScalingAcrossFactories() {
        // Both the prior baseline body and the evaluated body are the smallest
        // positive magnitude; halving each endpoint underflows to zero in
        // DoubleNum, which would erase the baseline and let the subnormal body
        // qualify as long. The raw subnormal magnitude is retained instead, so
        // the strict comparison fails in both factories.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            addExtremeCandle(series, 0, Double.MIN_VALUE, Double.MIN_VALUE, 0); // index 0: body MIN_VALUE
            addExtremeCandle(series, 0, Double.MIN_VALUE, Double.MIN_VALUE, 0); // index 1: body MIN_VALUE
            CandleThresholdSupport support = new CandleThresholdSupport(series, 1);

            assertFalse(support.isLongBody(1));
        }
    }

    @Test
    public void nonFiniteSourcePriceDisqualifiesShortShadow() {
        // A bar whose low price is non-finite is missing data: the derived
        // lower shadow is negative-infinite and must never qualify as short,
        // even though it is "not greater" than the baseline.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        addBars(series, 5, 10, 0, 0);
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                series.numFactory().numOf(1), series.numFactory().numOf(2),
                series.numFactory().numOf(Double.POSITIVE_INFINITY), series.numFactory().numOf(2)));
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        assertFalse(support.isShortShadow(5, support.lowerShadow()));
    }

    @Test
    public void overflowedPriorRangeKeepsDojiDecidable() {
        // The prior window contains one overflowed range (2 * MAX); the
        // half-scale source keeps every operand representable, so the baseline
        // stays finite and a flat candle still qualifies as a doji.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();

        addBars(series, 4, 10, 0, 0); // indexes 0-3: body 10, range 10
        addExtremeCandle(series, -Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE);
        addBar(series, 0, 0, 0); // index 5: flat candle, body 0, range 0
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        assertTrue(support.isDoji(5));
        assertTrue(support.isShortBody(5));
        assertFalse(support.isLongBody(5));
    }

    @Test
    public void overflowedPriorRangeKeepsDojiDecidableWithDecimalNum() {
        // DecimalNum computes the magnitudes exactly; the doji classification
        // must agree with the overflowed DoubleNum variant.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        addBars(series, 4, 10, 0, 0); // indexes 0-3: body 10, range 10
        addExtremeCandle(series, -Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE);
        addBar(series, 0, 0, 0); // index 5: flat candle, body 0, range 0
        CandleThresholdSupport support = new CandleThresholdSupport(series);

        assertTrue(support.isDoji(5));
        assertTrue(support.isShortBody(5));
        assertFalse(support.isLongBody(5));
    }

    /**
     * Adds a candle with the given exact prices, bypassing the shadow-based
     * {@link #addBar(BarSeries, double, double, double)} helper.
     */
    private static void addExtremeCandle(BarSeries series, double open, double close, double high, double low) {
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
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
    public void collectedReferenceOfAnotherSeriesIsCleanedFromItsOwnerMap() {
        BarSeries first = new MockBarSeriesBuilder().build();
        BarSeries second = new MockBarSeriesBuilder().build();
        addBars(first, 6, 10, 0, 0);

        CandleThresholdSupport expected = CandleThresholdSupport.forSeries(first, 3);
        Map<Integer, WeakReference<CandleThresholdSupport>> firstPeriods = CandleThresholdSupport
                .internedPeriods(first);
        WeakReference<CandleThresholdSupport> stored = firstPeriods.get(3);
        assertSame(expected, stored.get());

        // Simulate collection of the interned support, then a lookup on another
        // series: the drain must clean the entry from the owning map, not drop
        // the queued reference while leaving the stale entry retained.
        stored.enqueue();
        CandleThresholdSupport.forSeries(second, 4);

        assertFalse(firstPeriods.containsKey(3));
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
