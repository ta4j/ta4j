package org.ta4j.core.cache;

public class BaseCacheProvider<T> implements CacheProvider<T> {

    private final long maxSize;

    public BaseCacheProvider(long maxSize) {
        this.maxSize = maxSize;
    }


    @Override
    public Cache<T> getCache() {
        return new BaseCache<T>(maxSize);
    }
}
