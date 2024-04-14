package org.ta4j.core.mocks;

import org.ta4j.core.BacktestBarConvertibleBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

/**
 * @author Lukáš Kvídera
 */
public class MockBarBuilderFactory implements BarBuilderFactory {
    @Override
    public BacktestBarConvertibleBuilder createBarBuilder(final BarSeries series) {
        return new MockBarBuilder(series.numFactory()).bindTo(series);
    }
}
