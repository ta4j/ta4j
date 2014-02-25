/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

/**
 * True range indicator.
 * <p>
 */
public class TrueRangeIndicator implements Indicator<Double>{

	private TimeSeries series;

	public TrueRangeIndicator(TimeSeries series) {
		this.series = series;
	}
	
	@Override
	public Double getValue(int index) {
		double ts = series.getTick(index).getMaxPrice() - series.getTick(index).getMinPrice();
		double ys = index == 0 ? 0 : series.getTick(index).getMaxPrice() - series.getTick(index - 1).getClosePrice();
		double yst = index == 0 ? 0 : series.getTick(index - 1).getClosePrice() - series.getTick(index).getMinPrice();
		double max = Math.max(Math.abs(ts), Math.abs(ys));
		
		return Math.max(max, Math.abs(yst));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
