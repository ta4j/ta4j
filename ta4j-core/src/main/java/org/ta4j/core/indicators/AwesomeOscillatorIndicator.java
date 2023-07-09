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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Awesome oscillator (AO) indicator.
 *
 * @see https://www.tradingview.com/wiki/Awesome_Oscillator_(AO)
 */
public class AwesomeOscillatorIndicator extends CachedIndicator<Num> {

    private final SMAIndicator sma5;
    private final SMAIndicator sma34;

    /**
     * Constructor.
     *
     * @param indicator    (normally {@link MedianPriceIndicator})
     * @param barCountSma1 (normally 5)
     * @param barCountSma2 (normally 34)
     */
    public AwesomeOscillatorIndicator(Indicator<Num> indicator, int barCountSma1, int barCountSma2) {
        super(indicator);
        this.sma5 = new SMAIndicator(indicator, barCountSma1);
        this.sma34 = new SMAIndicator(indicator, barCountSma2);
    }

    /**
     * Constructor with:
     * 
     * <ul>
     * <li>{@code barCountSma1} = 5
     * <li>{@code barCountSma2} = 34
     * </ul>
     *
     * @param indicator (normally {@link MedianPriceIndicator})
     */
    public AwesomeOscillatorIndicator(Indicator<Num> indicator) {
        this(indicator, 5, 34);
    }

    /**
     * Constructor with:
     * 
     * <ul>
     * <li>{@code indicator} = {@link MedianPriceIndicator}
     * <li>{@code barCountSma1} = 5
     * <li>{@code barCountSma2} = 34
     * </ul>
     *
     * @param series the bar series
     */
    public AwesomeOscillatorIndicator(BarSeries series) {
        this(new MedianPriceIndicator(series), 5, 34);
    }

    @Override
    protected Num calculate(int index) {
        return sma5.getValue(index).minus(sma34.getValue(index));
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
