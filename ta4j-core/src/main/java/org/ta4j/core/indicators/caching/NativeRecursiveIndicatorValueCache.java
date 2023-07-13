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

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

public class NativeRecursiveIndicatorValueCache<T> extends NativeIndicatorValueCache<T> {

    private static final Logger log = LoggerFactory.getLogger(NativeRecursiveIndicatorValueCache.class);
    private static final int RECURSION_THRESHOLD = 100;

    public NativeRecursiveIndicatorValueCache(Indicator<T> indicator) {
        this(new IndicatorValueCacheConfig(indicator.getBarSeries().getMaximumBarCount()));
    }

    public NativeRecursiveIndicatorValueCache(BarSeries barSeries) {
        this(new IndicatorValueCacheConfig(barSeries.getMaximumBarCount()));
    }

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
                        super.get(BaseCacheKeyHolder.of(prevIdx, series.getBar(prevIdx), series), mappingFunction);
                    }
                }
            }
        }

        return super.get(keyHolder, mappingFunction);
    }

}
