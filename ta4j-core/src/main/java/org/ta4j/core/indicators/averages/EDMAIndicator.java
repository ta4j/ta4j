/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.averages;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Exponential Displaced Moving Average (EDMA)
 *
 * Exponential Displaced Moving Average (EDMA) is a variation of the traditional
 * Exponential Moving Average (EMA) where the calculated average is shifted in
 * time by a specified number of periods. This displacement enhances trend
 * visualization and provides traders with an adjusted perspective of price
 * action, either anticipating future price movements or analyzing past trends.
 *
 */
public class EDMAIndicator extends AbstractIndicator<Num> {

    private final int barCount;
    private final int displacement;
    /** List of cached results. */
    private final List<Num> results;

    /**
     * Constructor.
     *
     * @param indicator    an indicator
     * @param barCount     the Exponential Moving Average time frame
     * @param displacement the Exponential Moving Average displacement
     */
    public EDMAIndicator(Indicator<Num> indicator, int barCount, int displacement) {
        super(indicator.getBarSeries());
        BarSeries series = indicator.getBarSeries();
        this.barCount = barCount;
        this.displacement = displacement;
        this.results = new ArrayList<>();

        EMAIndicator ema = new EMAIndicator(indicator, barCount);
        for (int i = 0; i < series.getBarCount(); i++) {
            results.add(ema.getValue(i));
        }
    }

    protected Num calculate(int index) {

        int displacedIndex = index - displacement;
        if (index <= displacement) {
            return results.get(0);
        }
        return results.get(displacedIndex);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " displacement: " + displacement;
    }

    @Override
    public Num getValue(int index) {
        return calculate(index);
    }

}
