package org.ta4j.core.backtest;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

/**
 * @author Lukáš Kvídera
 */
class BacktestBarBuilderFactory implements BarBuilderFactory {

    @Override
    public BacktestBarBuilder createBarBuilder(final BarSeries series) {
        return new BacktestBarBuilder(series);
    }
}
