package org.ta4j.core;

/**
 * @author Lukáš Kvídera
 */
class BaseBarBuilderFactory implements BarBuilderFactory {

    @Override
    public BaseBarConvertibleBuilder createBarBuilder(BarSeries series) {
        return new BaseBarConvertibleBuilder(series.numFactory()).bindTo(series);
    }
}
