/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.criteria.helpers;

import java.util.function.Function;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.criteria.pnl.ProfitLossCriterion;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class StandardErrorCriterionTest extends AbstractCriterionTest {

    public StandardErrorCriterionTest(Function<Number, Num> numFunction) {
        super(params -> params.length == 2
                ? new StandardErrorCriterion((AnalysisCriterion) params[0], (boolean) params[1])
                : new StandardErrorCriterion((AnalysisCriterion) params[0]), numFunction);
    }

    @Test
    public void calculateStandardErrorPnL() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series, series.one()),
                Trade.sellAt(2, series, series.one()), Trade.buyAt(3, series, series.one()),
                Trade.sellAt(5, series, series.one()));

        AnalysisCriterion criterion = getCriterion(new ProfitLossCriterion());
        assertNumEquals(1.7677669529663687, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void betterThanWithLessIsBetter() {
        AnalysisCriterion criterion = getCriterion(new ProfitLossCriterion());
        assertFalse(criterion.betterThan(numOf(5000), numOf(4500)));
        assertTrue(criterion.betterThan(numOf(4500), numOf(5000)));
    }

    @Test
    public void betterThanWithLessIsNotBetter() {
        AnalysisCriterion criterion = getCriterion(new ProfitLossCriterion(), false);
        assertTrue(criterion.betterThan(numOf(5000), numOf(4500)));
        assertFalse(criterion.betterThan(numOf(4500), numOf(5000)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(new ProfitLossCriterion()), 0);
    }

}
