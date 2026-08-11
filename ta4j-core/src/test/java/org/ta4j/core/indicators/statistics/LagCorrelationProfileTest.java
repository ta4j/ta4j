/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.OptionalInt;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LagCorrelationProfileTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public LagCorrelationProfileTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void acceptsLargeTieHeavyProfiles() {
        // Validation must stay linear: a tie-heavy profile with tens of
        // thousands of lags would be unusably slow under quadratic validation.
        List<LagCorrelationPoint> points = new java.util.ArrayList<>();
        for (int lag = 0; lag < 50_000; lag++) {
            points.add(new LagCorrelationPoint(lag, numFactory.one(), 8));
        }
        List<Integer> bestLags = new java.util.ArrayList<>();
        for (int lag = 0; lag < 50_000; lag++) {
            bestLags.add(lag);
        }
        LagCorrelationProfile profile = new LagCorrelationProfile(100_000, 8, 0, 49_999,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(0), numFactory.one());

        assertEquals(50_000, profile.points().size());
        assertEquals(50_000, profile.bestLags().size());
        assertEquals(OptionalInt.of(0), profile.selectedLag());
    }

    @Test
    public void profileRejectsSelectedLagOutsideBestLags() {
        // Period-4 square wave autocorrelation over an 8-bar window: exactly
        // -1 at lags -2 and 2, 0 at lags -1 and 1, and 1 at lag 0.
        List<LagCorrelationPoint> points = List.of(new LagCorrelationPoint(-2, numFactory.numOf(-1), 8),
                new LagCorrelationPoint(-1, numFactory.zero(), 8), new LagCorrelationPoint(0, numFactory.one(), 8),
                new LagCorrelationPoint(1, numFactory.zero(), 8), new LagCorrelationPoint(2, numFactory.numOf(-1), 8));
        List<Integer> bestLags = List.of(0);

        assertThrows(IllegalArgumentException.class, () -> new LagCorrelationProfile(31, 8, -2, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(1), numFactory.one()));
    }

    @Test
    public void profileRejectsSelectionThatDoesNotMatchThePoints() {
        // A fixture with a two-way tie at lags -1 and 1 (correlation 1.0).
        List<LagCorrelationPoint> points = List.of(new LagCorrelationPoint(-2, numFactory.numOf(0.5), 8),
                new LagCorrelationPoint(-1, numFactory.one(), 8), new LagCorrelationPoint(0, numFactory.numOf(0.25), 8),
                new LagCorrelationPoint(1, numFactory.one(), 8), new LagCorrelationPoint(2, numFactory.numOf(0.5), 8));
        List<Integer> bestLags = List.of(-1, 1);

        // A best-lag list that is self-consistent but omits a maximal lag.
        assertThrows(IllegalArgumentException.class, () -> new LagCorrelationProfile(100, 8, -2, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, List.of(-1), OptionalInt.of(-1), numFactory.one()));
        // The deterministic tie-break is the smallest absolute lag (-1), not 1.
        assertThrows(IllegalArgumentException.class, () -> new LagCorrelationProfile(100, 8, -2, 2,
                LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags, OptionalInt.of(1), numFactory.one()));
        // The canonical selection is accepted.
        new LagCorrelationProfile(100, 8, -2, 2, LagSelectionPolicy.MAXIMUM_CORRELATION, points, bestLags,
                OptionalInt.of(-1), numFactory.one());
    }
}
