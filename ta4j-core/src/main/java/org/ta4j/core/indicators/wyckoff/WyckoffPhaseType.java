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
 * Enumerates the lettered Wyckoff phases within a market cycle.
 *
 * @since 0.19
 */
public enum WyckoffPhaseType {

    /** Phase A: stopping action. */
    PHASE_A('A'),

    /** Phase B: building a cause. */
    PHASE_B('B'),

    /** Phase C: testing. */
    PHASE_C('C'),

    /** Phase D: trend confirmation. */
    PHASE_D('D'),

    /** Phase E: trend in motion. */
    PHASE_E('E');

    private final char letter;

    WyckoffPhaseType(char letter) {
        this.letter = letter;
    }

    /**
     * Returns the canonical phase letter.
     *
     * @return the letter representing the phase
     * @since 0.19
     */
    public char getLetter() {
        return letter;
    }
}
