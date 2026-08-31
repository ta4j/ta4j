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

import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.NonFiniteBar;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DojiIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public DojiIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsDojiAtInclusiveBoundaryWithDefaults() {
        // Baseline of five range-10 candles yields a prior average range of 10, so
        // the doji threshold is 0.1 * 10 = 1.0. A body of exactly 1.0 is a doji.
        BarSeries series = dojiSeries(5, 1.0);
        DojiIndicator indicator = new DojiIndicator(series);

        assertFalse(indicator.getValue(4));
        assertTrue(indicator.getValue(5));
    }

    @Test
    public void rejectsBodyAboveThreshold() {
        BarSeries series = dojiSeries(5, 1.01);

        assertFalse(new DojiIndicator(series).getValue(5));
    }

    @Test
    public void boundaryIsInclusive() {
        assertTrue(new DojiIndicator(dojiSeries(5, 1.0)).getValue(5));
        assertFalse(new DojiIndicator(dojiSeries(5, 1.01)).getValue(5));
    }

    @Test
    public void customAveragePeriodShiftsWarmUpBoundary() {
        BarSeries series = dojiSeries(3, 1.0);
        DojiIndicator indicator = new DojiIndicator(series, 3, 0.1);
        assertEquals(3, indicator.getCountOfUnstableBars());

        assertFalse(indicator.getValue(2));
        assertTrue(indicator.getValue(3));
    }

    @Test
    public void customRangeFactorScalesThreshold() {
        assertTrue(new DojiIndicator(dojiSeries(5, 2.0), 5, 0.2).getValue(5));
        assertFalse(new DojiIndicator(dojiSeries(5, 2.01), 5, 0.2).getValue(5));
    }

    @Test
    public void zeroBodyAgainstZeroBaselineIsDoji() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 6; i++) {
            addBar(series, 0, 0, 0);
        }

        assertTrue(new DojiIndicator(series).getValue(5));
    }

    @Test
    public void signedZeroRangeFactorBehavesLikeZero() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 6; i++) {
            addBar(series, 0, 0, 0);
        }

        assertTrue(new DojiIndicator(series, 5, -0.0d).getValue(5));
    }

    @Test
    public void oversizedRangeFactorTreatsEveryFiniteBodyAsDoji() {
        // rangeFactor * averageRange overflows the DoubleNum threshold product to
        // positive infinity; the threshold is then unbounded, so any finite body
        // qualifies.
        assertTrue(new DojiIndicator(dojiSeries(5, 2.0), 5, Double.MAX_VALUE).getValue(5));
    }

    @Test
    public void overflowedBodyQualifiesAgainstOverflowedThreshold() {
        // Five range-3 baseline candles give a prior average range of 3. The
        // final candle spans -Double.MAX_VALUE to Double.MAX_VALUE, so its body
        // (2 * MAX) overflows DoubleNum while the threshold product (3 * MAX)
        // overflows upward: mathematically the body sits below the threshold,
        // so the doji must qualify instead of being rejected as non-finite.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 3, 0, 0);
        }
        series.barBuilder()
                .openPrice(-Double.MAX_VALUE)
                .closePrice(Double.MAX_VALUE)
                .highPrice(Double.MAX_VALUE)
                .lowPrice(-Double.MAX_VALUE)
                .add();

        assertTrue(new DojiIndicator(series, 5, Double.MAX_VALUE).getValue(5));
    }

    @Test
    public void overflowedBodyExceedingOverflowedThresholdIsNotADoji() {
        // One range-1.1 baseline candle gives a prior average range of 1.1. The
        // final candle spans -Double.MAX_VALUE to Double.MAX_VALUE, so its body
        // (2 * MAX) exceeds the overflowed threshold product (1.1 * MAX):
        // comparing the raw overflowed magnitudes directly would misclassify
        // the body as qualifying. The scaled comparison preserves the ordering,
        // rejecting the body under DoubleNum and matching DecimalNum's exact
        // arithmetic.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 1.1, 0, 0);
        series.barBuilder()
                .openPrice(-Double.MAX_VALUE)
                .closePrice(Double.MAX_VALUE)
                .highPrice(Double.MAX_VALUE)
                .lowPrice(-Double.MAX_VALUE)
                .add();

        assertFalse(new DojiIndicator(series, 1, Double.MAX_VALUE).getValue(1));
    }

    @Test
    public void unavailableBodyIsNotADojiEvenAgainstOverflowedThreshold() {
        // The overflowed threshold qualifies a body that overflowed from finite
        // operands, but a body missing because its inputs are genuinely
        // unavailable (a NaN close) stays conservatively not a doji.
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 3, 0, 0);
        }
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(Double.NaN), doubleFactory.numOf(10), doubleFactory.numOf(0),
                doubleFactory.numOf(Double.NaN)));

        assertFalse(new DojiIndicator(series, 5, Double.MAX_VALUE).getValue(5));
    }

    @Test
    public void overflowingPriorAverageIsNotADoji() {
        // Five baseline candles with range Double.MAX_VALUE overflow the
        // DoubleNum SMA accumulator before division; the divide-first baseline
        // still yields the finite MAX_VALUE average, so the correct threshold
        // (MAX_VALUE * 0.1) is finite and the body (MAX_VALUE / 2) exceeds it.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, Double.MAX_VALUE, 0, 0);
        }
        addBar(series, Double.MAX_VALUE / 2, 0, 0);

        assertFalse(new DojiIndicator(series, 5, 0.1).getValue(5));
    }

    @Test
    public void zeroBodyAgainstOverflowingRangeBaselineIsDoji() {
        // Five baseline candles with range Double.MAX_VALUE overflow the
        // DoubleNum SMA accumulator to positive infinity; the divide-first
        // baseline must still produce the finite MAX_VALUE average, so the
        // zero body is at most MAX_VALUE * 0.1 and qualifies as a doji.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 0, Double.MAX_VALUE, 0);
        }
        addBar(series, 0, 0, 0);

        assertTrue(new DojiIndicator(series, 5, 0.1).getValue(5));
    }

    @Test
    public void zeroBodyAgainstSumOverflowingRangeBaselineIsDoji() {
        // Three baseline candles with range Double.MAX_VALUE overflow the
        // half-scale sum as well (3 * MAX / 2 > MAX); the incremental Welford
        // rebuild keeps the mean at exactly MAX / 2, so the doubled average is
        // the finite MAX_VALUE and the zero body qualifies as a doji.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 3; i++) {
            addBar(series, 0, Double.MAX_VALUE, 0);
        }
        addBar(series, 0, 0, 0);

        assertTrue(new DojiIndicator(series, 3, 0.1).getValue(3));
    }

    @Test
    public void overflowedRestoredThresholdQualifiesZeroBodyAsDoji() {
        // One baseline candle spanning -MAX to MAX makes the prior average
        // range itself overflow DoubleNum, and its restored full-scale
        // threshold overflows upward too. Every finite body sits below an
        // overflowed positive threshold, so the zero body must qualify,
        // matching DecimalNum's exact arithmetic.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            series.barBuilder()
                    .openPrice(-Double.MAX_VALUE)
                    .closePrice(Double.MAX_VALUE)
                    .highPrice(Double.MAX_VALUE)
                    .lowPrice(-Double.MAX_VALUE)
                    .add();
            addBar(series, 0, 0, 0);

            assertTrue(new DojiIndicator(series, 1, 1d).getValue(1));
        }
    }

    @Test
    public void overflowedBodyAndThresholdKeepTheirExactOrderingAcrossFactories() {
        // The baseline range is 2 * MAX and the factor is 0.75, making the
        // exact threshold 1.5 * MAX. The candidate body is 1.8 * MAX. Both
        // magnitudes overflow DoubleNum after restoring full scale, so the
        // half-scale comparison must preserve their strict ordering.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            series.barBuilder()
                    .openPrice(0)
                    .closePrice(0)
                    .highPrice(Double.MAX_VALUE)
                    .lowPrice(-Double.MAX_VALUE)
                    .add();
            double endpoint = Double.MAX_VALUE * 0.9d;
            series.barBuilder().openPrice(-endpoint).closePrice(endpoint).highPrice(endpoint).lowPrice(-endpoint).add();

            assertFalse(new DojiIndicator(series, 1, 0.75d).getValue(1));
        }
    }

    @Test
    public void zeroBodyAfterOverflowedRangeDilutedByWindowIsDoji() {
        // A three-bar baseline whose ranges are [0, 0, 2 * MAX] has the finite
        // mean 2/3 * MAX even though the extreme range overflows the raw range
        // indicator; the half-scale source keeps every operand representable,
        // so a following zero-body candle qualifies instead of being rejected.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 0, 0, 0); // index 0: zero range
        addBar(series, 0, 0, 0); // index 1: zero range
        series.barBuilder()
                .openPrice(-Double.MAX_VALUE)
                .closePrice(Double.MAX_VALUE)
                .highPrice(Double.MAX_VALUE)
                .lowPrice(-Double.MAX_VALUE)
                .add(); // index 2: range 2 * MAX
        addBar(series, 0, 0, 0); // index 3: flat doji candidate

        assertTrue(new DojiIndicator(series, 3, 0.1).getValue(3));
    }

    @Test
    public void zeroBodyAgainstTinyRangeBaselineIsDoji() {
        // A one-bar baseline whose range is barely representable puts both
        // finite endpoints at positive infinity under per-endpoint division,
        // and their subtraction becomes NaN in DoubleNum. The shared finite
        // scale keeps a zero body at zero, so it qualifies for any factor.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(series, 1e-308, 0, 0); // index 0: range 1e-308
        series.barBuilder()
                .openPrice(Double.MAX_VALUE)
                .closePrice(Double.MAX_VALUE)
                .highPrice(Double.MAX_VALUE)
                .lowPrice(Double.MAX_VALUE)
                .add(); // index 1: zero body with non-finite-scaled endpoints

        assertTrue(new DojiIndicator(series, 1, 0.1).getValue(1));
    }

    @Test
    public void zeroRangeFactorOnlyQualifiesZeroBody() {
        // A zero range factor admits only a body with no magnitude at all. Under
        // DoubleNum the ratio below would underflow a nonzero subnormal body to
        // zero against the huge baseline and misclassify it as a doji, so the raw
        // body magnitude decides: a MIN_VALUE body is rejected, a zero body
        // qualifies, and DecimalNum's exact ratio agrees on both.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(Double.MAX_VALUE / 2).lowPrice(0).add(); // index 0:
                                                                                                          // baseline
                                                                                                          // range MAX /
                                                                                                          // 2
        series.barBuilder().openPrice(0).closePrice(Double.MIN_VALUE).highPrice(Double.MAX_VALUE / 2).lowPrice(0).add(); // index
                                                                                                                         // 1:
                                                                                                                         // subnormal
                                                                                                                         // body
        series.barBuilder().openPrice(0).closePrice(0).highPrice(Double.MAX_VALUE / 2).lowPrice(0).add(); // index 2:
                                                                                                          // zero body

        DojiIndicator doji = new DojiIndicator(series, 1, 0d);

        assertFalse(doji.getValue(1));
        assertTrue(doji.getValue(2));
    }

    @Test
    public void zeroBodyAgainstOverflowedPriorRangeQualifiesAcrossFactories() {
        // A period-1 baseline whose range spans -MAX to MAX doubles to 2 * MAX
        // and overflows the restored full-scale average in DoubleNum; the old
        // full-scale check rejected the doji outright. Applying the factor to
        // the half-scale average before restoring keeps the threshold finite,
        // so the zero body qualifies in both factories.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            series.barBuilder()
                    .openPrice(-Double.MAX_VALUE)
                    .closePrice(Double.MAX_VALUE)
                    .highPrice(Double.MAX_VALUE)
                    .lowPrice(-Double.MAX_VALUE)
                    .add(); // index 0: range 2 * MAX
            addBar(series, 0, 0, 0); // index 1: zero body

            assertTrue(new DojiIndicator(series, 1, 0.1).getValue(1));
        }
    }

    @Test
    public void subnormalFactorKeepsBodyRatioStrictlyOrderedAcrossFactories() {
        // The period-1 prior range is 0.75 and the factor is the smallest
        // positive double, so the true threshold is 0.75 * MIN_VALUE and a
        // MIN_VALUE body is not a doji. Dividing the body by the baseline
        // rounds the subnormal ratio down onto the factor in DoubleNum;
        // scaling the dividend into the normal range first preserves the
        // strict ordering, so both factories agree.
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).build();
            addBar(series, 0, 0.75, 0); // index 0: range 0.75
            addBar(series, Double.MIN_VALUE, 0, 0); // index 1: body MIN_VALUE

            assertFalse(new DojiIndicator(series, 1, Double.MIN_VALUE).getValue(1));
        }
    }

    @Test
    public void rejectsInvalidParameters() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 5, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DojiIndicator(series, 5, Double.POSITIVE_INFINITY));
    }

    @Test
    public void nonFiniteBodyIsNotADoji() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 10, 0, 0);
        }
        series.addBar(new NonFiniteBar(series.getBar(series.getEndIndex()).getEndTime().minus(Duration.ofHours(12)),
                doubleFactory.numOf(Double.NaN), doubleFactory.numOf(10), doubleFactory.numOf(0),
                doubleFactory.numOf(Double.NaN)));

        assertFalse(new DojiIndicator(series).getValue(5));
    }

    @Test
    public void contextBeforeBaselineWindowDoesNotChangeResult() {
        // Pattern candle at index 6 with period 3: the baseline window is [3, 5],
        // so bars before index 3 must not influence the result.
        BarSeries control = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 3; i++) {
            addBar(control, 10, 0, 0);
        }
        for (int i = 0; i < 3; i++) {
            addBar(control, 10, 0, 0);
        }
        addBar(control, 1.0, 0, 0);

        BarSeries varied = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addBar(varied, 50, 10, 10);
        addBar(varied, 2, 4, 22);
        addBar(varied, 99, 2, 9);
        for (int i = 0; i < 3; i++) {
            addBar(varied, 10, 0, 0);
        }
        addBar(varied, 1.0, 0, 0);

        DojiIndicator controlIndicator = new DojiIndicator(control, 3, 0.1);
        DojiIndicator variedIndicator = new DojiIndicator(varied, 3, 0.1);

        assertTrue(controlIndicator.getValue(6));
        assertTrue(variedIndicator.getValue(6));
    }

    @Test
    public void rollingSeriesWithNonzeroBeginIndex() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            addBar(series, 10, 0, 0);
        }
        for (int i = 0; i < 5; i++) {
            addBar(series, 10, 0, 0);
        }
        addBar(series, 1.0, 0, 0);
        for (int i = 0; i < 4; i++) {
            addBar(series, 10, 0, 0);
        }
        series.setMaximumBarCount(10);

        DojiIndicator indicator = new DojiIndicator(series);

        assertEquals(10, series.getBeginIndex());
        assertFalse(indicator.getValue(14));
        assertTrue(indicator.getValue(15));
    }

    @Test
    public void dependentCacheInvalidatesWhenHeadAdvanceLeavesBaseline() {
        // A cached dependent indicator must observe a pattern becoming false when
        // the head advances past its baseline window, even when its own cached
        // entry survives the eviction.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            addBar(series, 10, 0, 0);
        }
        addBar(series, 1.0, 0, 0); // index 5: doji at the inclusive boundary
        DojiIndicator indicator = new DojiIndicator(series);
        BooleanTransformIndicator<Boolean> dependent = new BooleanTransformIndicator<>(indicator, value -> value);

        assertTrue(indicator.getValue(5));
        assertTrue(dependent.getValue(5));

        // Slide the head past the [0, 4] baseline window of index 5.
        series.setMaximumBarCount(5);
        assertEquals(1, series.getBeginIndex());

        assertFalse(indicator.getValue(5));
        assertFalse(dependent.getValue(5));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new DojiIndicator(series), stableIndexes(series)),
                serializationFixture(series, new DojiIndicator(series, 5, 0.1), stableIndexes(series)));
    }

    /**
     * Builds {@code averagePeriod} range-10 baseline candles followed by one candle
     * with the given body.
     */
    private BarSeries dojiSeries(int averagePeriod, double body) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < averagePeriod; i++) {
            addBar(series, 10, 0, 0);
        }
        addBar(series, body, 0, 0);
        return series;
    }

    private void addBar(BarSeries series, double body, double upperShadow, double lowerShadow) {
        final double open = 0;
        final double close = body;
        final double high = close + upperShadow;
        final double low = open - lowerShadow;
        series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
    }
}
