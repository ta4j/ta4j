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
package org.ta4j.core.indicators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.caching.BaseIndicatorValueCache;
import org.ta4j.core.indicators.caching.IndicatorValueCache;

/**
 * Abstract {@link Indicator indicator}.
 */
public abstract class AbstractIndicator<T> implements Indicator<T> {

    /** The logger. */
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    private final BarSeries series;

    private IndicatorValueCache<T> cache;

    /**
     * Constructor.
     *
     * @param indicator the indicator holding a bar series
     */
    protected AbstractIndicator(Indicator<T> indicator, IndicatorValueCache<T> indicatorValueCache) {
        this(indicator.getBarSeries(), indicatorValueCache);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator holding a bar series
     */
    protected AbstractIndicator(Indicator<T> indicator) {
        this(indicator.getBarSeries(), new BaseIndicatorValueCache<>(indicator.getBarSeries()));
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected AbstractIndicator(BarSeries series) {
        this(series, new BaseIndicatorValueCache<>(series));
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param indicatorValueCache the cache implementation
     */
    protected AbstractIndicator(BarSeries series, IndicatorValueCache<T> indicatorValueCache) {
        this.series = series;
        cache = indicatorValueCache;
    }

    @Override
    public T getValue(final int index) {
        final BarSeries series = getBarSeries();
        if (series == null || index >= series.getEndIndex()) {
            // Don't cache result if last bar or no available bar
            return calculate(index);
        }

        if (index < series.getBeginIndex()) {
            return calculate(0);
        }

        final Bar bar = series.getBar(index);
        log.info("index: {}, bar: {}", index, bar);
        return cache.get(bar.getEndTime(), (endTime) -> calculate(index));
    }

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    public IndicatorValueCache<T> getCache() {
        return this.cache;
    }

    public void clearCache() {
        this.cache.clear();
    }

    public void setCache(IndicatorValueCache<T> cache) {
        this.cache = cache;
    }

    @Override
    public BarSeries getBarSeries() {
        return series;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
