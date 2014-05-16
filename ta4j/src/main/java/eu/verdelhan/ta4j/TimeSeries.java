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
package eu.verdelhan.ta4j;

import org.joda.time.Period;

/**
 * Set of ticks separated by a predefined period (e.g. 15 minutes)
 */
public interface TimeSeries {

    /**
     * @param i an index
     * @return the tick at the i position
     */
    Tick getTick(int i);

    /**
     * @return the number of ticks in the series
     */
    int getSize();

    /**
     * @return the begin index of the series
     */
    int getBegin();

    /**
     * @return the end index of the series
     */
    int getEnd();

    /**
     * @return the name of the series
     */
    String getName();

    /**
     * @return the period name of the series (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
     */
    String getPeriodName();

    /**
     * @return the period of the series
     */
    Period getPeriod();
}
