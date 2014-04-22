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

/**
 * A constrained {@link TimeSeries time series}.
 * <p>
 * A constrained time series is a sub-set of a series.
 * It has begin and end indexes which correspond to the bounds of the sub-set into the full series.
 */
public class ConstrainedTimeSeries implements TimeSeries {

    /** Original time series */
    private TimeSeries series;
    /** Begin index of the time series */
    private int beginIndex;
    /** End index of the time series */
    private int endIndex;

    /**
     * Constructor.
     * @param series the original time series
     * @param beginIndex the begin index (inclusive) of the time series
     * @param endIndex the end index (inclusive) of the time series
     */
    public ConstrainedTimeSeries(TimeSeries series, int beginIndex, int endIndex) {
        if (endIndex < beginIndex - 1) {
            throw new IllegalArgumentException("end cannot be < than begin - 1");
        }
        this.series = series;
        this.beginIndex = beginIndex;
        this.endIndex = endIndex;
    }

    @Override
    public int getSize() {
        return (endIndex - beginIndex) + 1;
    }

    @Override
    public Tick getTick(int i) {
        return series.getTick(i);
    }

    @Override
    public int getBegin() {
        return beginIndex;
    }

    @Override
    public int getEnd() {
        return endIndex;
    }

    @Override
    public String getName() {
        return series.getName();
    }

    @Override
    public String getPeriodName() {
        return series.getTick(beginIndex).getEndTime().toString("hh:mm dd/MM/yyyy - ")
                + series.getTick(endIndex).getEndTime().toString("hh:mm dd/MM/yyyy");
    }

    @Override
    public Period getPeriod() {
        return new Period(Math.min(series.getTick(series.getBegin() + 1).getEndTime().getMillis() - series.getTick(series.getBegin()).getEndTime().getMillis(), 
                series.getTick(series.getBegin() + 2).getEndTime().getMillis() - series.getTick(series.getBegin() + 1).getEndTime().getMillis()));
    }
}
