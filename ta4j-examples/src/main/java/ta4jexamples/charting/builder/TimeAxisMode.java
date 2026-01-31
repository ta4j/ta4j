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
package ta4jexamples.charting.builder;

/**
 * Controls how charts interpret gaps between bars on the domain axis.
 *
 * <p>
 * Use {@link #REAL_TIME} to preserve actual time gaps (weekends, holidays, or
 * missing bars). Use {@link #BAR_INDEX} to compress the domain axis so bars are
 * evenly spaced by index, eliminating visual gaps while keeping the underlying
 * data unchanged.
 * </p>
 *
 * @since 0.23
 */
public enum TimeAxisMode {

    /**
     * Plot bars using their real timestamps. Missing bars appear as gaps on the
     * time axis.
     */
    REAL_TIME,

    /**
     * Plot bars using their index positions, producing evenly spaced candles and
     * removing time gaps (e.g., weekends, holidays).
     */
    BAR_INDEX
}
