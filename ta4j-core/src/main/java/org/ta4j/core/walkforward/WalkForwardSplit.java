/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.Objects;

/**
 * Describes one walk-forward fold with explicit train/test index boundaries.
 *
 * @param foldId      stable fold identifier
 * @param trainStart  training start index (inclusive)
 * @param trainEnd    training end index (inclusive)
 * @param testStart   test start index (inclusive)
 * @param testEnd     test end index (inclusive)
 * @param purgeBars   bars purged before the test boundary
 * @param embargoBars bars embargoed between training and testing boundaries
 * @param holdout     whether this split is holdout-only
 * @since 0.22.4
 */
public record WalkForwardSplit(String foldId, int trainStart, int trainEnd, int testStart, int testEnd, int purgeBars,
        int embargoBars, boolean holdout) {

    /**
     * Creates a validated walk-forward split.
     */
    public WalkForwardSplit {
        Objects.requireNonNull(foldId, "foldId");
        if (foldId.isBlank()) {
            throw new IllegalArgumentException("foldId must not be blank");
        }
        if (trainStart < 0) {
            throw new IllegalArgumentException("trainStart must be >= 0");
        }
        if (trainEnd < trainStart) {
            throw new IllegalArgumentException("trainEnd must be >= trainStart");
        }
        if (testStart <= trainEnd) {
            throw new IllegalArgumentException("testStart must be > trainEnd");
        }
        if (testEnd < testStart) {
            throw new IllegalArgumentException("testEnd must be >= testStart");
        }
        if (purgeBars < 0) {
            throw new IllegalArgumentException("purgeBars must be >= 0");
        }
        if (embargoBars < 0) {
            throw new IllegalArgumentException("embargoBars must be >= 0");
        }
    }

    /**
     * @return number of bars in the training interval
     * @since 0.22.4
     */
    public int trainBarCount() {
        return trainEnd - trainStart + 1;
    }

    /**
     * @return number of bars in the testing interval
     * @since 0.22.4
     */
    public int testBarCount() {
        return testEnd - testStart + 1;
    }
}
