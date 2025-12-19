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

import java.util.List;

import org.ta4j.core.BarSeries;

/**
 * Test utility that provides pre-configured swing sequences for Elliott wave
 * tests.
 *
 * <p>
 * This stub allows tests to supply specific swing sequences indexed by bar
 * position, avoiding the need to build complex price series that naturally
 * produce the desired swing patterns.
 */
public class StubSwingIndicator extends ElliottSwingIndicator {

    private final List<List<ElliottSwing>> swingsByIndex;

    /**
     * Creates a stub swing indicator with the specified swing sequences.
     *
     * @param series        backing bar series
     * @param swingsByIndex list of swing lists, one per bar index
     */
    public StubSwingIndicator(final BarSeries series, final List<List<ElliottSwing>> swingsByIndex) {
        this(series, swingsByIndex, ElliottDegree.MINOR);
    }

    /**
     * Creates a stub swing indicator with the specified swing sequences and degree.
     *
     * @param series        backing bar series
     * @param swingsByIndex list of swing lists, one per bar index
     * @param degree        degree metadata applied to swings
     */
    public StubSwingIndicator(final BarSeries series, final List<List<ElliottSwing>> swingsByIndex,
            final ElliottDegree degree) {
        super(series, 1, degree);
        this.swingsByIndex = swingsByIndex;
    }

    @Override
    protected List<ElliottSwing> calculate(final int index) {
        if (index < swingsByIndex.size()) {
            return swingsByIndex.get(index);
        }
        return swingsByIndex.isEmpty() ? List.of() : swingsByIndex.get(swingsByIndex.size() - 1);
    }
}
