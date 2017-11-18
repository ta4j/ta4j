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
package org.ta4j.core.indicators.randomwalk;

import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.AverageTrueRangeIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;

/**
 * The Random Walk Index High Indicator. <p/>
 *
 * See https://www.technicalindicators.net/indicators-technical-analysis/168-rwi-random-walk-index
 */
public class RWIHighIndicator extends CachedIndicator<Decimal> {

    private final MaxPriceIndicator maxPrice;
    private final MinPriceIndicator minPrice;
    private final int timeFrame;

    /**
     * Constructor
     * <p/>
     * @param series the time series
     * @param timeFrame the time frame
     */
    public RWIHighIndicator(TimeSeries series, int timeFrame) {
        super(series);
        if(timeFrame <= 0){
            throw new IllegalArgumentException("Time frame must be bigger than zero");
        }

        this.timeFrame = timeFrame;
        maxPrice = new MaxPriceIndicator(series);
        minPrice = new MinPriceIndicator(series);

    }

    @Override
    protected Decimal calculate(int index) {
        Decimal highestRWI = Decimal.NaN;

        if(index <= timeFrame){
            return highestRWI;
        }

        for(int n = index-1; n >= index-timeFrame+1; n--) {
           Decimal currentRWI = calcRWIHighValue(index, n);
           if(currentRWI.isGreaterThan(highestRWI) || highestRWI.isNaN()){
               highestRWI = currentRWI;
           }   
        }
        return highestRWI;
    }

    /**
     * Calculates the current RWI
     * @param endIndex tick index of the current calculation
     * @param currentIndex iterates from index-1 to index-timeFrame+1
     * @return the RWI value for the current iteration
     */
    private Decimal calcRWIHighValue(int endIndex, int currentIndex){
        AverageTrueRangeIndicator averageTrueRange = new AverageTrueRangeIndicator(getTimeSeries(), endIndex-currentIndex);
        return maxPrice.getValue(endIndex).minus(minPrice.getValue(currentIndex))
                    .dividedBy(averageTrueRange.getValue(endIndex).multipliedBy(Decimal.valueOf(Math.sqrt(endIndex-currentIndex))));
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " time frame: "+timeFrame;
    }

}
