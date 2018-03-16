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
package org.ta4j.core.mocks;

import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.function.Function;


/**
 * A mock bar with sample data.
 */
public class MockBar extends BaseBar {

    private int trades = 0;

    public MockBar(double closePrice, Function<Number, Num> numFunction) {
        this(ZonedDateTime.now(), closePrice, numFunction);
    }

    public MockBar(double closePrice, double volume, Function<Number, Num> numFunction) {
        super(ZonedDateTime.now(), 0, 0, 0, closePrice, volume, numFunction);
    }

    public MockBar(ZonedDateTime endTime, double closePrice, Function<Number, Num> numFunction) {
        super(endTime, 0, 0, 0, closePrice, 0, numFunction);
    }

    public MockBar(ZonedDateTime endTime, double closePrice, double volume, Function<Number, Num> numFunction) {
        super(endTime, 0, 0, 0, closePrice, volume, numFunction);
    }

    public MockBar(double openPrice, double closePrice, double maxPrice, double minPrice, Function<Number, Num> numFunction) {
        super(ZonedDateTime.now(), openPrice, maxPrice, minPrice, closePrice, 1,numFunction);
    }

    public MockBar(double openPrice, double closePrice, double maxPrice, double minPrice, double volume, Function<Number, Num> numFunction) {
        super(ZonedDateTime.now(), openPrice, maxPrice, minPrice, closePrice, volume,numFunction);
    }

    public MockBar(ZonedDateTime endTime, double openPrice, double closePrice, double maxPrice, double minPrice, double amount, double volume, int trades, Function<Number, Num> numFunction) {
        super(endTime, openPrice, maxPrice, minPrice, closePrice, volume,numFunction);
        this.trades = trades;
    }

    @Override
    public int getTrades() {
        return trades;
    }
}
