package org.ta4j.core;

import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
            //String strategyName, Strategy strategy, TradingRecord tradingRecord, Num calculation, Num totalProfit, int profitTradeCount, Num totalLoss, int lossTradeCount, int breakEvenTradeCount
            Num calculation = criterion.calculate(seriesToTradeOn, tradingRecord);
            Num totalProfit = getTotalProfit(tradingRecord);
            long profitTradeCount = getProfitTradeCount(tradingRecord);
            Num totalLoss = getTotalLoss(tradingRecord);
            long lossTradeCount = getLossTradeCount(tradingRecord);
            long breakEvenTradeCount = getBreakEvenTradeCount(tradingRecord);
            backtestingResults.add(new BacktestingResult(strategy.getName(), strategy, tradingRecord, calculation, totalProfit, profitTradeCount, totalLoss, lossTradeCount, breakEvenTradeCount));
        }
    }

    public void printBacktestingResults(boolean printTrades) {
        Collections.sort(backtestingResults);
        for(BacktestingResult backtestingResult : backtestingResults) {
            backtestingResult.printBacktestingResult(printTrades, seriesToTradeOn);
        }
    }

    private Num getTotalProfit(TradingRecord tradingRecord) {
        List<Trade> profitTrades = tradingRecord.getTrades().stream()
                .filter(trade -> trade.getProfit().isGreaterThan(PrecisionNum.valueOf(0)))
                .collect(Collectors.toList());

        Num totalProfit = PrecisionNum.valueOf(0);
        for(Trade trade : profitTrades) {
            totalProfit = totalProfit.plus(trade.getProfit());
        };
        return totalProfit;
    }

    private Num getTotalLoss(TradingRecord tradingRecord) {
        List<Trade> lossTrades = tradingRecord.getTrades().stream()
                .filter(trade -> trade.getProfit().isLessThan(PrecisionNum.valueOf(0)))
                .collect(Collectors.toList());

        Num totalLoss = PrecisionNum.valueOf(0);
        for(Trade trade : lossTrades) {
            totalLoss = totalLoss.plus(trade.getProfit());
        };
        return totalLoss;
    }

    private long getProfitTradeCount(TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream().filter(trade -> trade.getProfit().isGreaterThan(PrecisionNum.valueOf(0))).count();
    }

    private long getLossTradeCount(TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream().filter(trade -> trade.getProfit().isLessThan(PrecisionNum.valueOf(0))).count();
    }

    private long getBreakEvenTradeCount(TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream().filter(trade -> trade.getProfit().isEqual(PrecisionNum.valueOf(0))).count();
    }

}
