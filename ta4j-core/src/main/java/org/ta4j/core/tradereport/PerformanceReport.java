package org.ta4j.core.tradereport;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.ProfitLossCriterion;
import org.ta4j.core.analysis.criteria.ProfitLossPercentageCriterion;
import org.ta4j.core.analysis.criteria.TotalLossCriterion;
import org.ta4j.core.analysis.criteria.TotalProfit2Criterion;
import org.ta4j.core.num.Num;

public class PerformanceReport {

    private final Num totalProfitLoss;
    private final Num totalProfitLossPercentage;
    private final Num totalProfit;
    private final Num totalLoss;

    public PerformanceReport(TradingRecord tradingRecord, TimeSeries series) {
        this.totalProfitLoss = new ProfitLossCriterion().calculate(series, tradingRecord);
        this.totalProfitLossPercentage = new ProfitLossPercentageCriterion().calculate(series, tradingRecord);
        this.totalProfit = new TotalProfit2Criterion().calculate(series, tradingRecord);
        this.totalLoss = new TotalLossCriterion().calculate(series, tradingRecord);
    }

    public Num getTotalProfitLoss() {
        return totalProfitLoss;
    }

    public Num getTotalProfitLossPercentage() {
        return totalProfitLossPercentage;
    }

    public Num getTotalProfit() {
        return totalProfit;
    }

    public Num getTotalLoss() {
        return totalLoss;
    }

}
