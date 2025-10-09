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
package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

/**
 * A builder to build a new {@link BaseBarSeries}.
 */
public class BaseBarSeriesBuilder implements BarSeriesBuilder {

    /** Default instance of Num to determine its Num type and function. */
    private static Num defaultNum = DecimalNum.ZERO;
    private List<Bar> bars;
    private String name;
    private Num num;
    private boolean constrained;
    private int maxBarCount;

    /** Constructor to build a {@code BaseBarSeries}. */
    public BaseBarSeriesBuilder() {
        initValues();
    }

    /**
     * @param defaultNum any instance of Num to be used as default to determine its
     *                   Num function; with this, we can convert a {@link Number} to
     *                   a {@link Num Num implementation}
     */
    public static void setDefaultNum(Num defaultNum) {
        BaseBarSeriesBuilder.defaultNum = defaultNum;
    }

    /**
     * @param defaultFunction a Num function to be used as default; with this, we
     *                        can convert a {@link Number} to a {@link Num Num
     *                        implementation}
     */
    public static void setDefaultNum(Function<Number, Num> defaultFunction) {
        BaseBarSeriesBuilder.defaultNum = defaultFunction.apply(0);
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = "unnamed_series";
        this.num = BaseBarSeriesBuilder.defaultNum;
        this.constrained = false;
        this.maxBarCount = Integer.MAX_VALUE;
    }

    @Override
    public BaseBarSeries build() {
        int beginIndex = -1;
        int endIndex = -1;
        if (!bars.isEmpty()) {
            beginIndex = 0;
            endIndex = bars.size() - 1;
        }
        BaseBarSeries series = new BaseBarSeries(name, bars, beginIndex, endIndex, constrained, num);
        series.setMaximumBarCount(maxBarCount);
        initValues(); // reinitialize values for next series
        return series;
    }

    /**
     * @param constrained to set {@link BaseBarSeries#constrained}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder setConstrained(boolean constrained) {
        this.constrained = constrained;
        return this;
    }

    /**
     * @param name to set {@link BaseBarSeries#getName()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param bars to set {@link BaseBarSeries#getBarData()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withBars(List<Bar> bars) {
        this.bars = bars;
        return this;
    }

    /**
     * @param maxBarCount to set {@link BaseBarSeries#getMaximumBarCount()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withMaxBarCount(int maxBarCount) {
        this.maxBarCount = maxBarCount;
        return this;
    }

    /**
     * @param type any instance of Num to determine its Num function; with this, we
     *             can convert a {@link Number} to a {@link Num Num implementation}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withNumTypeOf(Num type) {
        this.num = type;
        return this;
    }

    /**
     * @param type any Num function; with this, we can convert a {@link Number} to a
     *             {@link Num Num implementation}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withNumTypeOf(Function<Number, Num> function) {
        this.num = function.apply(0);
        return this;
    }

    /**
     * @param clazz any Num class; with this, we can convert a {@link Number} to a
     *              {@link Num Num implementation}; if {@code clazz} is not
     *              registered, then {@link #defaultNum} is used.
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withNumTypeOf(Class<? extends Num> clazz) {
        if (clazz == DecimalNum.class) {
            this.num = DecimalNum.ZERO;
            return this;
        } else if (clazz == DoubleNum.class) {
            this.num = DoubleNum.ZERO;
            return this;
        }
        this.num = defaultNum;
        return this;
    }

}
