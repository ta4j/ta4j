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

package org.ta4j.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.num.Num;

/**
 * Contextual implementation of a {@link TradingRecord}.
 * <p></p>
 */

public class ContextTradingRecord implements TradingRecord {

    /** The recorded trades */
    private List<Trade> trades = new ArrayList<Trade>();

    private Logger log = LoggerFactory.getLogger(ContextTradingRecord.class);

    /**
     * Constructor.
     * @param orders the orders to be recorded (cannot be empty)
     */
    public ContextTradingRecord(Order... orders) {
        log.trace("number of orders {}", orders.length);
        for (Order o : orders) {
            operate(o.getIndex(), o.getPrice(), o.getAmount());
        }
    }

    @Override
    public Trade getCurrentTrade() {
        return (trades.isEmpty()) ? null : trades.get(trades.size() - 1);
    }

    public Trade getCurrentTrade(int index) {
        log.trace("index {}", index);
        // ordered linear search, not great but low overhead with getters and int comparisons
        for (Trade trade : trades) {
            if (trade.getEntry().getIndex() > index) {
                log.trace("quit at newer Trade {}", trade);
                return null;
            }
            if (trade.getEntry().getIndex() <= index && trade.getExit() == null) {
                log.debug("found open Trade {}", trade);
                return trade;
            }
            if (trade.getEntry().getIndex() <= index && trade.getExit().getIndex() >= index) {
                log.debug("found current Trade {}", trade);
                return trade;
            }
        }
        return null;
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        log.trace("operate {} {} {}", index, price, amount);
        Trade currentTrade = getCurrentTrade(index);
        if (currentTrade == null) {
            currentTrade = new Trade();
            trades.add(currentTrade);
            log.debug("added new Trade");
        }
        if (currentTrade.isClosed()) {
            // Current trade closed, should not occur
            throw new IllegalStateException("Current trade should not be closed");
        }
        Order newOrder = currentTrade.operate(index, price, amount);
        log.debug("added new Order {}", newOrder);
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        Trade currentTrade = getCurrentTrade(index);
        if (currentTrade == null || currentTrade.getExit() == null) {
            operate(index, price, amount);
            return true;
        }
        log.warn("skip not-null or open Trade {}", currentTrade);
        return false;
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        Trade currentTrade = getCurrentTrade(index);
        if (currentTrade != null && currentTrade.isOpened()) {
            operate(index, price, amount);
            return true;
        }
        log.warn("skip not-open Trade {}", currentTrade);
        return false;
    }

    @Override
    public List<Trade> getTrades() {
        return getClosedTrades();
    }

    public List<Trade> getClosedTrades() {
        List<Trade> tradesCopy = new ArrayList<Trade>(trades);
        if (tradesCopy.isEmpty()) return tradesCopy;
        if (tradesCopy.get(tradesCopy.size() - 1).isOpened()) {
            tradesCopy.remove(tradesCopy.size() - 1);
        }
        return tradesCopy;
    }

    public List<Trade> getAllTrades() {
        return trades;
    }

    @Override
    public Order getLastOrder() {
        return getLastOrder(null);
    }

    @Override
    public Order getLastOrder(Order.OrderType orderType) {
        if (trades.isEmpty()) return null;
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade trade = trades.get(i);
            Order exit = trade.getExit();
            if (exit != null && (orderType == null || orderType.equals(exit.getType()))) return exit;
            Order entry = trade.getEntry();
            if (entry != null && (orderType == null || orderType.equals(entry.getType()))) return entry;
        }
        return null;
    }

    public Trade getLastTrade() {
        return getLastClosedTrade();
    }

    public Trade getLastClosedTrade() {
        List<Trade> closedTrades = getClosedTrades();
        return closedTrades.isEmpty() ? null : closedTrades.get(closedTrades.size() - 1);
    }

    @Override
    public Order getLastEntry() {
        List<Trade> allTrades = getAllTrades();
        return allTrades.isEmpty() ? null : allTrades.get(allTrades.size() - 1).getEntry();
    }

    @Override
    public Order getLastExit() {
        Trade lastClosedTrade = getLastClosedTrade();
        return lastClosedTrade == null ? null : lastClosedTrade.getExit();
    }

}
