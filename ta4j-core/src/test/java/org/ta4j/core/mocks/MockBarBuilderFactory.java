package org.ta4j.core.mocks;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarConvertibleBuilder;

/**
 * @author Lukáš Kvídera
 */
public class MockBarBuilderFactory implements BarBuilderFactory {
    @Override
    public BaseBarConvertibleBuilder createBarBuilder(final BarSeries series) {
        return new MockBarBuilder(series.numFactory()).bindTo(series);
    }
}
