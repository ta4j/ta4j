package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.NumberOfBreakEvenTradesCriterion;
import org.ta4j.core.analysis.criteria.NumberOfLosingTradesCriterion;
import org.ta4j.core.analysis.criteria.NumberOfWinningTradesCriterion;
import org.ta4j.core.num.Num;

public class TradeStatsReportBuilder implements ReportBuilder<TradeStatsReport> {

    @Override
    public TradeStatsReport buildReport(TradingRecord tradingRecord, TimeSeries series) {
        final Num profitTradeCount = new NumberOfWinningTradesCriterion().calculate(series, tradingRecord);
        final Num lossTradeCount = new NumberOfLosingTradesCriterion().calculate(series, tradingRecord);
        final Num breakEvenTradeCount = new NumberOfBreakEvenTradesCriterion().calculate(series, tradingRecord);
        return new TradeStatsReport(profitTradeCount, lossTradeCount, breakEvenTradeCount);
    }
}
