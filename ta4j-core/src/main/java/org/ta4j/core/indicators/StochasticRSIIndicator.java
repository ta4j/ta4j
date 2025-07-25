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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * The Stochastic RSI Indicator.
 *
 * <pre>
 * Stoch RSI = (RSI - MinimumRSIn) / (MaximumRSIn - MinimumRSIn)
 * </pre>
 */
public class StochasticRSIIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final StochasticOscillatorKIndicator stochasticOscillatorKIndicator;

    /**
     * Constructor.
     *
     * <p>
     * <b>Note:</b> In most cases, this constructor should be used to avoid
     * confusion about which indicator parameters to use.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public StochasticRSIIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator the Indicator (usually a {@link ClosePriceIndicator})
     * @param barCount  the time frame
     */
    public StochasticRSIIndicator(Indicator<Num> indicator, int barCount) {
        this(new RSIIndicator(indicator, barCount), barCount);
    }

    /**
     * Constructor.
     *
     * @param rsiIndicator the {@link RSIIndicator}
     * @param barCount     the time frame
     */
    public StochasticRSIIndicator(RSIIndicator rsiIndicator, int barCount) {
        super(rsiIndicator);
        this.barCount = barCount;
        this.stochasticOscillatorKIndicator = new StochasticOscillatorKIndicator(rsiIndicator, barCount, rsiIndicator,
                rsiIndicator);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return this.stochasticOscillatorKIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return this.barCount;
    }

}
