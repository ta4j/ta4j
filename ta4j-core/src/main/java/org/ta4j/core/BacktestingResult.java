package org.ta4j.core;

import org.ta4j.core.analysis.criteria.*;
import org.ta4j.core.num.Num;


public class BacktestingResult implements Comparable<BacktestingResult>{

    private String strategyName;
    private Strategy strategy;
    private TradingRecord tradingRecord;
    private Num totalProfitLoss;
    private Num totalProfit;
    private Num totalLoss;
    private Num profitTradeCount;
    private Num lossTradeCount;
    private Num breakEvenTradeCount;

    public BacktestingResult(String strategyName, Strategy strategy, TradingRecord tradingRecord, TimeSeries series) {
        this.strategyName = strategyName;
        this.strategy = strategy;
        this.tradingRecord = tradingRecord;
        this.totalProfitLoss = new ProfitLossCriterion(PriceType.OPEN).calculate(series, tradingRecord);
        this.totalProfit = new TotalProfit2Criterion(PriceType.OPEN).calculate(series, tradingRecord);
        this.totalLoss = new TotalLossCriterion(PriceType.OPEN).calculate(series, tradingRecord);
        this.profitTradeCount = new NumberOfWinningTradesCriterion(PriceType.OPEN).calculate(series, tradingRecord);
        this.lossTradeCount = new NumberOfLosingTradesCriterion(PriceType.OPEN).calculate(series, tradingRecord);
        this.breakEvenTradeCount = new NumberOfBreakEvenTradesCriterion(PriceType.OPEN).calculate(series, tradingRecord);
    }

    public String getStrategyName() {
        return strategyName;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    public Num getTotalProfitLoss() {
        return totalProfitLoss;
    }



    public void printBacktestingResult(boolean printTrades, TimeSeries series) {
        System.out.println("------------ " + strategyName + " ------------");
        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + totalProfit + " Trade count: " + profitTradeCount);
        System.out.println("Total loss: " + totalLoss + " Trade count: " + lossTradeCount);
        System.out.println("Break event trade count: " + breakEvenTradeCount);
        System.out.println("Total profitLoss: " + totalProfitLoss);
        if(printTrades) {
            tradingRecord.getTrades().stream().forEach(trade -> printTrade(trade, series));
            Order lastOrder = tradingRecord.getLastOrder();
            if(lastOrder.getType() == Order.OrderType.BUY) {
                Bar lastOrderBar = series.getBar(lastOrder.getIndex());
                System.out.println("Trade entry: " + lastOrderBar.getSimpleDateName() + ", exit: ?.");
            }
        }
        System.out.println();
    }

    private void printTrade(Trade trade, TimeSeries series) {
        Bar entryBar = series.getBar(trade.getEntry().getIndex());
        Bar exitBar = series.getBar(trade.getExit().getIndex());
        System.out.println("Trade entry: " + entryBar.getSimpleDateName() + ", exit: " + exitBar.getSimpleDateName() + ". Profit: " + trade.getProfit());
    }

    @Override
    public int compareTo(BacktestingResult that) {
        return this.totalProfitLoss.compareTo(that.getTotalProfitLoss());
    }
}
