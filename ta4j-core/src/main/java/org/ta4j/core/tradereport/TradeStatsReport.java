package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.NumberOfBreakEvenTradesCriterion;
import org.ta4j.core.analysis.criteria.NumberOfLosingTradesCriterion;
import org.ta4j.core.analysis.criteria.NumberOfWinningTradesCriterion;
import org.ta4j.core.num.Num;

/**
 * This class contains the result of a backtested strategy
 */
public class TradeStatsReport {

    private final Num profitTradeCount;
    private final Num lossTradeCount;
    private final Num breakEvenTradeCount;

    public TradeStatsReport(TradingRecord tradingRecord, TimeSeries series) {
        this.profitTradeCount = new NumberOfWinningTradesCriterion().calculate(series, tradingRecord);
        this.lossTradeCount = new NumberOfLosingTradesCriterion().calculate(series, tradingRecord);
        this.breakEvenTradeCount = new NumberOfBreakEvenTradesCriterion().calculate(series, tradingRecord);
    }

    public Num getProfitTradeCount() {
        return profitTradeCount;
    }

    public Num getLossTradeCount() {
        return lossTradeCount;
    }

    public Num getBreakEvenTradeCount() {
        return breakEvenTradeCount;
    }

}
