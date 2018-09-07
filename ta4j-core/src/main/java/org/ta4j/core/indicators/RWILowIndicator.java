/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The Class RandomWalkIndexLowIndicator.
 * @see <a href="http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html">Source of formular</a>
 */
public class RWILowIndicator extends CachedIndicator<Num>{
    
    private final int barCount;
    
    /**
     * Constructor.
     *
     * @param series the series
     * @param barCount the time frame
     */
    public RWILowIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
    	if (index - barCount + 1 < getTimeSeries().getBeginIndex()) {
    		return NaN.NaN;
    	}
    	
    	Num minRWIL = numOf(0);
    	for(int n = 2; n <= barCount; n++) {
    		minRWIL = minRWIL.max(calcRWIHFor(index, n));
    	}
    	
    	return minRWIL;
    }
    
    private Num calcRWIHFor(final int index, final int n) {
    	TimeSeries series = getTimeSeries();
    	Num low = series.getBar(index).getMinPrice();
    	Num high_N = series.getBar(index+1-n).getMaxPrice();
    	Num atr_N = new ATRIndicator(series, n).getValue(index);
    	Num sqrt_N = numOf(n).sqrt();
    	
    	return high_N.minus(low).dividedBy(atr_N.multipliedBy(sqrt_N));
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
