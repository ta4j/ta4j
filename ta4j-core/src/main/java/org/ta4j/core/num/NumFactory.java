/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.num;

public interface NumFactory {

    /**
     * @return the Num of -1
     */
    Num minusOne();

    /**
     * @return the Num of 0
     */
    Num zero();

    /**
     * @return the Num of 1
     */
    Num one();

    /**
     * @return the Num of 2
     */
    Num two();

    /**
     * @return the Num of 3
     */
    Num three();

    /**
     * @return the Num of 100
     */
    Num hundred();

    /**
     * @return the Num of 100
     */
    Num thousand();

    /**
     * Transforms a {@link Number} into the {@link Num implementation} used by this
     * bar series
     *
     * @param number a {@link Number} implementing object.
     * @return the corresponding value as a Num implementing object
     */
    Num numOf(Number number);

    /**
     * Transforms a {@link Number} into the {@link Num implementation} used by this
     * bar series
     *
     * @param number as string
     * @return the corresponding value as a Num implementing object
     */
    Num numOf(String number);

    /**
     * Determines whether num instance has been produced by this factory
     *
     * @param num to test
     * @return true if made by this factory
     */
    default boolean produces(final Num num) {
        return num == null || one().getClass() == num.getClass() || num.equals(NaN.NaN);
    }
}
