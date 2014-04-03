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


import org.joda.time.DateTime;

/**
 * End tick of a period.
 */
public interface Tick {

    /**
     * @return the begin timestamp of the tick period
     */
    DateTime getBeginTime();

    /**
     * @return the end timestamp of the tick period
     */
    DateTime getEndTime();

    /**
     * @return a human-friendly string of the end timestamp
     */
    String getDateName();

    /**
     * @return a even more human-friendly string of the end timestamp
     */
    String getSimpleDateName();

    /**
     * @return the close price of the period
     */
    double getClosePrice();

    /**
     * @return the open price of the period
     */
    double getOpenPrice();

    /**
     * @return the number of trades in the period
     */
    int getTrades();

    /**
     * @return the max price of the period
     */
    double getMaxPrice();

    /**
     * @return the whole traded amount of the period
     */
    double getAmount();

    /**
     * @return the whole traded volume in the period
     */
    double getVolume();

    /**
     * @return the min price of the period
     */
    double getMinPrice();
}
