package org.ta4j.core;

import org.ta4j.core.analysis.criteria.*;
import org.ta4j.core.num.Num;

/**
 * This class contains the result of a backtested strategy
 */
public class BacktestResult implements Comparable<BacktestResult> {

    private Strategy strategy;
    private TradingRecord tradingRecord;
    private Num totalProfitLoss;
    private Num totalProfitLossPercentage;
    private Num totalProfit;
    private Num totalLoss;
    private Num profitTradeCount;
    private Num lossTradeCount;
    private Num breakEvenTradeCount;

    public BacktestResult(Strategy strategy, TradingRecord tradingRecord, TimeSeries series) {
        this.strategy = strategy;
        this.tradingRecord = tradingRecord;
        this.totalProfitLoss = new ProfitLossCriterion().calculate(series, tradingRecord);
        this.totalProfitLossPercentage = new ProfitLossPercentageCriterion().calculate(series, tradingRecord);
        this.totalProfit = new TotalProfit2Criterion().calculate(series, tradingRecord);
        this.totalLoss = new TotalLossCriterion().calculate(series, tradingRecord);
        this.profitTradeCount = new NumberOfWinningTradesCriterion().calculate(series, tradingRecord);
        this.lossTradeCount = new NumberOfLosingTradesCriterion().calculate(series, tradingRecord);
        this.breakEvenTradeCount = new NumberOfBreakEvenTradesCriterion().calculate(series, tradingRecord);
    }

    public String getStrategyName() {
        return strategy.getName();
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

    public Num getTotalProfitLossPercentage() {
        return totalProfitLossPercentage;
    }

    /**
     * Print to console the backtesting result
     *
     * @param printTrades - Print trades from the backtesting result
     * @param series      - The time series
     */
    public void printBacktestingResult(boolean printTrades, TimeSeries series) {
        System.out.println("------------ " + getStrategyName() + " ------------");
        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + totalProfit + " Trade count: " + profitTradeCount);
        System.out.println("Total loss: " + totalLoss + " Trade count: " + lossTradeCount);
        System.out.println("Break event trade count: " + breakEvenTradeCount);
        System.out.println("Total profitLoss: " + totalProfitLoss);
        System.out.println("Total profitLoss: " + round(totalProfitLossPercentage, 1) + "%");
        if (printTrades) {
            tradingRecord.getTrades().forEach(trade -> printTrade(trade, series));
            Order lastOrder = tradingRecord.getLastOrder();
            if (lastOrder.getType() == Order.OrderType.BUY) {
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
    public int compareTo(BacktestResult that) {
        return this.totalProfitLossPercentage.compareTo(that.getTotalProfitLossPercentage());
    }

    private static double round(Num value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value.doubleValue() * scale) / scale;
    }
}
