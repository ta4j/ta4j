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
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.AverageTrueRangeIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;

/**
 * The Random Walk Index High Indicator. <p />
 *
 * See https://www.technicalindicators.net/indicators-technical-analysis/168-rwi-random-walk-index
 */
public class RWIHighIndicator extends CachedIndicator<Decimal> {

    private final MaxPriceIndicator maxPrice;
    
    private final MinPriceIndicator minPrice;
    
    private final int timeFrame;
    
    /**
     * Constructor.
     *
     * @param series the series
     * @param timeFrame the time frame
     */
    public RWIHighIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        maxPrice = new MaxPriceIndicator(series);
        minPrice = new MinPriceIndicator(series);

    }

    @Override
    protected Decimal calculate(int index) {
        Decimal highestRWI = Decimal.NaN;
        
        for(int n = 2; n <= this.timeFrame; n++) {
           Decimal currentRWI = calcRWIHighValue(index, index-n, n);
           if(currentRWI.isGreaterThan(highestRWI)){
               highestRWI = currentRWI;
           }   
        }
        return highestRWI;
    }
    
    /**
     * @param t = current index
     * @param t_n = current index - n
     * @param n = n starting at 2 increments until n = time frame
     */
    private Decimal calcRWIHighValue(int t, int t_n, int n){
        AverageTrueRangeIndicator averageTrueRange = new AverageTrueRangeIndicator(getTimeSeries(), n);
        return maxPrice.getValue(t).minus(minPrice.getValue(t_n))
                    .dividedBy(averageTrueRange.getValue(t).multipliedBy(Decimal.valueOf(Math.sqrt(n))));
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }

}
