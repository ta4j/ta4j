package org.ta4j.core.analysis.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Ratio Profit Loss Criterion = Average gross profit (with commissions) /
 * Average gross loss (with commissions)
 */
public class ProfitLossRatioCriterion extends AbstractAnalysisCriterion {

    private AverageProfitCriterion averageProfitCriterion = new AverageProfitCriterion();
    private AverageLossCriterion averageLossCriterion = new AverageLossCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num averageProfit = averageProfitCriterion.calculate(series, position);
        if (averageProfit.isZero()) {
            // only loosing positions means a ratio of 0
            return series.numOf(0);
        }
        Num averageLoss = averageLossCriterion.calculate(series, position);
        if (averageLoss.isZero()) {
            // only winning positions means a ratio of 1
            return series.numOf(1);
        }
        return averageProfit.dividedBy(averageLoss).abs();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num averageProfit = averageProfitCriterion.calculate(series, tradingRecord);
        if (averageProfit.isZero()) {
            // only loosing positions means a ratio of 0
            return series.numOf(0);
        }
        Num averageLoss = averageLossCriterion.calculate(series, tradingRecord);
        if (averageLoss.isZero()) {
            // only winning positions means a ratio of 1
            return series.numOf(1);
        }
        return averageProfit.dividedBy(averageLoss).abs();
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
