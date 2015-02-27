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
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.Operation.OperationType;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the full trading record when running a {@link Strategy strategy}.
 */
public class TradingRecord {

    private List<Trade> trades = new ArrayList<Trade>();

    private OperationType startingType;
    
    private Trade currentTrade;

    /**
     * Constructor.
     * @param startingType the starting {@link OperationType operation type} of the trading session
     */
    public TradingRecord(OperationType startingType) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
        currentTrade = new Trade(startingType);
    }

    public TradingRecord(Operation... operations) {
        // TODO add length check
        this(operations[0].getType());
        for (int i = 0; i < operations.length - 1; i += 2) {
            Operation o1 = operations[i];
            Operation o2 = i+1 < operations.length ? operations[i+1] : null;
            currentTrade = new Trade(o1, o2);
            if (currentTrade.isClosed()) {
                // Adding the trade when closed
                trades.add(currentTrade);
                currentTrade = new Trade(startingType);
            }
        }
    }
    
    public Trade getCurrentTrade() {
        return currentTrade;
    }
    
    public void addTrade(Trade trade) {
        trades.add(trade);
    }
    
    public void operate(int index) {
        operate(index, Decimal.NaN, Decimal.NaN);
    }
    
    public final void operate(int index, Decimal price, Decimal amount) {
        currentTrade.operate(index);
        if (currentTrade.isClosed()) {
            // Adding the trade when closed
            trades.add(currentTrade);
            currentTrade = new Trade(startingType);
        }
    }
    
    public boolean isClosed() {
        return !currentTrade.isOpened();
    }
    
    public boolean isEmpty() {
        return trades.isEmpty();
    }
    
    public Trade getTrade(int index) {
        return trades.get(index);
    }
    
    public List<Trade> getTrades() {
        return trades;
    }
    
    public int getTradeCount() {
        return trades.size();
    }
}
