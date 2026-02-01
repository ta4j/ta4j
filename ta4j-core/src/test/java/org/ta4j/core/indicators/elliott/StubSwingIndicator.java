/*
 * SPDX-License-Identifier: MIT
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
