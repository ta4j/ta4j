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
package eu.verdelhan.ta4j.series;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import org.joda.time.Period;

public class ConstrainedTimeSeries implements TimeSeries {

	private TimeSeries series;

	private int begin;

	private int end;

	/**
	 * @param series the original time series
	 * @param begin the begin index of the time series
	 * @param end the end index of the time series
	 */
	public ConstrainedTimeSeries(TimeSeries series, int begin, int end) {
		if (end < begin - 1) {
			throw new IllegalArgumentException("end cannot be < than begin - 1");
		}
		this.series = series;
		this.begin = begin;
		this.end = end;
	}

	@Override
	public int getSize() {
		return (end - begin) + 1;
	}

	@Override
	public Tick getTick(int i) {
		return series.getTick(i);
	}

	@Override
	public int getBegin() {
		return begin;
	}

	@Override
	public int getEnd() {
		return end;
	}

	@Override
	public String getName() {
		return series.getName();
	}

	@Override
	public String getPeriodName() {
		return series.getTick(begin).getEndTime().toString("hh:mm dd/MM/yyyy - ")
				+ series.getTick(end).getEndTime().toString("hh:mm dd/MM/yyyy");
	}

	@Override
	public Period getPeriod() {
		return new Period(Math.min(series.getTick(series.getBegin() + 1).getEndTime().getMillis() - series.getTick(series.getBegin()).getEndTime().getMillis(), 
				series.getTick(series.getBegin() + 2).getEndTime().getMillis() - series.getTick(series.getBegin() + 1).getEndTime().getMillis()));
	}
}
