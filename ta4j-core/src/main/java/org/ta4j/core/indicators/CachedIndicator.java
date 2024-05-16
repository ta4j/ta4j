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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Cached {@link Indicator indicator}.
 * 缓存的 {@link Indicator Indicator}。
 *
 * Caches the constructor of the indicator. Avoid to calculate the same index of the indicator twice.
 * 缓存指标的构造函数。避免对指标的同一指标进行两次计算。
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    /**
     * List of cached results
     * 缓存结果列表
     */
    private final List<T> results;

    /**
     * Should always be the index of the last result in the results list. I.E. the last calculated result.
     * * 应该始终是结果列表中最后一个结果的索引。 IE。 最后计算的结果。
     */
    protected int highestResultIndex = -1;

    /**
     * Constructor.
     *
     * @param series the related bar series
     *               相关酒吧系列
     */
    protected CachedIndicator(BarSeries series) {
        super(series);
        int limit = series.getMaximumBarCount();
        results = limit == Integer.MAX_VALUE ? new ArrayList<>() : new ArrayList<>(limit);
    }

    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series)
     *                  相关指标（带有条形系列）
     */
    protected CachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    /**
     * @param index the bar index
     *              条形索引
     * @return the value of the indicator
     *              指标值
     */
    protected abstract T calculate(int index);

    @Override
    public T getValue(int index) {
        BarSeries series = getBarSeries();
        if (series == null) {
            // Series is null; the indicator doesn't need cache.
            // (e.g. simple computation of the value)
            // --> Calculating the value
            // 系列为空； 该指标不需要缓存。
            //（例如，简单的值计算）
            // --> 计算值
            T result = calculate(index);
            log.trace("{}({}): {}", this, index, result);
            return result;
        }

        // Series is not null

        final int removedBarsCount = series.getRemovedBarsCount();
        final int maximumResultCount = series.getMaximumBarCount();

        T result;
        if (index < removedBarsCount) {
            // Result already removed from cache
            // 结果已经从缓存中删除
            log.trace("{}: result from bar 酒吧的结果 {} already removed from cache 已从缓存中删除, use 利用 {}-th instead 而是",
                    getClass().getSimpleName(), index, removedBarsCount);
            increaseLengthTo(removedBarsCount, maximumResultCount);
            highestResultIndex = removedBarsCount;
            result = results.get(0);
            if (result == null) {
                // It should be "result = calculate(removedBarsCount);".
                // We use "result = calculate(0);" as a workaround
                // to fix issue #120 (https://github.com/mdeverdelhan/ta4j/issues/120).
                // 它应该是“result = calculate(removedBarsCount);”。
                // 我们使用 "result = calculate(0);" 作为一种解决方法
                // 修复问题 #120 (https://github.com/mdeverdelhan/ta4j/issues/120)。
                result = calculate(0);
                results.set(0, result);
            }
        } else {
            if (index == series.getEndIndex()) {
                // Don't cache result if last bar
                // 如果最后一根柱子不缓存结果
                result = calculate(index);
            } else {
                increaseLengthTo(index, maximumResultCount);
                if (index > highestResultIndex) {
                    // Result not calculated yet
                    // 尚未计算结果
                    highestResultIndex = index;
                    result = calculate(index);
                    results.set(results.size() - 1, result);
                } else {
                    // Result covered by current cache
                    // 当前缓存覆盖的结果
                    int resultInnerIndex = results.size() - 1 - (highestResultIndex - index);
                    result = results.get(resultInnerIndex);
                    if (result == null) {
                        result = calculate(index);
                        results.set(resultInnerIndex, result);
                    }
                }
            }

        }
        log.trace("{}({}): {}", this, index, result);
        return result;
    }

    /**
     * Increases the size of cached results buffer.
     * * 增加缓存结果缓冲区的大小。
     *
     * @param index     the index to increase length to
     *                  将长度增加到的索引
     * @param maxLength the maximum length of the results buffer
     *                  结果缓冲区的最大长度
     */
    private void increaseLengthTo(int index, int maxLength) {
        if (highestResultIndex > -1) {
            int newResultsCount = Math.min(index - highestResultIndex, maxLength);
            if (newResultsCount == maxLength) {
                results.clear();
                results.addAll(Collections.nCopies(maxLength, null));
            } else if (newResultsCount > 0) {
                results.addAll(Collections.nCopies(newResultsCount, null));
                removeExceedingResults(maxLength);
            }
        } else {
            // First use of cache
            // 第一次使用缓存
            assert results.isEmpty() : "Cache results list should be empty 缓存结果列表应为空";
            results.addAll(Collections.nCopies(Math.min(index + 1, maxLength), null));
        }
    }

    /**
     * Removes the N first results which exceed the maximum bar count. (i.e. keeps only the last maximumResultCount results)
     * * 删除超过最大柱数的 N 个第一个结果。 （即只保留最后的 maximumResultCount 结果）
     *
     * @param maximumResultCount the number of results to keep
     *                           要保留的结果数
     */
    private void removeExceedingResults(int maximumResultCount) {
        int resultCount = results.size();
        if (resultCount > maximumResultCount) {
            // Removing old results
            // 删除旧结果
            final int nbResultsToRemove = resultCount - maximumResultCount;
            for (int i = 0; i < nbResultsToRemove; i++) {
                results.remove(0);
            }
        }
    }
}
