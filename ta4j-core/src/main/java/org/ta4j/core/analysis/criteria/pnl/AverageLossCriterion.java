package org.ta4j.core.analysis.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.analysis.criteria.NumberOfLosingPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * Average gross loss (with commissions).
 */
public class AverageLossCriterion extends AbstractAnalysisCriterion {

    private NumberOfLosingPositionsCriterion numberOfLosingPositionsCriterion = new NumberOfLosingPositionsCriterion();
    private GrossLossCriterion grossLossCriterion = new GrossLossCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfLosingTrades = numberOfLosingPositionsCriterion.calculate(series, position);
        if (numberOfLosingTrades.isZero()) {
            return series.numOf(0);
        }
        Num grossLoss = grossLossCriterion.calculate(series, position);
        if (grossLoss.isZero()) {
            return series.numOf(0);
        }
        return grossLoss.dividedBy(numberOfLosingTrades);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num numberOfLosingTrades = numberOfLosingPositionsCriterion.calculate(series, tradingRecord);
        if (numberOfLosingTrades.isZero()) {
            return series.numOf(0);
        }
        Num grossLoss = grossLossCriterion.calculate(series, tradingRecord);
        if (grossLoss.isZero()) {
            return series.numOf(0);
        }
        return grossLoss.dividedBy(numberOfLosingTrades);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
