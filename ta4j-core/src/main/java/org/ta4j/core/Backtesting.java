package org.ta4j.core;

import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.analysis.criteria.ProfitLossCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Backtesting {

    private TimeSeries seriesToTradeOn;
    private TimeSeriesManager seriesManager;
    private PriceType priceType;
    private List<Strategy> strategies = new ArrayList<>();
    private List<BacktestingResult> backtestingResults = new ArrayList<>();

    public Backtesting(TimeSeries series, PriceType priceType) {
        this(series, series, priceType);
    }

    public Backtesting(TimeSeries seriesToBacktest, TimeSeries seriesToTradeOn, PriceType priceType) {
        this.seriesToTradeOn = seriesToTradeOn;
        this.seriesManager = new TimeSeriesManager(seriesToBacktest);
        this.priceType = priceType;
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
            backtestingResults.add(new BacktestingResult(strategy.getName(), strategy, tradingRecord, seriesToTradeOn, priceType));
        }
    }

    public void printBacktestingResults(boolean printTrades) {
        Collections.sort(backtestingResults);
        for(BacktestingResult backtestingResult : backtestingResults) {
            backtestingResult.printBacktestingResult(printTrades, seriesToTradeOn);
        }
    }
}
