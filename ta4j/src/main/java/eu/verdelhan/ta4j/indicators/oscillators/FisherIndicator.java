/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.RecursiveCachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MedianPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;

/**
 * The Fisher Indicator.
 * @see http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf
 */
public class FisherIndicator extends RecursiveCachedIndicator<Decimal>{

    private static final Decimal ZERO_DOT_FIVE = Decimal.valueOf("0.5");
    private static final Decimal VALUE_MAX = Decimal.valueOf("0.999");
    private static final Decimal VALUE_MIN = Decimal.valueOf("-0.999");

    private final Indicator<Decimal> price;
    
    private final Indicator<Decimal> intermediateValue;
    
    /**
     * Constructor.
     *
     * @param series the series
     */
    public FisherIndicator(TimeSeries series) {
        this(new MedianPriceIndicator(series), 10);
    }
    
    /**
     * Constructor.
     *
     * @param price the price indicator (usually {@link MedianPriceIndicator})
     * @param timeFrame the time frame (usually 10)
     */
    public FisherIndicator(Indicator<Decimal> price, int timeFrame) {
        this(price, timeFrame, Decimal.valueOf("0.33"), Decimal.valueOf("0.67"));
    }
    
    /**
     * Constructor.
     *
     * @param price the price indicator (usually {@link MedianPriceIndicator})
     * @param timeFrame the time frame (usually 10)
     * @param alpha the alpha (usually 0.33)
     * @param beta the beta (usually 0.67)
     */
    public FisherIndicator(Indicator<Decimal> price, int timeFrame, final Decimal alpha, final Decimal beta) {
        super(price);
        this.price = price;
        final Indicator<Decimal> periodHigh = new HighestValueIndicator(new MaxPriceIndicator(price.getTimeSeries()), timeFrame);
        final Indicator<Decimal> periodLow = new LowestValueIndicator(new MinPriceIndicator(price.getTimeSeries()), timeFrame);
        intermediateValue = new RecursiveCachedIndicator<Decimal>(price) {

            @Override
            protected Decimal calculate(int index) {
                if (index <= 0) {
                    return Decimal.ZERO;
                }
                // alpha * 2 * ((price - MinL) / (MaxH - MinL) - 0.5) + beta * prior value
                Decimal currentPrice = FisherIndicator.this.price.getValue(index);
                Decimal minL = periodLow.getValue(index);
                Decimal maxH = periodHigh.getValue(index);
                Decimal firstPart = currentPrice.minus(minL).dividedBy(maxH.min(minL)).minus(ZERO_DOT_FIVE);
                Decimal secondPart = alpha.multipliedBy(Decimal.TWO).multipliedBy(firstPart);
                Decimal value = secondPart.plus(beta.multipliedBy(getValue(index - 1)));
                if (value.isGreaterThan(VALUE_MAX)) {
                    value = VALUE_MAX;
                } else if (value.isLessThan(VALUE_MIN)) {
                    value = VALUE_MIN;
                }
                return value;
            }
        };
    }

    @Override
    protected Decimal calculate(int index) {
        if (index <= 0) {
            return Decimal.ZERO;
        }
        //Fish = 0.5 * MathLog((1 + Value) / (1 - Value)) + 0.5 * Fish1
        Decimal value = intermediateValue.getValue(index);
        Decimal ext = Decimal.ONE.plus(value).dividedBy(Decimal.ONE.minus(value)).log();
        return ext.plus(getValue(index - 1)).dividedBy(Decimal.TWO);
    }

}
