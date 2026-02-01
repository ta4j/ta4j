/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class GrossProfitLossPercentageCriterionTest extends AbstractPnlCriterionTest {

    public GrossProfitLossPercentageCriterionTest(NumFactory numFactory) {
        super(params -> new GrossProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(0.125, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(-0.175, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(0.075, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(0.125, result);
    }

    @Override
    protected void handleCalculateOnlyWithLossPositions(Num result) {
        assertNumEquals(-0.175, result);
    }

    @Override
    protected void handleCalculateProfitWithShortPositions(Num result) {
        assertNumEquals(-0.2121212121, result);
    }

    @Override
    protected void handleBetterThan(AnalysisCriterion criterion) {
        assertTrue(criterion.betterThan(numOf(0.05), numOf(0.03)));
        assertFalse(criterion.betterThan(numOf(0.03), numOf(0.05)));
    }

    @Override
    protected void handleCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }

    @Override
    protected void handleCalculateWithOpenedPosition(Num result) {
        assertNumEquals(0.1, result);
    }

    @Override
    protected void handleCalculateWithNoPositions(Num result) {
        assertNumEquals(0, result);
    }
}
