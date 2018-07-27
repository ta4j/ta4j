package org.ta4j.core;

import org.ta4j.core.num.Num;


public class BacktestingResult implements Comparable<BacktestingResult>{

    private String strategyName;
    private Strategy strategy;
    private TradingRecord tradingRecord;
    private Num calculation;
    private Num totalProfit;
    private long profitTradeCount;
    private Num totalLoss;
    private long lossTradeCount;
    private long breakEvenTradeCount;


    public BacktestingResult(String strategyName, Strategy strategy, TradingRecord tradingRecord, Num calculation, Num totalProfit, long profitTradeCount, Num totalLoss, long lossTradeCount, long breakEvenTradeCount) {
        this.strategyName = strategyName;
        this.strategy = strategy;
        this.tradingRecord = tradingRecord;
        this.calculation = calculation;
        this.totalProfit = totalProfit;
        this.profitTradeCount = profitTradeCount;
        this.totalLoss = totalLoss;
        this.lossTradeCount = lossTradeCount;
        this.breakEvenTradeCount = breakEvenTradeCount;
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

    public Num getCalculation() {
        return calculation;
    }

    public void printBacktestingResult(boolean printTrades, TimeSeries series) {
        System.out.println("------------ " + strategyName + " ------------");
        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + totalProfit + " Trade count: " + profitTradeCount);
        System.out.println("Total loss: " + totalLoss + " Trade count: " + lossTradeCount);
        System.out.println("Break event trade count: " + breakEvenTradeCount);
        System.out.println("Calculation: " + calculation);
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
        return this.calculation.compareTo(that.getCalculation());
    }
}
