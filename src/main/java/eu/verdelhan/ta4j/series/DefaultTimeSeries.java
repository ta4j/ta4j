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
import java.util.List;
import org.joda.time.Period;

/**
 * Default implementation of {@link TimeSeries}.
 * <p>
 * This kind of time series has a name and a list of {@link Tick ticks}.
 */
public class DefaultTimeSeries implements TimeSeries {
	/** List of ticks */
    private final List<? extends Tick> ticks;
	/** Name of the series */
    private final String name;

	/**
	 * Constructor.
	 * @param name the name of the series
	 * @param ticks the list of ticks of the series
	 */
    public DefaultTimeSeries(String name, List<? extends Tick> ticks) {
        this.name = name;
        this.ticks = ticks;
    }

	/**
	 * Constructor of an unnamed series.
	 * @param ticks the list of ticks of the series
	 */
    public DefaultTimeSeries(List<? extends Tick> ticks) {
        this("unnamed", ticks);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Tick getTick(int i) {
        return ticks.get(i);
    }

    @Override
    public int getSize() {
        return ticks.size();
    }

    @Override
    public int getBegin() {
        return 0;
    }

    @Override
    public int getEnd() {
        return getSize() - 1;
    }

    @Override
    public String getPeriodName() {
        return ticks.get(0).getEndTime().toString("hh:mm dd/MM/yyyy - ")
                + ticks.get(getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy");
    }

    @Override
    public Period getPeriod() {
        return new Period(Math.min(ticks.get(1).getEndTime().getMillis() - ticks.get(0).getEndTime().getMillis(), ticks
                .get(2).getEndTime().getMillis()
                - ticks.get(1).getEndTime().getMillis()));
    }
}