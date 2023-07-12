package org.ta4j.core.indicators.caching;

public class IndicatorValueCacheConfig {

    private int maximumSize;

    public IndicatorValueCacheConfig(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    public int getMaximumSize() {
        return maximumSize;
    }
}
