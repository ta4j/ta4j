/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cached {@link Indicator indicator}.
 * <p>
 * Caches the constructor of the indicator. Avoid to calculate the same index of the indicator twice.
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    /** List of cached results */
    private final List<T> results = new ArrayList<T>();

    /**
     * Should always be the index of the last result in the results list.
     * I.E. the last calculated result.
     */
    protected int highestResultIndex = -1;
    
    /**
     * Constructor.
     * @param series the related time series
     */
    public CachedIndicator(TimeSeries series) {
        super(series);
    }

    /**
     * Constructor.
     * @param indicator a related indicator (with a time series)
     */
    public CachedIndicator(Indicator indicator) {
        this(indicator.getTimeSeries());
    }

    @Override
    public T getValue(int index) {
        TimeSeries series = getTimeSeries();
        if (series == null) {
            // Series is null; the indicator doesn't need cache.
            // (e.g. simple computation of the value)
            // --> Calculating the value
            return calculate(index);
        }

        // Series is not null
        
        final int removedTicksCount = series.getRemovedTicksCount();
        final int maximumResultCount = series.getMaximumTickCount();
        
        T result;
        if (index < removedTicksCount) {
            // Result already removed from cache
            log.trace("{}: result from tick {} already removed from cache, use {}-th instead",
                    getClass().getSimpleName(), index, removedTicksCount);
            increaseLengthTo(removedTicksCount, maximumResultCount);
            highestResultIndex = removedTicksCount;
            result = results.get(0);
            if (result == null) {
                result = calculate(removedTicksCount);
                results.set(0, result);
            }
        } else {
            increaseLengthTo(index, maximumResultCount);
            if (index > highestResultIndex) {
                // Result not calculated yet
                highestResultIndex = index;
                result = calculate(index);
                results.set(results.size()-1, result);
            } else {
                // Result covered by current cache
                int resultInnerIndex = results.size() - 1 - (highestResultIndex - index);
                result = results.get(resultInnerIndex);
                if (result == null) {
                    result = calculate(index);
                }
                results.set(resultInnerIndex, result);
            }
        }
        return result;
    }

    /**
     * @param index the tick index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    /**
     * Increases the size of cached results buffer.
     * @param index the index to increase length to
     * @param maxLength the maximum length of the results buffer
     */
    private void increaseLengthTo(int index, int maxLength) {
        if (highestResultIndex > -1) {
            int newResultsCount = Math.min(index-highestResultIndex, maxLength);
            if (newResultsCount == maxLength) {
                results.clear();
                results.addAll(Collections.<T> nCopies(maxLength, null));
            } else if (newResultsCount > 0) {
                results.addAll(Collections.<T> nCopies(newResultsCount, null));
                removeExceedingResults(maxLength);
            }
        } else {
            // First use of cache
            assert results.isEmpty() : "Cache results list should be empty";
            results.addAll(Collections.<T> nCopies(Math.min(index+1, maxLength), null));
        }
    }

    /**
     * Removes the N first results which exceed the maximum tick count.
     * (i.e. keeps only the last maximumResultCount results)
     * @param maximumResultCount the number of results to keep
     */
    private void removeExceedingResults(int maximumResultCount) {
        int resultCount = results.size();
        if (resultCount > maximumResultCount) {
            // Removing old results
            final int nbResultsToRemove = resultCount - maximumResultCount;
            for (int i = 0; i < nbResultsToRemove; i++) {
                results.remove(0);
            }
        }
    }
}
