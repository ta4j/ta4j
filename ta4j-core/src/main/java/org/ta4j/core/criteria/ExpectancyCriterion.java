/**
 * This copy of Woodstox XML processor is licensed under the
 * Apache (Software) License, version 2.0 ("the License").
 * See the License for details about distribution rights, and the
 * specific rights regarding derivate works.
 *
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code package
 * containing Woodstox, in file "ASL2.0", under the same directory
 * as this file.
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.ProfitLossRatioCriterion;
import org.ta4j.core.num.Num;

/**
 * Expectancy criterion (Kelly Criterion).
 *
 * Measures the positive or negative expectancy. The higher the positive number,
 * the better a winning expectation. A negative number means there is only
 * losing expectations.
 *
 * @see <a href=
 *      "https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/">https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/</a>
 * 
 */
public class ExpectancyCriterion extends AbstractAnalysisCriterion {

    private final ProfitLossRatioCriterion profitLossRatioCriterion = new ProfitLossRatioCriterion();
    private final NumberOfPositionsCriterion numberOfPositionsCriterion = new NumberOfPositionsCriterion();
    private final NumberOfWinningPositionsCriterion numberOfWinningPositionsCriterion = new NumberOfWinningPositionsCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num profitLossRatio = profitLossRatioCriterion.calculate(series, position);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        Num numberOfWinningPositions = numberOfWinningPositionsCriterion.calculate(series, position);
        return calculate(series, profitLossRatio, numberOfWinningPositions, numberOfPositions);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num profitLossRatio = profitLossRatioCriterion.calculate(series, tradingRecord);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        Num numberOfWinningPositions = numberOfWinningPositionsCriterion.calculate(series, tradingRecord);
        return calculate(series, profitLossRatio, numberOfWinningPositions, numberOfPositions);
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    private Num calculate(BarSeries series, Num profitLossRatio, Num numberOfWinningPositions,
            Num numberOfAllPositions) {
        Num one = series.one();
        if (numberOfAllPositions.isZero() || profitLossRatio.isZero()) {
            return series.zero();
        }
        // Expectancy = ((1 + AW/AL) * ProbabilityToWinOnePosition) - 1
        Num probabiltyToWinOnePosition = numberOfWinningPositions.dividedBy(numberOfAllPositions);
        return (one.plus(profitLossRatio)).multipliedBy(probabiltyToWinOnePosition).minus(one);
    }

}
