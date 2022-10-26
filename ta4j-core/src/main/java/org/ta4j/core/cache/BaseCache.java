package org.ta4j.core.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;

public class BaseCache<T> implements Cache<T> {

    private final com.github.benmanes.caffeine.cache.Cache<ZonedDateTime, T> results;

    public BaseCache(long maxSize) {
        this.results = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public T getValue(Bar bar) {
        return results.getIfPresent(bar.getEndTime());
    }

    @Override
    public void put(Bar bar, T value) {
        this.results.put(bar.getEndTime(), value);
    }
}
