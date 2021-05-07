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
package org.ta4j.core;

import java.util.TreeMap;

public class Ta4jCache<T> {
    private final BarSeries series;
    private final TreeMap<Integer, T> actualCache = new TreeMap<>();

    public Ta4jCache(BarSeries series) {
        this.series = series;
    }

    public boolean contains(int index) {
        if (index == series.getEndIndex()) {
            return false;
        }
        return actualCache.containsKey(index);
    }

    public T get(int index) {
        if (index == series.getEndIndex()) {
            return null;
        }
        return actualCache.get(index);
    }

    public void add(int index, T result) {
        if (index == series.getEndIndex()) {
            // never use cache for last index, as the corresponding bar can be updated
            return;
        }
        if (contains(index)) {
            return;
        }

        actualCache.put(index, result);

        int currentLimit = series.getMaximumBarCount();
        while (actualCache.size() > currentLimit) {
            actualCache.pollFirstEntry();
        }
    }

    public int lastAvailableIndex() {
        if (actualCache.isEmpty()) {
            return -1;
        }
        return actualCache.lastKey();
    }
}
