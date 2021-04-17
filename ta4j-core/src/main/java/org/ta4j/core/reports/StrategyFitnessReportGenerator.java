package org.ta4j.core.reports;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.ExpectancyCriterion;
import org.ta4j.core.analysis.criteria.RewardToRiskCriterion;
import org.ta4j.core.num.Num;

public class StrategyFitnessReportGenerator implements ReportGenerator<StrategyFitnessReport> {

    @Override
    public StrategyFitnessReport generate(Strategy strategy, TradingRecord tradingRecord, BarSeries series) {
        final Num rrRatio = new RewardToRiskCriterion().calculate(series, tradingRecord);
        final Num expectancy = new ExpectancyCriterion().calculate(series, tradingRecord);

        return new StrategyFitnessReport(rrRatio, expectancy);
    }
}
