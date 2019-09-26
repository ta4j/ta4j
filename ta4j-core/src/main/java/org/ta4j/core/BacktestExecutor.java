/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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
package org.ta4j.core;

import org.ta4j.core.num.Num;
import org.ta4j.core.tradereport.TradingStatement;
import org.ta4j.core.tradereport.TradingStatementGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * This class enables backtesting of multiple strategies and comparing them to
 * see which is the best
 */
public class BacktestExecutor {

    private final TradingStatementGenerator tradingStatementGenerator;
    private final BarSeriesManager seriesManager;

    public BacktestExecutor(BarSeries series) {
        this(series, new TradingStatementGenerator());
    }

    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator) {
        this.seriesManager = new BarSeriesManager(series);
        this.tradingStatementGenerator = tradingStatementGenerator;
    }

    /**
     * Execute given strategies and return trading statements
     *
     * @param amount - The amount used to open/close the trades
     */
    public List<TradingStatement> execute(List<Strategy> strategies, Num amount) {
        return execute(strategies, amount, Order.OrderType.BUY);
    }

    /**
     * Execute given strategies with specified order type to open trades and return
     * trading statements
     *
     * @param amount    - The amount used to open/close the trades
     * @param orderType the {@link Order.OrderType} used to open the trades
     */
    public List<TradingStatement> execute(List<Strategy> strategies, Num amount, Order.OrderType orderType) {
        final List<TradingStatement> tradingStatements = new ArrayList<>(strategies.size());
        for (Strategy strategy : strategies) {
            final TradingRecord tradingRecord = seriesManager.run(strategy, orderType, amount);
            final TradingStatement tradingStatement = tradingStatementGenerator.generate(strategy, tradingRecord,
                    seriesManager.getBarSeries());
            tradingStatements.add(tradingStatement);
        }
        return tradingStatements;
    }
}
