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

/**
 * Recursive cached {@link Indicator indicator}.
 * 递归缓存 {@link Indicator indicator}。
 *
 * Recursive indicators should extend this class.<br>
  This class is only here to avoid (OK, to postpone) the StackOverflowError
  that may be thrown on the first getValue(int) call of a recursive indicator.
  Concretely when an index value is asked, if the last cached value is too
  old/far, the computation of all the values between the last cached and the
  asked one is executed iteratively.

 递归指标应该扩展这个类。<br>
 这个类只是为了避免（好的，推迟）StackOverflowError
 可能会在递归指标的第一次 getValue(int) 调用中抛出。
 具体在询问某个索引值时，最后缓存的值是否太
 old/far，计算最后一个缓存和最后一个缓存之间的所有值
 问一个是迭代执行的。
 */
public abstract class RecursiveCachedIndicator<T> extends CachedIndicator<T> {

    /**
     * The recursion threshold for which an iterative calculation is executed. TODO
      Should be variable (depending on the sub-indicators used in this indicator)
     执行迭代计算的递归阈值。 去做
     应该是可变的（取决于该指标中使用的子指标）
     */
    private static final int RECURSION_THRESHOLD = 100;

    /**
     * Constructor.
     *
     * @param series the related bar series
     *               相关酒吧系列
     */
    protected RecursiveCachedIndicator(BarSeries series) {
        super(series);
    }

    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series)
     *                  相关指标（带有条形系列）
     */
    protected RecursiveCachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    @Override
    public T getValue(int index) {
        BarSeries series = getBarSeries();
        if (series != null) {
            final int seriesEndIndex = series.getEndIndex();
            if (index <= seriesEndIndex) {
                // We are not after the end of the series
                // 我们不是在系列结束之后
                final int removedBarsCount = series.getRemovedBarsCount();
                int startIndex = Math.max(removedBarsCount, highestResultIndex);
                if (index - startIndex > RECURSION_THRESHOLD) {
                    // Too many uncalculated values; the risk for a StackOverflowError becomes high.
                    // 太多未计算的值； StackOverflowError 的风险变得很高。
                    // Calculating the previous values iteratively
                    // 迭代计算先前的值
                    for (int prevIdx = startIndex; prevIdx < index; prevIdx++) {
                        super.getValue(prevIdx);
                    }
                }
            }
        }

        return super.getValue(index);
    }
}
