package org.ta4j.core;

import org.ta4j.core.num.Num;
import org.ta4j.core.tradereport.TradingStatement;
import org.ta4j.core.tradereport.TradingStatementBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * This class enables backtesting of multiple strategies and comparing them to see which is the best
 */
public class BacktestExecutor {

    private final TradingStatementBuilder tradingStatementBuilder = new TradingStatementBuilder();
    private final TimeSeriesManager seriesManager;

    public BacktestExecutor(TimeSeries series) {
        this.seriesManager = new TimeSeriesManager(series);
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
     * Execute given strategies with specified order type to open trades and return trading statements
     *
     * @param amount    - The amount used to open/close the trades
     * @param orderType the {@link Order.OrderType} used to open the trades
     */
    public List<TradingStatement> execute(List<Strategy> strategies, Num amount, Order.OrderType orderType) {
        final List<TradingStatement> tradingStatements = new ArrayList<>(strategies.size());
        for (Strategy strategy : strategies) {
            final TradingRecord tradingRecord = seriesManager.run(strategy, orderType, amount);
            final TradingStatement tradingStatement = tradingStatementBuilder.buildReport(tradingRecord, seriesManager.getTimeSeries());
            tradingStatements.add(tradingStatement);
        }
        return tradingStatements;
    }
}
