/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.assertIndicatorRoundTrips;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.FloatNumFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CusumIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;
    private MockIndicator source;
    private CusumIndicator cusum;

    public CusumIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        source = new MockIndicator(data, 0, numOf(0.010), numOf(0.010), numOf(-0.100));
        cusum = new CusumIndicator(source, 0, 0.005, 3.0, 0.5);
    }

    @Test
    public void winsorizedRecursionMatchesFormula() {
        // mu0 = 0, k = 0.005, clipFactor = 3, scaleDecay = 0.5:
        // S = [0, 0, 0.045] with the -0.100 increment clipped to 3 * 0.015.
        assertNumEquals(0, cusum.getValue(0));
        assertNumEquals(0, cusum.getValue(1));
        assertNumEquals(0.045, cusum.getValue(2));
    }

    @Test
    public void nonFiniteInputCarriesPreviousValue() {
        MockIndicator gapped = new MockIndicator(data, 0, numOf(0.010), NaN.NaN, numOf(-0.100));
        CusumIndicator gappedCusum = new CusumIndicator(gapped, 0, 0.005);

        // The gap carries the previous CUSUM value and deviation scale forward.
        assertNumEquals(0, gappedCusum.getValue(0));
        assertNumEquals(0, gappedCusum.getValue(1));
        assertNumEquals(0.045, gappedCusum.getValue(2));
    }

    @Test
    public void firstOutlierAfterOnTargetRunIsFullyDamped() {
        // All raw increments so far were exactly zero, so the deviation scale is
        // zero when the first outlier arrives: the winsorization bound is zero
        // and the outlier is fully damped while bootstrapping the scale.
        MockIndicator bootstrap = new MockIndicator(data, 0, numOf(0), numOf(-100), numOf(-100));
        CusumIndicator bootstrapCusum = new CusumIndicator(bootstrap, 0, 0, 3.0, 0.5);

        assertNumEquals(0, bootstrapCusum.getValue(0));
        assertNumEquals(0, bootstrapCusum.getValue(1));
        assertNumEquals(100, bootstrapCusum.getValue(2));
    }

    @Test
    public void propagatesSourceUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        MockIndicator unstable = new MockIndicator(series, 2, numOf(0.010), numOf(0.010), numOf(0.010), numOf(0.010));
        CusumIndicator unstableCusum = new CusumIndicator(unstable, 0, 0.005);

        assertEquals(2, unstableCusum.getCountOfUnstableBars());
        assertTrue(unstableCusum.getValue(0).isNaN());
        assertTrue(unstableCusum.getValue(1).isNaN());
        assertFalse(unstableCusum.getValue(2).isNaN());
    }

    @Test
    public void killSwitchFlipsWhenCusumCrossesControlLimit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        MockIndicator drift = new MockIndicator(series, 0, numOf(0.01), numOf(0.01), numOf(0.01), numOf(-0.02),
                numOf(-0.02), numOf(-0.02));
        CusumIndicator driftCusum = new CusumIndicator(drift, 0, 0, 3.0, 0.5);
        FixedIndicator<Num> limit = new FixedIndicator<>(series, numOf(0.05), numOf(0.05), numOf(0.05), numOf(0.05),
                numOf(0.05), numOf(0.05));
        Rule killSwitch = NumericIndicator.of(driftCusum).isLessThan(limit);
        TradingRecord record = new BaseTradingRecord();

        // S = [0, 0, 0, 0.02, 0.04, 0.06]: the switch opens only once S >= 0.05.
        for (int i = 0; i < 5; i++) {
            assertTrue(killSwitch.isSatisfied(i, record));
        }
        assertFalse(killSwitch.isSatisfied(5, record));
    }

    @Test
    public void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, 0, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, 0, 0.005, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, 0, 0.005, 3.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, Double.NaN, 0.005));
        assertThrows(IllegalArgumentException.class, () -> new CusumIndicator(source, Double.POSITIVE_INFINITY, 0.005));
        assertThrows(NullPointerException.class, () -> new CusumIndicator((Indicator<Num>) null, 0, 0.005));
    }

    @Test
    public void scaleDecaySurvivesCoarsePrecisionRounding() {
        // DecimalNumFactory at precision 1 rounds 0.9999 to 1.0, so the range
        // check must validate the raw value before conversion instead of
        // rejecting the rounded boundary; the complement 1 - decay must also
        // be converted in raw space, or the EWMA scale weight degenerates to
        // zero and the winsorization scale freezes.
        BarSeries coarseSeries = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance(1))
                .withData(1, 2, 3)
                .build();
        MockIndicator coarseSource = new MockIndicator(coarseSeries, 0, coarseSeries.numFactory().numOf(0.010),
                coarseSeries.numFactory().numOf(0.010), coarseSeries.numFactory().numOf(-0.100));

        CusumIndicator coarseCusum = new CusumIndicator(coarseSource, 0, 0.005, 3.0, 0.9999);

        // Ideal arithmetic: S(2) = 0.045 (same clip bound as the 0.5-decay
        // recursion); precision-1 rounding of the seed scale shifts the bound
        // to 0.06, so tolerate the rounding drift.
        assertTrue(Num.isFinite(coarseCusum.getValue(2)));
        assertNumEquals(coarseSeries.numFactory().numOf(0.045), coarseCusum.getValue(2), 0.02);
    }

    @Test
    public void zeroScaleBootstrapsFromRawIncrementUnderCoarsePrecision() {
        // When every prior raw increment was exactly zero the deviation scale
        // is zero and the first non-zero deviation is fully damped; its raw
        // magnitude must then bootstrap the scale through the separately
        // converted complement. With scaleDecay 0.9999 on a precision-1
        // factory the decay rounds to 1, so recomputing the complement as
        // 1 - roundedDecay collapses to zero and the scale never bootstraps,
        // suppressing the CUSUM indefinitely.
        BarSeries coarseSeries = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance(1))
                .withData(1, 2, 3)
                .build();
        MockIndicator coarseSource = new MockIndicator(coarseSeries, 0, coarseSeries.numFactory().numOf(-0.005),
                coarseSeries.numFactory().numOf(-0.100), coarseSeries.numFactory().numOf(-0.100));

        CusumIndicator coarseCusum = new CusumIndicator(coarseSource, 0, 0.005, 3.0, 0.9999);

        assertNumEquals(0, coarseCusum.getValue(0));
        assertNumEquals(0, coarseCusum.getValue(1));
        assertTrue(coarseCusum.getValue(2).isPositive());
    }

    @Test
    public void acceptsArbitraryPrecisionDecayOutsideDoubleRange() {
        // BigDecimal decays whose doubleValue() collapses to 0.0 or 1.0 are
        // still inside (0, 1): the interval must be validated on the raw
        // value without primitive narrowing, and the complement must survive
        // so the EWMA scale weight stays meaningful and the CUSUM remains
        // finite.
        NumFactory decimalFactory = DecimalNumFactory.getInstance(10);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(decimalFactory).withData(1, 2, 3).build();
        MockIndicator decimalSource = new MockIndicator(series, 0, decimalFactory.numOf(0.010),
                decimalFactory.numOf(0.010), decimalFactory.numOf(-0.100));

        CusumIndicator tinyDecay = new CusumIndicator(decimalSource, 0, 0.005, 3.0, new BigDecimal("1e-400"));
        assertTrue(Num.isFinite(tinyDecay.getValue(2)));

        CusumIndicator nearOneDecay = new CusumIndicator(decimalSource, 0, 0.005, 3.0,
                new BigDecimal("0.999999999999999999999"));
        assertTrue(Num.isFinite(nearOneDecay.getValue(2)));

        assertThrows(IllegalArgumentException.class,
                () -> new CusumIndicator(decimalSource, 0, 0.005, 3.0, new BigDecimal("1.000000000000000000001")));
    }

    @Test
    public void underflowedComplementStillBootstrapsWinsorizationScale() {
        // A decay within 1e-308 of one (1 - 1e-400) has an exact complement of
        // 1e-400, which underflows to zero on the double grid. Narrowing the
        // complement before weighting the delta would freeze the scale at its
        // zero seed and suppress the CUSUM indefinitely; recombining with the
        // raw exact complement bootstraps the bound so the clipped increment
        // (~3e-92) survives.
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(0, -1e308, -1).build();
        MockIndicator doubleSource = new MockIndicator(series, 0, doubleFactory.numOf(0), doubleFactory.numOf(-1e308),
                doubleFactory.numOf(-1));

        CusumIndicator cusum = new CusumIndicator(doubleSource, 0, 0, 3.0,
                BigDecimal.ONE.subtract(new BigDecimal("1e-400")));

        Num expected = doubleFactory.numOf(new BigDecimal("1e-92")).multipliedBy(doubleFactory.numOf(3));
        assertNumEquals(expected, cusum.getValue(2));
    }

    @Test
    public void underflowedScaleStillProducesRepresentableClippingBound() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        Num minimum = doubleFactory.numOf(Double.MIN_VALUE);
        Num negativeMinimum = minimum.negate();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(0, 0, 0).build();
        MockIndicator source = new MockIndicator(series, 0, doubleFactory.zero(), negativeMinimum, negativeMinimum);
        BigDecimal updateWeight = new BigDecimal("1e-100");
        CusumIndicator cusum = new CusumIndicator(source, 0, 0, new BigDecimal("1e100"),
                BigDecimal.ONE.subtract(updateWeight));

        // The first persistent deviation updates the exact scale to
        // Double.MIN_VALUE * 1e-100, which narrows to zero. The next clipping
        // bound reverses that intermediate underflow by multiplying the retained
        // exact scale by 1e100, admitting one representable minimum increment.
        assertNumEquals(doubleFactory.zero(), cusum.getValue(1));
        assertNumEquals(minimum, cusum.getValue(2));
    }

    @Test
    public void scaleDecayComplementAvoidsDoubleRoundingArtifact() {
        // The complement must be the exact BigDecimal difference 1 - decay: a
        // double-computed complement carries the binary rounding artifact
        // (1d - 0.94 = 0.060000000000000005) into the winsorization bound and
        // shifts the CUSUM by ~6e-17 in DecimalNum arithmetic. Exact
        // arithmetic yields S = [1, 4, 7.72]: the increment 5 clips to 3 at
        // index 1, and the increment 4.5 clips to the exact bound 3.72 at
        // index 2.
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(decimalFactory).withData(1, 2, 3).build();
        MockIndicator decimalSource = new MockIndicator(series, 0, decimalFactory.numOf(-1), decimalFactory.numOf(-5),
                decimalFactory.numOf(-4.5));
        CusumIndicator decimalCusum = new CusumIndicator(decimalSource, 0, 0, 3.0, 0.94);

        assertNumEquals(1, decimalCusum.getValue(0));
        assertNumEquals(4, decimalCusum.getValue(1));
        assertNumEquals("7.72", decimalCusum.getValue(2));
    }

    @Test
    public void saturatesOverflowingDeviationAndAccumulator() {
        // Deviations and accumulations that overflow the numeric representation
        // (a DoubleNum series jumping between opposite extremes) must saturate
        // at the largest finite magnitude instead of leaking infinity.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(-Double.MAX_VALUE, Double.MAX_VALUE / 2)
                .build();
        CusumIndicator extreme = new CusumIndicator(
                new MockIndicator(series, 0, numOf(-Double.MAX_VALUE), numOf(Double.MAX_VALUE / 2)), Double.MAX_VALUE,
                0, 3.0, 0.5);

        assertTrue(Num.isFinite(extreme.getValue(0)));
        assertTrue(extreme.getValue(0).isGreaterThanOrEqual(numOf(Double.MAX_VALUE)));
        assertTrue(Num.isFinite(extreme.getValue(1)));
        assertTrue(extreme.getValue(1).isGreaterThanOrEqual(numOf(Double.MAX_VALUE)));
    }

    @Test
    public void exactDeviationKeepsRepresentableCancellation() {
        // targetMean - current - allowance = 1.7e308 - (-1e308) - 1.7e308 =
        // 1e308 is representable, but the naive three-term subtraction
        // overflows its intermediate sum to infinity for DoubleNum. Combining
        // all operands exactly before the final factory narrowing must publish
        // the true increment and clipping scale.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(-1e308).build();
        CusumIndicator cusum = new CusumIndicator(new MockIndicator(series, 0, numOf(-1e308)), 1.7e308, 1.7e308);

        Num value = cusum.getValue(0);

        assertTrue(Num.isFinite(value));
        assertNumEquals(numOf(1e308), value, 1e308 * 1e-9);
    }

    @Test
    public void rawOutOfRangeParametersCancelBeforeFinalNarrowing() {
        BigDecimal allowance = new BigDecimal("1e400");
        BigDecimal targetMean = allowance.add(new BigDecimal("1e308"));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0).build();
        CusumIndicator cusum = new CusumIndicator(new ClosePriceIndicator(series), targetMean, allowance);

        // Narrowing each raw parameter first collapses both to the same ceiling
        // under DoubleNum and incorrectly yields zero. Their exact difference is
        // representable on both the DoubleNum and DecimalNum test factories.
        assertNumEquals(numFactory.numOf(new BigDecimal("1e308")), cusum.getValue(0));
        assertEquals(targetMean.toPlainString(), cusum.toDescriptor().getParameters().get("targetMean").toString());
        assertEquals(allowance.toPlainString(), cusum.toDescriptor().getParameters().get("allowance").toString());
        assertIndicatorRoundTrips(series, cusum, stableIndexes(series));
    }

    @Test
    public void finiteTargetMeanBeyondFactoryRangeSaturatesInsteadOfRejecting() {
        // targetMean = 1e400 is finite but overflows a primitive-backed
        // factory; only genuinely non-finite sources should be rejected. The
        // deviation scales up to and saturates at the representation ceiling,
        // so the valid extreme target must be accepted and publish a finite
        // positive CUSUM.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator drift = new MockIndicator(series, 0, numOf(0.010), numOf(0.010), numOf(0.010));
        CusumIndicator beyondRange = new CusumIndicator(drift, new BigDecimal("1e400"), 0.005);

        Num value = beyondRange.getValue(2);

        assertTrue(Num.isFinite(value));
        assertTrue(value.isPositive());
    }

    @Test
    public void negativeFiniteTargetMeanBeyondFactoryRangeStaysAtZero() {
        // targetMean = -1e400 is finite but overflows a primitive-backed
        // factory; saturation must preserve the raw sign. A negative extreme
        // target saturates to the negative ceiling, so every deviation is
        // hugely negative and the CUSUM clamps at zero instead of flipping to
        // the positive ceiling and reporting a maximum positive shift.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator drift = new MockIndicator(series, 0, numOf(0.010), numOf(0.010), numOf(0.010));
        CusumIndicator beyondRange = new CusumIndicator(drift, new BigDecimal("-1e400"), 0.005);

        Num value = beyondRange.getValue(2);

        assertTrue(Num.isFinite(value));
        assertNumEquals(0, value);
    }

    @Test
    public void negativeFiniteAllowanceBeyondFactoryRangeIsRejected() {
        // allowance = -1e400 saturates to the negative ceiling, which is still
        // negative and therefore fails the >= 0 validation instead of flipping
        // to the positive ceiling and being accepted.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator drift = new MockIndicator(series, 0, numOf(0.010), numOf(0.010), numOf(0.010));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CusumIndicator(drift, 0, new BigDecimal("-1e400")));

        assertEquals("allowance must be >= 0", thrown.getMessage());
    }

    @Test
    public void negativeFiniteOutlierClipFactorBeyondFactoryRangeIsRejected() {
        // outlierClipFactor = -1e400 remains exactly negative during validation,
        // so it fails the > 0 contract even when the active factory cannot
        // represent its magnitude.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CusumIndicator(series, 0, 0, new BigDecimal("-1e400"), 0.94));

        assertEquals("outlierClipFactor must be > 0", thrown.getMessage());
    }

    @Test
    public void positiveUnderflowingOutlierClipFactorKeepsRepresentableCompletedBound() {
        BigDecimal rawFactor = new BigDecimal("1e-400");
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1e308, -1).build();
        CusumIndicator cusum = new CusumIndicator(series, 0, 0, rawFactor, 0.5);

        // The factor itself underflows to zero under DoubleNum, but the completed
        // bound 1e308 * 1e-400 = 1e-92 is representable. Decimal-space
        // multiplication must narrow only that completed bound.
        assertNumEquals(numFactory.numOf(new BigDecimal("1e-92")), cusum.getValue(1));
        assertEquals(rawFactor.toPlainString(),
                cusum.toDescriptor().getParameters().get("outlierClipFactor").toString());
        assertIndicatorRoundTrips(series, cusum, stableIndexes(series));
    }

    @Test
    public void negativeOverflowingFactorIsRejectedWhenFactoryConversionWouldLoseItsSign() {
        NumFactory nanOnOverflowFactory = nanOnOverflowFactory();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(nanOnOverflowFactory).withData(1, 2).build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CusumIndicator(series, 0, 0, new BigDecimal("-1e400"), 0.5));

        assertEquals("outlierClipFactor must be > 0", thrown.getMessage());
    }

    @Test
    public void positiveOverflowingFactorUsesRawMagnitudeWithNaNOnOverflowFactory() {
        NumFactory nanOnOverflowFactory = nanOnOverflowFactory();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(nanOnOverflowFactory)
                .withData(1e-308, -1e100)
                .build();
        CusumIndicator cusum = new CusumIndicator(series, 0, 0, new BigDecimal("1e400"), 0.5);

        // The factory returns NaN for the raw factor, but the completed bound is
        // finite: 1e-308 * 1e400 = 1e92. Substituting a saturated factor would
        // instead produce a bound near one.
        BigDecimal expectedBound = ExactDecimalArithmetic.exactValueOf(series.getBar(0).getClosePrice())
                .multiply(new BigDecimal("1e400"));
        assertNumEquals(nanOnOverflowFactory.numOf(expectedBound), cusum.getValue(1));
    }

    @Test
    public void reanchorsAfterRetainedHeadPrunes() {
        // Retained-head pruning invalidates the recursion: the CUSUM must
        // reseed at the new head instead of publishing values cached against
        // the discarded prefix.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1).build();
        CusumIndicator pruned = new CusumIndicator(new MockIndicator(series, 0, numOf(1), numOf(1), numOf(1), numOf(1)),
                2, 0, 3.0, 0.94);

        // S = [1, 2, 3, 4]: the recursion is fully cached across the series.
        assertNumEquals(4, pruned.getValue(3));

        series.setMaximumBarCount(1);

        // index 3 is the new head: the deviation of the single retained bar is
        // 2 - 1 - 0 = 1, so the reseeded CUSUM is 1 (not the stale cached 4).
        assertNumEquals(1, pruned.getValue(3));
    }

    @Test
    public void removedIndexReadAnchorsAtRetainedHead() {
        // Reading a pruned index maps to the synthetic zero evaluation; it must
        // anchor at the retained head instead of recursing into removed bars
        // (StackOverflowError before the fix).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1).build();
        CusumIndicator pruned = new CusumIndicator(new MockIndicator(series, 0, numOf(1), numOf(1), numOf(1), numOf(1)),
                2, 0, 3.0, 0.94);
        pruned.getValue(3);
        series.setMaximumBarCount(1);

        assertNumEquals(1, pruned.getValue(0));
    }

    @Test
    public void subnormalScaleKeepsConstantSubnormalIncrement() {
        // A constant Double.MIN_VALUE increment at scaleDecay 0.5 must keep the
        // winsorization scale at MIN_VALUE: both convex operands round to zero
        // and the collapsed scale zeroes the clip bound, stalling the CUSUM at
        // 2 * MIN_VALUE instead of growing by MIN_VALUE per bar.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 0, 0, 0).build();
        CusumIndicator cusum = new CusumIndicator(new MockIndicator(series, 0, numOf(0), numOf(0), numOf(0), numOf(0)),
                Double.MIN_VALUE, 0, 3.0, 0.5);

        assertNumEquals(numOf(Double.MIN_VALUE).multipliedBy(numOf(4)), cusum.getValue(3));
    }

    @Test
    public void floatSubnormalScaleRoundsOnTheFloatGrid() {
        NumFactory floatFactory = FloatNumFactory.getInstance();
        Num minimum = floatFactory.numOf(Float.MIN_VALUE);
        Num negativeMinimum = minimum.multipliedBy(floatFactory.minusOne());
        Num negativeTwiceMinimum = negativeMinimum.multipliedBy(floatFactory.two());
        Num sixMinimum = minimum.multipliedBy(floatFactory.numOf(6));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatFactory).withData(0, 0, 0).build();
        CusumIndicator cusum = new CusumIndicator(
                new MockIndicator(series, 0, floatFactory.zero(), negativeMinimum, negativeTwiceMinimum),
                minimum.bigDecimalValue(), BigDecimal.ZERO, BigDecimal.valueOf(2), BigDecimal.valueOf(0.5));

        assertNumEquals(sixMinimum, cusum.getValue(2));
    }

    @Test
    public void weightedSubnormalScaleDeltaRoundsOnTheFloatGrid() {
        // scaleDecay 0.99999 makes oneMinusScaleDecay ~1e-5: the raw scale
        // delta is normal on the float grid while delta * oneMinusScaleDecay
        // is subnormal. Recovery must key off the weighted delta; the fast
        // path publishes a one-ulp-low scale (0x01dce616) whose clip bound
        // shifts the final CUSUM to 0x3782d27, while exact recovery publishes
        // 0x3782d28.
        NumFactory floatFactory = FloatNumFactory.getInstance();
        Num first = floatFactory.numOf(-Float.intBitsToFloat(0x01dc5cfa));
        Num second = floatFactory.numOf(-Float.intBitsToFloat(0x05d213a5));
        Num third = floatFactory.numOf(-1.0f);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatFactory).withData(0, 0, 0).build();
        CusumIndicator cusum = new CusumIndicator(new MockIndicator(series, 0, first, second, third), 0, 0, 4.0,
                0.99999);

        assertEquals(0x3782d28, Float.floatToRawIntBits(cusum.getValue(2).floatValue()));
    }

    @Test
    public void deviationScaleReanchorsAfterRetainedHeadPrunes() {
        // The winsorization scale must also reseed at the new retained head:
        // a stale scale would clip the deviation against a bound derived from
        // the discarded prefix.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 5, 1, -20).build();
        CusumIndicator pruned = new CusumIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(5), numOf(1), numOf(-20)), 10, 0, 3.0, 0.94);

        // Fill the caches across the whole series.
        pruned.getValue(3);

        series.setMaximumBarCount(2);

        // beginIndex = 2. Reseeded S(2) = 10 - 1 = 9, the fresh scale at index
        // 2 is 9, so the clip bound is 27 and S(3) = 9 + min(30, 27) = 36. A
        // stale scale (8.7744 -> bound 26.3232) would yield 35.3232.
        assertNumEquals(36, pruned.getValue(3));
    }

    @Test
    public void deviationScaleOverflowKeepsWinsorizationBound() {
        // Opposite extremes overflow the scale increment (DoubleNum): the
        // scale must saturate instead of going non-finite, or the parent
        // skips winsorization and a subsequent deviation accumulates far
        // beyond its intended clip bound.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, 0, -Double.MAX_VALUE, -Double.MAX_VALUE)
                .build();
        CusumIndicator cusum = new CusumIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(0), numOf(-Double.MAX_VALUE), numOf(-Double.MAX_VALUE)),
                Double.MAX_VALUE / 2d, 0, 0.1, 0.5);

        Num value = cusum.getValue(3);

        // With the saturated finite scale the clip bound at index 3 is
        // 0.1 * 0.75 * MAX and the CUSUM stays far below MAX; with a
        // non-finite scale the unwinsorized MAX deviation pushes the
        // accumulator into the MAX saturation.
        assertTrue(Num.isFinite(value));
        assertTrue(value.isLessThan(numFactory.numOf(Double.MAX_VALUE)));
    }

    @Test
    public void descriptorRoundTripPreservesRawDecayUnderCoarsePrecision() {
        // A precision-1 factory rounds the valid decay 0.9999 to its boundary
        // 1: the descriptor must carry the raw decay, otherwise reconstruction
        // rejects the serialized scaleDecay = 1 as outside (0, 1).
        NumFactory coarseFactory = DecimalNumFactory.getInstance(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(coarseFactory).withData(10, 9, 8, 7).build();
        CusumIndicator coarse = new CusumIndicator(new ClosePriceIndicator(series), 0, 0.005, 3.0, 0.9999);

        assertEquals("0.9999", coarse.toDescriptor().getParameters().get("scaleDecay").toString());

        assertIndicatorRoundTrips(series, coarse, stableIndexes(series));
    }

    @Test
    public void saturationMagnitudeFallsBackForFloatBackedFactory() {
        // Double- and decimal-backed factories saturate at the double ceiling;
        // a factory whose backing primitive overflows that ceiling (a
        // float-backed delegate converts it to infinity) must fall back to the
        // float range so the documented finite saturation holds for every
        // delegate type.
        NumFactory floatBackedFactory = new NumFactory() {
            private final NumFactory delegate = DoubleNumFactory.getInstance();

            @Override
            public Num minusOne() {
                return delegate.minusOne();
            }

            @Override
            public Num zero() {
                return delegate.zero();
            }

            @Override
            public Num one() {
                return delegate.one();
            }

            @Override
            public Num two() {
                return delegate.two();
            }

            @Override
            public Num three() {
                return delegate.three();
            }

            @Override
            public Num hundred() {
                return delegate.hundred();
            }

            @Override
            public Num thousand() {
                return delegate.thousand();
            }

            @Override
            public Num numOf(Number number) {
                return Math.abs(number.doubleValue()) > Float.MAX_VALUE ? NaN.NaN : delegate.numOf(number);
            }

            @Override
            public Num numOf(String number) {
                return numOf(Double.valueOf(number));
            }
        };

        assertTrue(CusumIndicator.saturationMagnitude(numFactory).isGreaterThan(numOf(Float.MAX_VALUE)));
        assertNumEquals(Float.MAX_VALUE, CusumIndicator.saturationMagnitude(floatBackedFactory));
    }

    @Test
    public void underflowedSubnormalScaleRecoversRepresentableBound() {
        // A subnormal scale increment followed by a zero increment makes the
        // published DoubleNum scale zero. Its retained exact half-MIN_VALUE state
        // still produces a representable bound when multiplied by the clip factor,
        // so the next minimum deviation is admitted just as it is for DecimalNum.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, Double.MIN_VALUE, 0)
                .build();
        CusumIndicator cusum = new CusumIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(Double.MIN_VALUE), numOf(0)), Double.MIN_VALUE, 0, 3.0,
                0.5);

        assertNumEquals(numOf(Double.MIN_VALUE).multipliedBy(numOf(2)), cusum.getValue(2));
    }

    @Test
    public void stalledSubnormalScaleRoundsOnceBeforeClipping() {
        // Deviations [MIN_VALUE, 2 * MIN_VALUE, 3 * MIN_VALUE] with clip 2
        // leave the second value on the initial bound. At index 1, the scale
        // update has previous MIN_VALUE and increment 2 * MIN_VALUE: its
        // difference product rounds 0.5 * MIN_VALUE to zero. The former
        // fallback rounded the convex products separately and retained
        // MIN_VALUE instead of fl(1.5 * MIN_VALUE) = 2 * MIN_VALUE. The
        // corrected scale admits the final 3 * MIN_VALUE deviation, producing
        // a cumulative CUSUM of 6 * MIN_VALUE instead of 5 * MIN_VALUE.
        Num minimum = numOf(new BigDecimal(Double.MIN_VALUE));
        Num twiceMinimum = minimum.multipliedBy(numFactory.two());
        Num negativeMinimum = minimum.multipliedBy(numFactory.minusOne());
        Num negativeTwiceMinimum = twiceMinimum.multipliedBy(numFactory.minusOne());
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, -Double.MIN_VALUE, -2 * Double.MIN_VALUE)
                .build();
        CusumIndicator cusum = new CusumIndicator(
                new MockIndicator(series, 0, numFactory.zero(), negativeMinimum, negativeTwiceMinimum),
                minimum.bigDecimalValue(), BigDecimal.ZERO, BigDecimal.valueOf(2), BigDecimal.valueOf(0.5));

        Num firstDeviation = minimum.minus(negativeMinimum);
        Num secondDeviation = minimum.minus(negativeTwiceMinimum);
        Num expected = minimum.plus(firstDeviation).plus(secondDeviation);

        assertNumEquals(expected, cusum.getValue(2));
    }

    @Test
    public void nonStallingSubnormalScaleRoundsOnceBeforeClipping() {
        // Deviations [MIN_VALUE, 4 * MIN_VALUE, 9 * MIN_VALUE] with clip 4
        // make the index-1 scale update non-stalling: the difference form
        // publishes 3 * MIN_VALUE although the exact combination 2.5 *
        // MIN_VALUE rounds once to 2 * MIN_VALUE for DoubleNum. The corrected
        // bound clips the final deviation to 8 * MIN_VALUE, producing 13 *
        // MIN_VALUE. DecimalNum retains 2.5 * MIN_VALUE, so it admits the
        // final deviation and has its own exact 16-digit expected sequence.
        Num minimum = numOf(new BigDecimal(Double.MIN_VALUE));
        Num threeMinimum = minimum.multipliedBy(numFactory.three());
        Num eightMinimum = minimum.multipliedBy(numOf(8));
        Num negativeThreeMinimum = threeMinimum.multipliedBy(numFactory.minusOne());
        Num negativeEightMinimum = eightMinimum.multipliedBy(numFactory.minusOne());
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, -3 * Double.MIN_VALUE, -8 * Double.MIN_VALUE)
                .build();
        CusumIndicator cusum = new CusumIndicator(
                new MockIndicator(series, 0, numFactory.zero(), negativeThreeMinimum, negativeEightMinimum),
                minimum.bigDecimalValue(), BigDecimal.ZERO, BigDecimal.valueOf(4), BigDecimal.valueOf(0.5));

        Num firstDeviation = minimum.minus(negativeThreeMinimum);
        Num secondDeviation = minimum.minus(negativeEightMinimum);
        Num expected;
        if (numFactory instanceof DoubleNumFactory) {
            expected = minimum.plus(firstDeviation).plus(eightMinimum);
        } else {
            Num initialBound = minimum.multipliedBy(numOf(4));
            expected = minimum.plus(initialBound).plus(secondDeviation);
        }

        assertNumEquals(expected, cusum.getValue(2));
    }

    @Test
    public void decimalSubdoubleScaleDecayPreservesItsExactWeightDuringRecovery() {
        NumFactory precise = DecimalNumFactory.getInstance(500);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precise).withData(0, 0, 0).build();
        CusumIndicator cusum = new CusumIndicator(
                new MockIndicator(series, 0, precise.numOf(new BigDecimal("-1E-1000")),
                        precise.numOf(new BigDecimal("-2E-1000")), precise.numOf(new BigDecimal("-3E-1000"))),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("1E-400"));

        Num expectedScale = precise.numOf(new BigDecimal("2E-1000").subtract(new BigDecimal("1E-1400")));
        Num expected = precise.numOf(new BigDecimal("2E-1000").add(expectedScale.bigDecimalValue()));

        assertNumEquals(expected, cusum.getValue(2));
    }

    @Test
    public void scaleRecoveryDerivesDecayFromAppliedComplement() {
        // At precision 1, the stored decay is 0.9 while the applied complement
        // is 0.06. A scale update from 9E-400 to 6E-400 must keep the scale at
        // 9E-400; separately multiplying stored 0.9 and 0.06 reduces it to
        // 8E-400, clips the next admission at 7E-399, and leaves 9E-399 rather
        // than the correct 1E-398 CUSUM.
        NumFactory precisionOne = DecimalNumFactory.getInstance(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionOne).withData(0, 0, 0).build();
        MockIndicator source = new MockIndicator(series, 0, precisionOne.numOf(new BigDecimal("-9E-400")),
                precisionOne.numOf(new BigDecimal("-6E-400")), precisionOne.numOf(new BigDecimal("-1E-398")));
        CusumIndicator cusum = new CusumIndicator(source, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(9),
                new BigDecimal("0.94"));

        assertNumEquals(precisionOne.numOf(new BigDecimal("1E-398")), cusum.getValue(2));
    }

    @Test
    public void barSeriesConstructorsMonitorClosePrice() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        CusumIndicator threeArg = new CusumIndicator(series, 0, 0.005);
        CusumIndicator threeArgReference = new CusumIndicator(new ClosePriceIndicator(series), 0, 0.005);
        CusumIndicator fiveArg = new CusumIndicator(series, 0, 0.005, 3.0, 0.94);
        CusumIndicator fiveArgReference = new CusumIndicator(new ClosePriceIndicator(series), 0, 0.005, 3.0, 0.94);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(threeArgReference.getValue(i), threeArg.getValue(i));
            assertNumEquals(fiveArgReference.getValue(i), fiveArg.getValue(i));
        }
    }

    @Test
    public void concurrentPruneDoesNotDeadlockRecursiveRead() throws Exception {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        series.setMaximumBarCount(4);
        CountDownLatch calculationStarted = new CountDownLatch(1);
        CountDownLatch allowCalculation = new CountDownLatch(1);
        AtomicBoolean blockCurrentIndex = new AtomicBoolean(true);
        Indicator<Num> blockingSource = new AbstractIndicator<Num>(series) {
            @Override
            public Num getValue(int index) {
                if (index == 3 && blockCurrentIndex.compareAndSet(true, false)) {
                    calculationStarted.countDown();
                    try {
                        assertTrue("recursive calculation was not released",
                                allowCalculation.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return getBarSeries().getBar(index).getClosePrice();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        CusumIndicator cusum = new CusumIndicator(blockingSource, 10, 0, 3.0, 0.5);
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "cusum-prune-regression");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch resetReaderStarted = new CountDownLatch(1);
        AtomicReference<Thread> resetReaderThread = new AtomicReference<>();

        try {
            Future<Num> recursiveRead = executor.submit(() -> cusum.getValue(3));
            assertTrue("recursive calculation never started", calculationStarted.await(5, TimeUnit.SECONDS));
            series.barBuilder().endTime(series.getLastBar().getEndTime().plusSeconds(1)).closePrice(5).add();

            Future<Num> resetRead = executor.submit(() -> {
                resetReaderThread.set(Thread.currentThread());
                resetReaderStarted.countDown();
                return cusum.getValue(4);
            });
            assertTrue("reset reader never started", resetReaderStarted.await(5, TimeUnit.SECONDS));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            Thread.State resetReaderState;
            do {
                resetReaderState = resetReaderThread.get().getState();
                if (resetReaderState == Thread.State.BLOCKED || resetReaderState == Thread.State.WAITING
                        || resetReaderState == Thread.State.TIMED_WAITING) {
                    break;
                }
                Thread.onSpinWait();
            } while (System.nanoTime() < deadline);
            assertTrue("reset reader did not contend with the recursive read", resetReaderState == Thread.State.BLOCKED
                    || resetReaderState == Thread.State.WAITING || resetReaderState == Thread.State.TIMED_WAITING);

            allowCalculation.countDown();
            assertNumEquals(21, recursiveRead.get(5, TimeUnit.SECONDS));
            assertNumEquals(26, resetRead.get(5, TimeUnit.SECONDS));
        } finally {
            allowCalculation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void reentrantPruneDuringCalculateDoesNotDoubleCountHeadDeviation() {
        // A source read inside calculate() prunes the series so that the index
        // being computed becomes the retained head. The outer computation then
        // recurses into getValue(index - 1), which re-enters the retained-head
        // reset while the cache write lock is held. The reset must defer to the
        // top-level read; otherwise the in-flight computation repopulates the
        // cache with the head deviation added twice (once by the re-anchored
        // recursion, once by the stale-prefix outer path).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1).build();
        PruningIndicator source = new PruningIndicator(series, numFactory.one(), 2);
        CusumIndicator cusum = new CusumIndicator(source, 2, 0, 3.0, 0.94);

        // After pruning, index 2 is the head with deviation 2 - 1 - 0 = 1 and a
        // single count CUSUM of 1. A double-counted bug publishes 2.
        assertNumEquals(1, cusum.getValue(2));
    }

    private static NumFactory nanOnOverflowFactory() {
        NumFactory delegate = DoubleNumFactory.getInstance();
        return new NumFactory() {

            @Override
            public Num minusOne() {
                return delegate.minusOne();
            }

            @Override
            public Num zero() {
                return delegate.zero();
            }

            @Override
            public Num one() {
                return delegate.one();
            }

            @Override
            public Num two() {
                return delegate.two();
            }

            @Override
            public Num three() {
                return delegate.three();
            }

            @Override
            public Num hundred() {
                return delegate.hundred();
            }

            @Override
            public Num thousand() {
                return delegate.thousand();
            }

            @Override
            public Num numOf(Number number) {
                return Double.isFinite(number.doubleValue()) ? delegate.numOf(number) : NaN.NaN;
            }

            @Override
            public Num numOf(String number) {
                return numOf(new BigDecimal(number));
            }
        };
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new CusumIndicator(close, 0, 0.005), stableIndexes(series)));
    }

    /**
     * A source indicator that prunes its series to {@code maximumBarCount} bars on
     * the first read, simulating a mid-computation window shrink.
     */
    private static final class PruningIndicator implements Indicator<Num> {

        private final BarSeries series;
        private final Num value;
        private final int maximumBarCount;
        private boolean pruned;

        PruningIndicator(BarSeries series, Num value, int maximumBarCount) {
            this.series = series;
            this.value = value;
            this.maximumBarCount = maximumBarCount;
        }

        @Override
        public Num getValue(int index) {
            if (!pruned) {
                pruned = true;
                series.setMaximumBarCount(maximumBarCount);
            }
            return value;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
