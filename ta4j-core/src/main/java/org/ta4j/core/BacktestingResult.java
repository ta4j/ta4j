package org.ta4j.core;

import org.ta4j.core.num.Num;


public class BacktestingResult implements Comparable<BacktestingResult>{

    private TradingRecord tradingRecord;
    private Num calculation;

    public BacktestingResult(TradingRecord tradingRecord, Num calculation) {
        this.tradingRecord = tradingRecord;
        this.calculation = calculation;
    }

    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    public Num getCalculation() {
        return calculation;
    }

    public void printBacktestingResult() {
        //tradingRecord.getTrades().stream().forEach(trade -> createTradeReport(trade));
        System.out.println("----------------------------");
        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + tradingRecord.getTotalProfit() + " Trade count: " + tradingRecord.getProfitTradeCount());
        System.out.println("Total loss: " + tradingRecord.getTotalLoss() + " Trade count: " + tradingRecord.getLossTradeCount());
        System.out.println("Break event trade count: " + tradingRecord.getBreakEvenTradeCount());
        System.out.println("Calculation: " + calculation);
        System.out.println();
    }

    private static void createTradeReport(Trade trade) {
        System.out.println("Profit: " + trade.getProfit());
    }

    @Override
    public int compareTo(BacktestingResult that) {
        return this.calculation.compareTo(that.getCalculation());
    }
}
