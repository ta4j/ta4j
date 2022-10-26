package org.ta4j.core.cache;

public class BaseCacheProviderBuilder {

    private long maxSize = 100;

    public BaseCacheProviderBuilder withMaxSize(long size) {
        this.maxSize = size;
        return this;
    }

    public CacheProvider build() {
        return new BaseCacheProvider(maxSize);
    }
}
