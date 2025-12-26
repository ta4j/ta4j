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
 * Categorizes Elliott wave structure patterns.
 *
 * @since 0.22.0
 */
public enum ScenarioType {

    /**
     * Standard five-wave motive structure (waves 1-2-3-4-5).
     */
    IMPULSE,

    /**
     * Three-wave corrective pattern with sharp moves (A-B-C where C exceeds A).
     */
    CORRECTIVE_ZIGZAG,

    /**
     * Three-wave corrective pattern where waves A and C are approximately equal and
     * wave B retraces most of wave A.
     */
    CORRECTIVE_FLAT,

    /**
     * Five-wave corrective pattern forming a contracting or expanding triangle
     * (A-B-C-D-E).
     */
    CORRECTIVE_TRIANGLE,

    /**
     * Combination of multiple corrective patterns (double/triple zigzag, double
     * three, etc.).
     */
    CORRECTIVE_COMPLEX,

    /**
     * Pattern type could not be determined.
     */
    UNKNOWN;

    /**
     * @return {@code true} if this is a motive (impulse) pattern
     * @since 0.22.0
     */
    public boolean isImpulse() {
        return this == IMPULSE;
    }

    /**
     * @return {@code true} if this is any corrective pattern type
     * @since 0.22.0
     */
    public boolean isCorrective() {
        return this == CORRECTIVE_ZIGZAG || this == CORRECTIVE_FLAT || this == CORRECTIVE_TRIANGLE
                || this == CORRECTIVE_COMPLEX;
    }

    /**
     * @return expected wave count for this pattern type (5 for impulse, 3 for
     *         zigzag/flat, 5 for triangle, 0 for unknown/complex)
     * @since 0.22.0
     */
    public int expectedWaveCount() {
        return switch (this) {
        case IMPULSE -> 5;
        case CORRECTIVE_ZIGZAG, CORRECTIVE_FLAT -> 3;
        case CORRECTIVE_TRIANGLE -> 5;
        default -> 0;
        };
    }
}
