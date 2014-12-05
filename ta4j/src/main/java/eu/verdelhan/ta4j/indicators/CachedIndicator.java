/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

    private List<T> results = new ArrayList<T>();

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
        final int innerIndex = index - removedTicksCount;
        if (innerIndex < 0) {
            throw new IllegalArgumentException("Tick " + index + " already removed from the series");
        } else {
            // Updating cache length
            increaseLength(innerIndex);
            removeExceedingResults(series.getMaximumTickCount());
        }

        // Calculating the cached results
        if (results.get(innerIndex) == null) {
            // Looking for the last non-null result
            int resultIndex = innerIndex;
            while ((resultIndex > 0) && (results.get(resultIndex--) == null)) {
                ;
            }
            // Calculating all null values
            for (; resultIndex <= innerIndex; resultIndex++) {
                if (results.get(resultIndex) == null) {
                    results.set(resultIndex, calculate(resultIndex + removedTicksCount));
                }
            }
        }

        return results.get(index);
    }

    /**
     * @param index the index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    /**
     * Increases the size of cached results buffer.
     * @param index
     */
    private void increaseLength(int index) {
        if (results.size() <= index) {
            results.addAll(Collections.<T> nCopies((index - results.size()) + 1, null));
        }
    }

    /**
     * Removes the N first results which exceed the maximum tick count.
     * (i.e. keeps only the last maxResultCount results)
     * @param maximumResultCount the number of results to keep
     */
    private void removeExceedingResults(int maximumResultCount) {
        int resultCount = results.size();
        if (resultCount > maximumResultCount) {
            // Removing old results
            int nbResultsToRemove = resultCount - maximumResultCount;
            for (int i = 0; i < nbResultsToRemove; i++) {
                results.remove(0);
            }
        }
    }
}
