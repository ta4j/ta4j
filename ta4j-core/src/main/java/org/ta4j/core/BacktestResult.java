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

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    public void setTradingRecord(TradingRecord tradingRecord) {
        this.tradingRecord = tradingRecord;
    }

    public Num getTotalProfitLoss() {
        return totalProfitLoss;
    }

    public void setTotalProfitLoss(Num totalProfitLoss) {
        this.totalProfitLoss = totalProfitLoss;
    }

    public Num getTotalProfitLossPercentage() {
        return totalProfitLossPercentage;
    }

    public void setTotalProfitLossPercentage(Num totalProfitLossPercentage) {
        this.totalProfitLossPercentage = totalProfitLossPercentage;
    }

    public Num getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(Num totalProfit) {
        this.totalProfit = totalProfit;
    }

    public Num getTotalLoss() {
        return totalLoss;
    }

    public void setTotalLoss(Num totalLoss) {
        this.totalLoss = totalLoss;
    }

    public Num getProfitTradeCount() {
        return profitTradeCount;
    }

    public void setProfitTradeCount(Num profitTradeCount) {
        this.profitTradeCount = profitTradeCount;
    }

    public Num getLossTradeCount() {
        return lossTradeCount;
    }

    public void setLossTradeCount(Num lossTradeCount) {
        this.lossTradeCount = lossTradeCount;
    }

    public Num getBreakEvenTradeCount() {
        return breakEvenTradeCount;
    }

    public void setBreakEvenTradeCount(Num breakEvenTradeCount) {
        this.breakEvenTradeCount = breakEvenTradeCount;
    }

    @Override
    public int compareTo(BacktestResult that) {
        return this.totalProfitLossPercentage.compareTo(that.getTotalProfitLossPercentage());
    }
}
