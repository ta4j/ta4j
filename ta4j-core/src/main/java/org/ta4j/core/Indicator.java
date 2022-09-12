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

import org.ta4j.core.num.Num;

/**
 * Indicator over a {@link BarSeries bar series}. <p/p> For each index of the bar series, returns a value of type <b>T</b>.
 * * {@link BarSeries bar series} 上的指标。 <p/p> 对于条形系列的每个索引，返回一个 <b>T</b> 类型的值。
 *
 * @param <T> the type of returned value (Double, Boolean, etc.)
 *           * @param <T> 返回值的类型（Double、Boolean 等）
 */
public interface Indicator<T> {

    /**
     * @param index the bar index
     *              条形索引
     *
     * @return the value of the indicator
     * * @return 指标的值
     */
    T getValue(int index);

    /**
     * @return the related bar series
     * * @return 相关栏系列
     */
    BarSeries getBarSeries();

    /**
     * @return the {@link Num Num extending class} for the given {@link Number}
     * * @return 给定 {@link Number} 的 {@link Num Num 扩展类}
     */
    Num numOf(Number number);

    /**
     * Returns all values from an {@link Indicator} as an array of Doubles. The returned doubles could have a minor loss of precise, if {@link Indicator} was based on {@link Num Num}.
     * * 将 {@link Indicator} 中的所有值作为双精度数组返回。 如果 {@link Indicator} 基于 {@link Num Num}，则返回的双精度值可能会有轻微的损失。
     *
     * @param ref      the indicator
     *                 指标
     *
     * @param index    the index
     *                 索引
     *
     * @param barCount the barCount
     *                 柱计数
     * @return array of Doubles within the barCount
     * * @return barCount 内的 Doubles 数组
     */
    static Double[] toDouble(Indicator<Num> ref, int index, int barCount) {

        Double[] all = new Double[barCount];

        int startIndex = Math.max(0, index - barCount + 1);
        for (int i = 0; i < barCount; i++) {
            Num number = ref.getValue(i + startIndex);
            all[i] = number.doubleValue();
        }

        return all;
    }

}
