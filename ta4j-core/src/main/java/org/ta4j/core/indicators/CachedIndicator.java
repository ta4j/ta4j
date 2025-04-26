/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cached indicator using an LRU map. Automatically evicts the oldest entries
 * when the cache size exceeds the series' maximum bar count.
 *
 * @param <T> the type of indicator value
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    private final int cacheSize;
    private final Map<Integer, T> cache;

    protected CachedIndicator(BarSeries series) {
        super(series);
        this.cacheSize = series.getMaximumBarCount();

        // accessOrder=false so it's insertion order, we evict oldest inserted entries
        this.cache = new LinkedHashMap<Integer, T>(cacheSize + 1, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, T> eldest) {
                return size() > cacheSize;
            }
        };
    }

    protected CachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    /**
     * Compute the value at the given index.
     */
    protected abstract T calculate(int index);

    @Override
    public synchronized T getValue(int index) {
        BarSeries series = getBarSeries();
        // no series → no caching
        if (series == null) {
            return calculate(index);
        }

        final int lastIndex = series.getEndIndex();
        final int removedBars = series.getRemovedBarsCount();

        // 1) if they ask for a bar that was evicted from the series...
        if (index < removedBars) {
            if (log.isTraceEnabled()) {
                log.trace("{}: bar {} was removed (removedBars={}), recalculating", getClass().getSimpleName(), index,
                        removedBars);
            }
            return calculate(index);
        }

        // 2) if it's the very last (open) bar, always recompute
        if (index == lastIndex) {
            return calculate(index);
        }

        // 3) else, look up in cache (or compute & cache)
        T value = cache.get(index);
        if (value == null) {
            value = calculate(index);
            cache.put(index, value);
        }
        if (log.isTraceEnabled()) {
            log.trace("{}({}): {}", this, index, value);
        }
        return value;
    }
}
