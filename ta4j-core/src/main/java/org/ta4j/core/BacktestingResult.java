package org.ta4j.core;

import org.ta4j.core.num.Num;


public class BacktestingResult implements Comparable<BacktestingResult>{

    private String strategyName;
    private Strategy strategy;
    private TradingRecord tradingRecord;
    private Num calculation;

    public BacktestingResult(String strategyName, Strategy strategy, TradingRecord tradingRecord, Num calculation) {
        this.strategyName = strategyName;
        this.strategy = strategy;
        this.tradingRecord = tradingRecord;
        this.calculation = calculation;
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

    public void printBacktestingResult(boolean printTrades) {
        tradingRecord.getTrades().stream().forEach(trade -> printTrade(trade));
        System.out.println("------------ " + strategyName + " ------------");
        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + tradingRecord.getTotalProfit() + " Trade count: " + tradingRecord.getProfitTradeCount());
        System.out.println("Total loss: " + tradingRecord.getTotalLoss() + " Trade count: " + tradingRecord.getLossTradeCount());
        System.out.println("Break event trade count: " + tradingRecord.getBreakEvenTradeCount());
        System.out.println("Calculation: " + calculation);
        if(printTrades) {
            tradingRecord.getTrades().stream().forEach(trade -> printTrade(trade));
        }
        System.out.println();
    }

    private static void printTrade(Trade trade) {
        System.out.println("Trade: " + trade.getEntry().getDateTime() + " Profit: " + trade.getProfit());
    }

    @Override
    public int compareTo(BacktestingResult that) {
        return this.calculation.compareTo(that.getCalculation());
    }
}
