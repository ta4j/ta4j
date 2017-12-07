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
package org.ta4j.core.indicators;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Lower donchian channel indicator. 
 * <p>
 * Returns the highest value of the time series within the tiemframe.
 *
 */
public class DonchianChannelUpper extends CachedIndicator<Decimal> {

	private static final long serialVersionUID = 6109484986843725281L;

	/**
	 * The price indicator
	 */
    private Indicator<Decimal> indicator;
    
    /**
     * The time frame of the channel
     */
	private int timeFrame;

	public DonchianChannelUpper(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator.getTimeSeries());
		this.indicator = indicator;
		this.timeFrame = timeFrame;
    }

	
	@Override
	protected Decimal calculate(int index) {
		int startIndex = Math.max(0, index - timeFrame + 1);
		
		Decimal result = indicator.getValue(index);
		
		for(int pos = startIndex; pos <= index; pos++) {
			final Decimal value = indicator.getValue(pos);
			
			if(value.isGreaterThan(result)) {
				result = value;
			}
		}
		
		return result;
	}
	
    @Override
    public String toString() {
        return getClass().getSimpleName() + "timeFrame: " + timeFrame;
    }

}
