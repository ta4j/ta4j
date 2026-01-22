/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

public class HeikinAshiBarBuilderFactory implements BarBuilderFactory {

    @Override
    public BarBuilder createBarBuilder(BarSeries series) {
        return new HeikinAshiBarBuilder(series.numFactory()).bindTo(series);
    }

}
