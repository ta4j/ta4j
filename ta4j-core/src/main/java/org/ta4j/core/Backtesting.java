package org.ta4j.core;

import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Backtesting {

    private TimeSeries seriesToTradeOn;
    private AbstractAnalysisCriterion criterion;
    private TimeSeriesManager seriesManager;
    private List<Strategy> strategies = new ArrayList<>();
    private List<BacktestingResult> backtestingResults = new ArrayList<>();

    public Backtesting(TimeSeries series, AbstractAnalysisCriterion criterion) {
        this(series, series, criterion);
    }

    public Backtesting(TimeSeries seriesToBacktest, TimeSeries seriesToTradeOn, AbstractAnalysisCriterion criterion) {
        this.seriesToTradeOn = seriesToTradeOn;
        this.criterion = criterion;
        this.seriesManager = new TimeSeriesManager(seriesToBacktest);
    }

    public void addStrategy(Strategy strategy) {
        this.strategies.add(strategy);
    }

    public List<BacktestingResult> getBacktestingResults() {
        return backtestingResults;
    }

    public void calculate(Num amount, PriceType priceType) {
        for(Strategy strategy : strategies) {
            TradingRecord tradingRecord = seriesManager.run(strategy, Order.OrderType.BUY, amount, priceType);
            Num calculation = criterion.calculate(seriesToTradeOn, tradingRecord);
            backtestingResults.add(new BacktestingResult(strategy.getName(), strategy, tradingRecord, calculation));
        }
    }

    public void printBacktestingResults(boolean printTrades) {
        Collections.sort(backtestingResults);
        for(BacktestingResult backtestingResult : backtestingResults) {
            backtestingResult.printBacktestingResult(printTrades, seriesToTradeOn);
        }
    }
}
