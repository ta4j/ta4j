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
package ta4jexamples.indicators.numeric.facades;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import ta4jexamples.indicators.numeric.NumericIndicator;

/**
 * A facade to create the 3 Bollinger Band indicators.
 * A simple moving average of close price is used as the middle band.
 * The BB bandwidth and %B indicators can be created on demand.
 *
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. Overall there
 * is much less caching and probably better performance.
 */
public class BollingerBands {

    private final NumericIndicator price;
    private final NumericIndicator middle;
    private final NumericIndicator upper;
    private final NumericIndicator lower;

    public BollingerBands(BarSeries bs, int n, Number k) {
        this.price = NumericIndicator.of(new ClosePriceIndicator(bs));
        this.middle = NumericIndicator.of(price.sma(n));
        NumericIndicator stdev = price.stddev(n);
        // StdDevIndicator creates another SMA(n); hard to fix with current design
        this.upper = middle.plus(stdev.multipliedBy(k));
        this.lower = middle.minus(stdev.multipliedBy(k));
    }

    /**
     * A fluent BB middle band
     * 
     * @return a NumericIndicator wrapped around a cached SMAIndicator of close price.
     */
    public NumericIndicator middle() {
        return middle;
    }

    /**
     * A fluent BB upper band
     * 
     * @return an object that calculates the sum of BB middle and a multiple of standard deviation.
     */
    public NumericIndicator upper() {
        return upper;
    }

    /**
     * A fluent BB lower band
     * 
     * @return an object that calculates the difference between BB middle and a multiple of standard deviation.
     */
    public NumericIndicator lower() {
        return lower;
    }

    /**
     * A fluent BB Bandwidth indicator
     * 
     * @return an object that calculates BB bandwidth from upper, lower and middle
     */
    public NumericIndicator bandwidth() {
        return upper.minus(lower).dividedBy(middle).multipliedBy(100);
    }

    /**
     * A fluent %B indicator
     * 
     * @return an object that calculates %B from close price, upper and lower
     */
    public NumericIndicator percentB() {
        return price.minus(lower).dividedBy(upper.minus(lower));
    }

}
