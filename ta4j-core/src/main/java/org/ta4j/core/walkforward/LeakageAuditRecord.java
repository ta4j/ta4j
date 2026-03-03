/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

/**
 * Audit record capturing decision and evaluation window boundaries for leakage
 * checks.
 *
 * @param foldId            fold id
 * @param decisionIndex     prediction decision index
 * @param visibleStartIndex lower bound of visible series for prediction
 * @param visibleEndIndex   upper bound of visible series for prediction
 * @param labelStartIndex   start index of realized label window
 * @param labelEndIndex     end index of realized label window
 * @param horizonBars       horizon in bars
 * @param withinFoldBounds  whether the label window is fully inside the fold
 *                          test
 * @param holdout           whether the snapshot belongs to holdout
 * @param note              diagnostic note
 * @since 0.22.4
 */
public record LeakageAuditRecord(String foldId, int decisionIndex, int visibleStartIndex, int visibleEndIndex,
        int labelStartIndex, int labelEndIndex, int horizonBars, boolean withinFoldBounds, boolean holdout,
        String note) {
}
