package org.ta4j.core.indicators.caching;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;

public class NativeRecursiveIndicatorValueCache<T> extends NativeIndicatorValueCache<T>{

    private static final Logger log = LoggerFactory.getLogger(NativeRecursiveIndicatorValueCache.class);
    private static final int RECURSION_THRESHOLD = 100;

    public NativeRecursiveIndicatorValueCache(IndicatorValueCacheConfig indicatorValueCacheConfig) {
        super(indicatorValueCacheConfig);
    }

    @Override
    public T get(CacheKeyHolder keyHolder, Function<CacheKeyHolder, T> mappingFunction) {
        BarSeries series = keyHolder.getBarSeries();
        int index = keyHolder.getIndex();
        if (series != null) {
            final int seriesEndIndex = series.getEndIndex();
            if (index <= seriesEndIndex) {
                // We are not after the end of the series
                final int removedBarsCount = series.getRemovedBarsCount();
                int startIndex = Math.max(removedBarsCount, highestResultIndex);
                if (index - startIndex > RECURSION_THRESHOLD) {
                    // Too many uncalculated values; the risk for a StackOverflowError becomes high.
                    // Calculating the previous values iteratively
                    for (int prevIdx = startIndex; prevIdx < index; prevIdx++) {
                        log.info("{}: calculating {}-th value iteratively", getClass().getSimpleName(), prevIdx);
                        super.get(BaseCacheKeyHolder.of(prevIdx, series.getBar(prevIdx), series), mappingFunction);
                    }
                }
            }
        }

        return super.get(keyHolder, mappingFunction);
    }

}
