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

import java.util.List;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.DecimalNumFactory;
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
        assertThrows(NullPointerException.class, () -> new CusumIndicator(null, 0, 0.005));
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

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new CusumIndicator(close, 0, 0.005), stableIndexes(series)));
    }
}
