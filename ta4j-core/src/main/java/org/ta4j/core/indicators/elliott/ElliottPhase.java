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
package org.ta4j.core.indicators.elliott;

/**
 * Enumerates the sequential phases of an Elliott wave structure.
 *
 * @since 0.22.0
 */
public enum ElliottPhase {

    /** No qualifying swing structure detected. */
    NONE,
    /** First impulsive wave. */
    WAVE1,
    /** Second impulsive wave retracement. */
    WAVE2,
    /** Third impulsive wave extension. */
    WAVE3,
    /** Fourth impulsive wave consolidation. */
    WAVE4,
    /** Fifth impulsive wave completion. */
    WAVE5,
    /** First corrective swing (wave A). */
    CORRECTIVE_A,
    /** Second corrective swing (wave B). */
    CORRECTIVE_B,
    /** Third corrective swing (wave C). */
    CORRECTIVE_C;

    /**
     * @return {@code true} when the phase represents an impulsive leg (waves 1-5)
     * @since 0.22.0
     */
    public boolean isImpulse() {
        return impulseIndex() > 0;
    }

    /**
     * @return {@code true} when the phase represents a corrective leg (waves A-C)
     * @since 0.22.0
     */
    public boolean isCorrective() {
        return correctiveIndex() > 0;
    }

    /**
     * @return {@code true} when the phase completes its respective structure
     * @since 0.22.0
     */
    public boolean completesStructure() {
        return this == WAVE5 || this == CORRECTIVE_C;
    }

    /**
     * @return zero when the phase is not impulsive otherwise the 1-based impulse
     *         ordinal
     * @since 0.22.0
     */
    public int impulseIndex() {
        return switch (this) {
        case WAVE1 -> 1;
        case WAVE2 -> 2;
        case WAVE3 -> 3;
        case WAVE4 -> 4;
        case WAVE5 -> 5;
        default -> 0;
        };
    }

    /**
     * @return zero when the phase is not corrective otherwise the 1-based
     *         corrective ordinal
     * @since 0.22.0
     */
    public int correctiveIndex() {
        return switch (this) {
        case CORRECTIVE_A -> 1;
        case CORRECTIVE_B -> 2;
        case CORRECTIVE_C -> 3;
        default -> 0;
        };
    }
}
