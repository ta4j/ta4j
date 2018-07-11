package org.ta4j.core;

import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

public class Backtesting {

    private TimeSeries series;
    private AbstractAnalysisCriterion criterion;
    private TimeSeriesManager seriesManager;
    private TradingRecord tradingRecord;

    public Backtesting(TimeSeries series, AbstractAnalysisCriterion criterion) {
        this.series = series;
        this.criterion = criterion;
        this.seriesManager = new TimeSeriesManager(series);
    }

    public Num calculate(Rule entryRule, Rule exitRule, Num amount) {
        Strategy strategy = new BaseStrategy(
                entryRule,
                exitRule
        );
        tradingRecord = seriesManager.run(strategy, Order.OrderType.BUY, amount);
        return criterion.calculate(series, tradingRecord);
    }

    public void createTradingRecordReport() {
        //tradingRecord.getTrades().stream().forEach(trade -> createTradeReport(trade));

        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + tradingRecord.getTotalProfit() + " Trade count: " + tradingRecord.getProfitTradeCount());
        System.out.println("Total loss: " + tradingRecord.getTotalLoss() + " Trade count: " + tradingRecord.getLossTradeCount());
        System.out.println("Break event trade count: " + tradingRecord.getBreakEvenTradeCount());
    }
}
