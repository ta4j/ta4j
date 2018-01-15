/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;

/**
 * The Fisher Indicator.
 * 
 * <p/>
 * @apiNote Minimal deviations in last decimal places possible. During the calculations this indicator converts {@link Decimal Decimal/BigDecimal} to to {@link Double double}
 * see http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf
 */
public class FisherIndicator extends RecursiveCachedIndicator<Decimal> {
	
    private static final long serialVersionUID = 4622250625267906228L;
	
    private static final Decimal ZERO_DOT_FIVE = Decimal.valueOf(0.5);
    private static final Decimal VALUE_MAX = Decimal.valueOf(0.999);
    private static final Decimal VALUE_MIN = Decimal.valueOf(-0.999);

    private final Indicator<Decimal> ref;
    private final Indicator<Decimal> intermediateValue;
    private final Decimal densityFactor;
    private final Decimal gamma;
    private final Decimal delta;
    
    /**
     * Constructor.
     *
     * @param series the series
     */
    public FisherIndicator(TimeSeries series) {
        this(new MedianPriceIndicator(series), 10);
    }
    
    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param price the price indicator (usually {@link MedianPriceIndicator})
     * @param timeFrame the time frame (usually 10)
     */
    public FisherIndicator(Indicator<Decimal> price, int timeFrame) {
        this(price, timeFrame, Decimal.valueOf(0.33), Decimal.valueOf(0.67), ZERO_DOT_FIVE, ZERO_DOT_FIVE, Decimal.ONE, true);
    }
    
    /**
     * Constructor (with gamma 0.5, delta 0.5).
     * 
     * @param price the price indicator (usually {@link MedianPriceIndicator})
     * @param timeFrame the time frame (usually 10)
     * @param alpha the alpha (usually 0.33 or 0.5)
     * @param beta the beta (usually 0.67 0.5 or)
     */
    public FisherIndicator(Indicator<Decimal> price, int timeFrame, double alpha, double beta) {
    		this(price, timeFrame, Decimal.valueOf(alpha), Decimal.valueOf(beta), ZERO_DOT_FIVE, ZERO_DOT_FIVE, Decimal.ONE, true);
    }
    
    /**
     * Constructor.
     * 
     * @param price the price indicator (usually {@link MedianPriceIndicator})
     * @param timeFrame the time frame (usually 10)
     * @param alpha the alpha (usually 0.33 or 0.5)
     * @param beta the beta (usually 0.67 or 0.5)
     * @param gamma the gamma (usually 0.25 or 0.5)
     * @param delta the delta (usually 0.5)
     */
    public FisherIndicator(Indicator<Decimal> price, int timeFrame, double alpha, double beta, double gamma, double delta) {
    		this(price, timeFrame, Decimal.valueOf(alpha), Decimal.valueOf(beta), Decimal.valueOf(gamma), Decimal.valueOf(delta), Decimal.ONE, true);
    }
    
    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     * 
     * @param ref the indicator
     * @param timeFrame the time frame (usually 10)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Decimal> ref, int timeFrame, boolean isPriceIndicator) {
        this(ref, timeFrame, Decimal.valueOf(0.33), Decimal.valueOf(0.67), ZERO_DOT_FIVE, ZERO_DOT_FIVE, Decimal.ONE, isPriceIndicator);
    }
    
    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     * 
     * @param ref the indicator
     * @param timeFrame the time frame (usually 10)
     * @param densityFactor the density factor (usually 1.0)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Decimal> ref, int timeFrame, double densityFactor, boolean isPriceIndicator) {
        this(ref, timeFrame, Decimal.valueOf(0.33), Decimal.valueOf(0.67), ZERO_DOT_FIVE, ZERO_DOT_FIVE, Decimal.valueOf(densityFactor), isPriceIndicator);
    }
    
    /**
     * Constructor.
     * 
     * @param ref the indicator
     * @param timeFrame the time frame (usually 10)
     * @param alpha the alpha (usually 0.33 or 0.5)
     * @param beta the beta (usually 0.67 or 0.5)
     * @param gamma the gamma (usually 0.25 or 0.5)
     * @param delta the delta (usually 0.5)
     * @param densityFactor the density factor (usually 1.0)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Decimal> ref, int timeFrame, double alpha, double beta, double gamma, double delta, double densityFactor, boolean isPriceIndicator) {
        this(ref, timeFrame, Decimal.valueOf(alpha), Decimal.valueOf(beta), Decimal.valueOf(gamma), Decimal.valueOf(delta), Decimal.valueOf(densityFactor), isPriceIndicator);
    }
    
    /**
     * Constructor
     *
     * @param ref the indicator
     * @param timeFrame the time frame (usually 10)
     * @param alpha the alpha (usually 0.33 or 0.5)
     * @param beta the beta (usually 0.67 or 0.5)
     * @param gamma the gamma (usually 0.25 or 0.5)
     * @param delta the delta (usually 0.5)
     * @param densityFactor the density factor (usually 1.0)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Decimal> ref, int timeFrame, final Decimal alpha, final Decimal beta,
        final Decimal gamma, final Decimal delta, Decimal densityFactor, boolean isPriceIndicator) {
        super(ref);
        this.ref = ref;
        this.gamma = gamma;
        this.delta = delta;

        if (densityFactor == null || densityFactor.isNaN()) {
            this.densityFactor = Decimal.ONE;
        } else {
            this.densityFactor = densityFactor;
        }

        final Indicator<Decimal> periodHigh = new HighestValueIndicator(isPriceIndicator ? new MaxPriceIndicator(ref.getTimeSeries()) : ref, timeFrame);
        final Indicator<Decimal> periodLow = new LowestValueIndicator(isPriceIndicator ? new MinPriceIndicator(ref.getTimeSeries()) : ref, timeFrame);

        intermediateValue = new RecursiveCachedIndicator<Decimal>(ref) {

            private static final long serialVersionUID = 1242564751445450654L;
            @Override
            protected Decimal calculate(int index) {
                if (index <= 0) {
                    return Decimal.ZERO;
                 }

                // Value = (alpha * 2 * ((ref - MinL) / (MaxH - MinL) - 0.5) + beta * priorValue) / densityFactor
                Decimal currentRef = FisherIndicator.this.ref.getValue(index);
                Decimal minL = periodLow.getValue(index);
                Decimal maxH = periodHigh.getValue(index);
                Decimal term1 = currentRef.minus(minL).dividedBy(maxH.minus(minL)).minus(ZERO_DOT_FIVE);
                Decimal term2 = alpha.multipliedBy(Decimal.TWO).multipliedBy(term1);
                Decimal term3 = term2.plus(beta.multipliedBy(getValue(index - 1)));
                return term3.dividedBy(FisherIndicator.this.densityFactor);
            }
        };
    }

    @Override
    protected Decimal calculate(int index) {
        if (index <= 0) {
            return Decimal.ZERO;
        }

        Decimal value = intermediateValue.getValue(index);

        if (value.isGreaterThan(VALUE_MAX)) {
            value = VALUE_MAX;
        } else if (value.isLessThan(VALUE_MIN)) {
            value = VALUE_MIN;
        }

        // Fisher = gamma * Log((1 + Value) / (1 - Value)) + delta * priorFisher
        Decimal term1 = Decimal.valueOf((Math.log(Decimal.ONE.plus(value).dividedBy(Decimal.ONE.minus(value)).doubleValue())));
        Decimal term2 = getValue(index - 1);
        return gamma.multipliedBy(term1).plus(delta.multipliedBy(term2));
    }

}
