/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
import org.ta4j.core.Ta4jCache;

/**
 * Cached {@link Indicator indicator}.
 *
 * Caches the constructor of the indicator. Avoid to calculate the same index of
 * the indicator twice.
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    /**
     * cached results
     */
    private final Ta4jCache<T> cache;

    /**
     * Constructor.
     *
     * @param series the related bar series
     */
    protected CachedIndicator(BarSeries series) {
        super(series);
        this.cache = new Ta4jCache<>(series);
    }

    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series)
     */
    protected CachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    @Override
    public T getValue(int index) {
        if (cache.contains(index)) {
            return cache.get(index);
        }
        BarSeries series = getBarSeries();
        if (series == null) {
            // Series is null; the indicator doesn't need cache.
            // (e.g. simple computation of the value)
            // --> Calculating the value
            T result = calculate(index);
            log.trace("{}({}): {}", this, index, result);
            return result;
        }

        // Series is not null
        final int removedBarsCount = series.getRemovedBarsCount();

        T result;
        if (index < removedBarsCount) {
            // Corresponding bar already removed series
            log.trace("{}: result from bar {} already removed from the series, use {}-th instead",
                    getClass().getSimpleName(), index, removedBarsCount);
            // It should be "result = calculate(removedBarsCount);".
            // We use "result = calculate(0);" as a workaround
            // to fix issue #120 (https://github.com/mdeverdelhan/ta4j/issues/120).
            result = calculate(0);
        } else {
            result = calculate(index);
            cache.add(index, result);
        }
        log.trace("{}({}): {}", this, index, result);
        return result;
    }

    protected int getLastCachedIndex() {
        return cache.lastAvailableIndex();
    }
}
