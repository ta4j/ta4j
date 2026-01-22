/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.mocks;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

public class MockBarBuilderFactory implements BarBuilderFactory {
    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        return new MockBarBuilder(series.numFactory()).bindTo(series);
    }
}
