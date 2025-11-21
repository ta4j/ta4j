/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria.pnl;

import static org.ta4j.core.TestUtils.assertNumEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NetProfitLossPercentageCriterionTest extends AbstractPnlCriterionTest {

    public NetProfitLossPercentageCriterionTest(NumFactory numFactory) {
        super(params -> new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(0.105, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(-0.19325, result);
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
