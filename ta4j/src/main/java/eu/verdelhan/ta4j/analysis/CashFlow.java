/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.analysis;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cash flow.
 * <p>
 * This class allows to follow the money cash flow involved by a list of trades over a time series.
 */
public class CashFlow implements Indicator<TADecimal> {

    /** The time series */
    private final TimeSeries timeSeries;

    /** The list of trades */
    private final List<Trade> trades;

    private List<TADecimal> values;

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param trades the list of trades
     */
    public CashFlow(TimeSeries timeSeries, List<Trade> trades) {
        this.timeSeries = timeSeries;
        this.trades = trades;
        values = new ArrayList<TADecimal>();
        values.add(TADecimal.ONE);
        calculate();
    }

    /**
     * @param index the index
     * @return the cash flow value at the index-th position
     */
    @Override
    public TADecimal getValue(int index) {
        return values.get(index);
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    /**
     * @return the size of the time series
     */
    public int getSize() {
        return timeSeries.getTickCount();
    }

    /**
     * Calculates the cash flow.
     */
    private void calculate() {

        for (Trade trade : trades) {
            // For each trade...
            int begin = trade.getEntry().getIndex() + 1;
            if (begin > values.size()) {
                values.addAll(Collections.nCopies(begin - values.size(), values.get(values.size() - 1)));
            }
            int end = trade.getExit().getIndex();
            for (int i = Math.max(begin, 1); i <= end; i++) {
                TADecimal ratio;
                if (trade.getEntry().isBuy()) {
                    ratio = timeSeries.getTick(i).getClosePrice().dividedBy(timeSeries.getTick(trade.getEntry().getIndex()).getClosePrice());
                } else {
                    ratio = timeSeries.getTick(trade.getEntry().getIndex()).getClosePrice().dividedBy(timeSeries.getTick(i).getClosePrice());
                }
                values.add(values.get(trade.getEntry().getIndex()).multipliedBy(ratio));
            }
        }
        if ((timeSeries.getEnd() - values.size()) >= 0) {
            values.addAll(Collections.nCopies((timeSeries.getEnd() - values.size()) + 1, values.get(values.size() - 1)));
        }
    }
}