/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class BaseBarSeriesBuilder implements BarSeriesBuilder {

    private static final long serialVersionUID = 111164611841087550L;
    /**
     * Default Num type function
     **/
    private static Function<Number, Num> defaultFunction = PrecisionNum::valueOf;
    private List<Bar> bars;
    private String name;
    private Function<Number, Num> numFunction;
    private boolean constrained;
    private int maxBarCount;

    public BaseBarSeriesBuilder() {
        initValues();
    }

    public static void setDefaultFunction(Function<Number, Num> defaultFunction) {
        BaseBarSeriesBuilder.defaultFunction = defaultFunction;
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = "unnamed_series";
        this.numFunction = BaseBarSeriesBuilder.defaultFunction;
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
        BaseBarSeries series = new BaseBarSeries(name, bars, beginIndex, endIndex, constrained, numFunction);
        series.setMaximumBarCount(maxBarCount);
        initValues(); // reinitialize values for next series
        return series;
    }

    public BaseBarSeriesBuilder setConstrained(boolean constrained) {
        this.constrained = constrained;
        return this;
    }

    public BaseBarSeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public BaseBarSeriesBuilder withBars(List<Bar> bars) {
        this.bars = bars;
        return this;
    }

    public BaseBarSeriesBuilder withMaxBarCount(int maxBarCount) {
        this.maxBarCount = maxBarCount;
        return this;
    }

    public BaseBarSeriesBuilder withNumTypeOf(Num type) {
        numFunction = type.function();
        return this;
    }

    public BaseBarSeriesBuilder withNumTypeOf(Function<Number, Num> function) {
        numFunction = function;
        return this;
    }

    public BaseBarSeriesBuilder withNumTypeOf(Class<? extends Num> abstractNumClass) {
        if (abstractNumClass == PrecisionNum.class) {
            numFunction = PrecisionNum::valueOf;
            return this;
        } else if (abstractNumClass == DoubleNum.class) {
            numFunction = DoubleNum::valueOf;
            return this;
        }
        numFunction = PrecisionNum::valueOf;
        return this;
    }

}
