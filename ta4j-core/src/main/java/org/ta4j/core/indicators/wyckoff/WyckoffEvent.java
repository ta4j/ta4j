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
 * Enumerates the structural and volume events referenced in Wyckoff analysis.
 *
 * @since 0.19
 */
public enum WyckoffEvent {

    /** Preliminary support. */
    PRELIMINARY_SUPPORT,
    /** Preliminary supply. */
    PRELIMINARY_SUPPLY,
    /** Selling climax. */
    SELLING_CLIMAX,
    /** Buying climax. */
    BUYING_CLIMAX,
    /** Automatic rally. */
    AUTOMATIC_RALLY,
    /** Automatic reaction. */
    AUTOMATIC_REACTION,
    /** Secondary test within the trading range. */
    SECONDARY_TEST,
    /** Sign of strength (SOS). */
    SIGN_OF_STRENGTH,
    /** Upthrust. */
    UPTHRUST,
    /** Upthrust after distribution (UTAD). */
    UPTHRUST_AFTER_DISTRIBUTION,
    /** Spring. */
    SPRING,
    /** Last point of support. */
    LAST_POINT_OF_SUPPORT,
    /** Last point of supply. */
    LAST_POINT_OF_SUPPLY,
    /** Range breakout. */
    RANGE_BREAKOUT,
    /** Range breakdown. */
    RANGE_BREAKDOWN
}
