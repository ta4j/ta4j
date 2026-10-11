/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.ta4j.core.TestUtils.assertNumEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class GrossAverageLossCriterionTest extends AbstractPnlCriterionTest {

    public GrossAverageLossCriterionTest(NumFactory numFactory) {
        super(params -> new GrossAverageLossCriterion(), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(-17.5, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateOnlyWithLossPositions(Num result) {
        assertNumEquals(-17.5, result);
    }

    @Override
    protected void handleCalculateProfitWithShortPositions(Num result) {
        assertNumEquals(-17.5, result);
    }

    @Override
    protected void handleBetterThan(AnalysisCriterion criterion) {
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Override
    protected void handleCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }

    @Override
    protected void handleCalculateWithOpenedPosition(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateWithNoPositions(Num result) {
        assertNumEquals(0, result);
    }
}
