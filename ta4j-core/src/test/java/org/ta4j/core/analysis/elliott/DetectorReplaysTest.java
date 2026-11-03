/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;

class DetectorReplaysTest {

    private static final int BARS = 60;

    private final BarSeries series = ScaleRelationFixtures.series(BARS);
    private final Supplier<SwingDetector> coarse = detector(ScaleRelationFixtures.PARENT,
            ScaleRelationFixtures.PARENT_LAG);
    private final Supplier<SwingDetector> fine = detector(ScaleRelationFixtures.CHILD, ScaleRelationFixtures.CHILD_LAG);

    private static Supplier<SwingDetector> detector(final List<ScaleRelationFixtures.Pt> pivots, final int lag) {
        final SwingDetector detector = ScaleRelationFixtures.scripted(pivots, lag);
        return () -> detector;
    }

    @Test
    void sameSupplierOverTheBoundSeriesIsReplayedOnce() {
        final DetectorReplays replays = DetectorReplays.forSeries(series, BARS - 1);

        final ConfirmationTracker.CausalReplay first = replays.replay(series, coarse, BARS - 1);
        final ConfirmationTracker.CausalReplay second = replays.replay(series, coarse, BARS - 1);
        replays.replay(series, fine, BARS - 1);

        assertSame(first, second);
        assertEquals(2, replays.computedCount());
    }

    @Test
    void otherSeriesOrEndIndexIsReplayedFreshAndNeverStored() {
        final DetectorReplays replays = DetectorReplays.forSeries(series, BARS - 1);
        final BarSeries other = ScaleRelationFixtures.series(BARS);

        final ConfirmationTracker.CausalReplay bound = replays.replay(series, coarse, BARS - 1);
        assertNotSame(bound, replays.replay(other, coarse, BARS - 1));
        assertNotSame(bound, replays.replay(series, coarse, BARS - 2));
        assertSame(bound, replays.replay(series, coarse, BARS - 1));
        assertEquals(3, replays.computedCount());
    }

    @Test
    void uncachedNeverSharesAndCountsNothing() {
        final DetectorReplays uncached = DetectorReplays.uncached();

        assertNotSame(uncached.replay(series, coarse, BARS - 1), uncached.replay(series, coarse, BARS - 1));
        assertEquals(0, uncached.computedCount());
    }

    @Test
    void relationStudyDrawsScaleTapesFromTheSharedCache() {
        final DetectorReplays replays = DetectorReplays.forSeries(series, BARS - 1);
        replays.replay(series, coarse, BARS - 1);
        replays.replay(series, fine, BARS - 1);
        final ScaleRelationStudy study = ScaleRelationFixtures
                .study(List.of(new ScaleRelationStudy.ScaleInput("coarse", coarse),
                        new ScaleRelationStudy.ScaleInput("fine", fine)), ScaleRelation.Policy.defaults());
        final List<ScaleRelationStudy.Frame> shared = new ArrayList<>();
        final List<ScaleRelationStudy.Frame> fresh = new ArrayList<>();

        study.run(series, 0, BARS - 1, replays, shared::add);
        study.run(series, 0, BARS - 1, fresh::add);

        assertEquals(2, replays.computedCount(), "the hierarchy must not re-detect scales already replayed");
        assertEquals(fresh.size(), shared.size());
        assertEquals(fresh.stream().map(ScaleRelationStudy.Frame::events).toList(),
                shared.stream().map(ScaleRelationStudy.Frame::events).toList());
    }
}
