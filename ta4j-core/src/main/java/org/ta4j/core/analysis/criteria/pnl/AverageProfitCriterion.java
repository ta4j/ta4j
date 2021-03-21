package org.ta4j.core.analysis.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.analysis.criteria.NumberOfWinningPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * Average gross profit (with commissions).
 */
public class AverageProfitCriterion extends AbstractAnalysisCriterion {

    private NumberOfWinningPositionsCriterion numberOfWinningPositionsCriterion = new NumberOfWinningPositionsCriterion();
    private GrossProfitCriterion grossProfitCriterion = new GrossProfitCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfWinningTrades = numberOfWinningPositionsCriterion.calculate(series, position);
        if (numberOfWinningTrades.isZero()) {
            return series.numOf(0);
        }
        Num grossProfit = grossProfitCriterion.calculate(series, position);
        if (grossProfit.isZero()) {
            return series.numOf(0);
        }
        return grossProfit.dividedBy(numberOfWinningTrades);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num numberOfWinningTrades = numberOfWinningPositionsCriterion.calculate(series, tradingRecord);
        if (numberOfWinningTrades.isZero()) {
            return series.numOf(0);
        }
        Num grossProfit = grossProfitCriterion.calculate(series, tradingRecord);
        if (grossProfit.isZero()) {
            return series.numOf(0);
        }
        return grossProfit.dividedBy(numberOfWinningTrades);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
