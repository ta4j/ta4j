package org.ta4j.core.tradereport;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.NumberOfBreakEvenTradesCriterion;
import org.ta4j.core.analysis.criteria.NumberOfLosingTradesCriterion;
import org.ta4j.core.analysis.criteria.NumberOfWinningTradesCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.BarSeries;

/**
 * This class generates TradeStatsReport basis on provided trading report and bar series
 *
 * @see TradeStatsReport
 */
public class TradeStatsReportGenerator implements ReportGenerator<TradeStatsReport> {

    @Override
    public TradeStatsReport generate(TradingRecord tradingRecord, BarSeries series) {
        final Num profitTradeCount = new NumberOfWinningTradesCriterion().calculate(series, tradingRecord);
        final Num lossTradeCount = new NumberOfLosingTradesCriterion().calculate(series, tradingRecord);
        final Num breakEvenTradeCount = new NumberOfBreakEvenTradesCriterion().calculate(series, tradingRecord);
        return new TradeStatsReport(profitTradeCount, lossTradeCount, breakEvenTradeCount);
    }
}
