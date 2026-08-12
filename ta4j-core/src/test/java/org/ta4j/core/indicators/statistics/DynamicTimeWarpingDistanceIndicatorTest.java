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
import java.util.Random;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

public class DynamicTimeWarpingDistanceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DynamicTimeWarpingDistanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static final DynamicTimeWarpingDistanceIndicator.Config DEFAULT_CONFIG = new DynamicTimeWarpingDistanceIndicator.Config(
            DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
            DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
            DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
            DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

    @Test
    public void identicalSequencesReturnZero() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1, 0);

        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(first, first, 6,
                DEFAULT_CONFIG);

        assertNumEquals(numFactory.zero(), dtw.getValue(11), 1.0e-12);
    }

    @Test
    public void radiusZeroMatchesPointwiseLocalDistance() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23);
        Indicator<Num> second = indicator(series, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24);
        DynamicTimeWarpingDistanceIndicator.Config diagonal = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(0),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(first, second, 6, diagonal);

        // A zero-radius band restricts the path to the diagonal: the raw cost is
        // the sum of pointwise absolute differences and the normalized cost is
        // the pointwise mean.
        assertNumEquals(numFactory.numOf(6), dtw.getValue(11), 1.0e-12);

        DynamicTimeWarpingDistanceIndicator.Config normalized = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(0),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);
        DynamicTimeWarpingDistanceIndicator normalizedDtw = new DynamicTimeWarpingDistanceIndicator(first, second, 6,
                normalized);
        assertNumEquals(numFactory.one(), normalizedDtw.getValue(11), 1.0e-12);
    }

    @Test
    public void timeStretchedCopyScoresBetterWithWarpingThanWithout() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> stretched = indicator(series, 0, 1, 2, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        DynamicTimeWarpingDistanceIndicator.Config plain = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(0),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);
        DynamicTimeWarpingDistanceIndicator.Config warped = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(3),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);

        Num withoutWarping = new DynamicTimeWarpingDistanceIndicator(first, stretched, 8, plain).getValue(11);
        Num withWarping = new DynamicTimeWarpingDistanceIndicator(first, stretched, 8, warped).getValue(11);

        assertTrue("warping should reduce the distance: " + withoutWarping + " vs " + withWarping,
                withWarping.isLessThan(withoutWarping));
    }

    @Test
    public void dissimilarSequencesScoreWorseThanSimilarOnes() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> similar = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> reversed = indicator(series, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0);
        DynamicTimeWarpingDistanceIndicator.Config plain = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);

        Num similarDistance = new DynamicTimeWarpingDistanceIndicator(first, similar, 6, plain).getValue(11);
        Num reversedDistance = new DynamicTimeWarpingDistanceIndicator(first, reversed, 6, plain).getValue(11);

        assertTrue(similarDistance.isLessThan(reversedDistance));
    }

    @Test
    public void isSymmetricForSymmetricLocalDistance() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> second = indicator(series, 1, 0, 3, 2, 5, 4, 7, 6, 9, 8, 11, 10);

        Num forward = new DynamicTimeWarpingDistanceIndicator(first, second, 6, DEFAULT_CONFIG).getValue(11);
        Num backward = new DynamicTimeWarpingDistanceIndicator(second, first, 6, DEFAULT_CONFIG).getValue(11);

        assertNumEquals(forward, backward, 1.0e-12);
    }

    @Test
    public void normalizedTieBreakIsSymmetricUnderReversal() {
        // Review regression: unrestricted absolute-distance DTW on (0,1,2,0)
        // versus (1,0,0,2) used to report 4/5 forward but 4/6 backward,
        // because equal-cost predecessors were resolved by the orientation-
        // dependent diagonal/vertical/horizontal order. The shorter-path
        // tie-break must make both directions agree on 4/5.
        BarSeries series = series(8);
        Indicator<Num> first = indicator(series, 0, 1, 2, 0, 0, 0, 0, 0);
        Indicator<Num> second = indicator(series, 1, 0, 0, 2, 0, 0, 0, 0);
        DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.unconstrained(),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

        Num forward = new DynamicTimeWarpingDistanceIndicator(first, second, 4, config).getValue(3);
        Num backward = new DynamicTimeWarpingDistanceIndicator(second, first, 4, config).getValue(3);

        assertNumEquals(numFactory.numOf(0.8), forward, 1.0e-12);
        assertNumEquals(forward, backward, 1.0e-12);
    }

    @Test
    public void isNeverNegative() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 3, -1, 2, 0, -2, 1, 4, -3, 2, 0, 1, -1);
        Indicator<Num> second = indicator(series, 1, 2, -2, 3, 0, -1, 2, 1, -3, 4, 0, 2);

        for (DynamicTimeWarpingDistanceIndicator.SequenceNormalization normalization : DynamicTimeWarpingDistanceIndicator.SequenceNormalization
                .values()) {
            for (DynamicTimeWarpingDistanceIndicator.LocalDistance localDistance : DynamicTimeWarpingDistanceIndicator.LocalDistance
                    .values()) {
                DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                        normalization, localDistance, DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(3),
                        DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);
                Num distance = new DynamicTimeWarpingDistanceIndicator(first, second, 6, config).getValue(11);
                assertTrue(distance.isPositive() || distance.isZero());
            }
        }
    }

    @Test
    public void zScoreNormalizationRemovesLevelDifferences() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        Indicator<Num> shifted = indicator(series, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011,
                1012);
        DynamicTimeWarpingDistanceIndicator.Config shape = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);
        DynamicTimeWarpingDistanceIndicator.Config raw = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);

        Num shapeDistance = new DynamicTimeWarpingDistanceIndicator(first, shifted, 6, shape).getValue(11);
        Num rawDistance = new DynamicTimeWarpingDistanceIndicator(first, shifted, 6, raw).getValue(11);

        assertNumEquals(numFactory.zero(), shapeDistance, 1.0e-12);
        assertTrue(rawDistance.isPositive());
    }

    @Test
    public void constantSequencesHaveZeroShapeDistanceRegardlessOfLevel() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
        Indicator<Num> second = indicator(series, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500);
        DynamicTimeWarpingDistanceIndicator.Config shape = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

        Num distance = new DynamicTimeWarpingDistanceIndicator(first, second, 6, shape).getValue(11);

        assertNumEquals(numFactory.zero(), distance, 1.0e-12);
    }

    @Test
    public void constantVersusVaryingSequenceMeasuresShapeToZero() {
        BarSeries series = series(12);
        Indicator<Num> constant = indicator(series, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
        Indicator<Num> varying = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        DynamicTimeWarpingDistanceIndicator.Config shape = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

        Num distance = new DynamicTimeWarpingDistanceIndicator(constant, varying, 6, shape).getValue(11);

        assertTrue(distance.isPositive());
    }

    @Test
    public void tinyMagnitudeWindowsZScoreLikeTheirScaledCounterparts() {
        // Squared deviations of values around 1e-200 underflow to zero in
        // double precision; without rescaling, both windows below would be
        // misclassified as constant and score a zero shape distance.
        BarSeries series = series(12);
        Indicator<Num> tinyLinear = indicator(series, 1e-200, 2e-200, 3e-200, 4e-200, 5e-200, 6e-200, 7e-200, 8e-200,
                9e-200, 1e-199, 1.1e-199, 1.2e-199);
        Indicator<Num> tinyFlat = indicator(series, 1e-200, 1e-200, 1e-200, 1e-200, 1e-200, 1e-200, 1e-200, 1e-200,
                1e-200, 1e-200, 1e-200, 1.2e-199);
        Indicator<Num> plainLinear = indicator(series, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        Indicator<Num> plainFlat = indicator(series, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 12);
        DynamicTimeWarpingDistanceIndicator.Config shape = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

        Num tinyDistance = new DynamicTimeWarpingDistanceIndicator(tinyLinear, tinyFlat, 6, shape).getValue(11);
        Num plainDistance = new DynamicTimeWarpingDistanceIndicator(plainLinear, plainFlat, 6, shape).getValue(11);

        // Z-scores are scale invariant: both pairs share the same shapes.
        assertNumEquals(plainDistance, tinyDistance, 1.0e-9);
        assertTrue(tinyDistance.isPositive());
    }

    @Test
    public void squaredRawLocalCostUnderflowReportsNaNInsteadOfZero() {
        // A nonzero delta of ~1e-200 squares to zero in double precision;
        // scoring it as identical would break the zero-means-identical
        // contract, so the cost is NaN and the distance undefined. Decimal
        // arithmetic keeps the square representable and reports the real
        // positive distance.
        BarSeries series = series(2);
        Indicator<Num> first = indicator(series, 0, 0);
        Indicator<Num> second = indicator(series, 1e-200, 1e-200);
        DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(0),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);

        Num distance = new DynamicTimeWarpingDistanceIndicator(first, second, 2, config).getValue(1);

        if (numFactory instanceof DoubleNumFactory) {
            assertTrue(distance.isNaN());
        } else {
            assertTrue(distance.isPositive());
        }
    }

    @Test
    public void widerBandNeverScoresWorseThanNarrowerBand() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> second = indicator(series, 0, 2, 1, 3, 5, 4, 6, 8, 7, 9, 11, 10);
        DynamicTimeWarpingDistanceIndicator.Config narrow = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(0),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);
        DynamicTimeWarpingDistanceIndicator.Config wide = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);

        Num narrowDistance = new DynamicTimeWarpingDistanceIndicator(first, second, 6, narrow).getValue(11);
        Num wideDistance = new DynamicTimeWarpingDistanceIndicator(first, second, 6, wide).getValue(11);

        assertTrue("wider band should not score worse", wideDistance.isLessThanOrEqual(narrowDistance));
    }

    @Test
    public void returnsNaNForUnavailableOrNonFiniteWindows() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> second = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(first, second, 6,
                DEFAULT_CONFIG);

        // Index below the unstable-bar boundary (barCount - 1 = 5).
        assertTrue(dtw.getValue(4).isNaN());
        assertTrue(dtw.getValue(0).isNaN());
        // Exactly at the boundary the stable path is taken: the first window
        // [0..5] holds identical values, so the z-score distance is zero.
        assertNumEquals(numFactory.zero(), dtw.getValue(5), 1.0e-12);

        // Non-finite window values poison the result.
        List<Num> nonFinite = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            nonFinite.add(numFactory.numOf(i));
        }
        // Index 8 lies inside the evaluated window [6..11].
        nonFinite.set(8, org.ta4j.core.num.NaN.NaN);
        Indicator<Num> poisoned = new MockIndicator(series, nonFinite);
        DynamicTimeWarpingDistanceIndicator poisonedDtw = new DynamicTimeWarpingDistanceIndicator(first, poisoned, 6,
                DEFAULT_CONFIG);
        assertTrue(poisonedDtw.getValue(11).isNaN());
    }

    @Test
    public void unstableBarCountCoversBothIndicatorsAndWindow() {
        BarSeries series = series(12);
        Indicator<Num> first = mockIndicator(series, 3, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> second = mockIndicator(series, 2, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(first, second, 6,
                DEFAULT_CONFIG);

        assertEquals(3 + 6 - 1, dtw.getCountOfUnstableBars());

        // One bar before the boundary the first indicator's window [2..7] still
        // reaches into its unstable region, so the distance is undefined...
        assertTrue(dtw.getValue(7).isNaN());
        // ...and exactly at the boundary the composed window [3..8] is fully
        // stable for both indicators: the identical values score zero.
        assertNumEquals(numFactory.zero(), dtw.getValue(8), 1.0e-12);
    }

    @Test
    public void rejectsInvalidConfiguration() {
        BarSeries series = series(12);
        Indicator<Num> first = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

        assertThrows(NullPointerException.class, () -> new DynamicTimeWarpingDistanceIndicator(first, first, 6, null));
        assertThrows(NullPointerException.class,
                () -> new DynamicTimeWarpingDistanceIndicator.Config(null,
                        DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                        DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(1),
                        DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE));
        assertThrows(NullPointerException.class,
                () -> new DynamicTimeWarpingDistanceIndicator.Config(
                        DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE, null,
                        DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(1),
                        DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE));
        assertThrows(NullPointerException.class,
                () -> new DynamicTimeWarpingDistanceIndicator.Config(
                        DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                        DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE, null,
                        DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE));
        assertThrows(NullPointerException.class,
                () -> new DynamicTimeWarpingDistanceIndicator.Config(
                        DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                        DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                        DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new DynamicTimeWarpingDistanceIndicator.WarpingWindow(1, true));
        assertThrows(IllegalArgumentException.class,
                () -> new DynamicTimeWarpingDistanceIndicator(first, first, 1, DEFAULT_CONFIG));
    }

    @Test
    public void rejectsIndicatorsOnDifferentSeries() {
        BarSeries firstSeries = series(12);
        BarSeries secondSeries = series(12);
        Indicator<Num> first = indicator(firstSeries, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        Indicator<Num> second = indicator(secondSeries, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

        assertThrows(IllegalArgumentException.class,
                () -> new DynamicTimeWarpingDistanceIndicator(first, second, 6, DEFAULT_CONFIG));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializesAndRestoresFromJson() {
        BarSeries series = series(12);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(close, average, 4,
                new DynamicTimeWarpingDistanceIndicator.Config(
                        DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                        DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                        DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(2),
                        DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH));

        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, dtw.toJson());

        assertTrue(restored instanceof DynamicTimeWarpingDistanceIndicator);
        assertNumEquals(dtw.getValue(6), restored.getValue(6), 1.0e-12);
        assertEquals(dtw.getCountOfUnstableBars(), restored.getCountOfUnstableBars());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void restoresFromDescriptorWithCanonicalEquality() {
        BarSeries series = series(12);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(close, average, 4,
                new DynamicTimeWarpingDistanceIndicator.Config(
                        DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                        DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                        DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(2),
                        DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH));

        Indicator<Num> restored = (Indicator<Num>) IndicatorSerialization.fromDescriptor(series, dtw.toDescriptor());

        // The flattened reconstruction constructor and transient config must
        // still yield the canonical descriptor of the original indicator.
        assertTrue(restored instanceof DynamicTimeWarpingDistanceIndicator);
        assertEquals(dtw.toDescriptor(), restored.toDescriptor());
        assertNumEquals(dtw.getValue(6), restored.getValue(6), 1.0e-12);
        assertEquals(dtw.getCountOfUnstableBars(), restored.getCountOfUnstableBars());
    }

    @Test
    public void extremeFiniteValuesZScoreWithoutOverflow() {
        BarSeries series = series(12);
        // Squared deviations of values at +/-1e308 would overflow the double
        // range; rescaling before the moments keeps the z-score finite under
        // both Num factories, so the distance is a number, not NaN.
        Indicator<Num> extreme = indicator(series, 1e308, -1e308, 1e308, -1e308, 1e308, -1e308, 1e308, -1e308, 1e308,
                -1e308, 1e308, -1e308);
        Indicator<Num> plain = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

        DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(5),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(extreme, plain, 6, config);
        assertFalse(dtw.getValue(11).isNaN());
        assertTrue(dtw.getValue(11).isGreaterThanOrEqual(numFactory.zero()));
        // Identical extreme windows score zero shape distance.
        DynamicTimeWarpingDistanceIndicator same = new DynamicTimeWarpingDistanceIndicator(extreme, extreme, 6, config);
        assertNumEquals(numFactory.zero(), same.getValue(11), 1.0e-12);
    }

    @Test
    public void diagonalTieBreakPrefersTheDiagonalPredecessor() {
        BarSeries series = series(4);
        // Window [2..3] carries the discriminating pair; leading bars are filler.
        Indicator<Num> first = indicator(series, 5, 5, 0, 1);
        Indicator<Num> second = indicator(series, 5, 5, 0, 0);
        DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE,
                DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE,
                DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(2),
                DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

        // Local costs: c(0,0)=0, c(0,1)=0, c(1,0)=1, c(1,1)=1, so the
        // accumulated D(0,0)=0, D(0,1)=0, D(1,0)=1. At cell (1,1) the diagonal
        // and vertical predecessors both cost 1 over a path length of 2, while
        // the horizontal predecessor costs 2; the deterministic tie-break must
        // take the diagonal, so the path-length-normalized distance is 1/2
        // rather than the horizontal path's 1.
        Num distance = new DynamicTimeWarpingDistanceIndicator(first, second, 2, config).getValue(3);
        assertNumEquals(numFactory.numOf(0.5), distance, 1.0e-12);
    }

    @Test
    public void matchesBruteForceMinimumOverAllMonotonicPaths() {
        Random random = new Random(20260809L);
        for (int trial = 0; trial < 20; trial++) {
            int windowSize = 3 + random.nextInt(3);
            double[] firstValues = new double[windowSize];
            double[] secondValues = new double[windowSize];
            for (int i = 0; i < windowSize; i++) {
                firstValues[i] = random.nextInt(9) - 4;
                secondValues[i] = random.nextInt(9) - 4;
            }
            BarSeries series = series(windowSize);
            Indicator<Num> first = indicator(series, firstValues);
            Indicator<Num> second = indicator(series, secondValues);
            int radius = trial % 3;
            // The exhaustive oracle is the ground truth for the minimum TOTAL
            // path cost; path-length-normalized costs are checked against the
            // full-matrix reference instead, because minimizing total cost and
            // minimizing the cost/length ratio are different objectives.
            DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                    trial % 2 == 0 ? DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE
                            : DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                    trial % 2 == 0 ? DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE
                            : DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                    DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(radius),
                    DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE);

            Num production = new DynamicTimeWarpingDistanceIndicator(first, second, windowSize, config)
                    .getValue(windowSize - 1);
            BruteForceResult oracle = bruteForceMinimum(firstValues, secondValues, radius, config);
            assertNumEquals(oracle.cost, production, 1.0e-9);
        }
    }

    @Test
    public void matchesFullMatrixDynamicProgrammingAcrossConfigs() {
        Random random = new Random(454L);
        for (int trial = 0; trial < 10; trial++) {
            int windowSize = 5 + random.nextInt(3);
            double[] firstValues = new double[windowSize];
            double[] secondValues = new double[windowSize];
            for (int i = 0; i < windowSize; i++) {
                firstValues[i] = random.nextInt(9) - 4;
                secondValues[i] = random.nextInt(9) - 4;
            }
            BarSeries series = series(windowSize);
            Indicator<Num> first = indicator(series, firstValues);
            Indicator<Num> second = indicator(series, secondValues);
            boolean unconstrained = trial % 3 == 2;
            DynamicTimeWarpingDistanceIndicator.WarpingWindow window = unconstrained
                    ? DynamicTimeWarpingDistanceIndicator.WarpingWindow.unconstrained()
                    : DynamicTimeWarpingDistanceIndicator.WarpingWindow.sakoeChiba(trial % 3);
            DynamicTimeWarpingDistanceIndicator.Config config = new DynamicTimeWarpingDistanceIndicator.Config(
                    trial % 2 == 0 ? DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE
                            : DynamicTimeWarpingDistanceIndicator.SequenceNormalization.Z_SCORE,
                    trial % 2 == 0 ? DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE
                            : DynamicTimeWarpingDistanceIndicator.LocalDistance.SQUARED,
                    window, trial % 2 == 0 ? DynamicTimeWarpingDistanceIndicator.PathCostNormalization.NONE
                            : DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH);

            Num production = new DynamicTimeWarpingDistanceIndicator(first, second, windowSize, config)
                    .getValue(windowSize - 1);
            BruteForceResult oracle = fullMatrixDp(firstValues, secondValues, window, config);

            assertNumEquals(oracle.cost, production, 1.0e-9);
        }
    }

    private static final class BruteForceResult {

        final Num cost;

        BruteForceResult(Num cost) {
            this.cost = cost;
        }
    }

    /**
     * Exhaustively enumerates every monotonic path inside the band and returns the
     * minimum cost. Independent of any tie-break order.
     */
    private BruteForceResult bruteForceMinimum(double[] first, double[] second, int radius,
            DynamicTimeWarpingDistanceIndicator.Config config) {
        double[] firstSequence = normalizeForOracle(first, config.normalization());
        double[] secondSequence = normalizeForOracle(second, config.normalization());
        List<int[]> path = new ArrayList<>();
        Num best = enumerate(firstSequence, secondSequence, radius, config, 0, 0, path, null);
        return new BruteForceResult(best);
    }

    private Num enumerate(double[] first, double[] second, int radius,
            DynamicTimeWarpingDistanceIndicator.Config config, int i, int j, List<int[]> path, Num best) {
        path.add(new int[] { i, j });
        if (i == first.length - 1 && j == second.length - 1) {
            Num cost = pathCost(first, second, config, path);
            if (best == null || cost.isLessThan(best)) {
                best = cost;
            }
        } else {
            int[][] moves = { { 1, 1 }, { 1, 0 }, { 0, 1 } };
            for (int[] move : moves) {
                int nextI = i + move[0];
                int nextJ = j + move[1];
                if (nextI < first.length && nextJ < second.length && Math.abs(nextI - nextJ) <= radius) {
                    best = enumerate(first, second, radius, config, nextI, nextJ, path, best);
                }
            }
        }
        path.remove(path.size() - 1);
        return best;
    }

    private Num pathCost(double[] first, double[] second, DynamicTimeWarpingDistanceIndicator.Config config,
            List<int[]> path) {
        NumFactory factory = DoubleNumFactory.getInstance();
        Num cost = factory.zero();
        int pathLength = path.size();
        for (int[] cell : path) {
            Num firstValue = factory.numOf(first[cell[0]]);
            Num secondValue = factory.numOf(second[cell[1]]);
            Num delta = firstValue.minus(secondValue);
            Num local = config.localDistance() == DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE
                    ? delta.abs()
                    : delta.multipliedBy(delta);
            cost = cost.plus(local);
        }
        if (config
                .pathCostNormalization() == DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH) {
            cost = cost.dividedBy(factory.numOf(pathLength));
        }
        return cost;
    }

    /**
     * Full-matrix dynamic programming with the documented deterministic tie-break:
     * strictly lower cost wins, then equal cost with a strictly shorter path
     * length, and survivor ties resolve in the order diagonal, vertical,
     * horizontal. Independent of the two-row implementation.
     */
    private BruteForceResult fullMatrixDp(double[] first, double[] second,
            DynamicTimeWarpingDistanceIndicator.WarpingWindow warpingWindow,
            DynamicTimeWarpingDistanceIndicator.Config config) {
        double[] firstSequence = normalizeForOracle(first, config.normalization());
        double[] secondSequence = normalizeForOracle(second, config.normalization());
        int sampleCount = first.length;
        NumFactory factory = DoubleNumFactory.getInstance();
        Num[][] costs = new Num[sampleCount][sampleCount];
        int[][] lengths = new int[sampleCount][sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            for (int j = 0; j < sampleCount; j++) {
                if (!warpingWindow.inBand(i, j)) {
                    continue;
                }
                Num local = localCost(factory, firstSequence[i], secondSequence[j], config.localDistance());
                Num bestCost = null;
                int bestLength = 0;
                if (i > 0 && j > 0 && costs[i - 1][j - 1] != null) {
                    bestCost = costs[i - 1][j - 1];
                    bestLength = lengths[i - 1][j - 1];
                }
                if (i > 0 && costs[i - 1][j] != null
                        && better(costs[i - 1][j], lengths[i - 1][j], bestCost, bestLength)) {
                    bestCost = costs[i - 1][j];
                    bestLength = lengths[i - 1][j];
                }
                if (j > 0 && costs[i][j - 1] != null
                        && better(costs[i][j - 1], lengths[i][j - 1], bestCost, bestLength)) {
                    bestCost = costs[i][j - 1];
                    bestLength = lengths[i][j - 1];
                }
                if (bestCost == null) {
                    bestCost = factory.zero();
                    bestLength = 0;
                }
                costs[i][j] = local.plus(bestCost);
                lengths[i][j] = bestLength + 1;
            }
        }
        Num total = costs[sampleCount - 1][sampleCount - 1];
        if (config
                .pathCostNormalization() == DynamicTimeWarpingDistanceIndicator.PathCostNormalization.BY_PATH_LENGTH) {
            total = total.dividedBy(factory.numOf(lengths[sampleCount - 1][sampleCount - 1]));
        }
        return new BruteForceResult(total);
    }

    private static boolean better(Num candidateCost, int candidateLength, Num bestCost, int bestLength) {
        if (bestCost == null) {
            return true;
        }
        int comparison = candidateCost.compareTo(bestCost);
        return comparison < 0 || (comparison == 0 && candidateLength < bestLength);
    }

    private static Num localCost(NumFactory factory, double first, double second,
            DynamicTimeWarpingDistanceIndicator.LocalDistance localDistance) {
        Num delta = factory.numOf(first).minus(factory.numOf(second));
        return localDistance == DynamicTimeWarpingDistanceIndicator.LocalDistance.ABSOLUTE ? delta.abs()
                : delta.multipliedBy(delta);
    }

    private static double[] normalizeForOracle(double[] values,
            DynamicTimeWarpingDistanceIndicator.SequenceNormalization normalization) {
        if (normalization == DynamicTimeWarpingDistanceIndicator.SequenceNormalization.NONE) {
            return values;
        }
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;
        double sumOfSquares = 0.0;
        for (double value : values) {
            double delta = value - mean;
            sumOfSquares += delta * delta;
        }
        double standardDeviation = Math.sqrt(sumOfSquares / values.length);
        double[] normalized = new double[values.length];
        if (standardDeviation == 0.0) {
            return normalized;
        }
        for (int i = 0; i < values.length; i++) {
            normalized[i] = (values[i] - mean) / standardDeviation;
        }
        return normalized;
    }

    private BarSeries series(int barCount) {
        double[] raw = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            raw[i] = i;
        }
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(raw).build();
    }

    private Indicator<Num> indicator(BarSeries series, double... values) {
        List<Num> nums = new ArrayList<>(values.length);
        for (double value : values) {
            nums.add(numFactory.numOf(value));
        }
        return new MockIndicator(series, nums);
    }

    private Indicator<Num> mockIndicator(BarSeries series, int unstableBars, double... values) {
        List<Num> nums = new ArrayList<>(values.length);
        for (double value : values) {
            nums.add(numFactory.numOf(value));
        }
        return new MockIndicator(series, unstableBars, nums);
    }
}
