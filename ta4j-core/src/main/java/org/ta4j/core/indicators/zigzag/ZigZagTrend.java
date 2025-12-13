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
package org.ta4j.core.indicators.zigzag;

/**
 * Represents the current trend direction in a ZigZag pattern.
 * <p>
 * The ZigZag indicator identifies significant price reversals by filtering out
 * price movements below a specified threshold. This enum tracks the current
 * direction of the trend being tracked by the ZigZag algorithm.
 *
 * @see ZigZagStateIndicator
 * @since 0.20
 */
public enum ZigZagTrend {
    /**
     * The trend is moving upward. The ZigZag is tracking a rising leg and looking
     * for a swing high that will be confirmed when price reverses down by at least
     * the reversal threshold.
     */
    UP,

    /**
     * The trend is moving downward. The ZigZag is tracking a falling leg and
     * looking for a swing low that will be confirmed when price reverses up by at
     * least the reversal threshold.
     */
    DOWN,

    /**
     * The trend direction has not yet been determined. This occurs at the beginning
     * of the bar series before the first reversal threshold is met.
     */
    UNDEFINED
}
