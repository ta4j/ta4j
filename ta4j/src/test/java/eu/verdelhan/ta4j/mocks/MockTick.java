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
package eu.verdelhan.ta4j.mocks;

import org.joda.time.DateTime;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Tick;

/**
 * A mock tick with sample data.
 */
public class MockTick extends Tick {

    private Decimal amount = Decimal.ZERO;

    private int trades = 0;

    public MockTick(double closePrice) {
        this(new DateTime(), closePrice);
    }

    public MockTick(double closePrice, double volume) {
        super(new DateTime(), 0, 0, 0, closePrice, volume);
    }
    
    public MockTick(DateTime endTime, double closePrice) {
        super(endTime, 0, 0, 0, closePrice, 0);
    }

    public MockTick(double openPrice, double closePrice, double maxPrice, double minPrice) {
        super(new DateTime(), openPrice, maxPrice, minPrice, closePrice, 1);
    }
    
    public MockTick(double openPrice, double closePrice, double maxPrice, double minPrice, double volume) {
        super(new DateTime(), openPrice, maxPrice, minPrice, closePrice, volume);
    }

    public MockTick(DateTime endTime, double openPrice, double closePrice, double maxPrice, double minPrice, double amount, double volume, int trades) {
        super(endTime, openPrice, maxPrice, minPrice, closePrice, volume);
        this.amount = Decimal.valueOf(amount);
        this.trades = trades;
    }

    @Override
    public Decimal getAmount() {
        return amount;
    }

    @Override
    public int getTrades() {
        return trades;
    }
}
