/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.NetProfitLossRatioCriterion;
import org.ta4j.core.num.Num;

/**
 * Expectancy criterion (also called "Kelly Criterion").
 *
 * <p>
 * Measures the positive or negative expectancy. The higher the positive number,
 * the better a winning expectation. A negative number means there is only
 * losing expectations.
 *
 * @see <a href=
 *      "https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/">https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/</a>
 *
 */
public class ExpectancyCriterion extends AbstractAnalysisCriterion {

    private final NetProfitLossRatioCriterion profitLossRatioCriterion = new NetProfitLossRatioCriterion();
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
        if (numberOfAllPositions.isZero() || profitLossRatio.isZero()) {
            return series.numFactory().zero();
        }
        // Expectancy = ((1 + AW/AL) * ProbabilityToWinOnePosition) - 1
        Num one = series.numFactory().one();
        Num probabiltyToWinOnePosition = numberOfWinningPositions.dividedBy(numberOfAllPositions);
        return (one.plus(profitLossRatio)).multipliedBy(probabiltyToWinOnePosition).minus(one);
    }

}
