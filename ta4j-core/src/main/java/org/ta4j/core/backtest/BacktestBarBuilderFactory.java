package org.ta4j.core.backtest;

import org.ta4j.core.BacktestBarConvertibleBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

/**
 * @author Lukáš Kvídera
 */
class BacktestBarBuilderFactory implements BarBuilderFactory {

    @Override
    public BacktestBarConvertibleBuilder createBarBuilder(BarSeries series) {
        return new BacktestBarConvertibleBuilder(series.numFactory()).bindTo(series);
    }
}
