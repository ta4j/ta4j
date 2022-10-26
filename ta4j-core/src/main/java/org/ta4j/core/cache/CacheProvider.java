package org.ta4j.core.cache;

public interface CacheProvider<T>{

    Cache<T> getCache();
}
