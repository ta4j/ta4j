/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.caching;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class BaseIndicatorValueCache<V> implements IndicatorValueCache<V> {

    private final Cache<ZonedDateTime, V> cache;

    public BaseIndicatorValueCache(BarSeries series) {
        this(new IndicatorValueCacheConfig(series.getMaximumBarCount()));
    }

    public BaseIndicatorValueCache(IndicatorValueCacheConfig cacheConfig) {
        if (cacheConfig.getMaximumSize() == Integer.MAX_VALUE) {
            cache = Caffeine.newBuilder().build();
        } else {
            cache = Caffeine.newBuilder().maximumSize(cacheConfig.getMaximumSize()).build();
        }
    }

    @Override
    public V get(CacheKeyHolder keyHolder, Function<CacheKeyHolder, V> mappingFunction) {
        Bar bar = keyHolder.getBar();
        ZonedDateTime endTime = bar.getEndTime();
        V value = cache.getIfPresent(endTime);
        if (value == null) {
            value = mappingFunction.apply(keyHolder);
            cache.put(endTime, value);
        }
        return value;
    }

    @Override
    public void put(CacheKeyHolder keyHolder, V result) {
        cache.put(keyHolder.getBar().getEndTime(), result);
    }

    @Override
    public Map<Object, V> getValues() {
        return Collections.unmodifiableMap(cache.asMap());
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        cache.cleanUp();
    }

}
