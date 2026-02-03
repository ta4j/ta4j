/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.wyckoff;

/**
 * Describes the inferred Wyckoff phase for a bar.
 *
 * @param cycleType        the broader Wyckoff cycle (accumulation or
 *                         distribution)
 * @param phaseType        the lettered Wyckoff phase (A-E)
 * @param confidence       confidence score between {@code 0.0} and {@code 1.0}
 * @param latestEventIndex index of the latest structural event backing the
 *                         inference, or {@code -1} if none
 *
 * @since 0.19
 */
public record WyckoffPhase(WyckoffCycleType cycleType, WyckoffPhaseType phaseType, double confidence,
        int latestEventIndex) {

    /** Constant describing an indeterminate phase with zero confidence. */
    public static final WyckoffPhase UNKNOWN = new WyckoffPhase(WyckoffCycleType.UNKNOWN, WyckoffPhaseType.PHASE_A, 0.0,
            -1);

    /**
     * Returns a new phase with the provided confidence value.
     *
     * @param confidenceValue the confidence to apply
     * @return a copy with the updated confidence
     * @since 0.19
     */
    public WyckoffPhase withConfidence(double confidenceValue) {
        return new WyckoffPhase(cycleType, phaseType, confidenceValue, latestEventIndex);
    }

    /**
     * Returns a new phase that records the supplied latest event index.
     *
     * @param eventIndex index of the latest structural event
     * @return a copy pointing to the supplied event index
     * @since 0.19
     */
    public WyckoffPhase withLatestEventIndex(int eventIndex) {
        return new WyckoffPhase(cycleType, phaseType, confidence, eventIndex);
    }
}
