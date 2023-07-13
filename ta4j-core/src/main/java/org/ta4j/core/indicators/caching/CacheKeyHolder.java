package org.ta4j.core.indicators.caching;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public interface CacheKeyHolder {

    int getIndex();

    Bar getBar();

    BarSeries getBarSeries();
}
