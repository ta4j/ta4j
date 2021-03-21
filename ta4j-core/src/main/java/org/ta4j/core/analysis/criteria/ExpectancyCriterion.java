package org.ta4j.core.analysis.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.pnl.ProfitLossRatioCriterion;
import org.ta4j.core.num.Num;

/**
 * Expectancy criterion (Kelly Criterion).
 *
 * Measures the positive or negative expectancy. The higher the positive number,
 * the better a winning expectation. A negative number means there is only
 * losing expectations.
 *
 * @see <a href=
 *      "https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/</a>
 *
 */
public class ExpectancyCriterion extends AbstractAnalysisCriterion {

    private ProfitLossRatioCriterion profitLossRatioCriterion = new ProfitLossRatioCriterion();
    private NumberOfPositionsCriterion numberOfPositionsCriterion = new NumberOfPositionsCriterion();
    private NumberOfWinningPositionsCriterion numberOfWinningPositionsCriterion = new NumberOfWinningPositionsCriterion();

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num profitLossRatio = profitLossRatioCriterion.calculate(series, tradingRecord);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        Num numberOfWinningPositions = numberOfWinningPositionsCriterion.calculate(series, tradingRecord);
        return calculate(series, profitLossRatio, numberOfWinningPositions, numberOfPositions);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num profitLossRatio = profitLossRatioCriterion.calculate(series, position);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        Num numberOfWinningPositions = numberOfWinningPositionsCriterion.calculate(series, position);
        return calculate(series, profitLossRatio, numberOfWinningPositions, numberOfPositions);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    private Num calculate(BarSeries series, Num profitLossRatio, Num numberOfWinningPositions,
            Num numberOfAllPositions) {
        Num one = series.numOf(1);
        if (numberOfAllPositions.isZero() || profitLossRatio.isZero()) {
            return series.numOf(0);
        }
        // Expectancy = (1 + AW/AL) * (ProbabilityToWinOnePosition - 1)
        Num probabiltyToWinOnePosition = numberOfWinningPositions.dividedBy(numberOfAllPositions);
        return (one.plus(profitLossRatio)).multipliedBy((probabiltyToWinOnePosition).minus(one));
    }

}
