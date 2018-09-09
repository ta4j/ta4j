package org.ta4j.core;

import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class enables backtesting of multiple strategies and comparing them to see wich is the best
 */
public class Backtesting {

    private TimeSeries seriesToTradeOn;
    private TimeSeriesManager seriesManager;
    private List<Strategy> strategies = new ArrayList<>();
    private List<BacktestingResult> backtestingResults = new ArrayList<>();

    public Backtesting(TimeSeries series) {
        this(series, series);
    }

    public Backtesting(TimeSeries seriesToBacktest, TimeSeries seriesToTradeOn) {
        this.seriesToTradeOn = seriesToTradeOn;
        this.seriesManager = new TimeSeriesManager(seriesToBacktest);
    }

    public void addStrategy(Strategy strategy) {
        this.strategies.add(strategy);
    }

    public void addStrategies(List<Strategy> strategies) {
        this.strategies.addAll(strategies);
    }

    public List<BacktestingResult> getBacktestingResults() {
        return backtestingResults;
    }

    /**
     * Calculate your strategies and giving you a backtesting result
     *
     * @param amount    - The amount used to open/close the trades
     */
    public List<BacktestingResult> calculate(Num amount) {
        return calculate(Order.OrderType.BUY, amount);
    }

    /**
     * Calculate your strategies and giving you a backtesting result
     *
     * @param orderType the {@link Order.OrderType} used to open the trades
     * @param amount    - The amount used to open/close the trades
     */
    public List<BacktestingResult> calculate(Order.OrderType orderType, Num amount) {
        for (Strategy strategy : strategies) {
            TradingRecord tradingRecord = seriesManager.run(strategy, orderType, amount);
            backtestingResults.add(new BacktestingResult(strategy, tradingRecord, seriesToTradeOn));
        }
        return backtestingResults;
    }

    /**
     * Print to console the backtesting results
     *
     * @param printTrades - Print trades on each backtesting result
     */
    public void printBacktestingResults(boolean printTrades) {
        Collections.sort(backtestingResults);
        for (BacktestingResult backtestingResult : backtestingResults) {
            backtestingResult.printBacktestingResult(printTrades, seriesToTradeOn);
        }
    }
}
