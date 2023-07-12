package org.ta4j.core.indicators.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.ta4j.core.BarSeries;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class BaseIndicatorValueCache<T> implements IndicatorValueCache<T> {

    private final Cache<ZonedDateTime, T> cache;

    public BaseIndicatorValueCache(BarSeries series) {
        this(new IndicatorValueCacheConfig(series.getMaximumBarCount()));
    }

    public BaseIndicatorValueCache(IndicatorValueCacheConfig cacheConfig) {
        cache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaximumSize())
                .build();
    }

    @Override
    public T get(ZonedDateTime endTime, Function<ZonedDateTime, T> mappingFunction) {
        return cache.get(endTime, mappingFunction);
    }

    @Override
    public void put(ZonedDateTime endTime, T result) {
        cache.put(endTime, result);
    }

    @Override
    public Map<ZonedDateTime, T> getValues() {
        return Collections.unmodifiableMap(cache.asMap());
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        cache.cleanUp();
    }

}
