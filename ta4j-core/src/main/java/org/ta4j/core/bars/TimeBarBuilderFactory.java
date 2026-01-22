/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

public class TimeBarBuilderFactory implements BarBuilderFactory {

    @Override
    public BarBuilder createBarBuilder(BarSeries series) {
        return new TimeBarBuilder(series.numFactory()).bindTo(series);
    }
}
