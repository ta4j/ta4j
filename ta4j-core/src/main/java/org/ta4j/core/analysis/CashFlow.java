/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cash flow.
 * <p></p>
 * This class allows to follow the money cash flow involved by a list of trades over a time series.
 */
public class CashFlow implements Indicator<Num> {

    /** The time series */
    private final TimeSeries timeSeries;

    /** The cash flow values */
    private List<Num> values;

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param trade a single trade
     */
    public CashFlow(TimeSeries timeSeries, Trade trade) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(trade);
        fillToTheEnd();
    }

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param tradingRecord the trading record
     */
    public CashFlow(TimeSeries timeSeries, TradingRecord tradingRecord) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord);

        fillToTheEnd();
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    @Override
    public Num numOf(Number number) {
        return timeSeries.numOf(number);
    }

    /**
     * @return the size of the time series
     */
    public int getSize() {
        return timeSeries.getBarCount();
    }

    /**
     * Calculates the cash flow for a single trade.
     * @param trade a single trade
     */
    private void calculate(Trade trade) {
        final int entryIndex = trade.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(begin - values.size(), lastValue));
        }
        int end = trade.getExit().getIndex();
        for (int i = Math.max(begin, 1); i <= end; i++) {
            Num ratio;
            if (trade.getEntry().isBuy()) {
                ratio = timeSeries.getBar(i).getClosePrice().dividedBy(timeSeries.getBar(entryIndex).getClosePrice());
            } else {
                ratio = timeSeries.getBar(entryIndex).getClosePrice().dividedBy(timeSeries.getBar(i).getClosePrice());
            }
            values.add(values.get(entryIndex).multipliedBy(ratio));
        }
    }

    /**
     * Calculates the cash flow for a trading record.
     * @param tradingRecord the trading record
     */
    private void calculate(TradingRecord tradingRecord) {
        for (Trade trade : tradingRecord.getTrades()) {
            // For each trade...
            calculate(trade);
        }
    }

    /**
     * Fills with last value till the end of the series.
     */
    private void fillToTheEnd() {
        if (timeSeries.getEndIndex() >= values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(timeSeries.getEndIndex() - values.size() + 1, lastValue));
        }
    }
}