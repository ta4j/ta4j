package org.ta4j.core.indicators.caching;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class BaseCacheKeyHolder implements CacheKeyHolder {

    private final int index;
    private final Bar bar;

    private final BarSeries barSeries;

    public static BaseCacheKeyHolder of(int index, Bar bar, BarSeries barSeries) {
        return new BaseCacheKeyHolder(index, bar, barSeries);
    }

    public BaseCacheKeyHolder(int index, Bar bar, BarSeries barSeries) {
        this.index = index;
        this.bar = bar;
        this.barSeries = barSeries;
    }

    @Override
    public int getIndex() {
        return this.index;
    }

    @Override
    public Bar getBar() {
        return this.bar;
    }

    @Override
    public BarSeries getBarSeries() {
        return this.barSeries;
    }

}
