package org.ta4j.core;

import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;

/**
 * This class enables backtesting of multiple strategies and comparing them to see which is the best
 */
public class Backtest {

    private TimeSeries seriesToTradeOn;
    private TimeSeriesManager seriesManager;
    private List<Strategy> strategies = new ArrayList<>();
    private List<BacktestResult> backtestResults = new ArrayList<>();

    public Backtest(TimeSeries series) {
        this(series, series);
    }

    public Backtest(TimeSeries seriesToBacktest, TimeSeries seriesToTradeOn) {
        this.seriesToTradeOn = seriesToTradeOn;
        this.seriesManager = new TimeSeriesManager(seriesToBacktest);
    }

    public void addStrategy(Strategy strategy) {
        this.strategies.add(strategy);
    }

    public void addStrategies(List<Strategy> strategies) {
        this.strategies.addAll(strategies);
    }

    public List<BacktestResult> getBacktestResults() {
        return backtestResults;
    }

    /**
     * Calculate your strategies and giving you a backtesting result
     *
     * @param amount    - The amount used to open/close the trades
     */
    public List<BacktestResult> calculate(Num amount) {
        return calculate(Order.OrderType.BUY, amount);
    }

    /**
     * Calculate your strategies and giving you a backtesting result
     *
     * @param orderType the {@link Order.OrderType} used to open the trades
     * @param amount    - The amount used to open/close the trades
     */
    public List<BacktestResult> calculate(Order.OrderType orderType, Num amount) {
        for(int i=0; i<strategies.size(); i++) {
            Strategy strategy = strategies.get(i);
            TradingRecord tradingRecord = seriesManager.run(strategy, orderType, amount);
            backtestResults.add(new BacktestResult(strategy, tradingRecord, seriesToTradeOn));
        }
        return backtestResults;
    }
}
