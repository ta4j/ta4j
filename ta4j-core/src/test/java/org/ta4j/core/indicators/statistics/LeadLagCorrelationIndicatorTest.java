/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator.LagSelectionPolicy;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator.Point;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator.Profile;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

public class LeadLagCorrelationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int SINE_PERIOD = 16;

    public LeadLagCorrelationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void warmUpIsOffsetFromRetainedSeriesHead() {
        // With a dropped head the unstable-bar count is relative to the
        // retained begin index: absolute indexes 10..12 must stay undefined
        // even though the underlying values themselves are finite, and the
        // first stable window ends at index 13.
        BarSeries series = series(20);
        series.setMaximumBarCount(10);
        List<Num> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            values.add(numFactory.numOf(i));
        }
        Indicator<Num> unstable = new MockIndicator(series, 2, values);
        LeadLagCorrelationIndicator leadLag = new LeadLagCorrelationIndicator(unstable, unstable, 2, 0, 0,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        assertTrue(leadLag.getValue(10).isNaN());
        assertTrue(leadLag.getValue(11).isNaN());
        assertTrue(leadLag.getValue(12).isNaN());
        assertNumEquals(numFactory.one(), leadLag.getValue(13), 1.0e-12);
    }

    @Test
    public void firstSignalLeadingSecondByExactlyKIsSelected() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);
        Indicator<Num> second = sine(series, -3);

        Profile profile = profile(first, second, 31, 8, -5, 5);

        assertEquals(OptionalInt.of(3), profile.selectedLag());
        assertEquals(List.of(3), profile.bestLags());
        assertNumEquals(numFactory.numOf(1), profile.selectedCorrelation(), 1.0e-12);
        assertTrue(profile.points().stream().allMatch(Point::isDefined));
    }

    @Test
    public void firstSignalTrailingSecondByExactlyKIsSelected() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, -3);
        Indicator<Num> second = sine(series, 0);

        Profile profile = profile(first, second, 31, 8, -5, 5);

        assertEquals(OptionalInt.of(-3), profile.selectedLag());
        assertEquals(List.of(-3), profile.bestLags());
        assertNumEquals(numFactory.numOf(1), profile.selectedCorrelation(), 1.0e-12);
    }

    @Test
    public void zeroLagRelationshipIsSelectedForIdenticalSignals() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);

        Profile profile = profile(first, first, 31, 8, -4, 4);

        assertEquals(OptionalInt.of(0), profile.selectedLag());
        assertEquals(List.of(0), profile.bestLags());
        assertNumEquals(numFactory.numOf(1), profile.selectedCorrelation(), 1.0e-12);
    }

    @Test
    public void floatBackedPerfectCorrelationClampsRoundedExcessToTheBound() {
        // Review regression: a perfectly correlated float pair can report a
        // Pearson coefficient one ULP above 1 (1.0000001192092896 for
        // {3.3, 1.0} vs {2.97, 0.9}); the Point validation previously
        // compared it against a double-sized 1e-12 tolerance and rejected
        // the profile. The evaluator clamps its own rounding excursions to
        // the mathematical bound, so the selected correlation settles on
        // exactly 1 while the Point record keeps only metric-precision
        // slack (FloatNumFactory epsilon is 1e-5).
        NumFactory floatFactory = FloatNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatFactory)
                .withData(new double[] { 3.3, 1.0, 3.3, 1.0 })
                .build();
        List<Num> firstValues = new ArrayList<>(4);
        List<Num> secondValues = new ArrayList<>(4);
        firstValues.add(floatFactory.numOf(3.3));
        firstValues.add(floatFactory.numOf(1.0));
        firstValues.add(floatFactory.numOf(3.3));
        firstValues.add(floatFactory.numOf(1.0));
        secondValues.add(floatFactory.numOf(2.97));
        secondValues.add(floatFactory.numOf(0.9));
        secondValues.add(floatFactory.numOf(2.97));
        secondValues.add(floatFactory.numOf(0.9));
        Indicator<Num> first = new MockIndicator(series, firstValues);
        Indicator<Num> second = new MockIndicator(series, secondValues);

        Profile profile = profile(first, second, 3, 2, -1, 1);

        Num selected = profile.selectedCorrelation();
        assertFalse(selected.isNaN());
        // The float correlation rounds one ULP above the mathematical bound;
        // the evaluator clamps the excursion instead of inflating the bound.
        assertNumEquals(floatFactory.one(), selected, 0);
    }

    @Test
    public void longPerfectAffineFloatSeriesClampsAccumulationNoiseToTheBound() {
        // Review regression: an inexact affine transform of a long float
        // series (10,000 samples, y = 0.1x - 0.7 in float arithmetic) is
        // mathematically correlated to about 1 - 2e-13, but the variance and
        // covariance sums accumulate one rounding per aligned sample and push
        // the Pearson coefficient about 1.13e-5 above one, far beyond the
        // factory epsilon (1.19e-7). The evaluator clamps the excursion to
        // the mathematical bound, so the profile reports exactly 1 while the
        // Point record's validation stays at the bare epsilon instead of
        // scaling with the accumulation size.
        NumFactory floatFactory = FloatNumFactory.getInstance();
        int sampleCount = 10_000;
        double[] seriesData = new double[sampleCount];
        List<Num> firstValues = new ArrayList<>(sampleCount);
        List<Num> secondValues = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            double x = 1.0e6 + (i % 7 - 3);
            seriesData[i] = i;
            firstValues.add(floatFactory.numOf(x));
            secondValues.add(floatFactory.numOf(0.1 * x - 0.7));
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatFactory).withData(seriesData).build();
        Indicator<Num> first = new MockIndicator(series, firstValues);
        Indicator<Num> second = new MockIndicator(series, secondValues);
        LeadLagCorrelationIndicator indicator = new LeadLagCorrelationIndicator(first, second, sampleCount, 0, 0,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        Num correlation = indicator.getValue(series.getEndIndex());

        assertFalse(correlation.isNaN());
        // The accumulation noise (about 1.13e-5 above one) is clamped to the
        // mathematical bound, exactly as the evaluator owns its roundoff and
        // the public record keeps only metric-precision slack.
        assertNumEquals(floatFactory.one(), correlation, 0);
    }

    @Test
    public void signedAndAbsolutePoliciesSelectDifferentLags() {
        BarSeries series = series(40);
        // Square wave with period 4: half-period negation is exact, so
        // correlations are exactly +1, -1, or 0 over any 8-bar (two-period)
        // window. second[t] = square(t - 2) => corr(lag) = +1 at lag {-2, 2},
        // -1 at lag {-4, 0, 4}, and 0 elsewhere in the scanned range.
        Indicator<Num> first = square(series);
        Indicator<Num> second = square(series, 2);

        Profile signed = profile(first, second, 31, 8, -5, 5, LagSelectionPolicy.MAXIMUM_CORRELATION);
        assertEquals(List.of(-2, 2), signed.bestLags());
        assertEquals(OptionalInt.of(-2), signed.selectedLag());
        assertNumEquals(numFactory.numOf(1), signed.selectedCorrelation(), 1.0e-12);

        Profile absolute = profile(first, second, 31, 8, -5, 5, LagSelectionPolicy.MAXIMUM_ABSOLUTE_CORRELATION);
        assertEquals(List.of(-4, -2, 0, 2, 4), absolute.bestLags());
        assertEquals(OptionalInt.of(0), absolute.selectedLag());
        // The original signed correlation is preserved under absolute selection.
        assertNumEquals(numFactory.numOf(-1), absolute.selectedCorrelation(), 1.0e-12);
    }

    @Test
    public void tieBreakPrefersSmallestAbsoluteThenSmallestSignedLag() {
        BarSeries series = series(40);
        // second[t] = square(t - 2): correlation +1 at lag -2 and lag 2, -1 at
        // lag 0. The symmetric {2, -2} tie resolves to the smaller signed lag.
        Indicator<Num> first = square(series);
        Indicator<Num> second = square(series, 2);

        Profile profile = profile(first, second, 31, 8, -3, 3);

        assertEquals(List.of(-2, 2), profile.bestLags());
        assertEquals(OptionalInt.of(-2), profile.selectedLag());
        assertNumEquals(numFactory.numOf(1), profile.selectedCorrelation(), 1.0e-12);
    }

    @Test
    public void periodicTiesReturnAllBestLagsInAscendingOrder() {
        BarSeries series = series(40);
        Indicator<Num> first = square(series);

        Profile profile = profile(first, first, 31, 8, -8, 8);

        assertEquals(List.of(-8, -4, 0, 4, 8), profile.bestLags());
        assertEquals(OptionalInt.of(0), profile.selectedLag());
    }

    @Test
    public void zeroVarianceLagsAreUndefinedAndNeverSelected() {
        BarSeries series = series(40);
        Indicator<Num> constant = indicator(series, constantValues(40));
        Indicator<Num> changing = sine(series, 0);

        Profile profile = profile(constant, changing, 31, 8, -3, 3);

        assertEquals(7, profile.points().size());
        for (Point point : profile.points()) {
            assertFalse(point.isDefined());
            assertTrue(point.correlation().isNaN());
            assertEquals(8, point.sampleCount());
        }
        assertTrue(profile.bestLags().isEmpty());
        assertEquals(OptionalInt.empty(), profile.selectedLag());
        assertTrue(profile.selectedCorrelation().isNaN());
    }

    @Test
    public void insufficientHistoryLagsRemainUndefined() {
        BarSeries series = series(6);
        Indicator<Num> first = indicator(series, 1, 2, 3, 4, 5, 6);
        Indicator<Num> second = indicator(series, 1, 2, 3, 4, 5, 6);

        Profile profile = profile(first, second, 5, 5, -4, 4);

        for (Point point : profile.points()) {
            if (point.lag() < -1 || point.lag() > 1) {
                assertFalse("lag " + point.lag() + " should be undefined", point.isDefined());
                assertEquals(0, point.sampleCount());
            } else {
                assertTrue("lag " + point.lag() + " should be defined", point.isDefined());
                assertEquals(5, point.sampleCount());
            }
        }
    }

    @Test
    public void constrainedSeriesKeepsUnavailableLagsUndefined() {
        BarSeries series = series(40);
        Indicator<Num> first = square(series);
        series.setMaximumBarCount(30);

        Profile profile = profile(first, first, 39, 8, -30, 30);

        // Lags whose windows start below the constrained begin index (10) are
        // undefined with zero samples: second windows for lags <= -23 and first
        // windows for lags >= 23.
        assertFalse(profile.points().get(0).isDefined());
        assertEquals(0, profile.points().get(0).sampleCount());
        assertFalse(profile.points().get(profile.points().size() - 1).isDefined());
        for (Point point : profile.points()) {
            if (point.lag() <= -23 || point.lag() >= 23) {
                assertFalse("lag " + point.lag() + " should be undefined", point.isDefined());
                assertEquals(0, point.sampleCount());
            } else {
                assertTrue("lag " + point.lag() + " should be defined", point.isDefined());
                assertEquals(8, point.sampleCount());
            }
        }
        assertEquals(List.of(-20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20), profile.bestLags());
        assertEquals(OptionalInt.of(0), profile.selectedLag());
    }

    @Test
    public void getValueReturnsTheSelectedCorrelation() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);
        Indicator<Num> second = sine(series, -3);

        LeadLagCorrelationIndicator indicator = indicator(first, second, 8, -5, 5);

        Profile profile = indicator.getProfile(31);
        assertNumEquals(profile.selectedCorrelation(), indicator.getValue(31), 1.0e-12);
        assertTrue(indicator.getValue(31).isGreaterThan(numFactory.numOf(0.99)));
    }

    @Test
    public void getValueIsNaNUntilEveryLagWindowIsAvailable() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);
        Indicator<Num> second = sine(series, -3);

        LeadLagCorrelationIndicator indicator = indicator(first, second, 8, -5, 5);

        // The worst lag bounds the indicator: max(5, 0) unstable bars plus
        // barCount - 1, i.e. laggedUnstableBars(8, 5) = laggedUnstableBars(8, -5)
        // = 12. The profile below the boundary is defined for inner lags only,
        // so the indicator stays NaN until the full range is available.
        assertEquals(12, indicator.getCountOfUnstableBars());
        assertTrue(indicator.getValue(11).isNaN());
        assertTrue(indicator.getValue(12).isNaN() == false);
    }

    @Test
    public void warmUpBoundaryHonorsTheWorstLag() {
        BarSeries series = series(40);
        // Finite values during warm-up: only the unstable-bar boundary exposes
        // the divergence from LaggedCorrelationIndicator semantics.
        Indicator<Num> warmingUp = mockIndicator(series, 5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40);
        Indicator<Num> plain = indicator(series, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40);

        // endIndex 10 with lag 2 needs first[1..8]: the first indicator is
        // still unstable there (5 unstable bars), so LaggedCorrelationIndicator
        // is NaN and the profile must report the lag as undefined.
        Profile overlapping = profile(warmingUp, plain, 10, 8, 2, 2);
        Point point = overlapping.points().get(0);
        assertFalse(point.isDefined());
        assertEquals(0, point.sampleCount());

        // The same lag becomes defined once the window starts at the unstable
        // boundary: laggedUnstableBars = max(5, 0) + 2 + 8 - 1 = 14.
        Profile boundary = profile(warmingUp, plain, 14, 8, 2, 2);
        assertTrue(boundary.points().get(0).isDefined());
    }

    @Test
    public void rejectsLagRangeWhereMinimumExceedsMaximum() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);

        assertThrows(IllegalArgumentException.class, () -> indicator(first, first, 8, 2, 1));
    }

    @Test
    public void rejectsWindowLengthAboveSharedCeiling() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> indicator(first, first, 100_000_000, -1, 1));
        assertEquals("barCount exceeds the maximum window length", e.getMessage());
    }

    @Test
    public void rejectsLagThatCannotBeIndexedSafely() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);

        assertThrows(IllegalArgumentException.class, () -> indicator(first, first, 8, Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> indicator(first, first, 8, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    public void rejectsEndIndexOutsideTheSeries() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);

        LeadLagCorrelationIndicator indicator = indicator(first, first, 8, -1, 1);
        assertThrows(IllegalArgumentException.class, () -> indicator.getProfile(-1));
        assertThrows(IllegalArgumentException.class, () -> indicator.getProfile(40));
    }

    @Test
    public void rejectsProfileOnEmptySeries() {
        BarSeries empty = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        Indicator<Num> signal = indicator(empty);
        LeadLagCorrelationIndicator leadLag = indicator(signal, signal, 2, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> leadLag.getProfile(-1));
    }

    @Test
    public void rejectsLagRangeThatExceedsTheProfileCapacityGuard() {
        BarSeries series = series(40);
        Indicator<Num> first = square(series);

        assertThrows(IllegalArgumentException.class, () -> indicator(first, first, 8, -600_000, 600_000));
        assertThrows(IllegalArgumentException.class, () -> indicator(first, first, 8, -2_000_000_000, 2_000_000_000));
    }

    @Test
    public void rejectsIndicatorsOnDifferentSeries() {
        BarSeries firstSeries = series(40);
        BarSeries secondSeries = series(40);
        Indicator<Num> first = sine(firstSeries, 0);
        Indicator<Num> second = sine(secondSeries, 0);

        assertThrows(IllegalArgumentException.class, () -> indicator(first, second, 8, -1, 1));
    }

    @Test
    public void symmetricConvenienceConstructorCoversTheFullSignedRange() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);
        Indicator<Num> second = sine(series, -3);

        LeadLagCorrelationIndicator symmetric = new LeadLagCorrelationIndicator(first, second, 8, 5,
                LagSelectionPolicy.MAXIMUM_CORRELATION);
        Profile fromSymmetric = symmetric.getProfile(31);
        Profile fromFull = profile(first, second, 31, 8, -5, 5);

        assertEquals(fromFull, fromSymmetric);
        assertEquals(OptionalInt.of(3), fromSymmetric.selectedLag());
    }

    @Test
    public void unstableBoundarySaturatesAtMaxInt() {
        BarSeries series = series(40);
        Indicator<Num> saturated = mockIndicator(series, Integer.MAX_VALUE, constantValues(40));
        Indicator<Num> plain = indicator(series, constantValues(40));

        LeadLagCorrelationIndicator indicator = new LeadLagCorrelationIndicator(saturated, plain, 2, 0, 0,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        assertEquals(Integer.MAX_VALUE, indicator.getCountOfUnstableBars());
        assertTrue(indicator.getValue(39).isNaN());
    }

    @Test
    public void unstableBoundaryAboveIntRangeStaysUnavailable() {
        // Sources unstable through Integer.MAX_VALUE saturate the published
        // boundary; with barCount = 2 the exact worst-lag boundary is
        // MAX_VALUE + 1, which no int index can reach. Availability must use
        // the exact long boundary so the saturated count does not make the
        // window at MAX_VALUE look complete.
        BarSeries series = series(2);
        BarSeries atMaxSeries = new BaseBarSeries(series.getName(), series.getBarData()) {
            @Override
            public int getBeginIndex() {
                return 0;
            }

            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE;
            }
        };
        Indicator<Num> unstable = new AbstractIndicator<Num>(atMaxSeries) {
            @Override
            public Num getValue(int index) {
                return numFactory.numOf((index & 1) == 0 ? 1 : 2);
            }

            @Override
            public int getCountOfUnstableBars() {
                return Integer.MAX_VALUE;
            }
        };
        LeadLagCorrelationIndicator indicator = indicator(unstable, unstable, 2, 0, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        assertEquals(Integer.MAX_VALUE, indicator.getCountOfUnstableBars());
        Profile profile = indicator.getProfile(Integer.MAX_VALUE);
        assertTrue(profile.points().stream().noneMatch(Point::isDefined));
        assertTrue(indicator.getValue(Integer.MAX_VALUE).isNaN());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializesAndRestoresFromJson() {
        BarSeries series = series(40);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        LeadLagCorrelationIndicator indicator = indicator(close, average, 8, -5, 5,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, indicator.toJson());

        assertTrue(restored instanceof LeadLagCorrelationIndicator);
        assertNumEquals(indicator.getValue(31), restored.getValue(31), 1.0e-12);
        assertEquals(indicator.getProfile(31), ((LeadLagCorrelationIndicator) restored).getProfile(31));
        assertEquals(indicator.getCountOfUnstableBars(), restored.getCountOfUnstableBars());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void restoresFromDescriptorWithCanonicalEquality() {
        BarSeries series = series(40);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        LeadLagCorrelationIndicator indicator = indicator(close, average, 8, -5, 5,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        Indicator<Num> restored = (Indicator<Num>) IndicatorSerialization.fromDescriptor(series,
                indicator.toDescriptor());

        // The flattened reconstruction constructor and lag range must still
        // yield the canonical descriptor of the original indicator.
        assertTrue(restored instanceof LeadLagCorrelationIndicator);
        assertEquals(indicator.toDescriptor(), restored.toDescriptor());
        assertNumEquals(indicator.getValue(31), restored.getValue(31), 1.0e-12);
        assertEquals(indicator.getProfile(31), ((LeadLagCorrelationIndicator) restored).getProfile(31));
        assertEquals(indicator.getCountOfUnstableBars(), restored.getCountOfUnstableBars());
    }

    @Test
    public void returnsUnmodifiableLists() {
        BarSeries series = series(40);
        Indicator<Num> first = sine(series, 0);

        Profile profile = profile(first, first, 31, 8, -1, 1);

        assertThrows(UnsupportedOperationException.class, () -> profile.points().add(null));
        assertThrows(UnsupportedOperationException.class, () -> profile.bestLags().add(0));
    }

    @Test
    public void acceptsLargeTieHeavyProfiles() {
        // Validation must stay linear: a tie-heavy profile with tens of
        // thousands of lags would be unusably slow under quadratic validation.
        List<Point> points = new java.util.ArrayList<>();
        for (int lag = 0; lag < 50_000; lag++) {
            points.add(new Point(lag, numFactory.one(), 8));
        }
        List<Integer> bestLags = new java.util.ArrayList<>();
        for (int lag = 0; lag < 50_000; lag++) {
            bestLags.add(lag);
        }
        Profile profile = new Profile(100_000, 8, 0, 49_999, LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags,
                OptionalInt.of(0), numFactory.one());

        assertEquals(50_000, profile.points().size());
        assertEquals(50_000, profile.bestLags().size());
        assertEquals(OptionalInt.of(0), profile.selectedLag());
    }

    @Test
    public void profileRejectsSelectedLagOutsideBestLags() {
        // Period-4 square wave autocorrelation over an 8-bar window: exactly
        // -1 at lags -2 and 2, 0 at lags -1 and 1, and 1 at lag 0.
        List<Point> points = List.of(new Point(-2, numFactory.numOf(-1), 8), new Point(-1, numFactory.zero(), 8),
                new Point(0, numFactory.one(), 8), new Point(1, numFactory.zero(), 8),
                new Point(2, numFactory.numOf(-1), 8));
        List<Integer> bestLags = List.of(0);

        assertThrows(IllegalArgumentException.class, () -> new Profile(31, 8, -2, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(1), numFactory.one()));
    }

    @Test
    public void profileRejectsSelectionThatDoesNotMatchThePoints() {
        // A fixture with a two-way tie at lags -1 and 1 (correlation 1.0).
        List<Point> points = List.of(new Point(-2, numFactory.numOf(0.5), 8), new Point(-1, numFactory.one(), 8),
                new Point(0, numFactory.numOf(0.25), 8), new Point(1, numFactory.one(), 8),
                new Point(2, numFactory.numOf(0.5), 8));
        List<Integer> bestLags = List.of(-1, 1);

        // A best-lag list that is self-consistent but omits a maximal lag.
        assertThrows(IllegalArgumentException.class, () -> new Profile(100, 8, -2, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, List.of(-1), OptionalInt.of(-1), numFactory.one()));
        // The deterministic tie-break is the smallest absolute lag (-1), not 1.
        assertThrows(IllegalArgumentException.class, () -> new Profile(100, 8, -2, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(1), numFactory.one()));
        // The canonical selection is accepted.
        new Profile(100, 8, -2, 2, LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(-1),
                numFactory.one());
    }

    @Test
    public void profileRejectsLagRangesAboveTheCapacityGuard() {
        // Review regression: the record must enforce the same MAX_PROFILE_LAGS
        // ceiling as the indicator constructor, so a directly constructed or
        // deserialized profile cannot advertise a 1,000,001-point scan that no
        // indicator could ever produce.
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> new Profile(0, 2, 0,
                1_000_000, LagSelectionPolicy.MAXIMUM_CORRELATION, List.of(), List.of(), OptionalInt.empty(), NaN.NaN));
        assertTrue(rejected.getMessage().contains("lag range is too large"));
    }

    @Test
    public void directlyConstructedProfileRejectsUnindexableLagBounds() {
        // The indicator constructor validates each lag bound against the window
        // length; the record must apply the same per-bound guard so a directly
        // constructed profile cannot describe a scan whose shifted window
        // indexes cannot be represented safely.
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> new Profile(0, 2, Integer.MIN_VALUE, Integer.MIN_VALUE, LagSelectionPolicy.MAXIMUM_CORRELATION,
                        List.of(new Point(Integer.MIN_VALUE, NaN.NaN, 0)), List.of(), OptionalInt.empty(), NaN.NaN));
        assertTrue(rejected.getMessage().contains("absolute lag is too large for barCount"));
    }

    @Test
    public void pointRejectsNonFiniteCorrelation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Point(0, DoubleNum.valueOf(Double.POSITIVE_INFINITY), 8));
        assertThrows(IllegalArgumentException.class,
                () -> new Point(0, DoubleNum.valueOf(Double.NEGATIVE_INFINITY), 8));
    }

    @Test
    public void pointRejectsCorrelationsOutsideTheUnitInterval() {
        // A Pearson correlation is mathematically bounded to [-1, 1]; computed
        // values may exceed the bound only by rounding noise, so anything
        // beyond a small tolerance is invalid and must be rejected.
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.numOf(2.0), 8));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.numOf(-2.0), 8));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.numOf(1.00000000001), 8));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.numOf(-1.00000000001), 8));
        // Rounding-scale deviations from the bounds stay accepted.
        new Point(0, numFactory.numOf(1.0 + 1.0e-13), 8);
        new Point(0, numFactory.numOf(-1.0 - 1.0e-13), 8);
    }

    @Test
    public void pointBoundDoesNotScaleWithTheSampleCount() {
        // Review regression: scaling the tolerance with the sample count lets
        // a low-precision factory paired with a large window accept
        // impossible correlations (1.9 with 100,000 samples under a
        // 1e-5-epsilon factory, whose scaled tolerance reaches about 1.0).
        // The evaluator clamps its own accumulation roundoff, so directly
        // constructed points only get metric-precision slack at any count.
        NumFactory floatFactory = FloatNumFactory.getInstance();
        assertThrows(IllegalArgumentException.class, () -> new Point(0, floatFactory.numOf(1.9), 100_000));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, floatFactory.numOf(-1.9), 100_000));
        // Metric-precision roundoff stays accepted at the same sample count.
        new Point(0, floatFactory.numOf(1.0 + 1.0e-6), 100_000);
        new Point(0, floatFactory.numOf(-1.0 - 1.0e-6), 100_000);
    }

    @Test
    public void pointRejectsFiniteCorrelationWithFewerThanTwoSamples() {
        // A finite Pearson correlation requires at least two aligned samples.
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.one(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.one(), 1));
        // NaN correlations keep working for unavailable windows at any count.
        new Point(0, NaN.NaN, 0);
        new Point(0, NaN.NaN, 1);
        // And a valid two-sample point is accepted.
        assertEquals(2, new Point(0, numFactory.one(), 2).sampleCount());
    }

    @Test
    public void isDefinedReflectsTheCorrelation() {
        Point undefined = new Point(0, NaN.NaN, 0);
        Point defined = new Point(0, numFactory.one(), 2);

        assertFalse(undefined.isDefined());
        assertTrue(defined.isDefined());
    }

    private Profile profile(Indicator<Num> first, Indicator<Num> second, int endIndex, int barCount, int minimumLag,
            int maximumLag) {
        return profile(first, second, endIndex, barCount, minimumLag, maximumLag,
                LagSelectionPolicy.MAXIMUM_CORRELATION);
    }

    @Test
    public void pearsonSurvivesExtremeFiniteValues() {
        // The reused Pearson implementation averaged the raw window: summing
        // 1e308 and 1.1e308 overflows to infinity, turning an exactly
        // correlated window into an undefined one.
        BarSeries series = series(2);
        Indicator<Num> first = indicator(series, 1e308, 1.1e308);
        Indicator<Num> second = indicator(series, 1e308, 1.1e308);

        Profile profile = profile(first, second, 1, 2, 0, 0);

        assertEquals(OptionalInt.of(0), profile.selectedLag());
        assertNumEquals(numFactory.numOf(1), profile.selectedCorrelation(), 1.0e-12);
    }

    @Test
    public void pearsonSurvivesMixedMagnitudeSeries() {
        // Rescaling both series by one shared maximum underflows the smaller
        // series: 1 and 2 next to 1e308 and 1.1e308 scale to ~9e-309 and
        // ~1.8e-308, whose centered deviations square to zero and turn the
        // exactly correlated pair undefined. Each series must be rescaled by
        // its own maximum instead.
        BarSeries series = series(2);
        Indicator<Num> first = indicator(series, 1, 2);
        Indicator<Num> second = indicator(series, 1e308, 1.1e308);

        Profile profile = profile(first, second, 1, 2, 0, 0);

        assertEquals(OptionalInt.of(0), profile.selectedLag());
        assertNumEquals(numFactory.numOf(1), profile.selectedCorrelation(), 1.0e-12);
    }

    @Test
    public void profileRejectsPartialSampleCounts() {
        // A point whose sampleCount is neither 0 (unavailable window) nor
        // barCount (full aligned window) would compare unequal window lengths
        // across lags; this indicator never produces such a profile.
        List<Point> points = List.of(new Point(-1, NaN.NaN, 0), new Point(0, numFactory.one(), 5),
                new Point(1, NaN.NaN, 0));
        List<Integer> bestLags = List.of(0);

        assertThrows(IllegalArgumentException.class, () -> new Profile(31, 8, -1, 1,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(0), numFactory.one()));
        // 0 and barCount both remain valid: an unavailable window and a full
        // aligned window, including NaN correlations with a full count.
        List<Point> valid = List.of(new Point(-1, NaN.NaN, 0), new Point(0, numFactory.one(), 8),
                new Point(1, NaN.NaN, 8));
        new Profile(31, 8, -1, 1, LagSelectionPolicy.MAXIMUM_CORRELATION, valid, List.of(0), OptionalInt.of(0),
                numFactory.one());
    }

    @Test
    public void nearEndpointScaleSurvivesPearsonCentering() {
        // Two samples are always perfectly collinear, so the correlation is
        // exactly 1. The rescaled values are 1 and Math.nextDown(1), whose
        // exact mean is 1 - 2^-54 and is not representable, so materializing
        // it rounds to the endpoint 1 and centers the window to [0, -2^-53],
        // reporting ~0.7071 instead of the exact 1. DecimalNum's 16-digit
        // context sees the same input as [1, 0.9999999999999999], whose mean
        // rounds to 1 at that precision, so the failure is factory
        // independent.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(new double[] { 0.0, 0.0, 1.0, Math.nextDown(1.0) })
                .build();
        Indicator<Num> first = indicator(series, 0.0, 0.0, 1.0, Math.nextDown(1.0));
        Indicator<Num> second = indicator(series, 0.0, 0.0, 1.0, 0.0);
        LeadLagCorrelationIndicator leadLag = new LeadLagCorrelationIndicator(first, second, 2, 0, 0,
                LagSelectionPolicy.MAXIMUM_CORRELATION);

        assertNumEquals(numFactory.one(), leadLag.getValue(3), 1.0e-12);
    }

    private Profile profile(Indicator<Num> first, Indicator<Num> second, int endIndex, int barCount, int minimumLag,
            int maximumLag, LagSelectionPolicy policy) {
        return indicator(first, second, barCount, minimumLag, maximumLag, policy).getProfile(endIndex);
    }

    private LeadLagCorrelationIndicator indicator(Indicator<Num> first, Indicator<Num> second, int barCount,
            int minimumLag, int maximumLag) {
        return indicator(first, second, barCount, minimumLag, maximumLag, LagSelectionPolicy.MAXIMUM_CORRELATION);
    }

    private LeadLagCorrelationIndicator indicator(Indicator<Num> first, Indicator<Num> second, int barCount,
            int minimumLag, int maximumLag, LagSelectionPolicy policy) {
        return new LeadLagCorrelationIndicator(first, second, barCount, minimumLag, maximumLag, policy);
    }

    private BarSeries series(int barCount) {
        double[] raw = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            raw[i] = i + 1;
        }
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(raw).build();
    }

    private Indicator<Num> square(BarSeries series) {
        return square(series, 0);
    }

    private Indicator<Num> square(BarSeries series, int shift) {
        Number[] raw = new Number[series.getBarCount()];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = ((i + shift) % 4 < 2) ? 1 : -1;
        }
        return indicator(series, raw);
    }

    private Indicator<Num> sine(BarSeries series, int phaseOffset) {
        Number[] raw = new Number[series.getBarCount()];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = Math.sin(2.0 * Math.PI * (i + phaseOffset) / SINE_PERIOD);
        }
        return indicator(series, raw);
    }

    private Indicator<Num> indicator(BarSeries series, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, nums);
    }

    private Indicator<Num> mockIndicator(BarSeries series, int unstableBars, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, unstableBars, nums);
    }

    private static Number[] constantValues(int barCount) {
        Number[] raw = new Number[barCount];
        java.util.Arrays.fill(raw, 5);
        return raw;
    }
}
