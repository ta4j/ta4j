/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.rules;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;

/**
 * Satisfied when the tradingRecord has at most a maximum number of trades
 * executed up to the index.
 */
public class MaximumNumberOfTradesRule extends AbstractRule {

    private final TradeType tradeType;
    private final int maxNumberOfTrades;

    /**
     * Constructor with the following settings:
     *
     * <ul>
     * <li>{@link #tradeType} = {@link TradeType#BUY}
     * <li>{@link #maxNumberOfTrades} = {@code 1}
     * </ul>
     */
    public MaximumNumberOfTradesRule() {
        this(TradeType.BUY, 1);
    }

    /**
     * Constructor.
     *
     * @param tradeType
     * @param maxNumberOfTrades the maximum number of trades of the given
     *                          {@code tradeType} that the TradingRecord must have
     */
    public MaximumNumberOfTradesRule(TradeType tradeType, int maxNumberOfTrades) {
        this.tradeType = tradeType;
        this.maxNumberOfTrades = maxNumberOfTrades;
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = true;

        if (tradingRecord != null && !tradingRecord.getTrades().isEmpty()) {
            var numberOfTrades = tradingRecord.getTrades()
                    .stream()
                    .filter(t -> t.getIndex() <= index && t.getType() == tradeType)
                    .count();
            satisfied = numberOfTrades <= maxNumberOfTrades;
        }

        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
