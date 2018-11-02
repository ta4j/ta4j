package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.ProfitLossCriterion;
import org.ta4j.core.analysis.criteria.ProfitLossPercentageCriterion;
import org.ta4j.core.analysis.criteria.TotalLossCriterion;
import org.ta4j.core.analysis.criteria.TotalProfit2Criterion;
import org.ta4j.core.num.Num;

/**
 * This class generates PerformanceReport basis on provided trading report and time series
 *
 * @see PerformanceReport
 */
public class PerformanceReportGenerator implements ReportGenerator<PerformanceReport> {

    @Override
    public PerformanceReport generate(TradingRecord tradingRecord, TimeSeries series) {
        final Num totalProfitLoss = new ProfitLossCriterion().calculate(series, tradingRecord);
        final Num totalProfitLossPercentage = new ProfitLossPercentageCriterion().calculate(series, tradingRecord);
        final Num totalProfit = new TotalProfit2Criterion().calculate(series, tradingRecord);
        final Num totalLoss = new TotalLossCriterion().calculate(series, tradingRecord);
        return new PerformanceReport(totalProfitLoss, totalProfitLossPercentage, totalProfit, totalLoss);
    }
}
