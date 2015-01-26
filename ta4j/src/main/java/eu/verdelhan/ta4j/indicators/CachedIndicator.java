/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
        int innerIndex = index - removedTicksCount;
        if (innerIndex < 0) {
            throw new IllegalArgumentException("Result from tick " + index + " already removed from cache");
        } else {
            // Updating cache length
            increaseLength(innerIndex);
            innerIndex -= removeExceedingResults(series.getMaximumTickCount());
        }

        T result = results.get(innerIndex);
        if (result == null) {
            try {
                result = calculate(index);
            } catch (Exception e) {
                // TODO: throw/catch custom exceptions
                result = null;
            }
            results.set(innerIndex, result);
        }
        return result;
    }

    /**
     * @param index the index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    /**
     * Increases the size of cached results buffer.
     * @param index the index to increase length to
     */
    private void increaseLength(int index) {
        if (results.size() <= index) {
            int newResultsCount = index - results.size() + 1;
            results.addAll(Collections.<T> nCopies(newResultsCount, null));
        }
    }

    /**
     * Removes the N first results which exceed the maximum tick count.
     * (i.e. keeps only the last maxResultCount results)
     * @param maximumResultCount the number of results to keep
     * @return the number of removed results
     */
    private int removeExceedingResults(int maximumResultCount) {
        int resultCount = results.size();
        if (resultCount > maximumResultCount) {
            // Removing old results
            int nbResultsToRemove = resultCount - maximumResultCount;
            for (int i = 0; i < nbResultsToRemove; i++) {
                results.remove(0);
            }
            return nbResultsToRemove;
        }
        return 0;
    }
}
