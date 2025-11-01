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
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Standard deviation indicator.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/standard-deviation-volatility">Standard
 *      Deviation (Volatility)</a>
 * @see <a href="https://en.wikipedia.org/wiki/Standard_deviation">Standard
 *      deviation on wikipedia</a>
 */
public class StandardDeviationIndicator extends CachedIndicator<Num> {

    private final VarianceIndicator variance;

    /**
     * Constructor.
     *
     * @param indicator  the indicator
     * @param barCount   the time frame
     * @param sampleType sample/population
     */
    public StandardDeviationIndicator(final Indicator<Num> indicator, final int barCount, final SampleType sampleType) {
        super(indicator);
        this.variance = sampleType.isSample() ? VarianceIndicator.ofSample(indicator, barCount)
                : VarianceIndicator.ofPopulation(indicator, barCount);
    }

    public static StandardDeviationIndicator ofSample(final Indicator<Num> indicator, final int barCount) {
        return new StandardDeviationIndicator(indicator, barCount, SampleType.SAMPLE);
    }

    public static StandardDeviationIndicator ofPopulation(final Indicator<Num> indicator, final int barCount) {
        return new StandardDeviationIndicator(indicator, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(final int index) {
        return this.variance.getValue(index).sqrt();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
